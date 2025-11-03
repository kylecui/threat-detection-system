-- Customer Time Weights Table
-- V5.0 新增: 客户时间段权重配置表
-- 支持多租户，每个客户可以自定义不同时间段的权重

CREATE TABLE IF NOT EXISTS customer_time_weights (
    id SERIAL PRIMARY KEY,
    customer_id VARCHAR(50) NOT NULL,
    start_hour INTEGER NOT NULL CHECK (start_hour >= 0 AND start_hour <= 23),
    end_hour INTEGER NOT NULL CHECK (end_hour >= 1 AND end_hour <= 24),
    time_range_name VARCHAR(100),
    weight DECIMAL(5,2) NOT NULL DEFAULT 1.0 CHECK (weight >= 0.5 AND weight <= 2.0),
    risk_description VARCHAR(50),
    attack_intent TEXT,
    description TEXT,
    priority INTEGER DEFAULT 0 CHECK (priority >= 0 AND priority <= 100),
    enabled BOOLEAN DEFAULT TRUE,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes
CREATE INDEX idx_customer_time_weights_customer ON customer_time_weights (customer_id);
CREATE INDEX idx_customer_time_weights_range ON customer_time_weights (start_hour, end_hour);
CREATE INDEX idx_customer_time_weights_enabled ON customer_time_weights (enabled);
CREATE INDEX idx_customer_time_weights_composite ON customer_time_weights (customer_id, enabled, priority);

-- Create unique constraint
ALTER TABLE customer_time_weights ADD CONSTRAINT uk_customer_time_range UNIQUE (customer_id, start_hour, end_hour);

-- Add table comment
COMMENT ON TABLE customer_time_weights IS '客户时间段权重配置表 - 支持多租户自定义时间权重';
COMMENT ON COLUMN customer_time_weights.customer_id IS '客户ID (租户标识)';
COMMENT ON COLUMN customer_time_weights.start_hour IS '时间段开始小时 (0-23)';
COMMENT ON COLUMN customer_time_weights.end_hour IS '时间段结束小时 (0-23)';
COMMENT ON COLUMN customer_time_weights.time_range_name IS '时间段名称';
COMMENT ON COLUMN customer_time_weights.weight IS '时间权重 (0.5-2.0)';
COMMENT ON COLUMN customer_time_weights.risk_description IS '风险等级描述';
COMMENT ON COLUMN customer_time_weights.attack_intent IS '攻击意图描述';
COMMENT ON COLUMN customer_time_weights.description IS '详细描述';
COMMENT ON COLUMN customer_time_weights.priority IS '优先级 (0-100)';
COMMENT ON COLUMN customer_time_weights.enabled IS '是否启用';
COMMENT ON COLUMN customer_time_weights.created_by IS '创建人';
COMMENT ON COLUMN customer_time_weights.updated_by IS '更新人';
COMMENT ON COLUMN customer_time_weights.created_at IS '创建时间';
COMMENT ON COLUMN customer_time_weights.updated_at IS '更新时间';

-- Insert default configurations for existing customers
-- These represent the default time weight logic that was previously hardcoded

-- For default customer: System-wide default time weights
INSERT INTO customer_time_weights (customer_id, start_hour, end_hour, time_range_name, weight, risk_description, attack_intent, description, priority, enabled, created_by) VALUES
('default', 0, 6, '深夜时段', 1.2, '高风险', 'APT行为', '深夜异常行为，权重提高20%', 90, true, 'system'),
('default', 6, 9, '早晨时段', 1.1, '中等风险', '常规活动', '早晨时段，权重提高10%', 80, true, 'system'),
('default', 9, 17, '工作时段', 1.0, '正常风险', '工作时间', '工作时间基准权重', 50, true, 'system'),
('default', 17, 21, '傍晚时段', 0.9, '低风险', '下班时间', '傍晚时段，权重降低10%', 40, true, 'system'),
('default', 21, 24, '夜间时段', 0.8, '低风险', '休息时间', '夜间时段，权重降低20%', 30, true, 'system');

-- Note: Individual customers only need database records when they customize their time weights.
-- Default weights are now stored in the 'default' customer configuration.
-- This allows for centralized management of default weights while maintaining flexibility for customizations.

-- Create trigger for updating updated_at timestamp
CREATE OR REPLACE FUNCTION update_customer_time_weights_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_customer_time_weights_timestamp_trigger
    BEFORE UPDATE ON customer_time_weights
    FOR EACH ROW
    EXECUTE FUNCTION update_customer_time_weights_timestamp();

-- Grant permissions (adjust as needed for your security model)
-- GRANT SELECT, INSERT, UPDATE, DELETE ON customer_time_weights TO threat_detection_user;

-- Log the creation
DO $$
BEGIN
    RAISE NOTICE 'Created customer_time_weights table with default configurations for "default" customer';
    RAISE NOTICE 'Default time weights are now stored in the "default" customer configuration';
    RAISE NOTICE 'Individual customers only need database records when customizing time weights';
END $$;