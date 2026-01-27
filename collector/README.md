# Collector Service

## 환경변수 설정

### 1. 로컬 환경 파일 생성

템플릿 파일을 복사하여 로컬 설정 파일을 만듭니다:

```bash
cp .env.local.example .env.local
```

### 2. 필수 환경변수 설정

`.env.local` 파일을 열어 실제 값으로 수정합니다:

```bash
# 데이터베이스 설정
DB_URL=jdbc:postgresql://localhost:5432/lit_maindb
DB_USERNAME=postgres
DB_PASSWORD=postgres

# Safety Data API 설정 (필수)
SAFETY_DATA_API_KEY=실제-발급받은-API-키

# 서버 설정
SERVER_PORT=8081
```

### 3. 환경변수 로드

#### 방법 A: 파일에서 환경변수 로드 후 실행

```bash
set -a && source .env.local && set +a
./gradlew collector:bootRun
```

#### 방법 B: IntelliJ IDEA 사용

1. Run/Debug Configurations → Edit Configurations
2. Environment variables → Load from file
3. `.env.local` 파일 선택

#### 방법 C: 수동으로 export

```bash
export DB_URL=jdbc:postgresql://localhost:5432/lit_maindb
export DB_USERNAME=postgres
export DB_PASSWORD=postgres
export SAFETY_DATA_API_KEY=실제-발급받은-API-키
export SERVER_PORT=8081

./gradlew collector:bootRun
```

### 4. 설정 확인

환경변수가 설정되지 않은 경우 다음 기본값이 사용됩니다:

| 환경변수 | 기본값 | 필수 여부 |
|----------|--------|-----------|
| `DB_URL` | `jdbc:postgresql://localhost:5432/lit_maindb` | 아니오 |
| `DB_USERNAME` | `postgres` | 아니오 |
| `DB_PASSWORD` | `postgres` | 아니오 |
| `SAFETY_DATA_API_KEY` | (없음) | **예** |
| `SERVER_PORT` | `8081` | 아니오 |

**주의**: `SAFETY_DATA_API_KEY`는 Safety Data API에서 기사를 수집하기 위해 반드시 설정되어야 합니다.

## 보안 주의사항

- `.env.local` 파일은 gitignore에 포함되어 있으며 **절대 커밋하면 안 됩니다**
- 팀원들에게는 `.env.local.example` 파일을 템플릿으로 공유하세요
- 프로덕션 환경에서는 적절한 비밀 관리 도구를 사용하세요 (AWS Secrets Manager, Vault 등)
