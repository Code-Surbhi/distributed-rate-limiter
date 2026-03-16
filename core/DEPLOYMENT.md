# AWS Deployment Guide

## Live Instance

🌐 **Public API:** http://54.144.176.238:8080

### Test Endpoints

**Check Rate Limit:**
```
http://54.144.176.238:8080/api/v1/ratelimit/check?userId=alice&apiKey=free-tier
```

**Performance Benchmark:**
```
http://54.144.176.238:8080/api/benchmark/run?requests=1000&threads=10
```

**Health Check:**
```
http://54.144.176.238:8080/actuator/health
```

---

## Production Performance

**AWS t2.micro instance:**
- **1,393 requests/second**
- **6.5ms average latency**
- **21ms p99 latency**

**2.2x faster than local development!**

---

## Architecture
```
Internet
    ↓
AWS EC2 t2.micro (54.144.176.238)
    ├── Spring Boot (port 8080)
    └── Redis (port 6379, internal only)
```

---

## Deployment Details

**Server:** AWS EC2 t2.micro (free tier)
**Region:** us-east-1 (N. Virginia)
**OS:** Ubuntu 24.04 LTS
**Java:** OpenJDK 21
**Redis:** 7.x
**Uptime:** 24/7 via systemd service

---

## Security

✅ SSH key authentication only
✅ Redis accessible only from localhost
✅ Rate limiting active on all APIs
✅ Regular security updates via apt

---

## Monitoring

**View logs:**
```bash
ssh -i ~/.ssh/rate-limiter-key.pem ubuntu@54.144.176.238
sudo journalctl -u rate-limiter -f
```

**Restart service:**
```bash
sudo systemctl restart rate-limiter
```

**Check status:**
```bash
sudo systemctl status rate-limiter
```

---

## Cost

**Total:** $0/month (AWS Free Tier)
**Free tier includes:**
- 750 hours/month t2.micro (we use 720)
- 30 GB storage (we use 8 GB)
- 100 GB data transfer

**Expires:** 12 months from AWS account creation
**After expiry:** ~$8/month (or stop instance = $0)