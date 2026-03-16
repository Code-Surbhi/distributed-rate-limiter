# Distributed Rate Limiter

A production-grade, distributed rate limiting service built with Spring Boot, Redis, and Lua scripts.

🌐 **Live Demo:** [http://54.144.176.238:8080](http://54.144.176.238:8080/api/v1/ratelimit/check?userId=demo&apiKey=free-tier)

[![Performance](https://img.shields.io/badge/Performance-1393_RPS-brightgreen)](http://54.144.176.238:8080/api/benchmark/run?requests=1000&threads=10)
[![Latency](https://img.shields.io/badge/Latency-6.5ms_avg-blue)]()
[![Deployment](https://img.shields.io/badge/AWS-EC2_t2.micro-orange)]()
[![Free Tier](https://img.shields.io/badge/Cost-$0/month-success)]()

---

## 🚀 Features

- **4 Rate Limiting Algorithms** - Token Bucket, Fixed Window, Sliding Window Log, Sliding Window Counter
- **Distributed Architecture** - Redis-backed with atomic Lua script operations
- **Multi-Tier Support** - Free (100 req/hr), Pro (1K req/hr), Enterprise (10K req/hr)
- **High Performance** - 1,393 RPS sustained, 6.5ms average latency
- **Production Ready** - 24/7 uptime on AWS, systemd service management
- **Comprehensive Testing** - 95%+ coverage with Testcontainers integration tests

---

## 📊 Performance Benchmarks


| Metric              | Value                        |
| ------------------- | ---------------------------- |
| **Throughput**      | 1,393 requests/second        |
| **Average Latency** | 6.5ms                        |
| **p99 Latency**     | 21ms                         |
| **Load Tested**     | 50,000 requests sustained    |
| **Deployment**      | AWS EC2 t2.micro (free tier) |

[View detailed benchmarks →](PERFORMANCE.md)

---

## 🏗️ Architecture

```
Client Applications
        ↓
   API Gateway (Port 8080)
        ↓
Rate Limiter Service (Spring Boot)
        ↓
   Redis (Lua Scripts)
        ↓
 In-Memory Config (Future: PostgreSQL)
```

**Key Design Decisions:**

- **Sliding Window Counter** for production (best accuracy/memory balance)
- **Lua scripts** for atomic operations (eliminates race conditions)
- **ThreadLocal caching** to reduce Redis calls (5 → 1)
- **Systemd service** for 24/7 reliability

---

## 🎯 Quick Start

### Test the Live API

**Check rate limit:**

```bash
curl "http://54.144.176.238:8080/api/v1/ratelimit/check?userId=alice&apiKey=free-tier"
```

**Run benchmark:**

```bash
curl "http://54.144.176.238:8080/api/benchmark/run?requests=1000&threads=10"
```

### Local Development

**Prerequisites:**

- Java 21+
- Maven 3.9+
- Docker (for Redis)

**Run locally:**

```bash
# Start Redis
docker run -d -p 6379:6379 redis:7-alpine

# Build and run
mvn clean package -DskipTests
java -jar target/core-0.0.1-SNAPSHOT.jar
```

**Test:**

```bash
curl "http://localhost:8080/api/v1/ratelimit/check?userId=test&apiKey=free-tier"
```

---

## 🧪 Testing

**Run unit tests:**

```bash
mvn test
```

**Run integration tests** (uses Testcontainers):

```bash
mvn verify
```

**Coverage:** 95%+ with JUnit 5, AssertJ, and Testcontainers

---

## 📈 API Reference

### Check Rate Limit

```http
GET /api/v1/ratelimit/check?userId={userId}&apiKey={apiKey}
```

**Response:**

```json
{
  "allowed": true,
  "limit": 100,
  "remaining": 99,
  "resetTime": 1773658800000,
  "algorithm": "SLIDING_WINDOW",
  "userId": "alice",
  "apiKey": "free-tier",
  "estimatedCount": 1.0
}
```

**Status Codes:**

- `200 OK` - Request allowed
- `429 Too Many Requests` - Rate limited

### Rate Limit Tiers


| Tier           | Limit           | Algorithm      |
| -------------- | --------------- | -------------- |
| **Free**       | 100 req/hour    | Sliding Window |
| **Pro**        | 1,000 req/hour  | Sliding Window |
| **Enterprise** | 10,000 req/hour | Token Bucket   |

[Full API documentation →](docs/API.md)

---

## 🔧 Optimizations Applied

**Performance improvements:**

1. **Lua Script Enhancement** - Return all data in single Redis call

   - Before: 5 Redis round trips per request
   - After: 1 Redis round trip
   - **Impact:** 48% throughput improvement
2. **ThreadLocal Response Caching** - Cache Lua response for subsequent calls

   - Eliminates redundant `getEstimatedCount()` calls
   - **Impact:** Reduced average latency 35%
3. **Connection Pooling** - Optimized Jedis pool configuration

   - Max active: 20
   - Min idle: 5
   - **Impact:** Better concurrency handling

[Performance documentation →](PERFORMANCE.md)

---

## 🌐 Deployment

**AWS Infrastructure:**

- **EC2:** t2.micro (1 vCPU, 1GB RAM)
- **Region:** us-east-1 (N. Virginia)
- **OS:** Ubuntu 24.04 LTS
- **Cost:** $0/month (free tier)

**Deployed as systemd service:**

```bash
sudo systemctl status rate-limiter
```

[Deployment guide →](DEPLOYMENT.md)

---

## 🛠️ Tech Stack

**Backend:**

- Java 21
- Spring Boot 3.5.11
- Redis 7.x
- Lua scripting

**Testing:**

- JUnit 5
- Testcontainers
- AssertJ
- Maven

**Infrastructure:**

- AWS EC2
- Ubuntu 24.04
- Systemd
- Docker

---

## 📚 Documentation

- [Performance Benchmarks](PERFORMANCE.md)
- [Algorithm Comparison](ALGORITHMS.md)
- [Deployment Guide](DEPLOYMENT.md)

---

## 🤝 Contributing

This is a portfolio project, but feedback and suggestions are welcome!

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push and create a Pull Request

---

## 📄 License

MIT License - feel free to use this project for learning!

---

## 👤 Author

**Surbhi**

- GitHub: [@Code-Surbhi](https://github.com/Code-Surbhi)
- Project: [distributed-rate-limiter](https://github.com/Code-Surbhi/distributed-rate-limiter)

---

## 🙏 Acknowledgments

Built as a learning project to understand:

- Distributed systems design
- Redis and caching strategies
- High-performance Java applications
- Production deployment on AWS

Inspired by rate limiting implementations at Stripe, Cloudflare, and Kong.
