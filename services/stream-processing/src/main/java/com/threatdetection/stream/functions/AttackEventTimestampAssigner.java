package com.threatdetection.stream.functions;

import com.threatdetection.stream.model.AttackEvent;
import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;

/**
 * 从AttackEvent中提取事件时间戳
 * 
 * V4.0 Phase 3: 支持Event Time语义
 * 作为独立的public类以确保Flink可以正确序列化和加载
 */
public class AttackEventTimestampAssigner implements SerializableTimestampAssigner<AttackEvent> {
    
    private static final long serialVersionUID = 1L;
    
    @Override
    public long extractTimestamp(AttackEvent event, long recordTimestamp) {
        // 从AttackEvent的timestamp字段提取事件时间（毫秒）
        return event.getTimestamp().toEpochMilli();
    }
}
