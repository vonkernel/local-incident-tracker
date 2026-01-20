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
interface ArticleAnalyzer {
    fun analyze(article: Article): AnalysisResult
}

@Service
class ArticleAnalyzerImpl(
    private val llmService: LlmService,
    private val geocodingService: GeocodingService
) : ArticleAnalyzer {
    override fun analyze(article: Article): AnalysisResult =
        analyzeArticle(article)  // delegate to pure function
}
```

**Pure Functional Stream with Coroutines** (FP):

```kotlin
// Pure function: immutable input → immutable output
// No side effects, no external state mutation
suspend fun analyzeArticle(article: Article): AnalysisResult = coroutineScope {
    // Parallel execution of independent analyses via async/await
    val disasterTypeDeferred = async { classifyDisasterType(article.body) }
    val locationsDeferred = async { extractLocations(article.body) }
    val urgencyDeferred = async { assessUrgency(article.body) }

    // Await all results and compose into single output
    AnalysisResult(
        articleId = article.id,
        disasterType = disasterTypeDeferred.await(),
        locations = locationsDeferred.await(),
        urgency = urgencyDeferred.await()
    )
}

// Pure functions: same input always produces same output
// LLM calls wrapped as suspending pure functions
private suspend fun classifyDisasterType(text: String): DisasterType =
    /* LLM invocation returns deterministic result for given text */

private suspend fun extractLocations(text: String): List<Location> =
    /* LLM invocation returns deterministic result for given text */

private suspend fun assessUrgency(text: String): Urgency =
    /* LLM invocation returns deterministic result for given text */
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
- **Ports (Interfaces)**: Secondary ports define dependencies (DB, LLM, Kakao API)
- **Adapters (Spring Beans)**: Implement ports, connect to real services

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
- `{External}Port` interfaces (what the module depends on)
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
   fun classifyDisasterType(text: String): DisasterType
   fun extractLocations(text: String): List<Location>
   ```

2. **Spring Components**: Orchestrate pure functions, handle side effects
   ```kotlin
   @Service
   class ArticleAnalyzer(val llmService: LlmService) {
       fun analyze(article: Article) =
           analyzeArticle(article)  // pure function
   ```

3. **Adapters**: Connect to infrastructure
   ```kotlin
   @Service
   class ArticleRepository(val jpa: ArticleJpa) : ArticlePort {
       override fun save(article: Article) =
           jpa.save(article.toEntity())
   }
   ```

**Principle**: Minimize side effects in outer layers (adapters), maximize purity in inner layers (domain).

