package com.threatdetection.ingestion.controller;

import com.threatdetection.ingestion.model.ImportMode;
import com.threatdetection.ingestion.model.ImportRequest;
import com.threatdetection.ingestion.model.ImportResult;
import com.threatdetection.ingestion.service.ScenarioAwareImportService;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 场景感知导入控制器
 *
 * <p>提供不同导入场景的REST API接口：
 * - 系统迁移导入
 * - 数据补全导入
 * - 离线分析导入
 *
 * <p>支持批量日志导入，自动去重和场景特定的数据合并
 */
@RestController
@RequestMapping("/api/v1/import")
public class ImportController {

    private static final Logger logger = LoggerFactory.getLogger(ImportController.class);

    private final ScenarioAwareImportService importService;

    public ImportController(ScenarioAwareImportService importService) {
        this.importService = importService;
    }

    /**
     * 通用场景导入接口
     *
     * @param request 导入请求
     * @return 导入结果
     */
    @PostMapping("/scenario")
    public ResponseEntity<ImportResult> importWithScenario(@RequestBody ImportRequest request) {
        logger.info("Received scenario import request: mode={}, customerId={}, logCount={}",
                   request.getMode(), request.getCustomerId(),
                   request.getLogs() != null ? request.getLogs().size() : 0);

        try {
            // 验证请求
            if (!request.isValid()) {
                logger.warn("Invalid import request");
                return ResponseEntity.badRequest().body(
                    ImportResult.error(0, "Invalid request: check mode and logs", 0)
                );
            }

            // 处理导入
            ImportResult result = importService.processBatchWithMode(
                request.getLogs(),
                request.getMode(),
                request.getCustomerId()
            );

            // 根据结果返回适当的状态码
            if (result.isSuccess()) {
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.badRequest().body(result);
            }

        } catch (IllegalArgumentException e) {
            logger.warn("Invalid import mode: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                ImportResult.error(request.getLogs().size(), "Invalid mode: " + e.getMessage(), 0)
            );
        } catch (Exception e) {
            logger.error("Import processing failed", e);
            return ResponseEntity.internalServerError().body(
                ImportResult.error(request.getLogs().size(), "Internal error: " + e.getMessage(), 0)
            );
        }
    }

    /**
     * 系统迁移导入接口
     *
     * <p>适用于从旧系统迁移数据，合并历史APT积累
     */
    @PostMapping("/migration")
    public ResponseEntity<ImportResult> importMigration(@RequestBody List<String> logs) {
        logger.info("Received migration import request with {} logs", logs.size());

        try {
            ImportResult result = importService.processBatchWithMode(logs, ImportMode.MIGRATION, null);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Migration import failed", e);
            return ResponseEntity.internalServerError().body(
                ImportResult.error(logs.size(), "Migration failed: " + e.getMessage(), 0)
            );
        }
    }

    /**
     * 数据补全导入接口
     *
     * <p>适用于补全特定客户的历史数据
     */
    @PostMapping("/completion/{customerId}")
    public ResponseEntity<ImportResult> importCompletion(@PathVariable String customerId,
                                                        @RequestBody List<String> logs) {
        logger.info("Received completion import request for customer {} with {} logs",
                   customerId, logs.size());

        try {
            ImportResult result = importService.processBatchWithMode(logs, ImportMode.COMPLETION, customerId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Completion import failed for customer {}", customerId, e);
            return ResponseEntity.internalServerError().body(
                ImportResult.error(logs.size(), "Completion failed: " + e.getMessage(), 0)
            );
        }
    }

    /**
     * 离线分析导入接口
     *
     * <p>适用于安全研究和威胁情报分析，不影响生产数据
     */
    @PostMapping("/offline")
    public ResponseEntity<ImportResult> importOffline(@RequestBody List<String> logs) {
        logger.info("Received offline import request with {} logs", logs.size());

        try {
            ImportResult result = importService.processBatchWithMode(logs, ImportMode.OFFLINE, null);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Offline import failed", e);
            return ResponseEntity.internalServerError().body(
                ImportResult.error(logs.size(), "Offline import failed: " + e.getMessage(), 0)
            );
        }
    }

    /**
     * 获取支持的导入模式
     */
    @GetMapping("/modes")
    public ResponseEntity<List<String>> getSupportedModes() {
        List<String> modes = List.of(
            ImportMode.MIGRATION.getValue(),
            ImportMode.COMPLETION.getValue(),
            ImportMode.OFFLINE.getValue()
        );
        return ResponseEntity.ok(modes);
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Scenario Aware Import Service is healthy");
    }
}