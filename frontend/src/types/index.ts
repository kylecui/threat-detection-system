/**
 * 威胁检测系统 - TypeScript类型定义
 *
 * 基于蜜罐机制的威胁评估系统数据结构
 */

// ============================================================
// 枚举
// ============================================================

/** 威胁等级 */
export enum ThreatLevel {
  INFO = 'INFO',
  LOW = 'LOW',
  MEDIUM = 'MEDIUM',
  HIGH = 'HIGH',
  CRITICAL = 'CRITICAL',
}

/** 告警状态 */
export enum AlertStatus {
  NEW = 'NEW',
  IN_PROGRESS = 'IN_PROGRESS',
  DEDUPLICATED = 'DEDUPLICATED',
  ENRICHED = 'ENRICHED',
  NOTIFIED = 'NOTIFIED',
  ESCALATED = 'ESCALATED',
  RESOLVED = 'RESOLVED',
  ARCHIVED = 'ARCHIVED',
}

/** 告警严重程度 */
export enum AlertSeverity {
  CRITICAL = 'CRITICAL',
  HIGH = 'HIGH',
  MEDIUM = 'MEDIUM',
  LOW = 'LOW',
  INFO = 'INFO',
}

/** 客户状态 */
export enum CustomerStatus {
  ACTIVE = 'ACTIVE',
  INACTIVE = 'INACTIVE',
  SUSPENDED = 'SUSPENDED',
}

/** 订阅等级 */
export enum SubscriptionTier {
  FREE = 'FREE',
  BASIC = 'BASIC',
  PROFESSIONAL = 'PROFESSIONAL',
  ENTERPRISE = 'ENTERPRISE',
}

/** IOC类型 */
export enum IocType {
  IP_V4 = 'IP_V4',
  IP_V6 = 'IP_V6',
  CIDR = 'CIDR',
  DOMAIN = 'DOMAIN',
  FILE_HASH = 'FILE_HASH',
}

/** 威胁情报严重性 */
export enum IntelSeverity {
  CRITICAL = 'CRITICAL',
  HIGH = 'HIGH',
  MEDIUM = 'MEDIUM',
  LOW = 'LOW',
  INFO = 'INFO',
}

// ============================================================
// 威胁评估 (Threat Assessment) - 端口 8083
// ============================================================

/** Score calculation breakdown (dev mode) */
export interface ScoreBreakdown {
  baseScore: number;
  timeWeight: number;
  timeWeightNote?: string;
  ipWeight: number;
  portWeight: number;
  portWeightNote?: string;
  deviceWeight: number;
  attackSourceWeight: number;
  honeypotSensitivityWeight: number;
  combinedSegmentWeight: number;
  attackRateWeight: number;
  attackRate: number;
  mlWeight: number;
  mlEnabled: boolean;
  rawScore: number;
  normalizedScore: number;
  formula: string;
}

/** 威胁评估数据 */
export interface ThreatAssessment {
  id: number;
  customerId: string;
  attackMac: string;
  attackIp?: string;
  threatScore: number;
  threatLevel: ThreatLevel;
  attackCount: number;
  uniqueIps: number;
  uniquePorts: number;
  uniqueDevices: number;
  assessmentTime: string;
  createdAt: string;
  portList?: string;
  portRiskScore?: number;
  detectionTier?: number;
  mitigationRecommendations?: string[];
  scoreBreakdown?: ScoreBreakdown;
}

/** 按设备分组的威胁评估 */
export interface GroupedThreatResponse {
  attackMac: string;
  customerId: string;
  maxThreatLevel: string;
  maxThreatScore: number;
  assessmentCount: number;
  latestAssessmentTime: string;
  totalAttackCount: number;
  tierCount: number;
}

/** 攻击事件 */
export interface AttackEvent {
  attack_mac: string;
  attack_ip: string;
  response_ip: string;
  response_port: number;
  device_serial: string;
  customer_id: string;
  timestamp: string;
  log_time: number;
}

/** 统计数据 */
export interface Statistics {
  customerId: string;
  totalCount: number;
  criticalCount: number;
  highCount: number;
  mediumCount: number;
  lowCount: number;
  infoCount: number;
  averageThreatScore: number;
  maxThreatScore: number;
  minThreatScore: number;
  levelDistribution: Record<string, number>;
}

/** Top攻击者 (后端聚合) */
export interface TopAttacker {
  attackMac: string;
  attackIp: string;
  totalCount: number;
  maxThreatScore: number;
  maxThreatLevel: string;
}

// ============================================================
// 告警管理 (Alert Management) - 端口 8082
// ============================================================

/** 告警 */
export interface Alert {
  id: number;
  title: string;
  description: string;
  status: AlertStatus;
  severity: AlertSeverity;
  source: string;
  eventType?: string;
  metadata?: string;
  attackMac?: string;
  threatScore?: number;
  affectedAssets: string[];
  recommendations: string[];
  assignedTo?: string;
  resolution?: string;
  resolvedBy?: string;
  resolvedAt?: string;
  lastNotifiedAt?: string;
  escalationLevel: number;
  escalationReason?: string;
  createdAt: string;
  updatedAt: string;
}

/** 按设备分组的告警 */
export interface GroupedAlertResponse {
  attackMac: string;
  maxSeverity: string;
  maxThreatScore: number;
  alertCount: number;
  unresolvedCount: number;
  latestAlertTime: string;
}

/** 告警分析统计 */
export interface AlertAnalytics {
  totalAlerts: number;
  resolvedAlerts: number;
  averageResolutionTime: number;
  escalatedAlerts: number;
  bySeverity: Record<string, number>;
  byStatus: Record<string, number>;
  topSources: Array<{ source: string; count: number }>;
}

/** 通知分析统计 */
export interface NotificationAnalytics {
  totalNotifications: number;
  successCount: number;
  failureCount: number;
  byChannel: Record<string, number>;
}

/** 升级分析统计 */
export interface EscalationAnalytics {
  totalEscalations: number;
  averageEscalationTime: number;
  byLevel: Record<string, number>;
}

// ============================================================
// 客户管理 (Customer Management) - 端口 8084
// ============================================================

/** 客户 */
export interface Customer {
  id: number;
  customerId: string;
  tenantId?: number;
  name: string;
  email: string;
  phone?: string;
  address?: string;
  status: CustomerStatus;
  subscriptionTier: SubscriptionTier;
  maxDevices: number;
  currentDevices: number;
  description?: string;
  createdAt: string;
  updatedAt: string;
  createdBy: string;
  updatedBy: string;
  subscriptionStartDate?: string;
  subscriptionEndDate?: string;
  alertEnabled: boolean;
}

/** 设备 */
export interface Device {
  id: number;
  devSerial: string;
  customerId: string;
  deviceName?: string;
  deviceType?: string;
  status: string;
  ipAddress?: string;
  macAddress?: string;
  location?: string;
  firmwareVersion?: string;
  lastHeartbeat?: string;
  createdAt: string;
  updatedAt: string;
  isActive?: boolean;
  description?: string;
  realHostCount?: number;
  /** Last heartbeat accepted by the ingestion pipeline. */
  lastHeartbeatAt?: string;
  /** Last raw attack event accepted for this device. */
  lastAttackAt?: string;
  /** Most recent heartbeat or raw attack event accepted by platform storage. */
  lastIngestedAt?: string;
  attackEvents24h?: number;
  /** Last threat assessment produced from this device's attack events. */
  lastThreatAt?: string;
  /** @deprecated Compatibility alias for heartbeatState. */
  connectionState?: 'DISABLED' | 'NEVER_SEEN' | 'ONLINE' | 'DELAYED' | 'OFFLINE';
  heartbeatState?: 'DISABLED' | 'NEVER_SEEN' | 'ONLINE' | 'DELAYED' | 'OFFLINE';
  dataActivityState?: 'DISABLED' | 'NEVER_SEEN' | 'ACTIVE' | 'IDLE';
  expectedHeartbeatIntervalSeconds?: number;
  heartbeatDelayedAfterSeconds?: number;
  heartbeatOfflineAfterSeconds?: number;
  pipelineState?:
    | 'DISABLED'
    | 'WAITING_FOR_DATA'
    | 'NO_ATTACK_DATA'
    | 'RECEIVING'
    | 'PROCESSING_DELAY'
    | 'PROCESSED';
}

export interface DeviceAttackEvent {
  id: number;
  devSerial: string;
  attackMac?: string;
  attackIp?: string;
  responseIp?: string;
  responsePort?: number;
  eventTimestamp?: string;
  receivedAt?: string;
}

export interface DeviceBatchFailure {
  devSerial: string;
  reason: string;
}

export interface DeviceBatchOperationResult {
  total: number;
  succeeded: number;
  failed: number;
  successfulDevices: string[];
  failures: DeviceBatchFailure[];
}

export interface OverviewSnapshot {
  generatedAt: string;
  statistics: Statistics;
  /** API DTO (后端 TrendDataPoint)；图表使用前须经 toTrendChartData 转换 */
  trend: ThreatTrendPoint[];
  portDistribution: PortDistribution[];
  nonPortProtocols: NonPortProtocolDistribution[];
  topAttackers: TopAttacker[];
  recentThreats: ThreatAssessment[];
}

export interface NonPortProtocolDistribution {
  protocol: 'ARP' | 'ICMP' | 'ICMPv6' | 'GRE' | 'ESP' | 'AH' | 'PORT_ANOMALY' | 'OTHER' | string;
  count: number;
}

export interface PortDistribution {
  port: number;
  portName?: string | null;
  count: number;
  percentage: number;
}

/** 设备配额 */
export interface DeviceQuota {
  customerId: string;
  maxDevices: number;
  currentDevices: number;
  remainingQuota: number;
  protectedHostCount: number;
}

/** 网段权重 */
export interface NetSegmentWeight {
  id: number;
  customerId: string;
  cidr: string;
  weight: number;
  description?: string;
  createdAt: string;
  updatedAt: string;
}

/** 通知配置 */
export interface NotificationConfig {
  id?: number;
  customerId: string;
  emailEnabled: boolean;
  smsEnabled: boolean;
  slackEnabled: boolean;
  webhookEnabled: boolean;
  emailRecipients?: string[];
  smsRecipients?: string[];
  slackWebhookUrl?: string;
  slackChannel?: string;
  webhookUrl?: string;
  webhookHeaders?: Record<string, string>;
  minSeverityLevel?: 'INFO' | 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  notifyOnSeverities?: Array<'INFO' | 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'>;
  maxNotificationsPerHour?: number;
  enableRateLimiting?: boolean;
  quietHoursEnabled?: boolean;
  quietHoursStart?: string;
  quietHoursEnd?: string;
  quietHoursTimezone?: string;
  isActive?: boolean;
  description?: string;
}

// ============================================================
// 威胁情报 (Threat Intelligence) - 端口 8085
// ============================================================

/** 威胁指标 */
export interface ThreatIndicator {
  id: number;
  iocValue: string;
  iocType: IocType;
  iocInet?: string;
  indicatorType: string;
  pattern?: string;
  patternType: string;
  confidence: number;
  validFrom?: string;
  validUntil?: string;
  severity: IntelSeverity;
  sourceName?: string;
  description?: string;
  tags?: string;
  sightingCount: number;
  firstSeenAt?: string;
  lastSeenAt?: string;
  createdAt: string;
  updatedAt: string;
}

/** 威胁情报Feed */
export interface ThreatFeed {
  id: number;
  feedName: string;
  feedUrl?: string;
  feedType: string;
  enabled: boolean;
  pollIntervalHours: number;
  apiKeyEnvVar?: string;
  sourceWeight: number;
  lastPollAt?: string;
  lastPollStatus?: string;
  lastPollError?: string;
  indicatorCount: number;
  pollable?: boolean;
  capability?: 'LOCAL_WRITE' | 'SCHEDULED_AND_MANUAL_POLL' | 'UNSUPPORTED' | string;
  nonPollableReason?: string;
  pollBatchLimit?: number;
  createdAt: string;
  updatedAt: string;
}

/** IP查询结果 */
export interface IpLookupResult {
  ip: string;
  found: boolean;
  confidence: number;
  severity: string;
  intelWeight: number;
  sources: string[];
  indicatorCount: number;
  lastSeenAt?: string;
  tireScore?: number;
  tireLevel?: string;
  tireVerdict?: string;
}

/** 威胁情报统计 */
export interface IntelStatistics {
  totalIndicators: number;
  activeIndicators: number;
  bySeverity: Record<string, number>;
  bySource: Record<string, number>;
  totalFeeds: number;
  activeFeeds: number;
}

// ============================================================
// ML检测 (ML Detection) - 端口 8086
// ============================================================

/** ML模型信息 */
export interface MlModelInfo {
  tier: number;
  available: boolean;
  threshold: number;
  modelPath: string;
  bigruAvailable?: boolean;
  bigruModelPath?: string;
  optimalAlpha?: number;
}

/** ML健康状态 */
export interface MlHealthStatus {
  status: string;
  modelLoaded: boolean;
  modelsAvailable: Record<string, boolean>;
  kafkaConnected: boolean;
}

/** ML模型重载结果 */
export interface MlReloadResult {
  status: string;
  reloadCount?: number;
  modelsLoaded?: Record<string, boolean>;
  error?: string;
}

/** ML序列缓冲区统计 */
export interface MlBufferStats {
  enabled: boolean;
  totalKeys: number;
  totalWindows: number;
}

/** ML漂移状态 */
export interface MlDriftStatus {
  enabled: boolean;
  [key: string]: unknown;
}

/** ML影子评分统计 */
export interface MlShadowStats {
  enabled: boolean;
  challengerDir?: string;
  challengerLoaded?: boolean;
  totalComparisons: number;
  [key: string]: unknown;
}

/** ML训练状态 */
export interface MlTrainingStatus {
  training: boolean;
  tiers: number[];
  startedAt?: string;
  completedAt?: string;
  elapsedSeconds?: number;
  results?: Record<number, Record<string, unknown>>;
  error?: string;
  currentTier?: number | null;
  completedTiers?: number;
}

/** ML训练数据就绪状态 */
export interface MlDataReadiness {
  ready: boolean;
  sampleCounts?: Record<number, number>;
  minimumRequired?: { autoencoder: number; bigru: number };
  error?: string;
}

// ============================================================
// 系统监控
// ============================================================

/** 服务健康状态 */
export interface ServiceHealth {
  name: string;
  port: number;
  status: 'UP' | 'DOWN' | 'UNKNOWN';
  responseTime?: number;
  details?: Record<string, unknown>;
  lastChecked: string;
}

/** 数据摄取统计 */
export interface DataIngestionStats {
  totalEvents: number;
  eventsPerSecond: number;
  kafkaLag?: number;
  lastEventTime?: string;
}

// ============================================================
// 通用
// ============================================================

/** API响应包装 */
export interface ApiResponse<T> {
  success: boolean;
  data: T;
  message?: string;
  timestamp: string;
}

/** 分页参数 */
export interface PaginationParams {
  page: number;
  size: number;
}

/** 分页响应 (Spring Data Page格式) */
export interface PaginatedResponse<T> {
  content: T[];
  pageable: {
    pageNumber: number;
    pageSize: number;
    offset: number;
    paged: boolean;
    unpaged: boolean;
  };
  totalElements: number;
  totalPages: number;
  last: boolean;
  first: boolean;
  size: number;
  number: number;
  numberOfElements: number;
  empty: boolean;
}

/** 查询过滤器 */
export interface ThreatQueryFilter extends PaginationParams {
  customer_id?: string;
  threat_level?: ThreatLevel;
  start_time?: string;
  end_time?: string;
  attack_mac?: string;
  detection_tier?: 1 | 2 | 3;
}

/** 告警查询过滤器 */
export interface AlertQueryFilter {
  customerId?: string;
  status?: AlertStatus;
  severity?: AlertSeverity;
  startTime?: string;
  endTime?: string;
  page?: number;
  size?: number;
  sortBy?: string;
  sortDir?: 'ASC' | 'DESC';
}

/** 时间范围 */
export interface TimeRange {
  start: string;
  end: string;
}

/**
 * 威胁趋势点（API 传输类型，INTERACTION-008）
 *
 * 与后端 `com.threatdetection.assessment.dto.TrendDataPoint` 一一对应，由
 * Overview 快照的 `trend`、`GET /api/v1/assessment/trend` 与
 * `GET /api/v1/assessment/trend/tenant` 返回。后端对空时间桶也会补齐全部
 * 字段（数值为 0），因此全部字段必填。不要直接交给图表；在展示边界用
 * `pages/Overview/trend.ts` 的 `toTrendChartData` 转换为 `ChartDataPoint`。
 */
export interface ThreatTrendPoint {
  /** 时间桶起点，ISO-8601 字符串 */
  timestamp: string;
  /** 该时间桶内的威胁评估数量 */
  count: number;
  averageScore: number;
  maxScore: number;
  criticalCount: number;
  highCount: number;
  mediumCount: number;
}

/** 图表数据点（前端展示模型，不是 API 响应） */
export interface ChartDataPoint {
  time: string;
  value: number;
  category?: string;
}

// ============================================================
// 系统配置 (System Config) — TIRE / LLM / 通用
// ============================================================

/** 系统配置项 */
export interface SystemConfig {
  id: number;
  key: string;
  value: string;
  category: string;
  description: string;
  isSecret: boolean;
  hasValue: boolean;
  updatedAt: string;
}

/** 系统配置分类 */
export type SystemConfigCategory = 'tire_api_keys' | 'llm' | 'tire_general' | 'tire_plugins';

// ============================================================
// SMTP配置 (Alert Management)
// ============================================================

/** SMTP配置 */
export interface SmtpConfig {
  configured: boolean;
  id?: number;
  configName?: string;
  host?: string;
  port?: number;
  username?: string;
  password?: string;
  passwordConfigured: boolean;
  fromAddress?: string;
  fromName?: string;
  encryption: 'NONE' | 'TLS' | 'STARTTLS' | 'SSL';
  active: boolean;
  isDefault: boolean;
  createdAt?: string;
  updatedAt?: string;
}

// ============================================================
// 多租户管理 (Multi-Tenant RBAC)
// ============================================================

/** 租户状态 */
export enum TenantStatus {
  ACTIVE = 'ACTIVE',
  SUSPENDED = 'SUSPENDED',
  DISABLED = 'DISABLED',
}

/** 用户角色 */
export enum UserRole {
  SUPER_ADMIN = 'SUPER_ADMIN',
  TENANT_ADMIN = 'TENANT_ADMIN',
  CUSTOMER_ADMIN = 'CUSTOMER_ADMIN',
  CUSTOMER_USER = 'CUSTOMER_USER',
}

/** 租户 */
export interface Tenant {
  id: number;
  tenantId: string;
  name: string;
  description?: string;
  contactEmail?: string;
  status: TenantStatus;
  maxCustomers: number;
  createdAt: string;
  updatedAt: string;
}

/** 创建租户请求 */
export interface CreateTenantRequest {
  tenantId: string;
  name: string;
  description?: string;
  contactEmail?: string;
  maxCustomers?: number;
}

/** 更新租户请求 */
export interface UpdateTenantRequest {
  name?: string;
  description?: string;
  contactEmail?: string;
  status?: TenantStatus;
  maxCustomers?: number;
}

/** 用户管理 - 用户 */
export interface ManagedUser {
  id: number;
  username: string;
  displayName?: string;
  email?: string;
  customerId?: string;
  tenantId?: number;
  enabled: boolean;
  roles: string[];
  createdAt: string;
  updatedAt: string;
}

/** 创建用户请求 */
export interface CreateUserRequest {
  username: string;
  password: string;
  displayName?: string;
  email?: string;
  customerId?: string;
  tenantId?: number;
  role: string;
}

/** 更新用户请求 */
export interface UpdateUserRequest {
  displayName?: string;
  email?: string;
  customerId?: string;
  password?: string;
  enabled?: boolean;
  role?: string;
}

// ============================================================
// 管道健康 (Pipeline Health)
// ============================================================

/** 单个服务状态 (来自后端聚合接口) */
export interface PipelineServiceStatus {
  status: 'UP' | 'DOWN';
  latencyMs?: number;
}

/** 管道健康响应 */
export interface PipelineHealthResponse {
  status: 'healthy' | 'degraded' | 'unhealthy';
  timestamp: string;
  services: Record<string, PipelineServiceStatus>;
  pipeline: {
    lastEventReceived: string | null;
    eventsLastHour: number | null;
    kafkaLag: number | null;
    flinkRunning: boolean | null;
  };
}
