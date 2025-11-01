package com.threatdetection.ingestion.service;

import org.springframework.stereotype.Service;

/**
 * APT时序累积服务接口
 *
 * <p>由于APT时序累积服务在threat-assessment微服务中，
 * 此接口提供本地代理调用。
 *
 * <p>TODO: 实现跨服务调用（Feign客户端或REST调用）
 */
@Service
public class AptTemporalAccumulationService {

    /**
     * 合并APT积累数据
     *
     * @param logs 日志列表
     */
    public void mergeAptAccumulations(java.util.List<String> logs) {
        // TODO: 实现跨服务调用threat-assessment服务的APT积累合并
        // 目前记录日志，后续实现实际调用
    }
}