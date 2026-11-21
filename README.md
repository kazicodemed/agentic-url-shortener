# Agentic URL Shortener

A production-grade URL shortener service with an **agentic SDLC orchestration layer**.

> **Start here for the orchestration work:** [ORCHESTRATION.md](ORCHESTRATION.md) —
> flow diagram, how to try it, design decisions, trade-offs and next steps.
>
> Requirements → (human approval when needed) → design, with validation gates,
> bounded retries, decision lineage and a full audit trail. Runs on free stub agents
> by default; no API key needed.

## 🚀 Quick Start

### Prerequisites
- Java 21 or higher
- Maven 3.6+
- **OR** Docker & Docker Compose

**Tech Stack:**
- Spring Boot 4.1.2
- Java 21 with Virtual Threads
- PostgreSQL / H2
- Redis (cache)
- Micrometer + Prometheus

### Option 1: Run with Docker (Recommended)

```bash
# Start all services (PostgreSQL, Redis, App)
# --build makes sure the app image contains the current code
docker-compose up -d --build

# View logs
docker-compose logs -f

# Test the API
./test-api.sh
```

See [DOCKER.md](DOCKER.md) for detailed Docker instructions.

### Option 2: Run Locally with Maven

```bash
# Build the project
mvn clean install

# Run with H2 (in-memory database)
mvn spring-boot:run
```

The application will start on `http://localhost:8080`

## 📖 API Endpoints

### 1. Shorten a URL

**POST** `/api/v1/shorten`

```bash
curl -X POST http://localhost:8080/api/v1/shorten \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://www.google.com",
    "customCode": "google",
    "expirationDays": 30
  }'
```

Response:
```json
{
  "shortCode": "google",
  "shortUrl": "http://localhost:8080/google",
  "originalUrl": "https://www.google.com",
  "createdAt": "2024-01-15T10:30:00",
  "expiresAt": "2024-02-14T10:30:00"
}
```

### 2. Access Short URL (Redirect)

**GET** `/{shortCode}`

```bash
curl -L http://localhost:8080/google
```

This will redirect to the original URL and track analytics.

### 3. Get Analytics

**GET** `/api/v1/analytics/{shortCode}`

```bash
curl http://localhost:8080/api/v1/analytics/google
```

Response:
```json
{
  "shortCode": "google",
  "originalUrl": "https://www.google.com",
  "totalClicks": 42,
  "createdAt": "2024-01-15T10:30:00",
  "clicksByCountry": {
    "US": 30,
    "UK": 12
  },
  "clicksLast24Hours": 15,
  "clicksLast7Days": 42
}
```

## 🏗️ Architecture

### Current Implementation

```
┌─────────────────────────────────────┐
│     REST API Layer                  │
│  (UrlShortenerController)           │
└────────────┬────────────────────────┘
             │
┌────────────▼────────────────────────┐
│     Service Layer                   │
│  (UrlShortenerService)              │
└────────────┬────────────────────────┘
             │
     ┌───────┴────────┐
     │                │
┌────▼─────┐    ┌─────▼──────┐
│   JPA    │    │   Cache    │
│Repository│    │  (Redis)   │
└────┬─────┘    └────────────┘
     │
┌────▼──────────────┐
│   H2 Database     │
│ (URL + Analytics) │
└───────────────────┘
```

### Components

- **Entities**: `UrlMapping`, `AnalyticsEvent`
- **Repositories**: JPA repositories for data access
- **Service**: Business logic and URL generation
- **Controller**: REST API endpoints
- **Exception Handling**: Global exception handler

## 🧪 Testing

```bash
# Run all tests
mvn test

# Run with coverage
mvn test jacoco:report
```

## 📊 Database

### Development (Maven)
H2 in-memory database:
- Console: http://localhost:8080/h2-console
- JDBC URL: `jdbc:h2:mem:urlshortener`
- Username: `sa`
- Password: (empty)

### Docker (Recommended)
PostgreSQL with persistent storage:
- Host: `localhost:5432`
- Database: `urlshortener`
- Username: `urlshortener`
- Password: `urlshortener_password`
- pgAdmin: http://localhost:5050 (with `--profile tools`)

## 🔧 Configuration

Edit `src/main/resources/application.yml`:

```yaml
url-shortener:
  base-url: http://localhost:8080
  short-code-length: 7
  cache:
    ttl: 3600
  rate-limit:
    enabled: false
    requests-per-hour: 100
```

## 🤖 Orchestration API

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/v1/runs` | Start a run: `{"requirement": "..."}` |
| GET | `/api/v1/runs/{id}` | Run status, tasks, outputs, audit trail, decisions |
| POST | `/api/v1/runs/{id}/approve` | Answer the open question: `{"approvedBy": "...", "decision": "..."}` |

Full walkthrough in [ORCHESTRATION.md](ORCHESTRATION.md).

## 🎯 Assignment Progress

- [x] Core URL shortener service (Spring Boot 4.1, Java 21 virtual threads, Postgres, Redis)
- [x] Orchestration engine: stages, persistent state, audit trail
- [x] Requirements stage: ambiguity detection, validator, bounded retries
- [x] Human-in-the-loop: approval gate, resume, decision lineage
- [x] Design stage: task DAG, cycle detection (Kahn), decision-driven plans
- [x] Claude requirements agent (Spring AI) behind a config switch; stub stays default
- [ ] Claude design agent
- [ ] Review gate after design, metrics per stage, implementation stage

See also: [ENGINEERING_JUDGEMENT.md](ENGINEERING_JUDGEMENT.md) · [UPGRADE.md](UPGRADE.md) · [DOCKER.md](DOCKER.md)
