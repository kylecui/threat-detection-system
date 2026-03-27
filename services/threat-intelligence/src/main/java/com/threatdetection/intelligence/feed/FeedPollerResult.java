package com.threatdetection.intelligence.feed;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedPollerResult {
    private String feedName;
    private boolean success;
    private int indicatorsProcessed;
    private int indicatorsNew;
    private int indicatorsUpdated;
    private long durationMs;
    private String errorMessage;
}
