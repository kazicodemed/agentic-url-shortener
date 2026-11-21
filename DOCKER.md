# Docker Setup Guide

## 🐳 Quick Start

### Start Everything
```bash
docker-compose up -d
```

### Stop Everything
```bash
docker-compose down
```

### View Logs
```bash
# All services
docker-compose logs -f

# Just the app
docker-compose logs -f app

# Just the database
docker-compose logs -f postgres
```

---

## 🏗️ What's Included

### Core Services (Always Running)

1. **PostgreSQL Database**
   - Port: `5432`
   - Database: `urlshortener`
   - User: `urlshortener`
   - Password: `urlshortener_password`

2. **Redis Cache**
   - Port: `6379`
   - Password: `redis_password`

3. **Spring Boot App**
   - Port: `8080`
   - Base URL: `http://localhost:8080`
   - Health: `http://localhost:8080/actuator/health`

### Optional Tools (Start with `--profile tools`)

4. **pgAdmin** (Database UI)
   - Port: `5050`
   - URL: `http://localhost:5050`
   - Email: `admin@urlshortener.com`
   - Password: `admin`

5. **Redis Commander** (Redis UI)
   - Port: `8081`
   - URL: `http://localhost:8081`

---

## 📋 Common Commands

### Start with Tools
```bash
docker-compose --profile tools up -d
```

### Rebuild After Code Changes
```bash
docker-compose up -d --build app
```

### View Container Status
```bash
docker-compose ps
```

### Execute Commands in Container
```bash
# Access PostgreSQL
docker-compose exec postgres psql -U urlshortener -d urlshortener

# Access Redis CLI
docker-compose exec redis redis-cli -a redis_password

# Access App Shell
docker-compose exec app sh
```

### Clean Everything
```bash
# Stop and remove containers, networks
docker-compose down

# Also remove volumes (⚠️ deletes all data)
docker-compose down -v

# Also remove images
docker-compose down --rmi all
```

---

## 🧪 Testing the Dockerized App

### 1. Start Services
```bash
docker-compose up -d
```

### 2. Wait for Health Checks
```bash
# Check status
docker-compose ps

# Wait for healthy status
watch docker-compose ps
```

### 3. Test API
```bash
# Create short URL
curl -X POST http://localhost:8080/api/v1/shorten \
  -H "Content-Type: application/json" \
  -d '{"url": "https://www.schwab.com", "customCode": "schwab"}'

# Test redirect
curl -L http://localhost:8080/schwab

# Check analytics
curl http://localhost:8080/api/v1/analytics/schwab
```

### 4. Check Health
```bash
curl http://localhost:8080/actuator/health
```

Expected response:
```json
{
  "status": "UP",
  "components": {
    "db": {"status": "UP"},
    "redis": {"status": "UP"}
  }
}
```

---

## 🔍 Database Access

### Using psql (Command Line)
```bash
docker-compose exec postgres psql -U urlshortener -d urlshortener

# Query URLs
SELECT * FROM url_mappings;

# Query analytics
SELECT * FROM analytics_events ORDER BY timestamp DESC LIMIT 10;

# Exit
\q
```

### Using pgAdmin (Web UI)
1. Start with tools: `docker-compose --profile tools up -d`
2. Open: `http://localhost:5050`
3. Login: `admin@urlshortener.com` / `admin`
4. Add Server:
   - Name: `urlshortener`
   - Host: `postgres`
   - Port: `5432`
   - Database: `urlshortener`
   - Username: `urlshortener`
   - Password: `urlshortener_password`

---

## 🔧 Configuration

### Environment Variables

Create `.env` file (copy from `.env.example`):
```bash
cp .env.example .env
```

Edit values:
```env
POSTGRES_PASSWORD=your_secure_password
REDIS_PASSWORD=your_redis_password
URL_SHORTENER_BASE_URL=https://your-domain.com
```

### Application Profiles

**Development (H2):**
```bash
mvn spring-boot:run
```

**Docker (PostgreSQL + Redis):**
```bash
docker-compose up
```

The app automatically uses `application-docker.yml` when running in Docker.

---

## 🐛 Troubleshooting

### Port Already in Use
```bash
# Check what's using port 8080
lsof -ti:8080

# Kill the process
lsof -ti:8080 | xargs kill -9
```

### Container Won't Start
```bash
# View logs
docker-compose logs app

# Check dependencies
docker-compose logs postgres
docker-compose logs redis
```

### Database Connection Failed
```bash
# Verify PostgreSQL is healthy
docker-compose ps postgres

# Check logs
docker-compose logs postgres

# Restart database
docker-compose restart postgres
```

### Redis Connection Failed
```bash
# Test Redis
docker-compose exec redis redis-cli -a redis_password ping

# Should return: PONG
```

### App Keeps Restarting
```bash
# Check health endpoint
curl http://localhost:8080/actuator/health

# View detailed logs
docker-compose logs -f app

# Check resource limits
docker stats
```

---

## 📊 Monitoring

### View Resource Usage
```bash
docker stats
```

### Check Container Health
```bash
docker-compose ps
```

Healthy output:
```
NAME                    STATUS
urlshortener-app        Up (healthy)
urlshortener-postgres   Up (healthy)
urlshortener-redis      Up (healthy)
```

---

## 🚀 Production Considerations

### Current Setup (Development)
- ⚠️ Uses default passwords (change in production!)
- ⚠️ No SSL/TLS (add reverse proxy)
- ⚠️ Single instance (no HA)
- ⚠️ Data persists in named volumes

### Production Checklist
- [ ] Change all passwords in `.env`
- [ ] Add nginx/traefik reverse proxy with SSL
- [ ] Configure database backups
- [ ] Set up monitoring (Prometheus/Grafana)
- [ ] Configure log aggregation
- [ ] Add resource limits in docker-compose
- [ ] Use secrets management (Docker secrets, Vault)
- [ ] Configure autoscaling (Kubernetes/Swarm)

---

## 📦 Docker Architecture

```
┌─────────────────────────────────────────────┐
│  Docker Network: urlshortener-network       │
│                                             │
│  ┌────────────┐  ┌──────────┐  ┌────────┐ │
│  │   App      │─→│PostgreSQL│  │ Redis  │ │
│  │ (Java 21)  │  │  (Port   │  │(Cache) │ │
│  │ :8080      │  │   5432)  │  │:6379   │ │
│  └────────────┘  └──────────┘  └────────┘ │
│                                             │
│  Volumes:                                   │
│  - postgres_data (persistent)               │
│  - redis_data (persistent)                  │
└─────────────────────────────────────────────┘
```

---

## ✅ Health Checks

All services have health checks:

**PostgreSQL:**
```bash
pg_isready -U urlshortener
```

**Redis:**
```bash
redis-cli --raw incr ping
```

**App:**
```bash
wget --spider http://localhost:8080/actuator/health
```

Docker automatically restarts unhealthy containers.

---

## 🎓 Learn More

- **Docker Compose Docs**: https://docs.docker.com/compose/
- **Spring Boot Docker**: https://spring.io/guides/gs/spring-boot-docker/
- **PostgreSQL Docker**: https://hub.docker.com/_/postgres
- **Redis Docker**: https://hub.docker.com/_/redis

---

**Ready to run!** 🚀

```bash
docker-compose up -d && docker-compose logs -f
```
