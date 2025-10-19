#!/bin/bash

# 批量修复API文档中的字段命名（camelCase → snake_case）
# 用途：将所有API文档中的camelCase字段名改为snake_case

set -e

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo "=========================================="
echo "API文档字段命名修复工具"
echo "camelCase → snake_case"
echo "=========================================="
echo ""

# 备份原文件
backup_file() {
    local file=$1
    local backup="${file}.backup.$(date +%Y%m%d_%H%M%S)"
    cp "$file" "$backup"
    echo -e "${GREEN}✅ 已备份: $backup${NC}"
}

# 替换字段名
fix_file() {
    local file=$1
    echo -e "${YELLOW}处理文件: $file${NC}"
    
    # 备份
    backup_file "$file"
    
    # 替换常见字段（Customer相关）
    sed -i 's/"customerId"/"customer_id"/g' "$file"
    sed -i 's/"companyName"/"company_name"/g' "$file"
    sed -i 's/"contactName"/"contact_name"/g' "$file"
    sed -i 's/"contactEmail"/"contact_email"/g' "$file"
    sed -i 's/"contactPhone"/"contact_phone"/g' "$file"
    sed -i 's/"subscriptionTier"/"subscription_tier"/g' "$file"
    sed -i 's/"maxDevices"/"max_devices"/g' "$file"
    sed -i 's/"currentDevices"/"current_devices"/g' "$file"
    sed -i 's/"isActive"/"is_active"/g' "$file"
    sed -i 's/"createdAt"/"created_at"/g' "$file"
    sed -i 's/"updatedAt"/"updated_at"/g' "$file"
    sed -i 's/"createdBy"/"created_by"/g' "$file"
    sed -i 's/"updatedBy"/"updated_by"/g' "$file"
    sed -i 's/"subscriptionStartDate"/"subscription_start_date"/g' "$file"
    sed -i 's/"subscriptionEndDate"/"subscription_end_date"/g' "$file"
    
    # 替换设备相关字段
    sed -i 's/"devSerial"/"dev_serial"/g' "$file"
    sed -i 's/"deviceSerial"/"dev_serial"/g' "$file"
    sed -i 's/"deviceName"/"device_name"/g' "$file"
    sed -i 's/"deviceType"/"device_type"/g' "$file"
    sed -i 's/"bindTime"/"bind_time"/g' "$file"
    sed -i 's/"unbindTime"/"unbind_time"/g' "$file"
    
    # 替换通知相关字段
    sed -i 's/"emailEnabled"/"email_enabled"/g' "$file"
    sed -i 's/"emailRecipients"/"email_recipients"/g' "$file"
    sed -i 's/"smsEnabled"/"sms_enabled"/g' "$file"
    sed -i 's/"smsRecipients"/"sms_recipients"/g' "$file"
    sed -i 's/"slackEnabled"/"slack_enabled"/g' "$file"
    sed -i 's/"slackWebhookUrl"/"slack_webhook_url"/g' "$file"
    sed -i 's/"slackChannel"/"slack_channel"/g' "$file"
    sed -i 's/"webhookEnabled"/"webhook_enabled"/g' "$file"
    sed -i 's/"webhookUrl"/"webhook_url"/g' "$file"
    sed -i 's/"webhookHeaders"/"webhook_headers"/g' "$file"
    sed -i 's/"notificationType"/"notification_type"/g' "$file"
    sed -i 's/"minSeverityLevel"/"min_severity_level"/g' "$file"
    sed -i 's/"notifyOnSeverities"/"notify_on_severities"/g' "$file"
    sed -i 's/"maxNotificationsPerHour"/"max_notifications_per_hour"/g' "$file"
    sed -i 's/"enableRateLimiting"/"enable_rate_limiting"/g' "$file"
    sed -i 's/"quietHoursEnabled"/"quiet_hours_enabled"/g' "$file"
    sed -i 's/"quietHoursStart"/"quiet_hours_start"/g' "$file"
    sed -i 's/"quietHoursEnd"/"quiet_hours_end"/g' "$file"
    sed -i 's/"quietHoursTimezone"/"quiet_hours_timezone"/g' "$file"
    
    # 替换其他常见字段
    sed -i 's/"totalProcessed"/"total_processed"/g' "$file"
    sed -i 's/"parsedSuccessfully"/"parsed_successfully"/g' "$file"
    sed -i 's/"parsingFailed"/"parsing_failed"/g' "$file"
    sed -i 's/"lastUpdated"/"last_updated"/g' "$file"
    sed -i 's/"resolvedBy"/"resolved_by"/g' "$file"
    sed -i 's/"availableDevices"/"available_devices"/g' "$file"
    sed -i 's/"usagePercentage"/"usage_percentage"/g' "$file"
    sed -i 's/"isQuotaExceeded"/"is_quota_exceeded"/g' "$file"
    sed -i 's/"syncedAt"/"synced_at"/g' "$file"
    
    echo -e "${GREEN}✅ 完成: $file${NC}"
    echo ""
}

# 主程序
if [ $# -eq 0 ]; then
    echo "用法: $0 <file1> [file2] [file3] ..."
    echo ""
    echo "示例:"
    echo "  $0 docs/api/customer_management_api.md"
    echo "  $0 docs/api/*.md"
    exit 1
fi

# 处理所有指定文件
for file in "$@"; do
    if [ -f "$file" ]; then
        fix_file "$file"
    else
        echo -e "${RED}❌ 文件不存在: $file${NC}"
    fi
done

echo "=========================================="
echo -e "${GREEN}全部完成！${NC}"
echo "=========================================="
echo ""
echo "备份文件位置: *.backup.*"
echo "如需恢复，可使用备份文件"
