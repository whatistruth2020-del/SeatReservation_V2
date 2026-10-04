#!/bin/bash
# burst.sh - Load test for seat reservation
# Usage: ./burst.sh <BASE_URL> [SHOW_ID] [SEAT] [USERS] [REQUESTS_PER_USER]

set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
SHOW_ID="${2:-1}"
SEAT="${3:-A1}"
USERS="${4:-200}"
REQ_PER_USER="${5:-10}"

TOTAL=$((USERS * REQ_PER_USER))
echo "=========================================="
echo "BURST TEST: $TOTAL requests for seat $SEAT"
echo "Target: $BASE_URL/shows/$SHOW_ID/reserve"
echo "Users: $USERS, Reqs/user: $REQ_PER_USER"
echo "=========================================="

# Create results file
RESULTS=$(mktemp)
trap "rm -f $RESULTS" EXIT

# Fire all requests in parallel
echo "Firing requests..."
start_time=$(date +%s%3N)

for u in $(seq 1 $USERS); do
    for r in $(seq 1 $REQ_PER_USER); do
        IDEMPOTENCY_KEY="burst-u${u}-r${r}-$(date +%s%3N)-$RANDOM"
        curl -s -X POST "$BASE_URL/shows/$SHOW_ID/reserve" \
            -H "Content-Type: application/json" \
            -H "X-User-Id: $u" \
            -H "Idempotency-Key: $IDEMPOTENCY_KEY" \
            -d "{\"seats\":[\"$SEAT\"]}" \
            -w "%{http_code}\n" -o /dev/null >> "$RESULTS" &
    done
done

wait
end_time=$(date +%s%3N)
duration_ms=$((end_time - start_time))

# Analyze results
echo ""
echo "=========================================="
echo "RESULTS (completed in ${duration_ms}ms)"
echo "=========================================="

TOTAL_REQ=$(wc -l < "$RESULTS")
HTTP_201=$(grep -c "^201$" "$RESULTS" || true)
HTTP_409=$(grep -c "^409$" "$RESULTS" || true)
HTTP_5XX=$(grep -c "^5" "$RESULTS" || true)
OTHER=$(grep -vE "^(201|409|5..)$" "$RESULTS" | wc -l || true)

echo "Total requests:  $TOTAL_REQ"
echo "  201 Created:   $HTTP_201"
echo "  409 Conflict:  $HTTP_409"
echo "  5xx Error:     $HTTP_5XX"
echo "  Other:         $OTHER"
echo ""

# Verify reconciliation
echo "Checking show state..."
STATE=$(curl -s "$BASE_URL/shows/$SHOW_ID" 2>/dev/null || echo "{}")
AVAIL=$(echo "$STATE" | grep -o '"availableSeats":[0-9]*' | cut -d: -f2 || echo 0)
HELD=$(echo "$STATE" | grep -o '"heldSeats":[0-9]*' | cut -d: -f2 || echo 0)
CONF=$(echo "$STATE" | grep -o '"confirmedSeats":[0-9]*' | cut -d: -f2 || echo 0)
TOTAL_SEATS=$(echo "$STATE" | grep -o '"totalSeats":[0-9]*' | cut -d: -f2 || echo 0)

echo "Show state:"
echo "  Available:  $AVAIL"
echo "  Held:       $HELD"
echo "  Confirmed:  $CONF"
echo "  Total:      $TOTAL_SEATS"
SUM=$((AVAIL + HELD + CONF))
echo "  Sum:        $SUM"
if [ "$SUM" -eq "$TOTAL_SEATS" ]; then
    echo "  ✓ RECONCILIATION OK"
else
    echo "  ✗ RECONCILIATION FAILED (sum=$SUM, total=$TOTAL_SEATS)"
fi

# Check seat status
SEAT_STATUS=$(echo "$STATE" | grep -A2 "\"seatNumber\":\"$SEAT\"" | grep '"status"' | head -1 | cut -d'"' -f4 || echo "unknown")
echo "  Seat $SEAT status: $SEAT_STATUS"

# Validation
echo ""
echo "=========================================="
echo "VALIDATION"
echo "=========================================="

PASS=true
if [ "$HTTP_5XX" -gt 0 ]; then echo "✗ FAIL: $HTTP_5XX server errors (should be 0)"; PASS=false; fi
if [ "$HTTP_201" -ne 1 ]; then echo "✗ FAIL: Expected exactly 1 confirmed, got $HTTP_201"; PASS=false; fi
if [ "$HTTP_409" -ne $((TOTAL - 1)) ]; then echo "✗ FAIL: Expected $((TOTAL-1)) conflicts, got $HTTP_409"; PASS=false; fi
if [ "$SUM" -ne "$TOTAL_SEATS" ]; then echo "✗ FAIL: Reconciliation broken"; PASS=false; fi
if [ "$SEAT_STATUS" != "held" ] && [ "$SEAT_STATUS" != "confirmed" ]; then echo "✗ FAIL: Seat not held/confirmed"; PASS=false; fi

if [ "$PASS" = true ]; then
    echo "✓ ALL CHECKS PASSED"
    exit 0
else
    echo "✗ SOME CHECKS FAILED"
    exit 1
fi