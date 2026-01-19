# Local Incident Tracker - Development Guide

## Quick Start

```bash
# 1. Infrastructure
cd infrastructure && docker-compose up -d

# 2. Build all modules
./gradlew build

# 3. Run services (separate terminals)
./gradlew collector:bootRun
./gradlew analyzer:bootRun
./gradlew indexer:bootRun
./gradlew searcher:bootRun
```

---

## System Composition

### Core Services
| Service | Role | Input | Output | Tech                               |
|---------|------|-------|--------|------------------------------------|
| **collector** | Data acquisition | Yonhapnews API | PostgreSQL(articles) | HTTP client, Spring Boot           |
| **analyzer** | Data enrichment | Kafka queue | PostgreSQL(analysis) | Kafka consumer, NLP(LLM)/geocoding |
| **indexer** | Search indexing | Kafka queue | OpenSearch | Kafka consumer, Opensearch client |
| **searcher** | Query interface | REST API | Search results | REST API, Opensearch queries               |

### Infrastructure
| Component | Purpose | Access                     |
|-----------|---------|----------------------------|
| **PostgreSQL** | Single source of truth (transactional data + analysis results) | JPA                        |
| **Kafka** | Event stream for CDC changes | Spring Kafka consumers     |
| **Debezium** | Captures DB changes & publishes events | Embedded in infrastructure |
| **OpenSearch** | Read-optimized search index | REST client                |

---

## Component Relationships

### Data Flow Chain
```
External API
    ↓
[collector] ← REST calls
    ↓ (write)
PostgreSQL (articles table)
    ↓ (CDC detects insert)
Debezium
    ↓ (publishes change event)
Kafka Topic: articles-changes
    ↓ (poll events)
[analyzer] → NLP/geocoding/classification
    ↓ (write analysis results)
PostgreSQL (analysis tables)
    ↓ (CDC detects insert)
Debezium
    ↓ (publishes change event)
Kafka Topic: analysis-changes
    ↓ (poll events)
[indexer] → Build search document
    ↓ (index)
OpenSearch
    ↓ (query)
[searcher] ← REST API calls
    ↓
User
```

### Dependency Relationships
```
[shared] ← Common data models
  ↑
  ├─ [collector] depends on shared
  ├─ [analyzer] depends on shared
  ├─ [indexer] depends on shared
  └─ [searcher] depends on shared

[collector] depends on PostgreSQL + external API
[analyzer] depends on PostgreSQL + Kafka consumer
[indexer] depends on Kafka consumer + OpenSearch client
[searcher] depends on OpenSearch client
```

### Data Model Relationships
```
Article (shared)
  └─ Used by: collector (output), analyzer (input)
  └─ Stored in: PostgreSQL
  └─ Fields: id, title, body, createdAt, sourceUrl

        ↓ (CDC event via Kafka)

AnalysisResult (shared)
  └─ Created by: analyzer
  └─ Stored in: PostgreSQL
  └─ Contains: disaster_type, location, urgency, keywords
  └─ Used by: indexer (input)

        ↓ (CDC event via Kafka)

ArticleIndexDocument (shared)
  └─ Created by: indexer
  └─ Stored in: OpenSearch
  └─ Optimized for: searcher queries
  └─ Contains: searchable fields + filter fields + geo fields
```

---

## Technology Stack

| Layer | Components |
|-------|------------|
| **Runtime** | JDK 21, Spring Boot 4.0 |
| **Language** | Kotlin 2.21 |
| **Build** | Gradle 9.2.1 |
| **Data (Write)** | PostgreSQL 18 |
| **Messaging** | Kafka 3.8 + Debezium 3.4 |
| **Data (Read)** | OpenSearch 3.3 |

---

## Key Architectural Patterns

### CQRS (Command Query Responsibility Segregation)
- **Write Model**: PostgreSQL (source of truth)
- **Read Model**: OpenSearch (optimized for search)
- **Sync**: Kafka events from CDC

### Event-Driven Architecture
- Service A writes → triggers DB event → CDC captures → Kafka publishes → Service B consumes
- Services are loosely coupled through event streams
- No direct service-to-service communication

### Polyglot Persistence
- PostgreSQL: Transactional consistency
- OpenSearch: Full-text search performance
- Synchronized via CDC + Kafka

---

## Module Breakdown

### `shared`
**Purpose**: Shared data contracts between all services
**Exports**:
- `Article`: Normalized incident article
- `AnalysisResult`: Enriched incident analysis
- `ArticleIndexDocument`: Search-optimized document

### `collector`
**Input**: Yonhapnews API
**Output**: Article → PostgreSQL
**Process**: Normalize external data → validate → persist
**Triggers**: AnalysisResult → Analyzer (via CDC/Kafka)

### `analyzer`
**Input**: Article events from Kafka
**Output**: AnalysisResult → PostgreSQL
**Process**:
- Classify disaster types
- Geocode addresses (Kakao API)
- Extract keywords
- Calculate urgency
**Triggers**: IndexDocument → Indexer (via CDC/Kafka)

### `indexer`
**Input**: AnalysisResult events from Kafka
**Output**: ArticleIndexDocument → OpenSearch
**Process**: Transform analysis results → create index documents → index
**Serves**: Searcher queries

### `searcher`
**Input**: HTTP REST API requests
**Output**: Search results (from OpenSearch)
**Process**:
- Parse search criteria (text, location, date, category)
- Build OpenSearch query
- Execute + rank results
- Return to client
**No persistence**: Only reads from OpenSearch

---

## Search Capability Matrix

| Capability              | Field | Relationship |
|-------------------------|-------|--------------|
| **Text Search**         | title, body, keywords | Full-text indexed in OpenSearch |
| **Location (Code)**     | jurisdiction_code | Exact match filter |
| **Location (Distance)** | coordinates | Geo-distance query + boost by distance |
| **Category**            | disaster_type | Faceted filter (35+ types) |
| **Temporal**            | incident_date | Range filter |
| **Ranking**             | urgency, distance, date | Multi-factor scoring |

---

## Testing

```bash
./gradlew test                  # All tests
./gradlew collector:test        # Specific module
```

**Test Focus**:
- Unit tests for business logic (classification, geocoding)
- Integration tests for Kafka/DB interactions
- Contract tests for shared model compatibility

---

## Design Constraints

- **Shared module**: Only data models, no business logic
- **No synchronous calls**: All inter-service communication via events
- **PostgreSQL** is the single source of truth (OpenSearch is derived)
- **Analyzer** is the only service that enriches data (no enrichment in collector/indexer)
- **Searcher** never writes to any persistent storage

---

**Tech Stack**: JDK 21 | Kotlin 2.21 | Spring Boot 4.0 | PostgreSQL 18 | Kafka 3.8 | Debezium 3.4 | OpenSearch 3.3