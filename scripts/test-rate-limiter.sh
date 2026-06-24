#!/usr/bin/env bash
# ------------------------------------------------------------------
# Rate Limiter Test Script
# Blasts requests to the API using free and premium keys to
# demonstrate the token bucket rate limiter in action.
#
# Prerequisites:
#   - Docker is running
#   - Run: docker compose up -d   (starts Redis on port 6379)
#   - The Spring Boot app is running on http://localhost:8080
# ------------------------------------------------------------------

BASE_URL="${1:-http://localhost:8080}"
ENDPOINT="${2:-/api/v1/data}"

echo "============================================"
echo "  Token Bucket Rate Limiter Test"
echo "============================================"
echo "Base URL : $BASE_URL"
echo "Endpoint : $ENDPOINT"
echo ""
echo "Tiers:"
echo "  FREE    -> key=free-key-123    (10 req/min)"
echo "  PREMIUM -> key=premium-key-456 (100 req/min)"
echo ""

# --------------------------------------------------
# Test 1: Free Key – should get 429 after 10 requests
# --------------------------------------------------
echo "--------------------------------------------"
echo "  Test 1: FREE KEY (expect 429 after ~10)"
echo "--------------------------------------------"
FREE_COUNT=0
FREE_429=0
for i in $(seq 1 15); do
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" \
        -H "X-API-KEY: free-key-123" \
        "$BASE_URL$ENDPOINT")
    if [ "$HTTP_CODE" = "429" ]; then
        FREE_429=$((FREE_429 + 1))
    fi
    FREE_COUNT=$((FREE_COUNT + 1))
    printf "  Request %2d -> HTTP %s\n" "$FREE_COUNT" "$HTTP_CODE"
    sleep 0.2
done
echo ""
echo "  Results: $FREE_COUNT total requests, $FREE_429 blocked (429)"
echo ""

# --------------------------------------------------
# Test 2: Premium Key – should handle all 15 requests
# --------------------------------------------------
echo "--------------------------------------------"
echo "  Test 2: PREMIUM KEY (expect all 200)"
echo "--------------------------------------------"
PREMIUM_COUNT=0
PREMIUM_429=0
for i in $(seq 1 15); do
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" \
        -H "X-API-KEY: premium-key-456" \
        "$BASE_URL$ENDPOINT")
    if [ "$HTTP_CODE" = "429" ]; then
        PREMIUM_429=$((PREMIUM_429 + 1))
    fi
    PREMIUM_COUNT=$((PREMIUM_COUNT + 1))
    printf "  Request %2d -> HTTP %s\n" "$PREMIUM_COUNT" "$HTTP_CODE"
    sleep 0.1
done
echo ""
echo "  Results: $PREMIUM_COUNT total requests, $PREMIUM_429 blocked (429)"
echo ""

# --------------------------------------------------
# Test 3: Missing / Invalid Key – should get 401
# --------------------------------------------------
echo "--------------------------------------------"
echo "  Test 3: MISSING KEY (expect 401)"
echo "--------------------------------------------"
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL$ENDPOINT")
echo "  No header       -> HTTP $HTTP_CODE"
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" \
    -H "X-API-KEY: unknown-key" \
    "$BASE_URL$ENDPOINT")
echo "  Invalid key     -> HTTP $HTTP_CODE"
echo ""

# --------------------------------------------------
# Summary
# --------------------------------------------------
echo "============================================"
echo "  SUMMARY"
echo "============================================"
if [ "$FREE_429" -gt 0 ]; then
    echo "  ✓ FREE key rate limiting worked  ($FREE_429 requests blocked)"
else
    echo "  ✗ FREE key was NOT rate limited  (check Redis / config)"
fi
if [ "$PREMIUM_429" -eq 0 ]; then
    echo "  ✓ PREMIUM key handled all requests (no rate limiting)"
else
    echo "  ✗ PREMIUM key was rate limited    ($PREMIUM_429 blocked)"
fi
echo ""
echo "Done."