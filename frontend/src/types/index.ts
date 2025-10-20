/**
 * 威胁检测系统 - TypeScript类型定义
 * 
 * 基于蜜罐机制的威胁评估系统数据结构
 */

/**
 * 威胁等级
 */
export enum ThreatLevel {
  INFO = 'INFO',
  LOW = 'LOW',
  MEDIUM = 'MEDIUM',
  HIGH = 'HIGH',
  CRITICAL = 'CRITICAL',
}

/**
 * 威胁评估数据
 */
export interface ThreatAssessment {
  id: number;
  customerId: string;
  attackMac: string;            // 被诱捕者MAC (内网失陷主机)
  attackIp?: string;            // 被诱捕者IP (可选)
  threatScore: number;
  threatLevel: ThreatLevel;
  attackCount: number;          // 对诱饵的探测次数
  uniqueIps: number;            // 访问的诱饵IP数量 (横向移动范围)
  uniquePorts: number;          // 尝试的端口种类 (攻击意图多样性)
  uniqueDevices: number;        // 检测到该攻击者的设备数
  assessmentTime: string;       // ISO 8601格式
  createdAt: string;
  portList?: string;            // 端口列表 (可选)
  portRiskScore?: number;       // 端口风险分数 (可选)
  detectionTier?: number;       // 检测层级 (可选)
  mitigationRecommendations?: string[];  // 缓解建议 (可选)
}

/**
 * 攻击事件
 */
export interface AttackEvent {
  attack_mac: string;           // 被诱捕者MAC
  attack_ip: string;            // 被诱捕者IP (内网地址)
  response_ip: string;          // 诱饵IP (不存在的虚拟哨兵)
  response_port: number;        // 攻击者尝试的端口 (暴露攻击意图)
  device_serial: string;        // 终端蜜罐设备序列号
  customer_id: string;
  timestamp: string;
  log_time: number;
}

/**
 * 统计数据
 */
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

/**
 * API响应包装
 */
export interface ApiResponse<T> {
  success: boolean;
  data: T;
  message?: string;
  timestamp: string;
}

/**
 * 分页参数
 */
export interface PaginationParams {
  page: number;
  page_size: number;
  sort_by?: string;
  sort_order?: 'asc' | 'desc';
}

/**
 * 分页响应 (Spring Data Page格式)
 */
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

/**
 * 查询过滤器
 */
export interface ThreatQueryFilter extends PaginationParams {
  customer_id?: string;
  threat_level?: ThreatLevel;
  start_time?: string;
  end_time?: string;
  attack_mac?: string;
}

/**
 * 时间范围
 */
export interface TimeRange {
  start: string;
  end: string;
}

/**
 * 图表数据点
 */
export interface ChartDataPoint {
  time: string;
  value: number;
  category?: string;
}
