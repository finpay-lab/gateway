# gateway

FinPay microservice — gateway. Part of the [finpay-lab](https://github.com/finpay-lab) multi-repository
distributed-systems laboratory. Each service owns its database (ADR-0005) and
consumes shared `com.finpay:common-*` libraries from the local `finpay-platform`
composite build (no GitHub Packages needed in this lab).

## Responsibilities
- Owns its own database / schema + Flyway migrations (ADR-0005).
- Event-driven: publishes/consumes domain events over Kafka (finpay-infra).
- Idempotent by `eventId`; async consumers define duplicate/out-of-order handling (Rule 7).
- **API gateway**: routing, JWT auth propagation, rate limiting.
- **AI-7 (FP-64):** `GuardrailFilter` → `RequestGuard` prompt-injection / anomaly filter on inbound requests.

## Tech baseline (ADR-0012)
Java 21 LTS · **Spring Boot 4.1.0** · Gradle 8.14.x · PostgreSQL 16 · Redis 7.4 ·
Kafka 3.8 (KRaft) · OpenSearch 2.17 · Flyway 11 · OpenTelemetry.

## Build (no local JDK — pinned Gradle image)
```bash
docker run --rm -v "$PWD":/work -w /work -v gradle-cache:/root/.gradle \
  gradle:8.14.5-jdk21 gradle clean build -Pversion=0.0.1 --no-daemon
```
`clean build` produces the executable bootJar. Run `clean` before rebuilds
(Docker volume mangles file mtimes).

## Deploy (local kind cluster)
Images are built, `kind load`ed as `finpaylab/gateway:fp9`, and rolled out via the
`finpay-services` Argo CD ApplicationSet. The API entrypoint is the **gateway**
(port 8080); this service is reached internally on port 8080.

## AI features (FP-58..65)
Implemented as dependency-free, **BYOK** components (FP-65 `common-ai`):
they read `FINPAY_LLM_BASE_URL` / `FINPAY_LLM_API_KEY` and run in a safe
**off-mode** (deterministic stand-in) when no key is configured.
