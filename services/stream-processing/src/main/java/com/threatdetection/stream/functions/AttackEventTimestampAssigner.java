package com.threatdetection.stream.functions;

import com.threatdetection.stream.model.AttackEvent;
import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;

import java.time.Instant;

public class AttackEventTimestampAssigner implements SerializableTimestampAssigner<AttackEvent> {
    
    private static final long serialVersionUID = 1L;
    private static final long MIN_VALID_EPOCH_MS = Instant.parse("2020-01-01T00:00:00Z").toEpochMilli();
    
    @Override
    public long extractTimestamp(AttackEvent event, long recordTimestamp) {
        long ts = event.getTimestamp().toEpochMilli();
        if (ts < MIN_VALID_EPOCH_MS) {
            return System.currentTimeMillis();
        }
        return ts;
    }
}
