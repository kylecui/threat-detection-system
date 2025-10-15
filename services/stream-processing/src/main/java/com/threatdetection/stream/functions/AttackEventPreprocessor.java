package com.threatdetection.stream.functions;

import com.threatdetection.stream.model.AttackEvent;
import org.apache.flink.api.common.functions.MapFunction;

/**
 * 攻击事件预处理器
 * 
 * <p>确保所有事件字段非空，为后续聚合做准备
 */
public class AttackEventPreprocessor implements MapFunction<AttackEvent, AttackEvent> {
    
    private static final long serialVersionUID = 1L;
    
    @Override
    public AttackEvent map(AttackEvent event) throws Exception {
        // 确保关键字段非空
        if (event.getCustomerId() == null) {
            event.setCustomerId("unknown");
        }
        if (event.getAttackMac() == null) {
            event.setAttackMac("unknown");
        }
        return event;
    }
}
