package com.threatdetection.alert.service.alert;

import com.threatdetection.alert.model.Alert;
import com.threatdetection.alert.repository.AlertRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 告警去重服务
 */
@Service
public class DeduplicationService {

    private static final Logger logger = LoggerFactory.getLogger(DeduplicationService.class);

    @Autowired
    private AlertRepository alertRepository;

    @Value("${alert.deduplication.time-window:300}")
    private int timeWindowSeconds;

    @Value("${alert.deduplication.similarity-threshold:0.8}")
    private double similarityThreshold;

    /**
     * 检查告警是否为重复
     */
    public boolean isDuplicate(Alert newAlert) {
        // 基于攻击MAC的时间窗口去重
        if (newAlert.getAttackMac() != null) {
            LocalDateTime since = LocalDateTime.now().minusSeconds(timeWindowSeconds);
            List<Alert> recentAlerts = alertRepository.findRecentAlertsByAttackMac(
                    newAlert.getAttackMac(), since);

            if (!recentAlerts.isEmpty()) {
                logger.info("Found {} recent alerts for MAC {} within {} seconds",
                           recentAlerts.size(), newAlert.getAttackMac(), timeWindowSeconds);
                return true;
            }
        }

        // 基于标题相似性的智能去重
        if (newAlert.getTitle() != null) {
            LocalDateTime since = LocalDateTime.now().minusSeconds(timeWindowSeconds);
            List<Alert> similarAlerts = alertRepository.findSimilarAlerts(
                    extractKeywords(newAlert.getTitle()), since);

            for (Alert existingAlert : similarAlerts) {
                double similarity = calculateSimilarity(newAlert.getTitle(), existingAlert.getTitle());
                if (similarity >= similarityThreshold) {
                    logger.info("Found similar alert with {}% similarity: {}",
                               (int)(similarity * 100), existingAlert.getTitle());
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * 提取关键词用于相似性匹配
     */
    private String extractKeywords(String title) {
        if (title == null) return "";

        // 提取关键部分（移除常见前缀）
        String[] commonPrefixes = {"High Risk", "Medium Risk", "Low Risk", "Info", "威胁检测"};
        String processedTitle = title;

        for (String prefix : commonPrefixes) {
            if (processedTitle.startsWith(prefix)) {
                processedTitle = processedTitle.substring(prefix.length()).trim();
                break;
            }
        }

        // 提取MAC地址、IP地址等关键信息
        return processedTitle.replaceAll("[^a-zA-Z0-9\\s]", " ").toLowerCase();
    }

    /**
     * 计算两个字符串的相似度（简单实现）
     */
    private double calculateSimilarity(String str1, String str2) {
        if (str1 == null || str2 == null) return 0.0;

        String s1 = str1.toLowerCase();
        String s2 = str2.toLowerCase();

        // 完全匹配
        if (s1.equals(s2)) return 1.0;

        // 包含关系
        if (s1.contains(s2) || s2.contains(s1)) return 0.9;

        // 计算共同词数
        String[] words1 = s1.split("\\s+");
        String[] words2 = s2.split("\\s+");

        int commonWords = 0;
        for (String word1 : words1) {
            for (String word2 : words2) {
                if (word1.equals(word2)) {
                    commonWords++;
                    break;
                }
            }
        }

        int maxWords = Math.max(words1.length, words2.length);
        return maxWords > 0 ? (double) commonWords / maxWords : 0.0;
    }

    /**
     * 合并重复告警
     */
    public Alert mergeDuplicates(Alert newAlert, List<Alert> duplicates) {
        if (duplicates.isEmpty()) {
            return newAlert;
        }

        logger.info("Merging {} duplicate alerts for: {}", duplicates.size(), newAlert.getTitle());

        // 合并受影响资产
        for (Alert duplicate : duplicates) {
            if (duplicate.getAffectedAssets() != null) {
                for (String asset : duplicate.getAffectedAssets()) {
                    if (!newAlert.getAffectedAssets().contains(asset)) {
                        newAlert.getAffectedAssets().add(asset);
                    }
                }
            }
        }

        // 更新威胁分数（取最高值）
        for (Alert duplicate : duplicates) {
            if (duplicate.getThreatScore() != null &&
                (newAlert.getThreatScore() == null || duplicate.getThreatScore() > newAlert.getThreatScore())) {
                newAlert.setThreatScore(duplicate.getThreatScore());
            }
        }

        return newAlert;
    }

    /**
     * 设置去重参数
     */
    public void setTimeWindowSeconds(int timeWindowSeconds) {
        this.timeWindowSeconds = timeWindowSeconds;
    }

    public void setSimilarityThreshold(double similarityThreshold) {
        this.similarityThreshold = similarityThreshold;
    }
}