# Local Incident Tracker - Development Guide

## Architecture & Development Principles

All development follows the principles and patterns defined in `ARCHITECTURE.md`:
- OOP + FP Hybrid Strategy with Spring component lifecycle management
- Hexagonal + CQRS + Event-Driven architecture
- Test-Driven Development (TDD) with interface-first design
- Pure functional domain logic with coroutine-based concurrency

**Reference**: See [`ARCHITECTURE.md`](./ARCHITECTURE.md) for detailed architecture, component hierarchy, development flow (Phase 1-3), and implementation patterns.

---

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

## System Overview

### Core Services
| Service | Purpose | Input → Output |
|---------|---------|-----------------|
| **collector** | Acquires incident data from external sources | Yonhapnews API → PostgreSQL |
| **analyzer** | Enriches articles with AI-powered analysis | Kafka(Article events) → PostgreSQL + Outbox |
| **indexer** | Indexes analysis results for search | Kafka(AnalysisResult events) → OpenSearch |
| **searcher** | Provides REST API for searching incidents | REST API ← OpenSearch |

### Infrastructure Components
| Component | Role | Key Point |
|-----------|------|-----------|
| **PostgreSQL** | Single source of truth for all transactional data | Write model (CQRS) |
| **Kafka + Debezium** | CDC-based event streaming from DB changes | Event-driven coupling |
| **AnalysisResult Table** | Acts as transactional outbox for analyzer events | Guarantees at-least-once delivery |
| **OpenSearch** | Read-optimized search index | Read model (CQRS) |

---

## Architecture & Data Flow

### End-to-End Data Pipeline
```
Yonhapnews API
    ↓
[collector] → normalize & validate
    ↓ (INSERT Article)
PostgreSQL (articles table)
    ↓
Debezium CDC
    ↓ (publishes)
Kafka (article-events topic)
    ↓
[analyzer] → Parallel LLM analysis + Kakao geocoding
    ↓ (INSERT to multiple analysis tables)
PostgreSQL (disaster_type_analysis, location_analysis,
            urgency_analysis, keywords_analysis, ...)
    ↓ (aggregate in transaction)
INSERT AnalysisResult (acts as outbox)
    ↓
Debezium CDC (from AnalysisResult)
    ↓ (publishes)
Kafka (analysis-events topic)
    ↓
[indexer] → build search document
    ↓ (indexes)
OpenSearch
    ↓
[searcher] ← REST API queries
    ↓
User
```

### Data Models
```
Article (shared, PostgreSQL)
  ├─ id, title, body, createdAt, sourceUrl
  └─ Source: collector, Consumed by: analyzer

AnalysisResult (shared, PostgreSQL)
  ├─ article_id, disaster_type, location, urgency, keywords
  └─ Source: analyzer, Consumed by: indexer

ArticleIndexDocument (shared, OpenSearch)
  ├─ searchable content, filter facets, geographic data
  └─ Source: indexer, Consumed by: searcher
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
- **Write Model**: PostgreSQL (transactional source of truth)
- **Read Model**: OpenSearch (denormalized search index)
- **Sync**: CDC-based eventual consistency via Kafka

### Event-Driven Architecture with CDC
- Every DB write automatically triggers a CDC event
- Debezium captures table changes and publishes to Kafka
- Services consume events asynchronously, no direct calls
- Loose coupling enables independent scaling and deployment

### Outbox Pattern (Analyzer Service)
**Problem**: Ensuring multiple concurrent analysis results are reliably published to Kafka as a single atomic event

**Solution**: AnalysisResult table serves as the outbox
- Multiple analyses (disaster_type, location, urgency, keywords, etc.) are performed via LLM
- Each analysis result stored in its own table (disaster_analysis, location_analysis, etc.)
- AnalysisResult aggregates all these analyses in a single transaction
- AnalysisResult INSERT acts as the CDC trigger for Kafka publication

**Flow**:
```
Analyzer receives Article event
  ↓
Concurrent LLM analysis tasks:
  ├─ disaster_type_analysis (INSERT)
  ├─ location_analysis (INSERT)
  ├─ urgency_analysis (INSERT)
  ├─ keywords_analysis (INSERT)
  └─ ... other analyses ...
  ↓
BEGIN TRANSACTION
  └─ INSERT AnalysisResult (aggregate all results)
COMMIT
  ↓
Debezium detects AnalysisResult insert (CDC)
  ↓
Publishes AnalysisEvent → Kafka
  ↓
Indexer consumes event → builds search document
```

**Key Benefits**:
- Transactional guarantee: All analyses committed together or not at all
- At-least-once delivery: Kafka gets event whenever AnalysisResult is committed
- No orphaned analysis rows: All individual analyses tied to one AnalysisResult

### Polyglot Persistence
- PostgreSQL: ACID compliance, transactional consistency
- OpenSearch: Full-text search, geo-spatial queries, fast retrieval
- Each optimized for its use case, synchronized via events

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
**Output**: AnalysisResult → PostgreSQL (serves as outbox)

**Analysis Pipeline** (LLM-driven):
1. **LLM Analysis** (parallel tasks):
   - Disaster type classification → `disaster_type_analysis` table
   - Location extraction & entity linking → `location_analysis` table
   - Urgency/severity assessment → `urgency_analysis` table
   - Keyword extraction & tagging → `keywords_analysis` table
   - (Additional analyses as needed) → respective tables

2. **Geocoding** (external API):
   - Kakao API: Convert extracted locations → geographic coordinates
   - Results stored separately for geo-spatial search

3. **Aggregate & Commit**:
   - All individual analysis results + geocoding combined into single AnalysisResult
   - Single transaction: AnalysisResult INSERT triggers Debezium CDC event
   - Kafka publishes AnalysisEvent for indexer consumption

**Transactional Guarantee**: All analyses succeed together or rollback together

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
- **Transactional guarantee**: All analyzer results (individual analysis tables + AnalysisResult) must commit atomically
- **Event ordering**: CDC events maintain insertion order per table
- **Idempotency**: Services should handle duplicate events gracefully

---

## Development Guidelines

### Adding New Analysis Types

When extending analyzer with new analysis:

1. **Create dedicated table** for results (e.g., `sentiment_analysis`)
2. **Perform analysis** in parallel with existing tasks
3. **Store results** in transaction before committing AnalysisResult
4. **Update AnalysisResult** model to include new field
5. **Verify CDC** captures changes correctly

### Scaling Considerations

**Analyzer bottleneck?**
- LLM analysis is typically the bottleneck
- Consider: LLM batching, rate limiting, async invocation patterns
- Monitor: Queue depth, processing latency per article

**Indexer bottleneck?**
- Optimize OpenSearch mapping for your query patterns
- Consider: Index settings, refresh interval tuning
- Monitor: Indexing latency, query performance

**Database bottleneck?**
- Monitor: Connection pool, long-running queries, CDC lag
- Consider: Query optimization, appropriate indexing

### Event Processing Guarantees

- **At-least-once delivery**: Events may be replayed, ensure idempotency
- **Eventual consistency**: Read model (OpenSearch) may lag write model (PostgreSQL)
- **Event ordering**: Per-table ordering guaranteed, but multi-table events not totally ordered

---

**Tech Stack**: JDK 21 | Kotlin 2.2 | Spring Boot 4.0 | PostgreSQL 18 | Kafka 3.8 | Debezium 3.4 | OpenSearch 3.3