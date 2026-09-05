# Cloud-Native URL Shortener

A production-grade URL shortening service built with Java 21 and Spring Boot 3, deployed on Kubernetes (AWS EKS) with auto-scaling, real-time observability, and a full CI/CD pipeline. Built to prove out cloud-native deployment patterns under real load — not a toy project.

## Live Demo

```
http://<your-alb-dns-name>
```

## Architecture

```
Internet → Route 53 → ALB → EKS Ingress → Spring Boot Pods (3-10, auto-scaled)
                                                    ↓
                                    ElastiCache Redis (hot cache)
                                                    ↓
                                    RDS PostgreSQL (persistent store)
```

| Layer | Technology | Purpose |
|---|---|---|
| Application | Java 21, Spring Boot 3 | Core service |
| Cache | Redis (AWS ElastiCache) | Sub-millisecond redirects |
| Database | PostgreSQL (AWS RDS) | Durable URL storage |
| Container | Docker (multi-stage build) | 128MB production image |
| Registry | Amazon ECR | Image storage |
| Orchestration | Kubernetes (AWS EKS) | Self-healing, auto-scaling |
| Ingress | AWS Load Balancer Controller | ALB provisioning from K8s config |
| Autoscaling | Horizontal Pod Autoscaler | 3 → 10 pods based on CPU |
| CI/CD | GitHub Actions | Build → push → deploy pipeline |
| Observability | Prometheus + Grafana (via Helm) | Real-time metrics dashboards |
| Load testing | k6 | Verified scaling under real traffic |

## Key Engineering Decisions

**302 over 301 redirects.** A 301 gets cached permanently by the browser, which means click analytics disappear after the first visit. Using 302 forces every redirect through the server, keeping click counts accurate — the tradeoff is more server-side load, which is why caching and autoscaling matter here.

**Two-layer lookup (Redis → PostgreSQL).** Every redirect checks Redis first. A cache hit returns in under a millisecond; a miss falls back to PostgreSQL and re-populates the cache with a 24-hour TTL. Since a URL shortener is read-heavy (one write, thousands of reads), this is where nearly all read traffic gets absorbed.

**Multi-stage Docker build.** The build stage compiles with the full Maven toolchain; the runtime stage copies out only the final JAR onto a slim JRE Alpine base. Result: a 128MB production image instead of 700MB+. Smaller images pull faster into new pods, which directly affects how quickly the autoscaler can add capacity.

**Asymmetric HPA scaling policy.** Scale-up adds 2 pods every 30 seconds — fast, because under real load you need capacity immediately. Scale-down removes only 1 pod every 60 seconds, with a 2-minute stabilization window first — deliberately slow, to avoid "flapping" (rapid scale up/down) from normal traffic fluctuation.

**Resource requests/limits on every pod.** Kubernetes cannot make scaling decisions without knowing how much CPU/memory a pod is supposed to use. Every pod declares `requests` and `limits` explicitly — this is what the HPA measures utilization against.

## Load Test Results

Tested with k6, ramping from 0 to 200 virtual users over 8 minutes against the live AWS deployment.

| Metric | Result |
|---|---|
| Total requests | 140,924 |
| Failed requests | 0 (0.00%) |
| Sustained throughput | 293 req/s |
| Average latency | 316ms |
| p95 latency | 397ms |
| Max latency | 1.72s |
| Pods at baseline | 3 |
| Pods at peak load | 10 |
| Peak CPU utilization | 320%+ (across pods) |

Kubernetes HPA scaled the deployment from 3 to 10 pods automatically as CPU crossed the 70% target, sustained the full load with zero failed requests, and scaled back down after traffic subsided — all without manual intervention.

## Project Structure

```
url-shortener/
├── src/main/java/com/paymentengine/urlshortener/
│   ├── UrlShortenerApplication.java
│   ├── domain/ShortUrl.java
│   ├── repository/ShortUrlRepository.java
│   ├── service/
│   │   ├── UrlShortenerService.java
│   │   └── CacheService.java
│   └── api/UrlController.java
├── k8s/
│   ├── configmap.yaml
│   ├── deployment.yaml
│   ├── service.yaml
│   ├── ingress.yaml
│   ├── hpa.yaml
│   └── servicemonitor.yaml
├── .github/workflows/deploy.yml
├── loadtest.js
├── Dockerfile
└── pom.xml
```

## Running Locally

```bash
# Start infrastructure
cd docker && docker-compose up -d

# Run the app
mvn spring-boot:run
```

## API Reference

**Create a short URL**
```bash
curl -X POST http://localhost:8080/shorten \
  -H "Content-Type: application/json" \
  -d '{"url": "https://example.com/some/long/path"}'
```

**Redirect**
```bash
curl -L http://localhost:8080/{shortCode}
```

**Get stats**
```bash
curl http://localhost:8080/stats/{shortCode}
```

## Deploying to AWS

Full deployment requires an EKS cluster, RDS PostgreSQL instance, ElastiCache Redis cluster, and the AWS Load Balancer Controller. See [`k8s/`](./k8s) for all manifests.

```bash
# Build and push image
docker build -t url-shortener .
docker tag url-shortener:latest <ecr-repo-uri>:v1
docker push <ecr-repo-uri>:v1

# Deploy to cluster
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml
kubectl apply -f k8s/ingress.yaml
kubectl apply -f k8s/hpa.yaml
```

## Observability

Prometheus and Grafana are installed via the `kube-prometheus-stack` Helm chart, with a `ServiceMonitor` scraping the app's `/actuator/prometheus` endpoint every 15 seconds. Dashboard tracks:

- Redirects per second
- Cache hit rate
- Live pod count
- CPU utilization vs HPA target

## Load Testing

```bash
k6 run loadtest.js
```

Ramps traffic from 0 to 200 virtual users over 8 minutes. Watch autoscaling live:

```bash
kubectl get hpa url-shortener-hpa -w
kubectl get pods -w
```

## What I Learned

- A cache-first architecture is what makes a 302-redirect-per-click design viable at scale — without Redis absorbing nearly all read traffic, this approach wouldn't hold up under load
- HPA scaling policy is not just "add pods when busy" — the asymmetry between scale-up and scale-down speed is a deliberate stability decision, not a default
- Security group misconfiguration is the most common (and most silent) failure mode connecting EKS pods to RDS/ElastiCache — a connection timeout is almost never an application bug
- Multi-stage Docker builds matter beyond image size — they directly affect how fast new pods can start during a scaling event

## Related Projects

This is part of a three-project portfolio covering different dimensions of backend engineering:

1. **[Adaptive API Rate Limiter](#)** — distributed state, Redis, Kafka, adaptive policy engine
2. **[Distributed Payment Engine](#)** — Saga orchestration, idempotency, fraud detection
3. **Cloud-Native URL Shortener** (this project) — Kubernetes, AWS EKS, CI/CD, verified auto-scaling under load

