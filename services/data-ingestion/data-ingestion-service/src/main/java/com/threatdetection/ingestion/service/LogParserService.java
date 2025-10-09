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
    
    // 攻击日志解析模式 (log_type=1)
    private static final Pattern ATTACK_LOG_PATTERN = Pattern.compile(
        "^syslog_version=(\\d+(?:\\.\\d+)*)\\s*,\\s*" +
        "dev_serial=([0-9A-Fa-f]+)\\s*,\\s*" +
        "log_type=(\\d+)\\s*,\\s*" +
        "sub_type\\s*=\\s*(\\d+)\\s*,\\s*" +
        "attack_mac=([0-9A-Fa-f:]+)\\s*,\\s*" +
        "attack_ip=((?:\\d{1,3}\\.){3}\\d{1,3})\\s*,\\s*" +
        "response_ip=((?:\\d{1,3}\\.){3}\\d{1,3})\\s*,\\s*" +
        "response_port=(\\d+)\\s*,\\s*" +
        "line_id=(\\d+)\\s*,\\s*" +
        "Iface_type=(\\d+)\\s*,\\s*" +
        "Vlan_id=(\\d+)\\s*,\\s*" +
        "log_time=(\\d+)\\s*,\\s*" +
        "eth_type\\s*=\\s*(\\d+)\\s*,\\s*" +
        "ip_type\\s*=\\s*(\\d+)$"
    );

    // private static final Pattern ATTACK_LOG_PATTERN = Pattern.compile(
    //     "syslog_version=([\\d.]+)," +
    //     "dev_serial=([a-f0-9]+)," +
    //     "log_type=([\\d]+)," +
    //     "sub_type =([\\d]+)," +
    //     "attack_mac=([a-f0-9:]+)," +
    //     "attack_ip=([\\d.]+)," +
    //     "response_ip=([\\d.]+)," +
    //     "response_port=([\\d]+)," +
    //     "line_id=([\\d]+)," +
    //     "Iface_type=([\\d]+)," +
    //     "Vlan_id=([\\d]+)," +
    //     "log_time=([\\d]+)," +
    //     "eth_type =([\\d]+)," +
    //     "ip_type =([\\d]+)"
    // );
    

    // 状态日志解析模式 (log_type=2)
    private static final Pattern STATUS_LOG_PATTERN = Pattern.compile(
        "^syslog_version=(\\d+(?:\\.\\d+)*)\\s*,\\s*" +
        "dev_serial=([0-9a-fA-F]+)\\s*,\\s*" +
        "log_type=(\\d+)\\s*,\\s*" +
        "sentry_count=(\\d+)\\s*,\\s*" +
        "real_host_count=(\\d+)\\s*,\\s*" +
        "dev_start_time=(\\d+)\\s*,\\s*" +
        "dev_end_time=(-?\\d+|[0-9]{4}-[0-9]{2}-[0-9]{2})\\s*,\\s*" +
        "time=([0-9]{4}-[0-9]{2}-[0-9]{2}\\s+[0-9]{2}:[0-9]{2}:[0-9]{2})$"
    );


    // private static final Pattern STATUS_LOG_PATTERN = Pattern.compile(
    //     "syslog_version=([\\d.]+)," +
    //     "dev_serial=([a-f0-9]+)," +
    //     "log_type=([\\d]+)," +
    //     "sentry_count=([\\d]+)," +
    //     "real_host_count=([\\d]+)," +
    //     "dev_start_time=([\\d]+)," +
    //     "dev_end_time=([\\d-]+)," +
    //     "time=([\\d-]+ [\\d:]+)"
    // );




    public Optional<Object> parseLog(String rawLog) {
        try {
            // 提取sniff: 之后的内容
            String logContent = extractLogContent(rawLog);
            if (logContent == null) {
                logger.warn("Failed to extract log content from: {}", rawLog);
                return Optional.empty();
            }
            
            // 确定日志类型
            int logType = extractLogType(logContent);
            
            if (logType == 1) {
                return parseAttackLog(logContent).map(event -> (Object) event);
            } else if (logType == 2) {
                return parseStatusLog(logContent).map(event -> (Object) event);
            } else {
                logger.warn("Unknown log type: {} in log: {}", logType, rawLog);
                return Optional.empty();
            }
            
        } catch (Exception e) {
            logger.error("Failed to parse log: {}", rawLog, e);
            return Optional.empty();
        }
    }
    
    private String extractLogContent(String rawLog) {
        // 从JSON格式的message字段中提取内容
        // 或者直接处理原始syslog格式
        if (rawLog.contains("\"message\":\"syslog_version")) {
            // JSON格式，提取message字段
            int messageStart = rawLog.indexOf("\"message\":\"syslog_version");
            if (messageStart == -1) return null;
            
            int contentStart = rawLog.indexOf("syslog_version", messageStart);
            int contentEnd = rawLog.indexOf("\"", contentStart);
            if (contentEnd == -1) return null;
            
            return rawLog.substring(contentStart, contentEnd);
        } else if (rawLog.contains("syslog_version")) {
            // 直接是syslog内容
            return rawLog;
        }
        
        return null;
    }
    
    private int extractLogType(String logContent) {
        Pattern typePattern = Pattern.compile("log_type=([\\d]+)");
        Matcher matcher = typePattern.matcher(logContent);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return -1;
    }
    
    private Optional<AttackEvent> parseAttackLog(String logContent) {
        Matcher matcher = ATTACK_LOG_PATTERN.matcher(logContent);
        if (matcher.find()) {
            try {
                String devSerial = matcher.group(2);
                int logType = Integer.parseInt(matcher.group(3));
                int subType = Integer.parseInt(matcher.group(4));
                String attackMac = matcher.group(5);
                String attackIp = matcher.group(6);
                String responseIp = matcher.group(7);
                int responsePort = Integer.parseInt(matcher.group(8));
                int lineId = Integer.parseInt(matcher.group(9));
                int ifaceType = Integer.parseInt(matcher.group(10));
                int vlanId = Integer.parseInt(matcher.group(11));
                long logTime = Long.parseLong(matcher.group(12));
                int ethType = Integer.parseInt(matcher.group(13));
                int ipType = Integer.parseInt(matcher.group(14));
                
                AttackEvent event = new AttackEvent(devSerial, logType, subType, attackMac,
                                                  attackIp, responseIp, responsePort, lineId,
                                                  ifaceType, vlanId, logTime, ethType, ipType,
                                                  logContent);
                
                return Optional.of(event);
                
            } catch (Exception e) {
                logger.error("Failed to parse attack log fields: {}", logContent, e);
            }
        }
        return Optional.empty();
    }
    
    private Optional<StatusEvent> parseStatusLog(String logContent) {
        Matcher matcher = STATUS_LOG_PATTERN.matcher(logContent);
        if (matcher.find()) {
            try {
                String devSerial = matcher.group(2);
                int logType = Integer.parseInt(matcher.group(3));
                int sentryCount = Integer.parseInt(matcher.group(4));
                int realHostCount = Integer.parseInt(matcher.group(5));
                long devStartTime = Long.parseLong(matcher.group(6));
                long devEndTime = matcher.group(7).equals("-1") ? -1 : Long.parseLong(matcher.group(7));
                String time = matcher.group(8);
                
                StatusEvent event = new StatusEvent(devSerial, logType, sentryCount, realHostCount,
                                                  devStartTime, devEndTime, time, logContent);
                
                return Optional.of(event);
                
            } catch (Exception e) {
                logger.error("Failed to parse status log fields: {}", logContent, e);
            }
        }
        return Optional.empty();
    }
}