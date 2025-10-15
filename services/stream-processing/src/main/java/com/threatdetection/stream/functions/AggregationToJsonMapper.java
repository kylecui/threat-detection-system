package com.threatdetection.stream.functions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.threatdetection.stream.model.AggregatedAttackData;
import org.apache.flink.api.common.functions.MapFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 将聚合数据转换为JSON字符串
 */
public class AggregationToJsonMapper implements MapFunction<AggregatedAttackData, String> {
    
    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(AggregationToJsonMapper.class);
    
    private transient ObjectMapper objectMapper;
    
    @Override
    public String map(AggregatedAttackData aggregation) throws Exception {
        if (objectMapper == null) {
            objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        }
        
        try {
            return objectMapper.writeValueAsString(aggregation);
        } catch (Exception e) {
            logger.error("Failed to serialize aggregation: customerId={}, attackMac={}", 
                aggregation.getCustomerId(), aggregation.getAttackMac(), e);
            throw e;
        }
    }
}
