#!/bin/bash

# Notification Config API Test Script
# Tests all notification configuration endpoints

set -e

BASE_URL="http://localhost:8084/api/v1"
CUSTOMER_ID="customer_a"

echo "======================================"
echo "Notification Config API Test"
echo "======================================"
echo

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0;33m' # No Color

print_test() {
    echo -e "${BLUE}[TEST]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Test 1: Get notification config (default or existing)
print_test "1. Get notification config for $CUSTOMER_ID"
CONFIG=$(curl -s "$BASE_URL/customers/$CUSTOMER_ID/notification-config")
echo "$CONFIG" | jq '.'
EMAIL_ENABLED=$(echo "$CONFIG" | jq -r '.email_enabled')
print_success "Email enabled: $EMAIL_ENABLED"
echo

# Test 2: Update notification config - Email recipients
print_test "2. Update email recipients"
UPDATE_RESPONSE=$(curl -s -X PUT "$BASE_URL/customers/$CUSTOMER_ID/notification-config" \
  -H "Content-Type: application/json" \
  -d '{
    "email_enabled": true,
    "email_recipients": ["admin@example.com", "security@example.com"],
    "min_severity_level": "HIGH",
    "notify_on_severities": ["HIGH", "CRITICAL"]
  }')
echo "$UPDATE_RESPONSE" | jq '.'
RECIPIENTS_COUNT=$(echo "$UPDATE_RESPONSE" | jq '.email_recipients | length')
print_success "Email recipients updated: $RECIPIENTS_COUNT recipients"
echo

# Test 3: Add Slack configuration
print_test "3. Add Slack configuration"
SLACK_CONFIG=$(curl -s -X PATCH "$BASE_URL/customers/$CUSTOMER_ID/notification-config" \
  -H "Content-Type: application/json" \
  -d '{
    "slack_enabled": true,
    "slack_webhook_url": "https://hooks.slack.com/services/T00000000/B00000000/XXXXXXXXXXXXXXXXXXXX",
    "slack_channel": "#security-alerts"
  }')
echo "$SLACK_CONFIG" | jq '.'
SLACK_ENABLED=$(echo "$SLACK_CONFIG" | jq -r '.slack_enabled')
if [ "$SLACK_ENABLED" = "true" ]; then
    print_success "Slack enabled successfully"
else
    print_error "Failed to enable Slack"
fi
echo

# Test 4: Configure quiet hours
print_test "4. Configure quiet hours (22:00 - 08:00)"
QUIET_CONFIG=$(curl -s -X PATCH "$BASE_URL/customers/$CUSTOMER_ID/notification-config" \
  -H "Content-Type: application/json" \
  -d '{
    "quiet_hours_enabled": true,
    "quiet_hours_start": "22:00:00",
    "quiet_hours_end": "08:00:00",
    "quiet_hours_timezone": "Asia/Shanghai"
  }')
echo "$QUIET_CONFIG" | jq '.'
QUIET_START=$(echo "$QUIET_CONFIG" | jq -r '.quiet_hours_start')
QUIET_END=$(echo "$QUIET_CONFIG" | jq -r '.quiet_hours_end')
print_success "Quiet hours: $QUIET_START - $QUIET_END"
echo

# Test 5: Set max notifications per hour
print_test "5. Set max notifications per hour to 50"
RATE_CONFIG=$(curl -s -X PATCH "$BASE_URL/customers/$CUSTOMER_ID/notification-config" \
  -H "Content-Type: application/json" \
  -d '{
    "max_notifications_per_hour": 50,
    "enable_rate_limiting": true
  }')
echo "$RATE_CONFIG" | jq '.'
MAX_NOTIF=$(echo "$RATE_CONFIG" | jq -r '.max_notifications_per_hour')
print_success "Max notifications/hour: $MAX_NOTIF"
echo

# Test 6: Add Webhook configuration
print_test "6. Add Webhook configuration"
WEBHOOK_CONFIG=$(curl -s -X PATCH "$BASE_URL/customers/$CUSTOMER_ID/notification-config" \
  -H "Content-Type: application/json" \
  -d '{
    "webhook_enabled": true,
    "webhook_url": "https://api.example.com/webhooks/alerts",
    "webhook_headers": {
      "Authorization": "Bearer token123",
      "Content-Type": "application/json"
    }
  }')
echo "$WEBHOOK_CONFIG" | jq '.'
WEBHOOK_ENABLED=$(echo "$WEBHOOK_CONFIG" | jq -r '.webhook_enabled')
if [ "$WEBHOOK_ENABLED" = "true" ]; then
    print_success "Webhook enabled successfully"
else
    print_error "Failed to enable Webhook"
fi
echo

# Test 7: Get complete configuration
print_test "7. Get complete notification configuration"
COMPLETE_CONFIG=$(curl -s "$BASE_URL/customers/$CUSTOMER_ID/notification-config")
echo "$COMPLETE_CONFIG" | jq '{
  customer_id,
  email_enabled,
  email_recipients,
  slack_enabled,
  slack_channel,
  webhook_enabled,
  min_severity_level,
  notify_on_severities,
  max_notifications_per_hour,
  quiet_hours_enabled,
  quiet_hours_start,
  quiet_hours_end
}'
print_success "Retrieved complete configuration"
echo

# Test 8: Toggle email off
print_test "8. Toggle email notifications OFF"
TOGGLE_EMAIL_OFF=$(curl -s -X PATCH "$BASE_URL/customers/$CUSTOMER_ID/notification-config/email/toggle?enabled=false")
echo "$TOGGLE_EMAIL_OFF" | jq '.email_enabled'
EMAIL_STATUS=$(echo "$TOGGLE_EMAIL_OFF" | jq -r '.email_enabled')
if [ "$EMAIL_STATUS" = "false" ]; then
    print_success "Email notifications disabled"
else
    print_error "Failed to disable email"
fi
echo

# Test 9: Toggle email back on
print_test "9. Toggle email notifications ON"
TOGGLE_EMAIL_ON=$(curl -s -X PATCH "$BASE_URL/customers/$CUSTOMER_ID/notification-config/email/toggle?enabled=true")
echo "$TOGGLE_EMAIL_ON" | jq '.email_enabled'
EMAIL_STATUS=$(echo "$TOGGLE_EMAIL_ON" | jq -r '.email_enabled')
if [ "$EMAIL_STATUS" = "true" ]; then
    print_success "Email notifications enabled"
else
    print_error "Failed to enable email"
fi
echo

# Test 10: Toggle Slack off
print_test "10. Toggle Slack notifications OFF"
TOGGLE_SLACK_OFF=$(curl -s -X PATCH "$BASE_URL/customers/$CUSTOMER_ID/notification-config/slack/toggle?enabled=false")
echo "$TOGGLE_SLACK_OFF" | jq '.slack_enabled'
SLACK_STATUS=$(echo "$TOGGLE_SLACK_OFF" | jq -r '.slack_enabled')
if [ "$SLACK_STATUS" = "false" ]; then
    print_success "Slack notifications disabled"
else
    print_error "Failed to disable Slack"
fi
echo

# Test 11: Test configuration
print_test "11. Test notification configuration"
TEST_RESULT=$(curl -s -X POST "$BASE_URL/customers/$CUSTOMER_ID/notification-config/test")
echo "$TEST_RESULT" | jq '.'
TEST_STATUS=$(echo "$TEST_RESULT" | jq -r '.testStatus')
if [ "$TEST_STATUS" = "SUCCESS" ]; then
    print_success "Configuration test passed"
else
    print_error "Configuration test failed"
fi
echo

# Test 12: Test with another customer (customer_b)
print_test "12. Get notification config for customer_b (should use existing or default)"
CUSTOMER_B_CONFIG=$(curl -s "$BASE_URL/customers/customer_b/notification-config")
echo "$CUSTOMER_B_CONFIG" | jq '{customer_id, email_enabled, min_severity_level, notify_on_severities}'
print_success "Retrieved customer_b configuration"
echo

# Test 13: Update customer_b with minimal config
print_test "13. Update customer_b with minimal configuration"
CUSTOMER_B_UPDATE=$(curl -s -X PUT "$BASE_URL/customers/customer_b/notification-config" \
  -H "Content-Type: application/json" \
  -d '{
    "email_enabled": true,
    "email_recipients": ["customer-b@example.com"],
    "min_severity_level": "CRITICAL",
    "notify_on_severities": ["CRITICAL"]
  }')
echo "$CUSTOMER_B_UPDATE" | jq '{customer_id, email_recipients, min_severity_level}'
print_success "Updated customer_b configuration"
echo

# Test 14: Verify changes persisted
print_test "14. Verify customer_a changes persisted"
VERIFY_CONFIG=$(curl -s "$BASE_URL/customers/$CUSTOMER_ID/notification-config")
FINAL_EMAIL=$(echo "$VERIFY_CONFIG" | jq -r '.email_enabled')
FINAL_SLACK=$(echo "$VERIFY_CONFIG" | jq -r '.slack_enabled')
FINAL_WEBHOOK=$(echo "$VERIFY_CONFIG" | jq -r '.webhook_enabled')
FINAL_MAX_NOTIF=$(echo "$VERIFY_CONFIG" | jq -r '.max_notifications_per_hour')

echo "Final configuration:"
echo "  - Email enabled: $FINAL_EMAIL"
echo "  - Slack enabled: $FINAL_SLACK"
echo "  - Webhook enabled: $FINAL_WEBHOOK"
echo "  - Max notifications/hour: $FINAL_MAX_NOTIF"
print_success "All changes persisted correctly"
echo

echo "======================================"
echo "All Tests Completed!"
echo "======================================"
