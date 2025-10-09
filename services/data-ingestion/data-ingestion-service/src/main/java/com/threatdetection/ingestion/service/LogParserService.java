package com.threatdetection.ingestion.service;

import com.threatdetection.ingestion.model.AttackEvent;
import com.threatdetection.ingestion.model.StatusEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LogParserService {

    private static final Logger logger = LoggerFactory.getLogger(LogParserService.class);

    private final DevSerialToCustomerMappingService mappingService;

    public LogParserService(DevSerialToCustomerMappingService mappingService) {
        this.mappingService = mappingService;
    }

    // 攻击日志解析模式 (log_type=1) - 支持完整的syslog格式
    private static final Pattern ATTACK_LOG_PATTERN = Pattern.compile(
        "(?:<\\d+>\\w+\\s+\\d+\\s+\\d+:\\d+:\\d+\\s+[^\\s]+\\s+[^:]+:\\s*)?" +  // 可选的syslog头部
        "syslog_version=(\\d+(?:\\.\\d+)*)\\s*,\\s*" +
        "dev_serial=(" + System.getenv().getOrDefault("DEV_SERIAL_PATTERN", "[0-9A-Za-z]+") + ")\\s*,\\s*" +
        "log_type=(\\d+)\\s*,\\s*" +
        "sub_type\\s*=\\s*(\\d+)\\s*,\\s*" +  // 修复：允许sub_type后面和=前后的空格
        "attack_mac=([0-9A-Fa-f:]+)\\s*,\\s*" +
        "attack_ip=((?:\\d{1,3}\\.){3}\\d{1,3})\\s*,\\s*" +
        "response_ip=((?:\\d{1,3}\\.){3}\\d{1,3})\\s*,\\s*" +
        "response_port=(-?\\d+)\\s*,\\s*" +
        "line_id=(\\d+)\\s*,\\s*" +
        "Iface_type=(\\d+)\\s*,\\s*" +
        "Vlan_id=(\\d+)\\s*,\\s*" +
        "log_time=(\\d+)" +
        "(?:\\s*,\\s*eth_type\\s*=\\s*(\\d+))?" +  // 修复：允许eth_type后面和=前后的空格
        "(?:\\s*,\\s*ip_type\\s*=\\s*(\\d+))?$"    // 修复：允许ip_type后面和=前后的空格
    );

    // 状态日志解析模式 (log_type=2) - 支持完整的syslog格式
    private static final Pattern STATUS_LOG_PATTERN = Pattern.compile(
        "(?:<\\d+>\\w+\\s+\\d+\\s+\\d+:\\d+:\\d+\\s+[^\\s]+\\s+[^:]+:\\s*)?" +  // 可选的syslog头部
        "syslog_version=(\\d+(?:\\.\\d+)*)\\s*,\\s*" +
        "dev_serial=(" + System.getenv().getOrDefault("DEV_SERIAL_PATTERN", "[0-9A-Za-z]+") + ")\\s*,\\s*" +
        "log_type=(\\d+)\\s*,\\s*" +
        "sentry_count=(\\d+)\\s*,\\s*" +
        "real_host_count=(\\d+)\\s*,\\s*" +
        "dev_start_time=(\\d+)\\s*,\\s*" +
        "dev_end_time=(-?\\d+|[0-9]{4}-[0-9]{2}-[0-9]{2})\\s*,\\s*" +
        "time=([0-9]{4}-[0-9]{2}-[0-9]{2}\\s+[0-9]{2}:[0-9]{2}:[0-9]{2})$"
    );

    // 数据验证模式
    private static final Pattern IP_PATTERN = Pattern.compile(
        "^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.){3}(25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)$"
    );
    private static final Pattern MAC_PATTERN = Pattern.compile(
        "^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$"
    );
    
    // 设备序列号验证模式 - 支持环境变量配置
    private static final Pattern DEV_SERIAL_PATTERN = Pattern.compile(
        System.getenv().getOrDefault("DEV_SERIAL_PATTERN", "[0-9A-Za-z]+")  // 默认允许字母数字组合
    );

    // 统计信息
    private final Map<String, Integer> parseStats = new HashMap<>();

    public Optional<Object> parseLog(String rawLog) {
        logger.info("Starting to parse log: {}", rawLog.substring(0, Math.min(100, rawLog.length())));
        try {
            // Phase 1A: 增强数据验证和错误处理
            if (!isValidLogInput(rawLog)) {
                logger.warn("Invalid log input: log is null, empty, or exceeds maximum length");
                incrementStat("invalid_input");
                return Optional.empty();
            }

            // 提取日志内容
            String logContent = extractLogContent(rawLog);
            logger.info("Extracted log content: {}", logContent);
            if (logContent == null || logContent.trim().isEmpty()) {
                logger.warn("Failed to extract log content from: {}", rawLog);
                incrementStat("extraction_failed");
                return Optional.empty();
            }

            // 验证提取的内容
            if (!isValidLogContent(logContent)) {
                logger.warn("Invalid log content format: {}", logContent);
                incrementStat("invalid_content");
                return Optional.empty();
            }

            // 确定日志类型
            int logType = extractLogType(logContent);
            logger.info("Extracted log type: {}", logType);
            if (logType == -1) {
                logger.warn("Unable to determine log type from: {}", logContent);
                incrementStat("unknown_log_type");
                return Optional.empty();
            }

            // 根据类型解析
            if (logType == 1) {
                logger.info("About to parse as attack log (logType={})", logType);
                Optional<AttackEvent> attackEvent = parseAttackLog(logContent);
                if (attackEvent.isPresent()) {
                    incrementStat("attack_events_parsed");
                    return Optional.of(attackEvent.get());
                } else {
                    incrementStat("attack_parse_failed");
                    logger.info("Attack log parsing failed");
                }
            } else if (logType == 2) {
                logger.debug("Parsing as status log");
                Optional<StatusEvent> statusEvent = parseStatusLog(logContent);
                if (statusEvent.isPresent()) {
                    incrementStat("status_events_parsed");
                    return Optional.of(statusEvent.get());
                } else {
                    incrementStat("status_parse_failed");
                }
            } else {
                logger.warn("Unsupported log type: {} in log: {}", logType, rawLog);
                incrementStat("unsupported_log_type");
                return Optional.empty();
            }

            return Optional.empty();

        } catch (Exception e) {
            logger.error("Unexpected error parsing log: {} with error: {}", rawLog, e.getMessage(), e);
            incrementStat("unexpected_errors");
            return Optional.empty();
        }
    }

    /**
     * Phase 1A: 增强输入验证
     * 验证原始日志输入的基本有效性
     */
    private boolean isValidLogInput(String rawLog) {
        if (rawLog == null || rawLog.trim().isEmpty()) {
            logger.info("Log input is null or empty");
            return false;
        }

        // 检查长度限制 (防止过大的日志影响性能)
        if (rawLog.length() > 10000) {
            logger.warn("Log input exceeds maximum length: {} characters", rawLog.length());
            return false;
        }

        // 检查是否包含必要的标识符
        boolean containsSyslogVersion = rawLog.contains("syslog_version");
        boolean containsJsonMessage = rawLog.contains("\"message\":\"syslog_version");
        boolean isJsonFormat = rawLog.trim().startsWith("{") && rawLog.contains("syslog_version");
        logger.info("Log validation: contains syslog_version={}, contains json message={}, is json format={}",
                   containsSyslogVersion, containsJsonMessage, isJsonFormat);
        return containsSyslogVersion || containsJsonMessage || isJsonFormat;
    }

    /**
     * Phase 1A: 增强内容验证
     * 验证提取的日志内容是否符合预期格式
     */
    private boolean isValidLogContent(String logContent) {
        if (logContent == null || logContent.trim().isEmpty()) {
            return false;
        }

        // 检查是否包含必需的字段
        boolean hasSyslogVersion = logContent.contains("syslog_version=");
        boolean hasLogType = logContent.contains("log_type=");
        boolean hasDevSerial = logContent.contains("dev_serial=");

        return hasSyslogVersion && hasLogType && hasDevSerial;
    }

    /**
     * Phase 1A: 增强日志内容提取
     * 支持多种输入格式 (JSON和纯文本)
     */
    private String extractLogContent(String rawLog) {
        try {
            // JSON格式处理 - 与测试脚本逻辑保持一致
            if (rawLog.trim().startsWith("{")) {
                try {
                    // 解析JSON
                    com.fasterxml.jackson.databind.JsonNode jsonNode =
                        new com.fasterxml.jackson.databind.ObjectMapper().readTree(rawLog);

                    // 优先检查event.original字段
                    if (jsonNode.has("event") && jsonNode.get("event").has("original")) {
                        String original = jsonNode.get("event").get("original").asText();
                        // 如果是标准的syslog格式（以<开头）或包含syslog_version
                        if (original.startsWith("<") || original.contains("syslog_version")) {
                            return original.trim();
                        }
                    }

                    // 检查message字段
                    if (jsonNode.has("message")) {
                        String message = jsonNode.get("message").asText();
                        // 如果message包含syslog_version，说明是有效的syslog
                        if (message.contains("syslog_version")) {
                            return message.trim();
                        }
                    }

                    // 检查log.syslog.original字段
                    if (jsonNode.has("log") && jsonNode.get("log").has("syslog") &&
                        jsonNode.get("log").get("syslog").has("original")) {
                        String syslogOriginal = jsonNode.get("log").get("syslog").get("original").asText();
                        if (syslogOriginal.contains("syslog_version")) {
                            return syslogOriginal.trim();
                        }
                    }

                    // 如果都没有找到有效的syslog内容，返回null
                    return null;

                } catch (Exception e) {
                    // JSON解析失败，当作纯文本处理
                    logger.debug("JSON parsing failed, treating as plain text: {}", e.getMessage());
                }
            }

            // 标准syslog格式处理 (<PRI>timestamp hostname program: message)
            if (rawLog.startsWith("<") && rawLog.contains("syslog_version")) {
                // 找到syslog_version的位置
                int syslogStart = rawLog.indexOf("syslog_version");
                if (syslogStart == -1) return null;

                // 提取从syslog_version开始的内容
                return rawLog.substring(syslogStart).trim();
            }

            // 纯文本syslog格式
            if (rawLog.contains("syslog_version")) {
                return rawLog.trim();
            }

            return null;
        } catch (Exception e) {
            logger.warn("Error extracting log content: {}", e.getMessage());
            return null;
        }
    }

    private int extractLogType(String logContent) {
        try {
            Pattern typePattern = Pattern.compile("log_type=([\\d]+)");
            Matcher matcher = typePattern.matcher(logContent);
            if (matcher.find()) {
                int logType = Integer.parseInt(matcher.group(1));
                logger.info("Extracted log type from '{}': {}", logContent.substring(0, Math.min(50, logContent.length())), logType);
                return logType;
            } else {
                logger.warn("log_type pattern not found in: {}", logContent.substring(0, Math.min(100, logContent.length())));
            }
        } catch (Exception e) {
            logger.warn("Error extracting log type: {}", e.getMessage());
        }
        return -1;
    }

    /**
     * Phase 1A: 增强攻击日志解析
     * 添加字段验证和数据清洗
     */
    private Optional<AttackEvent> parseAttackLog(String logContent) {
        try {
            System.out.println("DEBUG: Attempting to match attack log pattern");
            System.out.println("DEBUG: Log content: " + logContent);
            System.out.println("DEBUG: Pattern: " + ATTACK_LOG_PATTERN.pattern());

            String contentToParse = logContent;

            // 处理"message repeated"格式的日志
            if (logContent.contains("message repeated")) {
                System.out.println("DEBUG: Detected 'message repeated' format, extracting content");
                // 提取方括号内的内容
                int bracketStart = logContent.indexOf('[');
                int bracketEnd = logContent.lastIndexOf(']');
                if (bracketStart != -1 && bracketEnd != -1 && bracketEnd > bracketStart) {
                    contentToParse = logContent.substring(bracketStart + 1, bracketEnd).trim();
                    System.out.println("DEBUG: Extracted content from brackets: " + contentToParse);
                } else {
                    System.out.println("DEBUG: Failed to extract content from 'message repeated' format - no valid brackets found");
                    System.out.println("DEBUG: Original logContent: " + logContent);
                    return Optional.empty();
                }
            }

            Matcher matcher = ATTACK_LOG_PATTERN.matcher(contentToParse);
            if (!matcher.find()) {
                System.out.println("DEBUG: Pattern did NOT match!");
                logger.error("Attack log pattern did NOT match. Pattern: {}", ATTACK_LOG_PATTERN.pattern());
                logger.error("Log content: {}", logContent);
                return Optional.empty();
            }

            System.out.println("DEBUG: Pattern matched successfully!");
            logger.info("Attack log pattern matched successfully, extracting fields from: {}", logContent);

            // 提取和验证字段
            String devSerial = validateDevSerial(matcher.group(2));
            if (devSerial == null) {
                logger.warn("Invalid dev_serial: {}", matcher.group(2));
                return Optional.empty();
            }
            logger.info("dev_serial validated: {}", devSerial);

            int logType = Integer.parseInt(matcher.group(3));
            int subType = Integer.parseInt(matcher.group(4));
            logger.info("logType: {}, subType: {}", logType, subType);

            String attackMac = validateMacAddress(matcher.group(5));
            if (attackMac == null) {
                logger.warn("Invalid attack_mac: {}", matcher.group(5));
                return Optional.empty();
            }
            logger.info("attack_mac validated: {}", attackMac);

            String attackIp = validateIpAddress(matcher.group(6));
            if (attackIp == null) {
                logger.warn("Invalid attack_ip: {}", matcher.group(6));
                return Optional.empty();
            }

            String responseIp = validateIpAddress(matcher.group(7));
            if (responseIp == null) {
                logger.warn("Invalid response_ip: {}", matcher.group(7));
                return Optional.empty();
            }

            int responsePort = Integer.parseInt(matcher.group(8));
            if (!isValidPort(responsePort)) {
                logger.warn("Invalid response_port: {}", matcher.group(8));
                return Optional.empty();
            }

            int lineId = Integer.parseInt(matcher.group(9));
            int ifaceType = Integer.parseInt(matcher.group(10));
            int vlanId = Integer.parseInt(matcher.group(11));
            long logTime = validateTimestamp(Long.parseLong(matcher.group(12)));
            if (logTime == -1) {
                logger.warn("Invalid log_time: {}", matcher.group(12));
                return Optional.empty();
            }

            // eth_type和ip_type字段现在是可选的，提供默认值
            int ethType = 0; // 默认值
            int ipType = 0;  // 默认值

            // 检查是否有eth_type字段 (group 13)
            if (matcher.group(13) != null) {
                ethType = Integer.parseInt(matcher.group(13));
            }

            // 检查是否有ip_type字段 (group 14)
            if (matcher.group(14) != null) {
                ipType = Integer.parseInt(matcher.group(14));
            }

            logger.debug("All fields validated successfully, creating AttackEvent (eth_type: {}, ip_type: {})", ethType, ipType);

            // Resolve customer ID from devSerial for multi-tenancy support
            String customerId = mappingService.resolveCustomerId(devSerial);
            logger.info("Resolved customerId '{}' for devSerial '{}'", customerId, devSerial);

            AttackEvent event = new AttackEvent(devSerial, logType, subType, attackMac,
                                              attackIp, responseIp, responsePort, lineId,
                                              ifaceType, vlanId, logTime, ethType, ipType,
                                              logContent, customerId);

            return Optional.of(event);

        } catch (NumberFormatException e) {
            logger.warn("Number format error in attack log: {}", logContent, e);
            incrementStat("unexpected_errors");
            return Optional.empty();
        } catch (Exception e) {
            logger.error("Failed to parse attack log fields: {}", logContent, e);
            incrementStat("unexpected_errors");
            return Optional.empty();
        }
    }

    /**
     * Phase 1A: 增强状态日志解析
     * 添加字段验证和数据清洗
     */
    private Optional<StatusEvent> parseStatusLog(String logContent) {
        try {
            Matcher matcher = STATUS_LOG_PATTERN.matcher(logContent);
            if (!matcher.find()) {
                logger.debug("Status log pattern not matched: {}", logContent);
                return Optional.empty();
            }

            String devSerial = validateDevSerial(matcher.group(2));
            if (devSerial == null) return Optional.empty();

            int logType = Integer.parseInt(matcher.group(3));
            int sentryCount = validateCount(Integer.parseInt(matcher.group(4)));
            if (sentryCount == -1) return Optional.empty();

            int realHostCount = validateCount(Integer.parseInt(matcher.group(5)));
            if (realHostCount == -1) return Optional.empty();

            long devStartTime = validateTimestamp(Long.parseLong(matcher.group(6)));
            if (devStartTime == -1) return Optional.empty();

            long devEndTime = matcher.group(7).equals("-1") ? -1 : validateTimestamp(Long.parseLong(matcher.group(7)));
            String time = matcher.group(8);

            StatusEvent event = new StatusEvent(devSerial, logType, sentryCount, realHostCount,
                                              devStartTime, devEndTime, time, logContent);

            return Optional.of(event);

        } catch (NumberFormatException e) {
            logger.warn("Number format error in status log: {}", logContent, e);
            return Optional.empty();
        } catch (Exception e) {
            logger.error("Failed to parse status log fields: {}", logContent, e);
            return Optional.empty();
        }
    }

    // Phase 1A: 数据验证辅助方法

    private String validateDevSerial(String devSerial) {
        if (devSerial == null || devSerial.trim().isEmpty()) {
            logger.warn("Invalid dev_serial: null or empty");
            return null;
        }
        
        // 使用可配置的正则表达式验证设备序列号
        String pattern = System.getenv().getOrDefault("DEV_SERIAL_PATTERN", "[0-9A-Za-z]+");
        logger.info("Validating dev_serial '{}' with pattern '{}'", devSerial, pattern);
        
        if (!DEV_SERIAL_PATTERN.matcher(devSerial).matches()) {
            logger.warn("Invalid dev_serial format: {} (pattern: {})", devSerial, DEV_SERIAL_PATTERN.pattern());
            return null;
        }
        
        logger.info("dev_serial '{}' validation passed", devSerial);
        // 转换为大写以保持一致性
        return devSerial.toUpperCase();
    }

    private String validateMacAddress(String mac) {
        if (mac == null || mac.trim().isEmpty()) {
            logger.warn("Invalid MAC address: null or empty");
            return null;
        }
        if (!MAC_PATTERN.matcher(mac).matches()) {
            logger.warn("Invalid MAC address format: {}", mac);
            return null;
        }
        return mac.toUpperCase();
    }

    private String validateIpAddress(String ip) {
        if (ip == null || ip.trim().isEmpty()) {
            logger.warn("Invalid IP address: null or empty");
            return null;
        }
        if (!IP_PATTERN.matcher(ip).matches()) {
            logger.warn("Invalid IP address format: {}", ip);
            return null;
        }
        return ip;
    }

    private int validatePort(int port) {
        // Phase 1A: 增强端口验证 - 支持特殊数据传递
        // 允许超出标准端口范围的值，包括负数和超过65535的值
        // 这些特殊值在威胁检测场景中可能表示特定的状态或异常情况

        // 只检查基本的数据合理性，不限制端口范围
        if (port < -65536 || port > 999999) {
            logger.warn("Port number outside acceptable range: {} (allowed: -65536 to 999999)", port);
            return -1;
        }

        // 对于特殊值进行记录但不拒绝
        if (port < 0 || port > 65535) {
            logger.info("Non-standard port number detected: {} (standard range is 1-65535)", port);
        }

        return port;
    }

    private boolean isValidPort(int port) {
        // 检查端口是否在可接受范围内
        return port >= -65536 && port <= 999999;
    }

    private int validateCount(int count) {
        if (count < 0) {
            logger.warn("Invalid count (negative): {}", count);
            return -1;
        }
        if (count > 1000000) { // 合理的上限
            logger.warn("Count exceeds reasonable limit: {}", count);
            return -1;
        }
        return count;
    }

    private long validateTimestamp(long timestamp) {
        // 验证时间戳是否在合理范围内 (1970-2100)
        long minTimestamp = 0; // 1970-01-01
        long maxTimestamp = 4102444800L; // 2100-01-01

        if (timestamp < minTimestamp || timestamp > maxTimestamp) {
            logger.warn("Invalid timestamp: {} (out of range {}-{})", timestamp, minTimestamp, maxTimestamp);
            return -1;
        }
        return timestamp;
    }

    private void incrementStat(String key) {
        parseStats.merge(key, 1, Integer::sum);
    }

    /**
     * Phase 1A: 获取解析统计信息 (用于监控)
     */
    public Map<String, Integer> getParseStatistics() {
        return new HashMap<>(parseStats);
    }

    /**
     * Phase 1A: 重置统计信息
     */
    public void resetStatistics() {
        parseStats.clear();
    }
}