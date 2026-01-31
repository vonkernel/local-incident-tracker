# Architecture & Development Paradigm

## OOP + FP Hybrid Strategy

**Design Strategy**: Interface-driven OOP with Spring component lifecycle, functional core for business logic using pure data transformations

### Component Hierarchy

```
┌────────────────────────────────────────────────────────────┐
│ LAYER 1: Component Contracts (OOP)                         │
│ - Kotlin interfaces defining inter-component relationships │
│ - Spring @Service beans with IoC lifecycle management      │
│ - Dependencies injected via constructor                    │
│ └─ Adapter Layer (event listeners, controllers, repos)     │
└──────────────────────┬─────────────────────────────────────┘
                       ↓
┌────────────────────────────────────────────────────────────┐
│ LAYER 2: Domain Logic (FP)                                 │
│ - Pure functions: input → output → input → output ...      │
│ - No side effects, no external state mutation              │
│ - Functional streams using Kotlin's immutable operations   │
│ - Error handling via sealed classes / Result types         │
│ └─ Only Spring core (no Spring Data, no DB frameworks)     │
└──────────────────────┬─────────────────────────────────────┘
                       ↓
┌────────────────────────────────────────────────────────────┐
│ LAYER 3: Data Models (Immutable)                           │
│ - Pure data classes (defined in `shared` module)           │
│ - Value Objects with business semantics                    │
│ - Shared boundary objects across all modules               │
└────────────────────────────────────────────────────────────┘
```

### Practical Implementation

**Spring Component + Dependency Injection** (OOP):
```kotlin
// Orchestration service composes multiple extractors
@Service
class ArticleAnalysisService(
    private val articleRefiner: ArticleRefiner,
    private val incidentTypeExtractor: IncidentTypeExtractor,
    private val urgencyExtractor: UrgencyExtractor,
    private val locationsExtractor: LocationsExtractor,
    private val keywordsExtractor: KeywordsExtractor,
    private val topicExtractor: TopicExtractor,
    private val analysisResultRepository: AnalysisResultRepository
) {
    suspend fun analyze(article: Article) {
        analyzeArticle(article)
            .let { analysisResultRepository.save(it) }
    }
}
```

**Pure Functional Stream with Coroutines** (FP):

```kotlin
// 2-phase analysis: sequential refinement → parallel extraction
private suspend fun analyzeArticle(article: Article): AnalysisResult = coroutineScope {
    // Phase 1: Sequential article refinement via LLM
    articleRefiner.process(article).let { refinedArticle ->
        // Phase 2: Parallel extraction from refined content
        val incidentTypes = async { incidentTypeExtractor.process(articleId, title, content) }
        val urgency = async { urgencyExtractor.process(articleId, title, content) }
        val locations = async { locationsExtractor.process(articleId, title, content) }
        val keywords = async { keywordsExtractor.process(articleId, summary) }
        val topic = async { topicExtractor.process(articleId, summary) }

        AnalysisResult(
            articleId = article.articleId,
            refinedArticle = refinedArticle,
            incidentTypes = incidentTypes.await(),
            urgency = urgency.await(),
            keywords = keywords.await(),
            topic = topic.await(),
            locations = locations.await()
        )
    }
}
```

**Functional Principles Applied**:
- **Purity**: Each function returns same result for same input (LLM results are deterministic)
- **Immutability**: All inputs and outputs are immutable data classes
- **Composition**: Multiple independent tasks combined into single result
- **No Side Effects**: Functions don't modify external state or article object
- **Concurrency**: Parallel execution via coroutines doesn't violate functional purity

**Dependency Constraint (Domain Core Layer Only)**:
- ✅ Spring framework (core, context)
- ✅ Kotlin coroutines
- ❌ Spring Data (JPA, MongoDB, etc.)
- ❌ ORM frameworks in domain logic
- ❌ Database-specific libraries
- ❌ Any framework that introduces side effects

**Note**: These constraints apply strictly to the **Domain Core Layer** only. The **Adapter Layer** may use Spring Data, database drivers, and other infrastructure frameworks as needed.

---

## Architecture Style: Hexagonal + CQRS + Event-Driven

### Hexagonal Foundation (Ports & Adapters)
- **Domain Core**: Pure business logic, framework-independent
- **Ports (Interfaces)**: Role-based naming (e.g., `NewsFetcher`, `Geocoder`, `PromptExecutor`) define dependencies
- **Adapters (Spring Beans)**: Implement ports, connect to real services (e.g., `SafetyDataApiAdapter`, `KakaoGeocodingAdapter`, `OpenAiPromptExecutor`)

### CQRS Layering
- **Write Side** (PostgreSQL): Single source of truth, transactional consistency via analyzer
- **Read Side** (OpenSearch): Denormalized search index, updated via CDC events
- **Eventual Consistency**: CDC bridges write and read models

### Event-Driven Coupling
- No synchronous inter-service calls
- All communication via Kafka topics published by Debezium CDC
- Services remain loosely coupled, independently deployable
- Outbox Pattern ensures analyzer's multi-table inserts publish atomically

---

## Development Flow

### Phase 1: Interface Design (OOP)
Define Kotlin interfaces for each component within a module:
- **Port Interfaces**: Service contracts and dependencies
- **Domain Types**: Value objects, aggregate roots (as data classes)
- **Adapter Interfaces**: Database, messaging, external API contracts

**Deliverables**:
- `{Module}Service` interfaces (what the module does)
- Role-based port interfaces (e.g., `NewsFetcher`, `Geocoder`, `PromptExecutor`)
- `{DomainType}` data classes (what the module produces/consumes)

### Phase 2: Test-Driven Development (TDD)
Write tests BEFORE implementation to validate interface contracts:
- **Unit Tests**: Pure function behavior with immutable inputs/outputs
- **Integration Tests**: Component interaction via Spring context
- **Contract Tests**: Interface implementations match port definitions

Tests should:
- NOT require external services (mock via Spring `@MockBean`)
- Validate functional purity (same input = same output)
- Use `data class` equality to verify transformations
- Ensure idempotency for message handlers

**Deliverables**:
- Test suites covering all public interfaces
- Mock implementations of secondary ports
- Evidence that components work in isolation

### Phase 3: Implementation (FP + Spring)
Implement pure functions first, wrap with Spring beans:

1. **Pure Functions**: Business logic without side effects
   ```kotlin
   // Domain service extractors with retry support
   class KeywordsExtractor(val keywordAnalyzer: KeywordAnalyzer) {
       suspend fun process(articleId: String, summary: String): List<Keyword>
   }
   ```

2. **Spring Components**: Orchestrate pure functions, handle side effects
   ```kotlin
   @Service
   class ArticleAnalysisService(
       val articleRefiner: ArticleRefiner,
       val incidentTypeExtractor: IncidentTypeExtractor,
       // ... other extractors
   ) {
       suspend fun analyze(article: Article) { /* orchestration */ }
   }
   ```

3. **Adapters**: Connect to infrastructure
   ```kotlin
   @Service
   class ArticleRepositoryAdapter(val jpa: JpaArticleRepository) : ArticleRepository {
       override fun save(article: Article) =
           jpa.save(ArticleMapper.toEntity(article))
   }
   ```

**Principle**: Minimize side effects in outer layers (adapters), maximize purity in inner layers (domain).

