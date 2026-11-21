# Spring Boot 3.2.0 → 4.1.2 upgrade

## Why
- **End of life.** Spring Boot 3.2.x has had no open-source security fixes since
  December 2024, and every 3.x line is now out of open-source support.
- **Spring AI.** The orchestration agents use Spring AI 2.x, which requires Spring Boot 4.
- **4.1, not 4.0.** 4.0 open-source support ends December 2026; 4.1 runs to mid-2027.
- **Now, not later.** The codebase was ~20 classes; upgrading before the orchestrator
  and agents exist keeps the change small and easy to verify.

## What changed
| Area | Change |
|---|---|
| `pom.xml` | Parent `3.2.0` → `4.1.2`; Guava `32.1.3-jre` → `33.4.0-jre` |
| Test mocks | `@MockBean` (removed in Boot 4) → `@MockitoBean` from `org.springframework.test.context.bean.override.mockito` |
| Auto-config packages | Boot 4 split auto-configuration into modules: `ValidationAutoConfiguration` moved to `org.springframework.boot.validation.autoconfigure` |
| JSON | Jackson 3 comes in transitively (`jackson-databind` 3.1.x). No code change: the app does not use Jackson APIs directly |

Starters (`spring-boot-starter-web`, `-data-jpa`, ...) were kept as they are; they still
work in 4.1. Moving to the new finer-grained starters is optional and not done here.

## Verification
- `mvn clean test`: **24 tests, 0 failures, 0 errors** on Spring Boot 4.1.2.
- The packaged jar contains `spring-boot-4.1.2` and `jackson-databind-3.1.7`.
- The validation binding test checks that `@Min(1)` limits still fail startup.
  Removing the validation auto-configuration would make it pass for the wrong reason,
  so it was restored at its new package, not dropped.

## Not part of this change
Configuration and observability changes (Prometheus, tracing, `open-in-view`, error
detail settings) are in a separate commit so this diff shows only the upgrade.
