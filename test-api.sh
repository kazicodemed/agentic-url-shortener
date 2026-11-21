#!/bin/bash

# API Testing Script for URL Shortener
# Usage: ./test-api.sh

BASE_URL="http://localhost:8080"
echo "🧪 Testing Agentic URL Shortener API"
echo "======================================"
echo ""

# Test 1: Create a short URL with custom code
echo "📝 Test 1: Create short URL with custom code 'schwab'"
echo "Request: POST /api/v1/shorten"
curl -X POST "$BASE_URL/api/v1/shorten" \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://www.schwab.com",
    "customCode": "schwab",
    "expirationDays": 30
  }' \
  -w "\nStatus: %{http_code}\n" \
  -s | jq '.'

echo ""
echo "---"
echo ""

# Test 2: Create another short URL (auto-generated code)
echo "📝 Test 2: Create short URL with auto-generated code"
echo "Request: POST /api/v1/shorten"
curl -X POST "$BASE_URL/api/v1/shorten" \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://www.google.com"
  }' \
  -w "\nStatus: %{http_code}\n" \
  -s | jq '.'

echo ""
echo "---"
echo ""

# Test 3: Access short URL (redirect)
echo "📝 Test 3: Access short URL (this will redirect and track analytics)"
echo "Request: GET /schwab"
curl -L "$BASE_URL/schwab" -w "\nFinal URL: %{url_effective}\nStatus: %{http_code}\n" -o /dev/null -s
echo ""
echo "---"
echo ""

# Access a few more times to generate analytics data
echo "📊 Generating analytics data (5 more clicks)..."
for i in {1..5}; do
  curl -L "$BASE_URL/schwab" -o /dev/null -s
  echo -n "."
done
echo " Done!"
echo ""
echo "---"
echo ""

# Test 4: Get analytics
echo "📝 Test 4: Get analytics for 'schwab'"
echo "Request: GET /api/v1/analytics/schwab"
curl "$BASE_URL/api/v1/analytics/schwab" \
  -w "\nStatus: %{http_code}\n" \
  -s | jq '.'

echo ""
echo "---"
echo ""

# Test 5: Error case - Invalid URL
echo "📝 Test 5: Error case - Invalid URL format"
echo "Request: POST /api/v1/shorten (with invalid URL)"
curl -X POST "$BASE_URL/api/v1/shorten" \
  -H "Content-Type: application/json" \
  -d '{
    "url": "not-a-valid-url"
  }' \
  -w "\nStatus: %{http_code}\n" \
  -s | jq '.'

echo ""
echo "---"
echo ""

# Test 6: Error case - Non-existent short code
echo "📝 Test 6: Error case - Non-existent short code"
echo "Request: GET /api/v1/analytics/doesnotexist"
curl "$BASE_URL/api/v1/analytics/doesnotexist" \
  -w "\nStatus: %{http_code}\n" \
  -s | jq '.'

echo ""
echo "======================================"
echo "✅ All tests complete!"
echo ""
echo "💡 You can access the H2 console at:"
echo "   http://localhost:8080/h2-console"
echo "   JDBC URL: jdbc:h2:mem:urlshortener"
echo "   Username: sa"
echo "   Password: (leave empty)"
