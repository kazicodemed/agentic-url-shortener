# Quick Start Guide

## 🚀 Get Running in 60 Seconds

### Option 1: Docker (Recommended)
```bash
# Start all services
docker-compose up -d

# View logs
docker-compose logs -f app
```

### Option 2: Maven (Local Development)
```bash
# Build
mvn clean install

# Run with H2 database
mvn spring-boot:run

# Test (in another terminal)
./test-api.sh
```

## 📝 Quick API Reference

### Create Short URL
```bash
curl -X POST http://localhost:8080/api/v1/shorten \
  -H "Content-Type: application/json" \
  -d '{"url": "https://www.schwab.com", "customCode": "schwab"}'
```

### Use Short URL
```bash
curl -L http://localhost:8080/schwab
```

### Get Analytics
```bash
curl http://localhost:8080/api/v1/analytics/schwab
```

## 🗂️ Project Structure

```
schwabprac/
├── src/main/java/com/schwab/urlshortener/
│   ├── AgenticUrlShortenerApplication.java  ← Start here
│   ├── controller/         ← REST APIs
│   ├── service/           ← Business logic
│   ├── repository/        ← Data access
│   ├── entity/            ← Database models
│   ├── dto/               ← API models
│   └── exception/         ← Error handling
├── src/main/resources/
│   └── application.yml    ← Configuration
├── pom.xml                ← Dependencies
├── README.md              ← Full documentation
├── TASK_PROGRESS.md       ← Assignment tracking
└── test-api.sh           ← API test script
```

## 🎯 What We Built

**Task 1: URL Shortener Service** ✅

- 3 REST APIs (shorten, redirect, analytics)
- Database with indexing
- Analytics tracking
- Error handling
- Input validation
- **Java 21 with Virtual Threads** 🚀
  - High-concurrency redirects
  - Async analytics processing
  - Ready for orchestration parallelism

**Next: Task 2 - Orchestration Layer** ⏳

Will build agentic system that can:
- Accept requirements
- Decompose into tasks
- Execute with approval gates
- Track metrics

## 📖 Documents to Read

1. **README.md** - Full API documentation
2. **TASK_PROGRESS.md** - Assignment progress & next steps
3. **Tutorial artifacts in chat** - Deep dives into concepts

## 💡 Key Files to Understand

```java
// 1. Start here - Main application
AgenticUrlShortenerApplication.java

// 2. REST API endpoints
controller/UrlShortenerController.java

// 3. Business logic (short code generation, analytics)
service/UrlShortenerService.java

// 4. Database models
entity/UrlMapping.java
entity/AnalyticsEvent.java
```

## 🔧 Useful Commands

```bash
# Build only
mvn clean compile

# Run tests (when we add them)
mvn test

# Package as JAR
mvn clean package

# Run the JAR
java -jar target/agentic-url-shortener-1.0.0-SNAPSHOT.jar

# Access H2 console
open http://localhost:8080/h2-console
```

## 🎓 Learning Path

**Phase 1: Understanding (Now)** ✅
- Built working URL shortener
- Learned architecture patterns
- Understood API design

**Phase 2: Orchestration (Next)**
- Design state machine
- Build agent framework
- Add governance layer

**Phase 3: Scenarios (Final)**
- Greenfield demo
- Brownfield demo
- Ambiguous requirement handling

## ⚡ Pro Tips

1. **Spring Boot knows:** Leverage your existing knowledge
2. **Focus on orchestration:** That's the differentiator
3. **Start simple:** Get it working, then add complexity
4. **Document decisions:** Evaluators care about "why"
5. **Test as you go:** Don't leave testing for the end

## 🆘 Troubleshooting

**Port already in use:**
```bash
# Kill process on port 8080
lsof -ti:8080 | xargs kill -9
```

**Maven build fails:**
```bash
# Clean Maven cache
mvn dependency:purge-local-repository
```

**H2 console won't open:**
- Check application is running: `curl http://localhost:8080/actuator/health`
- Verify URL: `jdbc:h2:mem:urlshortener`

---

**Ready to code!** 🚀

Start with: `mvn spring-boot:run`
