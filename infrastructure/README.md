# Local Infrastructure Setup

Local 개발 환경을 위한 인프라 구성 (PostgreSQL, Kafka, Debezium, Kafka UI)

## 서비스 구성

| Service | Port  | Description |
|---------|-------|-------------|
| **PostgreSQL** | 5432  | 메인 데이터베이스 (CDC 활성화) |
| **Kafka** | 9092  | 이벤트 스트리밍 플랫폼 (KRaft 모드) |
| **Debezium Connect** | 18083 | CDC 커넥터 (PostgreSQL → Kafka) |
| **Kafka UI** | 18080 | Kafka 관리 웹 인터페이스 |

## Quick Start

### 1. 인프라 시작

```bash
cd infrastructure
docker-compose up -d
```

모든 서비스가 헬스체크를 통과할 때까지 대기 (~30초)

### 2. Debezium Connector 등록

PostgreSQL CDC를 활성화하고 Kafka 토픽을 생성하기 위해 connector를 등록합니다:

```bash
./setup-connectors.sh
```

**등록되는 Connector**:
- `lit-articles-connector`: article 테이블 CDC → `lit.public.article` 토픽
- `lit-analysis-connector`: analysis_result_outbox 테이블 CDC → `lit.public.analysis_result_outbox` 토픽

### 3. 확인

**Kafka UI 접속**: http://localhost:18080

**Kafka 토픽 확인**:
```bash
docker exec lit-kafka kafka-topics --bootstrap-server localhost:9092 --list
```

**Debezium Connector 상태 확인**:
```bash
curl http://localhost:18083/connectors/lit-articles-connector/status | jq
curl http://localhost:18083/connectors/lit-analysis-connector/status | jq
```

## 데이터 플로우

```
PostgreSQL (article 테이블)
  ↓ (INSERT/UPDATE)
Debezium CDC
  ↓
Kafka Topic: lit.public.article
  ↓
analyzer 서비스 소비

PostgreSQL (analysis_result_outbox 테이블)
  ↓ (INSERT - Outbox Pattern)
Debezium CDC
  ↓
Kafka Topic: lit.public.analysis_result_outbox
  ↓
indexer 서비스 소비
```

## Kafka 토픽 구조

### lit.public.article
- **Source**: collector 서비스가 article 테이블에 INSERT
- **Consumer**: analyzer 서비스
- **Payload**: Article 엔티티 (articleId, title, content, sourceId, originId, writtenAt, modifiedAt, sourceUrl)

### lit.public.analysis_result_outbox
- **Source**: analyzer 서비스가 analysis_result_outbox 테이블에 INSERT (Outbox Pattern)
- **Consumer**: indexer 서비스
- **Payload**: AnalysisResult 엔티티 (articleId, incidentTypes, urgency, keywords, locations)

## 관리 및 모니터링

### Kafka UI (권장)
- URL: http://localhost:18080
- 기능: 토픽 조회, 메시지 확인, 커넥터 관리, 컨슈머 그룹 모니터링

### CLI 도구

**토픽 목록 조회**:
```bash
docker exec lit-kafka kafka-topics --bootstrap-server localhost:9092 --list
```

**토픽 상세 정보**:
```bash
docker exec lit-kafka kafka-topics --bootstrap-server localhost:9092 --describe --topic lit.public.article
```

**메시지 소비 (실시간 모니터링)**:
```bash
docker exec lit-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic lit.public.article \
  --from-beginning
```

**Connector 목록 조회**:
```bash
curl http://localhost:18083/connectors | jq
```

**Connector 삭제** (재등록 필요 시):
```bash
curl -X DELETE http://localhost:18083/connectors/lit-articles-connector
curl -X DELETE http://localhost:18083/connectors/lit-analysis-connector
```

## PostgreSQL Publication & Replication Slot

### 개념

Debezium이 PostgreSQL의 변경사항을 실시간으로 캡처(CDC)하기 위해 PostgreSQL의 논리적 복제(Logical Replication) 기능을 활용합니다. 이 과정에서 **Publication**과 **Replication Slot** 두 가지 PostgreSQL 오브젝트가 핵심 역할을 합니다.

#### Publication

PostgreSQL에서 어떤 테이블의 변경사항을 외부에 공개할지 정의하는 오브젝트입니다. 신문의 "구독 목록"에 해당합니다.

- Debezium connector를 등록하면, `publication.autocreate.mode: filtered` 설정에 따라 `table.include.list`에 지정한 테이블만 포함하는 publication을 자동 생성합니다.
- 각 connector는 고유한 publication name을 가져야 합니다. 같은 이름을 사용하면 테이블 필터가 충돌하여 connector가 실패합니다.

**현재 구성**:

| Connector | Publication Name | 감시 테이블 |
|-----------|-----------------|-------------|
| `lit-articles-connector` | `dbz_articles_pub` | `public.article` |
| `lit-analysis-connector` | `dbz_analysis_pub` | `public.analysis_result_outbox` |

#### Replication Slot

PostgreSQL이 소비자(Debezium)가 어디까지 변경사항을 읽었는지 추적하는 북마크입니다. PostgreSQL은 slot에 기록된 위치 이후의 WAL(Write-Ahead Log)을 유지하여 소비자가 재시작해도 놓친 변경사항을 다시 읽을 수 있도록 보장합니다.

- 각 connector는 `slot.name`으로 지정한 고유한 replication slot을 생성합니다.
- Slot이 존재하는 동안 PostgreSQL은 해당 위치 이후의 WAL을 삭제하지 않습니다.

**현재 구성**:

| Connector | Slot Name | Plugin |
|-----------|-----------|--------|
| `lit-articles-connector` | `debezium_articles` | `pgoutput` |
| `lit-analysis-connector` | `debezium_analysis` | `pgoutput` |

### Debezium과의 관계

Debezium connector가 시작되면 다음과 같은 순서로 PostgreSQL 오브젝트를 활용합니다:

```
Connector 시작
  ↓
1. Publication 생성/확인 (테이블 필터 적용)
  ↓
2. Replication Slot 생성/확인 (WAL 위치 추적)
  ↓
3. 초기 스냅샷 수행 (최초 실행 시)
  ↓
4. WAL 스트리밍 시작 (실시간 변경사항 캡처)
  ↓
5. Kafka 토픽으로 이벤트 발행
```

Debezium은 Kafka의 `debezium_connect_offsets` 토픽에도 현재 읽고 있는 WAL 위치(LSN)를 저장합니다. 따라서 **PostgreSQL의 replication slot**과 **Kafka의 offset 토픽** 양쪽에 위치 정보가 존재하며, 이 둘이 일관성을 유지해야 정상 동작합니다.

### 상태 확인

**Publication 목록 조회**:
```bash
docker exec lit-maindb psql -U postgres -d lit_maindb -c "SELECT * FROM pg_publication;"
```

**Publication에 포함된 테이블 조회**:
```bash
docker exec lit-maindb psql -U postgres -d lit_maindb -c "SELECT * FROM pg_publication_tables;"
```

**Replication Slot 상태 조회**:
```bash
docker exec lit-maindb psql -U postgres -d lit_maindb -c "SELECT slot_name, plugin, slot_type, active FROM pg_replication_slots;"
```

### 전체 리셋 절차

Connector 설정을 변경하거나, CDC 파이프라인에 문제가 생겨 처음부터 다시 구성해야 할 때 아래 순서로 리셋합니다.

**리셋이 필요한 경우**:
- Connector 설정 변경 (테이블명, publication name 등)
- `No table filters found for filtered publication` 에러 발생
- `change stream starting at ... is no longer available` 에러 발생
- PostgreSQL과 Kafka 간의 offset 불일치

**리셋 순서와 이유**:

```bash
# 1. Connector 삭제 (Debezium이 PostgreSQL 연결을 해제하도록)
curl -X DELETE http://localhost:18083/connectors/lit-articles-connector
curl -X DELETE http://localhost:18083/connectors/lit-analysis-connector

# 2. PostgreSQL Publication 삭제 (connector가 새로운 설정으로 재생성할 수 있도록)
docker exec lit-maindb psql -U postgres -d lit_maindb -c "DROP PUBLICATION IF EXISTS dbz_articles_pub;"
docker exec lit-maindb psql -U postgres -d lit_maindb -c "DROP PUBLICATION IF EXISTS dbz_analysis_pub;"

# 3. PostgreSQL Replication Slot 삭제 (slot이 active 상태면 삭제 불가 → 1번에서 connector 먼저 삭제하는 이유)
docker exec lit-maindb psql -U postgres -d lit_maindb -c "SELECT pg_drop_replication_slot('debezium_articles');"
docker exec lit-maindb psql -U postgres -d lit_maindb -c "SELECT pg_drop_replication_slot('debezium_analysis');"

# 4. Debezium Connect 중지 (Kafka 토픽 삭제 전에 중지해야 충돌 방지)
docker-compose stop debezium-connect

# 5. Kafka의 Debezium 내부 토픽 삭제 (이전 offset/config 정보 제거 → 새로 시작할 수 있도록)
docker exec lit-kafka kafka-topics --bootstrap-server localhost:9092 --delete --topic debezium_connect_offsets
docker exec lit-kafka kafka-topics --bootstrap-server localhost:9092 --delete --topic debezium_connect_configs
docker exec lit-kafka kafka-topics --bootstrap-server localhost:9092 --delete --topic debezium_connect_statuses
docker exec lit-kafka kafka-topics --bootstrap-server localhost:9092 --delete --topic __debezium-heartbeat.lit

# 6. CDC 데이터 토픽도 삭제 (필요 시, 기존 이벤트 데이터를 버리고 스냅샷부터 다시 시작하려는 경우)
docker exec lit-kafka kafka-topics --bootstrap-server localhost:9092 --delete --topic lit.public.article
docker exec lit-kafka kafka-topics --bootstrap-server localhost:9092 --delete --topic lit.public.analysis_result_outbox

# 7. 삭제 확인
docker exec lit-kafka kafka-topics --bootstrap-server localhost:9092 --list

# 8. Debezium Connect 재시작
docker-compose up -d debezium-connect

# 9. Debezium이 준비될 때까지 대기 후 Connector 재등록
./setup-connectors.sh
```

**중요**: 순서를 지키지 않으면 다음 문제가 발생합니다:
- Connector 삭제 전 slot 삭제 시도 → `replication slot is active` 에러
- Kafka offset 토픽을 남긴 채 slot만 삭제 → `change stream is no longer available` 에러 (Kafka에 저장된 LSN 위치의 WAL이 이미 삭제됨)
- Debezium 실행 중 내부 토픽 삭제 → Debezium이 크래시 루프에 빠짐

## 문제 해결

### Connector Task FAILED: "No table filters found for filtered publication"

**원인**: Publication에 지정된 테이블이 PostgreSQL에 존재하지 않거나, 여러 connector가 같은 publication name을 공유하여 필터가 충돌

**해결**:
1. 테이블명이 정확한지 확인 (`public.article`, `public.analysis_result_outbox`)
2. 각 connector가 고유한 `publication.name`을 사용하는지 확인
3. 위의 전체 리셋 절차 수행

### Connector Task FAILED: "change stream is no longer available"

**원인**: Kafka의 offset 토픽에 저장된 WAL 위치(LSN)가 PostgreSQL에서 이미 삭제된 상태. Replication slot을 삭제했지만 Kafka offset은 남아있을 때 발생

**해결**: 위의 전체 리셋 절차 수행 (PostgreSQL과 Kafka 양쪽 모두 정리해야 함)

### Connector 등록 실패

**증상**: setup-connectors.sh 실행 시 에러

**해결**:
1. 모든 서비스가 실행 중인지 확인:
   ```bash
   docker-compose ps
   ```

2. Debezium Connect 로그 확인:
   ```bash
   docker logs lit-debezium
   ```

3. PostgreSQL WAL 설정 확인:
   ```bash
   docker exec lit-maindb psql -U postgres -d lit_maindb -c "SHOW wal_level;"
   ```
   → `logical`이어야 함

### Kafka 연결 실패

**증상**: 서비스에서 Kafka 연결 불가

**해결**:
1. Kafka 헬스체크 확인:
   ```bash
   docker exec lit-kafka kafka-broker-api-versions --bootstrap-server localhost:9092
   ```

2. Docker 네트워크 확인:
   ```bash
   docker network inspect infrastructure_lit_network
   ```

3. 서비스에서 사용하는 Kafka 주소:
   - **외부 (호스트)**: `localhost:9092`
   - **내부 (Docker 네트워크)**: `kafka:29092`

### 토픽이 생성되지 않음

**증상**: Connector가 RUNNING인데 Kafka에 데이터 토픽이 없음

**원인**: CDC 토픽은 해당 테이블에 변경(INSERT/UPDATE/DELETE)이 발생해야 생성됨

**해결**:
1. Connector가 정상 등록되었는지 확인:
   ```bash
   curl http://localhost:18083/connectors | jq
   ```

2. PostgreSQL 테이블에 테스트 데이터 삽입:
   ```sql
   INSERT INTO article (article_id, origin_id, source_id, title, content, written_at, modified_at)
   VALUES ('test-001', 'origin-001', 'source-001', 'Test Article', 'Test Content', NOW(), NOW());
   ```

## 초기화 및 재시작

### 전체 초기화 (볼륨 삭제)
```bash
docker-compose down -v
docker-compose up -d
./setup-connectors.sh
```

### 서비스 재시작 (데이터 유지)
```bash
docker-compose restart
```

### 특정 서비스만 재시작
```bash
docker-compose restart kafka
docker-compose restart debezium-connect
```

## 기술 스택

- **PostgreSQL**: 18-alpine
- **Kafka**: 7.8.0 (KRaft 모드, Confluent Platform)
- **Debezium**: 3.4
- **Kafka UI**: kafbat/kafka-ui v1.4.2
