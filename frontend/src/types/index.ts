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
  BASIC = 'BASIC',
  STANDARD = 'STANDARD',
  PREMIUM = 'PREMIUM',
  ENTERPRISE = 'ENTERPRISE',
}

/** IOC类型 */
export enum IocType {
  IPV4 = 'IPV4',
  IPV6 = 'IPV6',
  DOMAIN = 'DOMAIN',
  URL = 'URL',
  MD5 = 'MD5',
  SHA1 = 'SHA1',
  SHA256 = 'SHA256',
  EMAIL = 'EMAIL',
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
  customerId: string;
  emailEnabled: boolean;
  smsEnabled: boolean;
  slackEnabled: boolean;
  webhookEnabled: boolean;
  emailRecipients?: string[];
  slackWebhookUrl?: string;
  webhookUrl?: string;
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
  name: string;
  url: string;
  feedType: string;
  enabled: boolean;
  pollingIntervalMinutes: number;
  lastPolledAt?: string;
  lastSuccessAt?: string;
  indicatorCount: number;
  status: string;
  createdAt: string;
  updatedAt: string;
}

/** IP查询结果 */
export interface IpLookupResult {
  ip: string;
  found: boolean;
  indicators: ThreatIndicator[];
  reputation?: number;
  categories?: string[];
}

/** 威胁情报统计 */
export interface IntelStatistics {
  totalIndicators: number;
  activeIndicators: number;
  expiredIndicators: number;
  byType: Record<string, number>;
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
  page_size: number;
  sort_by?: string;
  sort_order?: 'asc' | 'desc';
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

/** 图表数据点 */
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
  id: number;
  host: string;
  port: number;
  username: string;
  password?: string;
  fromAddress: string;
  fromName?: string;
  encryption: 'NONE' | 'TLS' | 'SSL';
  enabled: boolean;
  isDefault: boolean;
  createdAt: string;
  updatedAt: string;
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
// 多区域部署 (Multi-Region)
// ============================================================

/** 区域标识 */
export type RegionId = 'auto' | 'east' | 'west' | 'cn';

/** 区域配置 */
export interface RegionConfig {
  id: RegionId;
  label: string;
  apiBase: string;
  description: string;
}

/** 预定义区域端点 */
export const REGION_ENDPOINTS: Record<RegionId, RegionConfig> = {
  auto: {
    id: 'auto',
    label: '自动 (Auto)',
    apiBase: '',
    description: '根据网络延迟自动选择最近区域',
  },
  east: {
    id: 'east',
    label: 'US East',
    apiBase: 'https://east.threat-detection.io',
    description: '美国东部 (主区域)',
  },
  west: {
    id: 'west',
    label: 'US West',
    apiBase: 'https://west.threat-detection.io',
    description: '美国西部 (DR备份)',
  },
  cn: {
    id: 'cn',
    label: '中国 (China)',
    apiBase: 'https://cn.threat-detection.io',
    description: '中国区域 (数据主权隔离)',
  },
};
