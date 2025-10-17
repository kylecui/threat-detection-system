package com.threatdetection.customer.device.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量操作响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchOperationResponse {

    /**
     * 总数
     */
    @JsonProperty("total")
    private int total;

    /**
     * 成功数
     */
    @JsonProperty("succeeded")
    private int succeeded;

    /**
     * 失败数
     */
    @JsonProperty("failed")
    private int failed;

    /**
     * 成功的设备列表
     */
    @JsonProperty("successful_devices")
    @Builder.Default
    private List<String> successfulDevices = new ArrayList<>();

    /**
     * 失败的设备及原因
     */
    @JsonProperty("failures")
    @Builder.Default
    private List<FailureDetail> failures = new ArrayList<>();

    /**
     * 失败详情
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FailureDetail {
        @JsonProperty("dev_serial")
        private String devSerial;
        
        @JsonProperty("reason")
        private String reason;
    }
}
