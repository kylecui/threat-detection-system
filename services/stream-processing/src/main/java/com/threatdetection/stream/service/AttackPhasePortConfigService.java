package com.threatdetection.stream.service;

import com.threatdetection.stream.config.DatabaseConfig;
import com.threatdetection.stream.model.AttackPhasePortConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 攻击阶段端口配置服务 (多租户版本)
 *
 * <p>从数据库动态加载攻击阶段端口配置，支持：
 * - RECON: 侦察阶段端口
 * - EXPLOITATION: 利用阶段端口
 * - PERSISTENCE: 持久化阶段端口
 *
 * <p>多租户特性：
 * - 支持客户自定义配置
 * - 优先级: 客户自定义 > 全局默认
 * - 启动时从数据库加载所有配置
 * - 内存缓存提高性能
 */
public class AttackPhasePortConfigService {

    private static final Logger logger = LoggerFactory.getLogger(AttackPhasePortConfigService.class);

    // 缓存：客户+阶段 -> 端口集合
    private final Map<String, Set<Integer>> customerPhasePortsCache = new ConcurrentHashMap<>();

    // 缓存：客户+端口 -> 阶段列表
    private final Map<String, Set<String>> customerPortPhasesCache = new ConcurrentHashMap<>();

    // 缓存：客户+阶段 -> 配置详情
    private final Map<String, List<AttackPhasePortConfig>> customerPhaseConfigsCache = new ConcurrentHashMap<>();

    private volatile boolean initialized = false;

    public AttackPhasePortConfigService() {
        initialize();
    }

    /**
     * 初始化：从数据库加载所有配置
     */
    public synchronized void initialize() {
        if (initialized) {
            logger.debug("AttackPhasePortConfigService already initialized");
            return;
        }

        logger.info("Initializing AttackPhasePortConfigService from database...");

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT customer_id, phase, port_number, port_name, priority, enabled, description " +
                 "FROM attack_phase_port_configs " +
                 "WHERE enabled = TRUE " +
                 "ORDER BY customer_id NULLS FIRST, phase, priority DESC, port_number")) {

            try (ResultSet rs = stmt.executeQuery()) {
                loadConfigurations(rs);
            }

            initialized = true;
            logger.info("AttackPhasePortConfigService initialized successfully: {} customer configurations loaded",
                       customerPhasePortsCache.size());

        } catch (SQLException e) {
            logger.error("Failed to initialize AttackPhasePortConfigService from database", e);
            // 加载默认配置作为fallback
            loadDefaultConfigurations();
        }
    }

    /**
     * 从ResultSet加载配置
     */
    private void loadConfigurations(ResultSet rs) throws SQLException {
        Map<String, Map<String, List<AttackPhasePortConfig>>> tempConfigs = new HashMap<>();
        Map<String, Map<String, Set<Integer>>> tempPorts = new HashMap<>();
        Map<String, Map<Integer, Set<String>>> tempPortPhases = new HashMap<>();

        while (rs.next()) {
            String customerId = rs.getString("customer_id");
            String phase = rs.getString("phase");
            int portNumber = rs.getInt("port_number");
            String portName = rs.getString("port_name");
            int priority = rs.getInt("priority");
            boolean enabled = rs.getBoolean("enabled");
            String description = rs.getString("description");

            // 使用 customerId 作为缓存键，NULL 转换为 "GLOBAL"
            String cacheKey = customerId != null ? customerId : "GLOBAL";

            AttackPhasePortConfig config = new AttackPhasePortConfig(
                phase, portNumber, portName, priority, enabled, description);

            // 按客户+阶段分组配置
            tempConfigs.computeIfAbsent(cacheKey, k -> new HashMap<>())
                      .computeIfAbsent(phase, k -> new ArrayList<>())
                      .add(config);

            // 按客户+阶段分组端口
            tempPorts.computeIfAbsent(cacheKey, k -> new HashMap<>())
                    .computeIfAbsent(phase, k -> new HashSet<>())
                    .add(portNumber);

            // 按客户+端口分组阶段
            tempPortPhases.computeIfAbsent(cacheKey, k -> new HashMap<>())
                         .computeIfAbsent(portNumber, k -> new HashSet<>())
                         .add(phase);
        }

        // 转换为最终缓存格式
        for (Map.Entry<String, Map<String, List<AttackPhasePortConfig>>> customerEntry : tempConfigs.entrySet()) {
            String customerKey = customerEntry.getKey();
            for (Map.Entry<String, List<AttackPhasePortConfig>> phaseEntry : customerEntry.getValue().entrySet()) {
                String phaseKey = customerKey + ":" + phaseEntry.getKey();
                customerPhaseConfigsCache.put(phaseKey, phaseEntry.getValue());
            }
        }

        for (Map.Entry<String, Map<String, Set<Integer>>> customerEntry : tempPorts.entrySet()) {
            String customerKey = customerEntry.getKey();
            for (Map.Entry<String, Set<Integer>> phaseEntry : customerEntry.getValue().entrySet()) {
                String phaseKey = customerKey + ":" + phaseEntry.getKey();
                customerPhasePortsCache.put(phaseKey, phaseEntry.getValue());
            }
        }

        for (Map.Entry<String, Map<Integer, Set<String>>> customerEntry : tempPortPhases.entrySet()) {
            String customerKey = customerEntry.getKey();
            for (Map.Entry<Integer, Set<String>> portEntry : customerEntry.getValue().entrySet()) {
                String portKey = customerKey + ":" + portEntry.getKey();
                customerPortPhasesCache.put(portKey, portEntry.getValue());
            }
        }

        logger.info("Loaded configurations: {} customer configs, {} total ports",
                   tempConfigs.size(),
                   tempPortPhases.values().stream().mapToInt(Map::size).sum());
    }

    /**
     * 加载默认配置（数据库连接失败时的fallback）
     */
    private void loadDefaultConfigurations() {
        logger.warn("Loading default attack phase port configurations as fallback");

        String globalKey = "GLOBAL";

        // RECON阶段默认端口
        Set<Integer> reconPorts = Set.of(21, 22, 23, 25, 53, 80, 110, 143, 443, 993, 995);
        customerPhasePortsCache.put(globalKey + ":RECON", new HashSet<>(reconPorts));

        // EXPLOITATION阶段默认端口
        Set<Integer> exploitationPorts = Set.of(135, 139, 445, 3389, 5985, 5986);
        customerPhasePortsCache.put(globalKey + ":EXPLOITATION", new HashSet<>(exploitationPorts));

        // PERSISTENCE阶段默认端口
        Set<Integer> persistencePorts = Set.of(3306, 5432, 6379, 27017, 1433);
        customerPhasePortsCache.put(globalKey + ":PERSISTENCE", new HashSet<>(persistencePorts));

        // 构建端口到阶段的映射
        for (int port : reconPorts) {
            String portKey = globalKey + ":" + port;
            customerPortPhasesCache.computeIfAbsent(portKey, k -> new HashSet<>()).add("RECON");
        }
        for (int port : exploitationPorts) {
            String portKey = globalKey + ":" + port;
            customerPortPhasesCache.computeIfAbsent(portKey, k -> new HashSet<>()).add("EXPLOITATION");
        }
        for (int port : persistencePorts) {
            String portKey = globalKey + ":" + port;
            customerPortPhasesCache.computeIfAbsent(portKey, k -> new HashSet<>()).add("PERSISTENCE");
        }

        logger.info("Default configurations loaded: {} phases", 3);
    }

    /**
     * 获取指定客户和阶段的所有端口
     * @param customerId 客户ID (null表示全局默认)
     * @param phase 阶段名称
     * @return 端口集合
     */
    public Set<Integer> getCustomerPhasePorts(String customerId, String phase) {
        ensureInitialized();

        // 首先尝试客户自定义配置
        String customKey = (customerId != null ? customerId : "GLOBAL") + ":" + phase;
        Set<Integer> customPorts = customerPhasePortsCache.get(customKey);
        if (customPorts != null) {
            return customPorts;
        }

        // 回退到全局默认配置
        String globalKey = "GLOBAL:" + phase;
        return customerPhasePortsCache.getOrDefault(globalKey, Collections.emptySet());
    }

    /**
     * 获取指定客户和端口所属的所有阶段
     * @param customerId 客户ID (null表示全局默认)
     * @param portNumber 端口号
     * @return 阶段集合
     */
    public Set<String> getCustomerPortPhases(String customerId, int portNumber) {
        ensureInitialized();

        // 首先尝试客户自定义配置
        String customKey = (customerId != null ? customerId : "GLOBAL") + ":" + portNumber;
        Set<String> customPhases = customerPortPhasesCache.get(customKey);
        if (customPhases != null) {
            return customPhases;
        }

        // 回退到全局默认配置
        String globalKey = "GLOBAL:" + portNumber;
        return customerPortPhasesCache.getOrDefault(globalKey, Collections.emptySet());
    }

    /**
     * 检查端口是否属于指定客户的阶段
     * @param customerId 客户ID (null表示全局默认)
     * @param portNumber 端口号
     * @param phase 阶段名称
     * @return 是否属于
     */
    public boolean isPortInCustomerPhase(String customerId, int portNumber, String phase) {
        ensureInitialized();
        Set<Integer> phasePorts = getCustomerPhasePorts(customerId, phase);
        return phasePorts.contains(portNumber);
    }

    /**
     * 获取指定客户和阶段的配置详情
     * @param customerId 客户ID (null表示全局默认)
     * @param phase 阶段名称
     * @return 配置列表
     */
    public List<AttackPhasePortConfig> getCustomerPhaseConfigs(String customerId, String phase) {
        ensureInitialized();

        // 首先尝试客户自定义配置
        String customKey = (customerId != null ? customerId : "GLOBAL") + ":" + phase;
        List<AttackPhasePortConfig> customConfigs = customerPhaseConfigsCache.get(customKey);
        if (customConfigs != null) {
            return customConfigs;
        }

        // 回退到全局默认配置
        String globalKey = "GLOBAL:" + phase;
        return customerPhaseConfigsCache.getOrDefault(globalKey, Collections.emptyList());
    }

    /**
     * 获取所有支持的阶段
     * @return 阶段集合
     */
    public Set<String> getAllPhases() {
        ensureInitialized();
        Set<String> phases = new HashSet<>();
        for (String key : customerPhasePortsCache.keySet()) {
            if (key.startsWith("GLOBAL:")) {
                String phase = key.substring(7); // Remove "GLOBAL:" prefix
                phases.add(phase);
            }
        }
        return phases;
    }

    /**
     * 获取配置统计信息
     * @return 统计信息
     */
    public Map<String, Object> getStatistics() {
        ensureInitialized();

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalCustomerConfigs", customerPhasePortsCache.size());
        stats.put("totalPortMappings", customerPortPhasesCache.size());
        stats.put("initialized", initialized);

        // 统计客户数量
        Set<String> customers = new HashSet<>();
        for (String key : customerPhasePortsCache.keySet()) {
            if (key.contains(":")) {
                String customerId = key.substring(0, key.indexOf(":"));
                if (!"GLOBAL".equals(customerId)) {
                    customers.add(customerId);
                }
            }
        }
        stats.put("totalCustomers", customers.size());

        return stats;
    }

    /**
     * 确保服务已初始化
     */
    private void ensureInitialized() {
        if (!initialized) {
            initialize();
        }
    }

    /**
     * 重新加载配置（用于配置更新）
     */
    public synchronized void reload() {
        logger.info("Reloading attack phase port configurations...");
        initialized = false;
        customerPhasePortsCache.clear();
        customerPortPhasesCache.clear();
        customerPhaseConfigsCache.clear();
        initialize();
    }
}