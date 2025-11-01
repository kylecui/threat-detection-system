package com.threatdetection.ingestion.model;

/**
 * 导入模式枚举
 *
 * <p>定义不同的日志导入场景：
 * - MIGRATION: 系统迁移，合并历史数据和APT积累
 * - COMPLETION: 数据补全，针对特定客户的缺失数据
 * - OFFLINE: 离线分析，独立处理用于安全研究
 */
public enum ImportMode {

    /**
     * 系统迁移模式
     *
     * <p>适用场景：
     * - 从旧系统迁移到新系统
     * - 需要保持历史数据的连续性
     * - 合并APT积累数据
     *
     * <p>处理策略：
     * - 合并历史威胁评估
     * - 保持APT时间序列连续性
     * - 跨时间段数据整合
     */
    MIGRATION("migration"),

    /**
     * 数据补全模式
     *
     * <p>适用场景：
     * - 补全特定客户的历史数据
     * - 修复数据丢失或不完整的情况
     * - 客户特定数据导入
     *
     * <p>处理策略：
     * - 客户隔离的数据合并
     * - 避免影响其他客户数据
     * - 增量数据补全
     */
    COMPLETION("completion"),

    /**
     * 离线分析模式
     *
     * <p>适用场景：
     * - 安全研究和威胁情报分析
     * - 离线数据处理和报告生成
     * - 不影响生产系统的分析任务
     *
     * <p>处理策略：
     * - 独立处理，不影响生产数据
     * - 生成全局威胁关联分析
     * - 支持复杂的安全分析查询
     */
    OFFLINE("offline");

    private final String value;

    ImportMode(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * 根据字符串值获取枚举
     */
    public static ImportMode fromValue(String value) {
        for (ImportMode mode : values()) {
            if (mode.value.equalsIgnoreCase(value)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown import mode: " + value);
    }

    @Override
    public String toString() {
        return value;
    }
}