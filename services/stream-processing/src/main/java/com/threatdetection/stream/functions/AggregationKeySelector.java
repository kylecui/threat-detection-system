package com.threatdetection.stream.functions;

import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.api.java.tuple.Tuple2;

/**
 * 聚合键选择器 - 用于按 customerId:attackMac 分组
 * 
 * <p>独立公共类确保Flink Application Mode可以正确序列化和分发
 */
public class AggregationKeySelector implements KeySelector<Tuple2<String, String>, String> {
    
    private static final long serialVersionUID = 1L;
    
    @Override
    public String getKey(Tuple2<String, String> tuple) throws Exception {
        return tuple.f0; // customerId:attackMac
    }
}
