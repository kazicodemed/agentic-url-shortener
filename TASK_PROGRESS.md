# Agentic URL Shortener - Task Progress

## 📋 Assignment Overview

Building a URL shortener with **agentic SDLC orchestration** that demonstrates:
1. Requirement understanding
2. Task decomposition  
3. Multi-step execution
4. Human-in-the-loop approval
5. Observability and metrics

## ✅ Completed: Task 1 - Core URL Shortener Service

### What Was Built

A production-grade Spring Boot application with:

#### **Core Features**
- ✅ URL shortening with auto-generated codes (MurmurHash)
- ✅ Custom short code support
- ✅ URL expiration with TTL
- ✅ Click tracking and analytics
- ✅ Geographic analytics (country-based)
- ✅ Time-based analytics (24h, 7d)
- ✅ Automatic redirect

#### **Technical Implementation**
- ✅ Spring Boot 4.1.2 with Java 21 (upgraded from 3.2.0, see UPGRADE.md)
- ✅ JPA/Hibernate with H2 database
- ✅ RESTful API design
- ✅ DTO pattern for clean separation
- ✅ Global exception handling
- ✅ Input validation
- ✅ Database indexing for performance
- ✅ Caching infrastructure (ready for Redis)
- ✅ Transactional integrity

#### **API Endpoints**
```
POST   /api/v1/shorten           - Create short URL
GET    /{shortCode}              - Redirect to original URL
GET    /api/v1/analytics/{code}  - Get analytics data
```

#### **Files Created**
```
pom.xml                                          - Maven dependencies
src/main/resources/application.yml               - Configuration
src/main/java/com/schwab/urlshortener/
  ├── AgenticUrlShortenerApplication.java       - Main class
  ├── controller/
  │   └── UrlShortenerController.java           - REST API
  ├── service/
  │   └── UrlShortenerService.java              - Business logic
  ├── repository/
  │   ├── UrlMappingRepository.java             - URL data access
  │   └── AnalyticsEventRepository.java         - Analytics data access
  ├── entity/
  │   ├── UrlMapping.java                       - URL entity
  │   └── AnalyticsEvent.java                   - Analytics entity
  ├── dto/
  │   ├── ShortenUrlRequest.java                - Request DTO
  │   ├── ShortenUrlResponse.java               - Response DTO
  │   └── UrlAnalyticsResponse.java             - Analytics DTO
  └── exception/
      ├── GlobalExceptionHandler.java           - Error handling
      ├── UrlNotFoundException.java             - Custom exception
      └── InvalidUrlException.java              - Custom exception
```

### How to Run

```bash
# 1. Compile
mvn clean compile

# 2. Run application
mvn spring-boot:run

# 3. Test APIs (in another terminal)
./test-api.sh
```

### Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Hashing Algorithm | MurmurHash3 | Fast, good distribution, distributed-system friendly |
| Database | H2 (dev), PostgreSQL (prod) | Quick iteration now, production-ready later |
| Short Code Length | 7 characters | 268M combinations, collision-resistant |
| Analytics Storage | Raw events | Flexible querying, simple implementation |
| API Design | RESTful with clean URLs | Industry standard, user-friendly |

### Testing

**Build Status:** ✅ SUCCESS (mvn clean compile)

**Manual Testing:**
- ✅ Create short URL with custom code
- ✅ Create short URL with auto-generated code
- ✅ Redirect functionality
- ✅ Analytics tracking
- ✅ Error handling (invalid URL)
- ✅ Error handling (not found)

---

## 🎯 Next: Task 2 - Agentic Orchestration Layer

### What We'll Build

An orchestration engine that can:

1. **Accept Requirements** (as natural language)
   - "Add rate limiting to the URL creation API"
   - "Refactor analytics to use time-series database"
   - "Make the system more scalable"

2. **Decompose Into Tasks**
   - Requirement analysis
   - Architecture design
   - Implementation
   - Testing
   - Documentation

3. **Build Dependency Graph**
   ```
   Requirements → Design → Implementation → Testing → Docs
                     ↓          ↓
                  Approval   Approval
   ```

4. **Execute with Governance**
   - Human approval gates for high-risk changes
   - Policy enforcement (security, compliance)
   - Retry/rollback on failure
   - Audit logging

5. **Track Metrics**
   - Success rate
   - Retry/rollback frequency
   - MTTR (mean time to recovery)
   - End-to-end latency

### Components to Build

```
orchestration/
├── engine/
│   ├── OrchestrationEngine.java      - Main coordinator
│   ├── TaskGraph.java                - Dependency graph
│   └── StateManager.java             - Task state tracking
├── agent/
│   ├── RequirementsAgent.java        - Clarify requirements
│   ├── ArchitectureAgent.java        - Design solutions
│   ├── CodeAgent.java                - Generate code
│   ├── TestAgent.java                - Create tests
│   └── DocumentationAgent.java       - Write docs
├── governance/
│   ├── ApprovalGate.java             - Human checkpoints
│   ├── PolicyValidator.java          - Security/compliance
│   └── AuditLogger.java              - Track all decisions
├── model/
│   ├── Task.java                     - Task definition
│   ├── TaskState.java                - State enum
│   └── ExecutionContext.java         - Cross-task data
└── metrics/
    ├── MetricsCollector.java         - Track KPIs
    └── OrchestrationMetrics.java     - Metrics model
```

### Key Patterns

1. **State Machine Pattern**
   ```
   PENDING → READY → IN_PROGRESS → NEEDS_APPROVAL → APPROVED → COMPLETED
                  ↓                                           ↓
                BLOCKED                                    FAILED
                                                              ↓
                                                         ROLLED_BACK
   ```

2. **Saga Pattern**
   - Each task has a compensating action
   - If Task 5 fails, rollback Tasks 4, 3, 2, 1

3. **Chain of Responsibility**
   - Request flows through agents
   - Each agent handles what it knows

---

## 📊 Three Scenarios to Demonstrate

### Scenario 1: Greenfield (New Feature)

**Requirement:** "Add rate limiting to URL creation API"

**Expected Orchestration:**
1. Requirements Agent clarifies: "100 requests/hour per IP?"
2. Architecture Agent designs: Redis-based sliding window
3. Code Agent implements: RateLimitFilter + Redis operations
4. Test Agent creates: Unit + integration tests
5. Doc Agent updates: API documentation with rate limit headers

**Human Approval Points:**
- After architecture design (review approach)
- Before deployment (release control)

### Scenario 2: Brownfield (Refactoring)

**Requirement:** "Migrate analytics to ClickHouse time-series DB"

**Expected Orchestration:**
1. Requirements Agent analyzes: Current analytics implementation
2. Architecture Agent assesses impact: Repository layer, queries
3. Migration Agent creates: Data migration script
4. Code Agent refactors: New repository implementation
5. Test Agent validates: Data integrity, performance benchmarks

**Human Approval Points:**
- After impact analysis (review blast radius)
- Before data migration (irreversible operation)

### Scenario 3: Ambiguous Requirement

**Requirement:** "Make the system more scalable"

**Expected Orchestration:**
1. Requirements Agent asks:
   - "What's the current bottleneck?"
   - "Expected load: 1K or 1M requests/sec?"
   - "Budget constraints?"
2. Architecture Agent proposes options:
   - Option A: Add Redis caching
   - Option B: Horizontal scaling with load balancer
   - Option C: Database sharding
3. Human chooses Option A
4. Code Agent implements caching layer
5. Test Agent runs load tests to validate improvement

**Human Approval Points:**
- After requirement clarification (choose approach)
- After load test results (validate success)

---

## 🎓 Learning Approach

**Our Strategy: Task → Tutorial → Task → Tutorial**

1. ✅ **Task 1:** Built URL shortener
2. ✅ **Tutorial 1:** Architecture, design decisions, code deep dive
3. 🔄 **Task 2:** Orchestration engine (NEXT)
4. ⏳ **Tutorial 2:** State machines, agent patterns, governance
5. ⏳ **Task 3:** Three scenarios
6. ⏳ **Tutorial 3:** Metrics, observability, production readiness

**Benefit:** Learn by doing, understand by teaching.

---

## 📈 Success Criteria

### What Evaluators Will Look For

1. **Orchestration Quality**
   - ❓ Is it truly stateful (not just linear chaining)?
   - ❓ Does it handle dependencies correctly?
   - ❓ Are approval gates enforced?

2. **Engineering Output**
   - ✅ Does the URL shortener work?
   - ❓ Is generated code production-quality?
   - ❓ Are tests comprehensive?

3. **Decomposition Depth**
   - ❓ Are requirements analyzed thoughtfully?
   - ❓ Are tasks granular and actionable?
   - ❓ Is the dependency graph correct?

4. **Risk Management**
   - ❓ Are high-risk operations identified?
   - ❓ Are rollback mechanisms in place?
   - ❓ Is audit logging comprehensive?

5. **Decision Quality**
   - ❓ Can you defend tech choices?
   - ❓ Are trade-offs documented?
   - ❓ Are limitations acknowledged?

---

## 🔜 Immediate Next Steps

1. ✅ Verify URL shortener works
2. ⏳ Design orchestration engine architecture
3. ⏳ Implement Task and TaskGraph models
4. ⏳ Build StateMachine for task transitions
5. ⏳ Create first agent (RequirementsAgent)
6. ⏳ Integrate with URL shortener codebase
7. ⏳ Demonstrate Scenario 1 (greenfield)

---

## 📝 Notes

- **Spring Boot expertise:** ✅ Leveraging existing knowledge
- **Focus area:** Orchestration layer (the differentiator)
- **Time constraint:** 2-3 days
- **Priority:** Working prototype > perfect code

**Remember:** Agents execute, humans govern. Show both automation AND control.
