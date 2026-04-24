package com.threatdetection.customer.device.dto;

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
    private int total;

    /**
     * 成功数
     */
    private int succeeded;

    /**
     * 失败数
     */
    private int failed;

    /**
     * 成功的设备列表
     */
    @Builder.Default
    private List<String> successfulDevices = new ArrayList<>();

    /**
     * 失败的设备及原因
     */
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
        private String devSerial;

        private String reason;
    }
}
