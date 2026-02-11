#!/bin/bash

set -e

GREEN='\033[0;32m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m'

function print_header() {
    echo -e "\n${BLUE}=== $1 ===${NC}\n"
}

function print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

function print_error() {
    echo -e "${RED}✗ $1${NC}"
}

function test_sample() {
    local SAMPLE_NAME=$1
    local PORT=$2
    local BASE_URL="http://localhost:$PORT"

    print_header "Testing $SAMPLE_NAME Sample (port $PORT)"

    print_header "1. Health Check"
    HEALTH=$(curl -s "$BASE_URL/api/health")
    if [[ $HEALTH == *"UP"* ]]; then
        print_success "Health endpoint: $HEALTH"
    else
        print_error "Health endpoint failed: $HEALTH"
        return 1
    fi

    print_header "2. Login"
    curl -s -X POST "$BASE_URL/api/auth/login" \
        -H "Content-Type: application/json" \
        -d '{"username":"user1@example.com","password":"password"}' \
        -i > /tmp/login-response-$PORT.txt

    LOGIN_RESPONSE=$(tail -1 /tmp/login-response-$PORT.txt)

    if [[ $LOGIN_RESPONSE == *"accessToken"* ]]; then
        print_success "Login successful"
        echo "$LOGIN_RESPONSE" | python3 -m json.tool
    else
        print_error "Login failed: $LOGIN_RESPONSE"
        return 1
    fi

    TOKEN=$(echo "$LOGIN_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin)['accessToken'])")
    CLIENT=$(echo "$LOGIN_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin)['client'])")
    USER_ID=$(echo "$LOGIN_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin)['uid'])")
    EXPIRY=$(echo "$LOGIN_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin)['expiry'])")

    BEARER_HEADER=$(grep "Authorization:" /tmp/login-response-$PORT.txt | cut -d' ' -f2- | tr -d '\r')

    echo "Tokens extracted:"
    echo "  TOKEN: $TOKEN"
    echo "  CLIENT: $CLIENT"
    echo "  USER_ID: $USER_ID"
    echo "  EXPIRY: $EXPIRY"
    echo "  BEARER: ${BEARER_HEADER:0:50}... (truncated)"

    print_header "3. Authentication Method 1: HTTP Headers"
    HEADERS_RESPONSE=$(curl -s "$BASE_URL/api/demo/headers" \
        -H "access-token: $TOKEN" \
        -H "client: $CLIENT" \
        -H "uid: $USER_ID" \
        -H "expiry: $EXPIRY")

    if [[ $HEADERS_RESPONSE == *"authenticated"*"true"* ]]; then
        print_success "Header-based authentication successful"
        echo "$HEADERS_RESPONSE" | python3 -m json.tool
    else
        print_error "Header-based authentication failed: $HEADERS_RESPONSE"
    fi

    print_header "4. Authentication Method 2: Secure Cookies"
    COOKIES_RESPONSE=$(curl -s "$BASE_URL/api/demo/cookies" \
        -b "access-token=$TOKEN;client=$CLIENT;uid=$USER_ID;expiry=$EXPIRY")

    if [[ $COOKIES_RESPONSE == *"authenticated"*"true"* ]]; then
        print_success "Cookie-based authentication successful"
        echo "$COOKIES_RESPONSE" | python3 -m json.tool
    else
        print_error "Cookie-based authentication failed: $COOKIES_RESPONSE"
    fi

    print_header "5. Authentication Method 3: Bearer Token"
    BEARER_RESPONSE=$(curl -s "$BASE_URL/api/demo/bearer" \
        -H "Authorization: $BEARER_HEADER")

    if [[ $BEARER_RESPONSE == *"authenticated"*"true"* ]]; then
        print_success "Bearer token authentication successful"
        echo "$BEARER_RESPONSE" | python3 -m json.tool
    elif [[ $BEARER_RESPONSE == *"500"* || $BEARER_RESPONSE == *"Internal Server Error"* ]]; then
        print_error "Bearer token authentication failed with 500 error (known issue - missing messages.properties)"
        echo "Response: $BEARER_RESPONSE"
    else
        print_error "Bearer token authentication failed: $BEARER_RESPONSE"
    fi

    print_header "6. General Auth Info (works with any method)"
    INFO_RESPONSE=$(curl -s "$BASE_URL/api/demo/info" \
        -H "access-token: $TOKEN" \
        -H "client: $CLIENT" \
        -H "uid: $USER_ID" \
        -H "expiry: $EXPIRY")

    if [[ $INFO_RESPONSE == *"authenticated"* ]]; then
        print_success "General auth endpoint successful"
        echo "$INFO_RESPONSE" | python3 -m json.tool
    else
        print_error "General auth endpoint failed: $INFO_RESPONSE"
    fi

    print_header "7. Current User Info (/api/me)"
    ME_RESPONSE=$(curl -s "$BASE_URL/api/me" \
        -H "access-token: $TOKEN" \
        -H "client: $CLIENT" \
        -H "uid: $USER_ID" \
        -H "expiry: $EXPIRY")

    if [[ $ME_RESPONSE == *"username"* ]]; then
        print_success "Current user endpoint successful"
        echo "$ME_RESPONSE" | python3 -m json.tool
    else
        print_error "Current user endpoint failed: $ME_RESPONSE"
    fi

    print_header "8. Logout"
    LOGOUT_RESPONSE=$(curl -s -X POST "$BASE_URL/api/auth/logout" \
        -H "access-token: $TOKEN" \
        -H "client: $CLIENT" \
        -H "uid: $USER_ID" \
        -H "expiry: $EXPIRY")

    if [[ $LOGOUT_RESPONSE == *"Logout successful"* ]]; then
        print_success "Logout successful"
        echo "$LOGOUT_RESPONSE" | python3 -m json.tool
    else
        print_error "Logout failed: $LOGOUT_RESPONSE"
    fi

    print_header "9. Verify token invalidated after logout"
    AFTER_LOGOUT=$(curl -s "$BASE_URL/api/demo/headers" \
        -H "access-token: $TOKEN" \
        -H "client: $CLIENT" \
        -H "uid: $USER_ID" \
        -H "expiry: $EXPIRY")

    if [[ $AFTER_LOGOUT == *"authenticated"*"false"* || $AFTER_LOGOUT == *"Unauthorized"* ]]; then
        print_success "Token correctly invalidated after logout"
    else
        print_error "Token still valid after logout: $AFTER_LOGOUT"
    fi

    print_header "$SAMPLE_NAME Sample Tests Complete!"
}

echo -e "${BLUE}"
echo "╔════════════════════════════════════════════════════╗"
echo "║  Ogiri Security Sample Application Test Suite    ║"
echo "╚════════════════════════════════════════════════════╝"
echo -e "${NC}"

test_sample "Java" 48080
test_sample "Kotlin" 48081

echo ""
echo -e "${GREEN}All tests completed!${NC}"
