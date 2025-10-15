package com.threatdetection.stream.functions;

import com.threatdetection.stream.model.AttackEvent;
import org.apache.flink.api.java.functions.KeySelector;

/**
 * 攻击事件键选择器
 * 
 * <p>按 customerId:attackMac 分组
 */
public class AttackEventKeySelector implements KeySelector<AttackEvent, String> {
    
    private static final long serialVersionUID = 1L;
    
    @Override
    public String getKey(AttackEvent event) throws Exception {
        return event.getCustomerId() + ":" + event.getAttackMac();
    }
}
