package com.threatdetection.stream.model;

/**
 * 攻击阶段端口配置数据模型
 */
public class AttackPhasePortConfig {

    private final String phase;
    private final int portNumber;
    private final String portName;
    private final int priority;
    private final boolean enabled;
    private final String description;

    public AttackPhasePortConfig(String phase, int portNumber, String portName,
                                int priority, boolean enabled, String description) {
        this.phase = phase;
        this.portNumber = portNumber;
        this.portName = portName;
        this.priority = priority;
        this.enabled = enabled;
        this.description = description;
    }

    public String getPhase() { return phase; }
    public int getPortNumber() { return portNumber; }
    public String getPortName() { return portName; }
    public int getPriority() { return priority; }
    public boolean isEnabled() { return enabled; }
    public String getDescription() { return description; }

    @Override
    public String toString() {
        return String.format("AttackPhasePortConfig{phase='%s', port=%d, name='%s', priority=%d, enabled=%s}",
                           phase, portNumber, portName, priority, enabled);
    }
}