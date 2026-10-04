# Seat Reservation at Scale

Paytm Backend Engineering Assignment — Deploy & Observe Round

## Quick Start

### Prerequisites
- Java 21, Maven 3.9+
- Docker & Docker Compose

### Local Development
```bash
# Start infrastructure
docker-compose up -d postgres redis prometheus grafana zipkin

# Build & run app
./mvnw clean package -DskipTests
java -jar target/seat-reservation-1.0.0-SNAPSHOT.jar
# Or from IDE: run SeatReservationApplication.main()
```

### API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/shows` | Create show (admin) |
| GET | `/shows/{id}` | Get show state |
| POST | `/shows/{id}/reserve` | Reserve seats (creates HOLD) |
| POST | `/reservations/{id}/confirm` | Confirm HOLD → CONFIRMED |
| DELETE | `/reservations/{id}/cancel` | Cancel HOLD or CONFIRMED |
| GET | `/health/live` | Liveness probe |
| GET | `/health/ready` | Readiness probe (checks DB + Redis) |
| GET | `/actuator/prometheus` | Prometheus metrics |

### Example Flow

```bash
# 1. Create show
curl -X POST localhost:8080/shows \
  -H "Content-Type: application/json" \
  -d '{"name":"friday-night","seats":["A1","A2","A3","A4","A5"],"price_paise":25000}'

# 2. Reserve (creates HOLD, 5 min TTL)
curl -X POST localhost:8080/shows/1/reserve \
  -H "Content-Type: application/json" -H "X-User-Id: 42" -H "Idempotency-Key: key-1" \
  -d '{"seats":["A1"]}'
# Response: {"reservation_id":"uuid","status":"held","expires_at":"...",...}

# 3. Confirm (HELD → CONFIRMED)
curl -X POST localhost:8080/reservations/<uuid>/confirm \
  -H "X-User-Id: 42"

# 4. Or cancel
curl -X DELETE localhost:8080/reservations/<uuid>/cancel \
  -H "X-User-Id: 42"

# 5. Check state
curl localhost:8080/shows/1
```

### Load Test
```bash
chmod +x burst.sh
./burst.sh http://localhost:8080
```

### Observability
- **Prometheus**: http://localhost:9090
- **Grafana**: http://localhost:3000 (admin/admin) — pre-configured dashboard
- **Zipkin**: http://localhost:9411

### Key Metrics
- `reservation.confirmed.total` — successful reservations
- `reservation.declined.seat_taken.total` — seat conflicts
- `reservation.declined.user_limit.total` — per-user limit hits
- `reservation.declined.idempotent_replay.total` — idempotent replays
- `reservation.errors.server.total` — 5xx errors (should be 0)
- `seats.available{show_id="1"}` — real-time available count
- `reservation.latency` — p50/p95/p99 latency