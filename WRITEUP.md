# WRITEUP.md — Seat Reservation at Scale

## Atomic Decision

**Mechanism**: Database optimistic locking via `@Version` on `Seat` entity with conditional UPDATE.

```sql
UPDATE seats
SET status = 'HELD', user_id = ?, hold_expires_at = ?, version = version + 1
WHERE id = ? AND version = ? AND status = 'AVAILABLE'
```

**Why race-free**: The `WHERE version = ? AND status = 'AVAILABLE'` clause makes the check-and-update a single atomic operation. Only one transaction can match the expected version and status; others get 0 rows updated and retry/fail. The Redis distributed lock (`SET NX PX`) is a **fast-path optimization** that reduces contention on hot seats but is **not the source of truth** — if Redis fails, the DB version check still guarantees correctness.

**Multi-seat deadlock prevention**: Seat locks acquired in sorted order (by `seat_number`) in both Redis and DB (`SELECT ... FOR UPDATE ORDER BY seat_number`).

## Idempotency

**Storage**: Two-layer — Redis cache (fast) + PostgreSQL primary key (durable).

**Flow**:
1. `GET idempotency:{key}` → if hit, return cached response (exactly-once replay)
2. `SET idempotency:{key}:pending NX EX 24h` → if fails, another request processes same key → poll for cache
3. Execute reservation transaction
4. `SET idempotency:{key} <response> EX 24h` → cache result
5. `DEL idempotency:{key}:pending`

**Same key, different body**: DB `PRIMARY KEY (idempotency_key)` on `reservations` table causes unique constraint violation → 409 `IDEMPOTENCY_CONFLICT`.

## Holds & Expiry

**Model**: Time-boxed HOLD (5 min TTL), auto-expired by scheduled job (`@Scheduled` every 30s).

**Hold creation**: `AVAILABLE → HELD`, `hold_expires_at = now + 5min`, `Reservation.status = HELD`.

**Expiry job**: Finds `HELD` seats with `hold_expires_at < now`, releases via optimistic update `HELD → AVAILABLE`, marks reservation `EXPIRED`, decrements user held count.

**Safety**: Expiry only runs `WHERE status = 'HELD' AND version = ?` — cannot resurrect a `CONFIRMED` seat. Cancel endpoint (`DELETE /reservations/{id}/cancel`) releases both `HELD` and `CONFIRMED` seats.

## Consistency vs Availability Under Partition

**Choice**: **CP** (consistency over availability) for seat allocation.

- **Redis partition**: Lock acquisition fails → fall back to DB optimistic locking (more retries, higher latency, but **zero double-sells**).
- **DB partition**: Writes fail → 500 errors (unavoidable for CP). Reads (`GET /shows/{id}`) may still serve from replica if configured.
- **Tradeoff**: During Redis partition, contention increases but correctness preserved. During DB partition, service degrades but never corrupts state.

## Observability

**2am Pages**:
1. `reservation.errors.server.total > 0` — any 5xx indicates bug/infrastructure issue
2. `seats.available` gauge drops unexpectedly without corresponding `confirmed` increase — seat leak
3. `reservation.latency` p99 > 500ms — contention spike or DB issue
4. `reservation.declined.seat_taken.total` rate spike — normal for hot seats, but sustained high rate may indicate bot attack
5. `/health/ready` DOWN — DB or Redis unreachable

**Dashboards**: Grafana dashboard shows real-time confirmed/declined rates, seat availability, latency percentiles, and reconciliation invariant.

## AI Usage

**Directed (I designed, AI implemented)**:
- Overall architecture, concurrency strategy (optimistic lock + Redis lock), data model, API design, state machine, observability strategy, deployment topology.

**Decided (AI generated boilerplate I reviewed)**:
- Maven POM, Dockerfile, docker-compose.yml, Flyway migrations, Lombok annotations, Spring configuration classes, exception hierarchy, DTOs, repository query annotations, MetricsConfig, HealthController, burst.sh script, prometheus.yml.

**Honest assessment**: Every line of business logic (`BookingService`, `RedisLockService`, `IdempotencyService`, `HoldExpiryService`) was reviewed and understood. I can explain the locking semantics, retry behavior, and failure modes in the interview.

## What I'd Do Next

1. **Add payment integration** — webhook to call `/confirm`, idempotent payment processing.
2. **Seat maps / sections** — hierarchical seating (zone → row → seat) with section-level limits.
3. **Waitlist** — queue for sold-out shows, auto-promote on cancellation.
4. **Rate limiting** — per-user/IP token bucket at gateway level.
5. **Multi-region** — active-active with CRDTs or synchronous replication for global on-sales.
6. **Chaos testing** — inject Redis/DB failures, verify zero double-sells under partition.
7. **Audit log** — immutable event log for all seat state changes.