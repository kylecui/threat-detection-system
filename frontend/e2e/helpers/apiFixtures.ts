/**
 * INTERACTION-003: typed E2E API fixtures.
 *
 * Every fixture is checked against the production response type with
 * `satisfies`, so a renamed/removed field fails `npm run typecheck:e2e`
 * instead of silently rendering a broken page. Enums are value imports
 * (string enums do not accept plain string literals); everything else is
 * `import type` so specs gain no runtime dependency on production code.
 */
import {
  AlertSeverity,
  AlertStatus,
  CustomerStatus,
  IntelSeverity,
  IocType,
  SubscriptionTier,
  TenantStatus,
  ThreatLevel,
} from "../../src/types";
import type {
  Alert,
  AlertAnalytics,
  Customer,
  Device,
  DeviceAttackEvent,
  DeviceQuota,
  EscalationAnalytics,
  GroupedAlertResponse,
  GroupedThreatResponse,
  IntelStatistics,
  IpLookupResult,
  ManagedUser,
  MlBufferStats,
  MlDataReadiness,
  MlDriftStatus,
  MlHealthStatus,
  MlModelInfo,
  MlShadowStats,
  MlTrainingStatus,
  NetSegmentWeight,
  NotificationAnalytics,
  NotificationConfig,
  OverviewSnapshot,
  PaginatedResponse,
  PipelineHealthResponse,
  SmtpConfig,
  Statistics,
  SystemConfig,
  Tenant,
  ThreatAssessment,
  ThreatTrendPoint,
  ThreatFeed,
  ThreatIndicator,
  TopAttacker,
  PortDistribution,
} from "../../src/types";
import type {
  ConfigAssignment,
  LlmProvider,
  ResolvedConfig,
  TireCustomPlugin,
  UserConfig,
} from "../../src/services/tire";
import type {
  ConfigInheritanceItem,
  EntityGroup,
  EntityGroupMember,
} from "../../src/services/configCascading";

const T0 = "2026-01-01T00:00:00Z";
const T0_PLUS_1H = "2026-01-01T01:00:00Z";

/** Spring Data page wrapper — use ONLY for endpoints typed as PaginatedResponse. */
export function page<T>(content: T[], size = 20): PaginatedResponse<T> {
  return {
    content,
    pageable: { pageNumber: 0, pageSize: size, offset: 0, paged: true, unpaged: false },
    totalElements: content.length,
    totalPages: content.length ? 1 : 0,
    last: true,
    first: true,
    size,
    number: 0,
    numberOfElements: content.length,
    empty: content.length === 0,
  };
}

// ---------------------------------------------------------------- threats
export const E2E_THREAT = {
  id: 42,
  customerId: "e2e-customer",
  assessmentTime: T0,
  attackMac: "AA:BB:CC:DD:EE:FF",
  attackIp: "203.0.113.10",
  threatLevel: ThreatLevel.HIGH,
  threatScore: 87,
  attackCount: 12,
  uniqueIps: 1,
  uniquePorts: 2,
  uniqueDevices: 1,
  portList: "22,443",
  portRiskScore: 70,
  detectionTier: 1,
  createdAt: T0,
  mitigationRecommendations: ["Block the source address"],
} satisfies ThreatAssessment;

export const GROUPED_THREAT = {
  attackMac: "AA:BB:CC:DD:EE:FF",
  customerId: "e2e-customer",
  maxThreatLevel: "HIGH",
  maxThreatScore: 87,
  assessmentCount: 3,
  latestAssessmentTime: T0,
  totalAttackCount: 36,
  tierCount: 2,
} satisfies GroupedThreatResponse;

export const STATISTICS = {
  customerId: "e2e-customer",
  totalCount: 12,
  criticalCount: 2,
  highCount: 3,
  mediumCount: 4,
  lowCount: 2,
  infoCount: 1,
  averageThreatScore: 62,
  maxThreatScore: 96,
  minThreatScore: 8,
  levelDistribution: { CRITICAL: 2, HIGH: 3, MEDIUM: 4, LOW: 2, INFO: 1 },
} satisfies Statistics;

export const TOP_ATTACKERS = [
  { attackMac: "AA:BB:CC:DD:EE:FF", attackIp: "203.0.113.10", totalCount: 36, maxThreatScore: 87, maxThreatLevel: "HIGH" },
  { attackMac: "10:E9:53:D4:67:A0", attackIp: "2001:db8:85a3::8a2e:370:7334", totalCount: 9, maxThreatScore: 41.5, maxThreatLevel: "MEDIUM" },
] satisfies TopAttacker[];

export const PORT_DISTRIBUTION = [
  { port: 443, portName: "HTTPS", count: 6, percentage: 50 },
] satisfies PortDistribution[];

/**
 * INTERACTION-008: exactly the backend `TrendDataPoint` DTO. No chart-model
 * fields (`time`/`value`): the page maps the DTO via `toTrendChartData`.
 * The second bucket is an empty one (count 0), as the backend zero-fills.
 */
export const THREAT_TREND = [
  { timestamp: T0, count: 3, averageScore: 62.5, maxScore: 87, criticalCount: 1, highCount: 1, mediumCount: 1 },
  { timestamp: T0_PLUS_1H, count: 0, averageScore: 0, maxScore: 0, criticalCount: 0, highCount: 0, mediumCount: 0 },
] satisfies ThreatTrendPoint[];

export const OVERVIEW_SNAPSHOT = {
  generatedAt: T0,
  statistics: STATISTICS,
  trend: THREAT_TREND,
  portDistribution: PORT_DISTRIBUTION,
  nonPortProtocols: [],
  topAttackers: TOP_ATTACKERS,
  recentThreats: [E2E_THREAT],
} satisfies OverviewSnapshot;

// ----------------------------------------------------------------- alerts
export const E2E_ALERT = {
  id: 7,
  title: "Lateral movement from honeypot segment with an unusually long title for overflow checks",
  description: "Repeated SSH probes",
  status: AlertStatus.NEW,
  severity: AlertSeverity.HIGH,
  source: "threat-assessment",
  attackMac: "AA:BB:CC:DD:EE:FF",
  threatScore: 87,
  affectedAssets: ["203.0.113.10"],
  recommendations: ["Isolate the host"],
  assignedTo: "Security Operations On-Call Engineer",
  escalationLevel: 0,
  createdAt: T0,
  updatedAt: T0,
} satisfies Alert;

export const GROUPED_ALERT = {
  attackMac: "AA:BB:CC:DD:EE:FF",
  maxSeverity: "HIGH",
  maxThreatScore: 87,
  alertCount: 2,
  unresolvedCount: 1,
  latestAlertTime: T0,
} satisfies GroupedAlertResponse;

export const ALERT_ANALYTICS = {
  totalAlerts: 2,
  resolvedAlerts: 1,
  averageResolutionTime: 30,
  escalatedAlerts: 0,
  bySeverity: { HIGH: 2 },
  byStatus: { NEW: 1, RESOLVED: 1 },
  topSources: [{ source: "threat-assessment", count: 2 }],
} satisfies AlertAnalytics;

export const NOTIFICATION_ANALYTICS = {
  totalNotifications: 0,
  successCount: 0,
  failureCount: 0,
  byChannel: {},
} satisfies NotificationAnalytics;

export const ESCALATION_ANALYTICS = {
  totalEscalations: 0,
  averageEscalationTime: 0,
  byLevel: {},
} satisfies EscalationAnalytics;

// --------------------------------------------------- tenants / customers
export const E2E_TENANT = {
  id: 1,
  tenantId: "e2e-tenant",
  name: "E2E Tenant",
  contactEmail: "tenant-owner@example.com",
  status: TenantStatus.ACTIVE,
  maxCustomers: 10,
  createdAt: T0,
  updatedAt: T0,
} satisfies Tenant;

export const E2E_CUSTOMER = {
  id: 1,
  customerId: "e2e-customer",
  tenantId: 1,
  name: "E2E Customer",
  email: "security@example.com",
  status: CustomerStatus.ACTIVE,
  subscriptionTier: SubscriptionTier.PROFESSIONAL,
  maxDevices: 10,
  currentDevices: 1,
  createdAt: T0,
  updatedAt: T0,
  createdBy: "e2e",
  updatedBy: "e2e",
  alertEnabled: true,
} satisfies Customer;

export const E2E_DEVICE = {
  id: 1,
  devSerial: "E2E-DEVICE-0001",
  customerId: "e2e-customer",
  status: "ACTIVE",
  isActive: true,
  description: "Honeypot sensor in the north datacenter rack with a deliberately long description",
  realHostCount: 12,
  heartbeatState: "ONLINE",
  dataActivityState: "ACTIVE",
  pipelineState: "PROCESSED",
  lastIngestedAt: T0,
  createdAt: T0,
  updatedAt: T0,
} satisfies Device;

export const DEVICE_QUOTA = {
  customerId: "e2e-customer",
  maxDevices: 10,
  currentDevices: 1,
  remainingQuota: 9,
  protectedHostCount: 12,
} satisfies DeviceQuota;

export const DEVICE_EVENT = {
  id: 1,
  devSerial: "E2E-DEVICE-0001",
  attackMac: "AA:BB:CC:DD:EE:FF",
  attackIp: "203.0.113.10",
  responseIp: "2001:db8:85a3::8a2e:370:7334",
  responsePort: 443,
  eventTimestamp: T0,
  receivedAt: T0,
} satisfies DeviceAttackEvent;

export const NOTIFICATION_CONFIG = {
  customerId: "e2e-customer",
  emailEnabled: false,
  smsEnabled: false,
  slackEnabled: false,
  webhookEnabled: false,
} satisfies NotificationConfig;

export const NET_WEIGHTS = [] satisfies NetSegmentWeight[];

export const SMTP_CONFIG = {
  configured: false,
  passwordConfigured: false,
  encryption: "TLS",
  active: false,
  isDefault: true,
} satisfies SmtpConfig;

export const MANAGED_USERS = [
  {
    id: 11,
    username: "analyst.with.a.long.username",
    displayName: "Security Analyst",
    email: "analyst@example.com",
    customerId: "e2e-customer",
    tenantId: 1,
    enabled: true,
    roles: ["CUSTOMER_ADMIN", "CUSTOMER_USER"],
    createdAt: T0,
    updatedAt: T0,
  },
] satisfies ManagedUser[];

// ----------------------------------------------------------- threat intel
export const THREAT_INDICATOR = {
  id: 5,
  iocValue: "198.51.100.23",
  iocType: IocType.IP_V4,
  indicatorType: "ip",
  patternType: "stix",
  confidence: 80,
  severity: IntelSeverity.HIGH,
  sourceName: "Internal honeypot correlation feed",
  sightingCount: 3,
  lastSeenAt: T0,
  createdAt: T0,
  updatedAt: T0,
} satisfies ThreatIndicator;

export const THREAT_FEEDS = [
  {
    id: 1,
    feedName: "Abuse IP blocklist",
    feedUrl: "https://feeds.example.com/very/long/path/to/the/blocklist.json",
    feedType: "EXTERNAL",
    enabled: true,
    pollIntervalHours: 6,
    sourceWeight: 1,
    lastPollAt: T0,
    lastPollStatus: "SUCCESS",
    indicatorCount: 120,
    pollable: true,
    createdAt: T0,
    updatedAt: T0,
  },
  {
    id: 2,
    feedName: "Internal",
    feedType: "INTERNAL",
    enabled: true,
    pollIntervalHours: 0,
    sourceWeight: 1,
    indicatorCount: 4,
    pollable: false,
    createdAt: T0,
    updatedAt: T0,
  },
] satisfies ThreatFeed[];

export const INTEL_STATISTICS = {
  totalIndicators: 124,
  activeIndicators: 120,
  bySeverity: { HIGH: 4 },
  bySource: { internal: 4 },
  totalFeeds: 2,
  activeFeeds: 2,
} satisfies IntelStatistics;

export const IP_LOOKUP = {
  ip: "198.51.100.23",
  found: false,
  confidence: 0,
  severity: "INFO",
  intelWeight: 0,
  sources: [],
  indicatorCount: 0,
} satisfies IpLookupResult;

// --------------------------------------------------------------- pipeline
export const PIPELINE_HEALTH = {
  status: "healthy",
  timestamp: T0,
  services: {
    "threat-assessment-service-with-a-long-identifier": { status: "UP", latencyMs: 42 },
    "alert-management": { status: "UP", latencyMs: 180 },
    "ml-detection": { status: "DOWN" },
  },
  pipeline: {
    lastEventReceived: T0,
    eventsLastHour: 12,
    kafkaLag: 0,
    flinkRunning: true,
  },
} satisfies PipelineHealthResponse;

// --------------------------------------------------------------------- ML
export const ML_HEALTH = {
  status: "UP",
  modelLoaded: true,
  modelsAvailable: { "1": true, "2": true, "3": false },
  kafkaConnected: true,
} satisfies MlHealthStatus;

export const ML_MODELS = [
  { tier: 1, available: true, threshold: 0.0421, modelPath: "/opt/models/tier1/autoencoder-with-a-very-long-file-name-v2026.onnx", bigruAvailable: true, optimalAlpha: 0.65 },
  { tier: 2, available: true, threshold: 0.0388, modelPath: "/opt/models/tier2/autoencoder.onnx", bigruAvailable: false },
  { tier: 3, available: false, threshold: 0, modelPath: "" },
] satisfies MlModelInfo[];

export const ML_BUFFER = { enabled: true, totalKeys: 4, totalWindows: 16 } satisfies MlBufferStats;
export const ML_DRIFT = { enabled: false } satisfies MlDriftStatus;
export const ML_SHADOW = { enabled: false, totalComparisons: 0 } satisfies MlShadowStats;
export const ML_TRAINING = { training: false, tiers: [] } satisfies MlTrainingStatus;
export const ML_READINESS = {
  ready: false,
  sampleCounts: { 1: 10, 2: 0, 3: 0 },
  minimumRequired: { autoencoder: 100, bigru: 200 },
} satisfies MlDataReadiness;

// ------------------------------------------------------- config / AI / TIRE
export const SYSTEM_CONFIGS = [] satisfies SystemConfig[];

export const LLM_PROVIDERS = [
  {
    id: 1,
    name: "Primary provider",
    model: "gpt-like-model-with-a-long-identifier",
    baseUrl: "https://llm.example.com/v1/a/very/long/base/url/path",
    hasApiKey: true,
    apiKey: "",
    isDefault: true,
    enabled: true,
    ownerType: "SYSTEM",
    ownerId: null,
    createdAt: T0,
    updatedAt: T0,
  },
] satisfies LlmProvider[];

export const CONFIG_ASSIGNMENTS = [
  {
    id: 1,
    customerId: "e2e-customer",
    llmProviderId: 1,
    hasTireApiKeys: false,
    lockLlm: true,
    lockTire: false,
    assignedBy: 1,
    createdAt: T0,
  },
] satisfies ConfigAssignment[];

export const TIRE_PLUGINS = [
  {
    id: 1,
    name: "Reputation lookup",
    slug: "reputation-lookup",
    description: "",
    pluginUrl: "https://plugins.example.com/reputation/lookup/with/a/long/path",
    hasApiKey: false,
    apiKey: "",
    authType: "NONE",
    authHeader: "",
    parserType: "JSON",
    requestMethod: "GET",
    requestBody: null,
    responsePath: null,
    enabled: true,
    priority: 10,
    timeout: 5,
    ownerType: "SYSTEM",
    ownerId: null,
    createdAt: T0,
    updatedAt: T0,
  },
] satisfies TireCustomPlugin[];

export const USER_CONFIG = { userId: 1, useOwnLlm: false, useOwnTire: false } satisfies UserConfig;
export const RESOLVED_CONFIG = {
  userId: 1,
  llmSource: "admin_assigned",
  tireSource: "admin_assigned",
} satisfies ResolvedConfig;

// --------------------------------------------------- config cascading
export const ENTITY_GROUPS = [
  {
    id: 1,
    groupName: "North region customers with a long group name",
    groupType: "customer_group",
    description: "Customers served by the north datacenter",
    tenantId: 1,
    createdBy: 1,
    createdAt: T0,
    updatedAt: T0,
  },
] satisfies EntityGroup[];

export const ENTITY_GROUP_MEMBERS = [
  { id: 1, groupId: 1, entityId: 101, entityType: "customer", addedAt: T0 },
  { id: 2, groupId: 1, entityId: 102, entityType: "customer", addedAt: T0 },
] satisfies EntityGroupMember[];

export const CONFIG_LOCKS = [
  {
    id: 1,
    entityType: "customer",
    entityId: 101,
    configType: "llm_provider_assignment_policy",
    lockMode: "inherit_only",
    lockedBy: 1,
    lockedAt: T0,
    createdAt: T0,
    updatedAt: T0,
  },
] satisfies ConfigInheritanceItem[];
