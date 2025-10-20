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
  customer_id: string;
  attack_mac: string;           // 被诱捕者MAC (内网失陷主机)
  threat_score: number;
  threat_level: ThreatLevel;
  attack_count: number;         // 对诱饵的探测次数
  unique_ips: number;           // 访问的诱饵IP数量 (横向移动范围)
  unique_ports: number;         // 尝试的端口种类 (攻击意图多样性)
  unique_devices: number;       // 检测到该攻击者的设备数
  assessment_time: string;      // ISO 8601格式
  created_at: string;
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
  total_threats: number;
  critical_threats: number;
  high_threats: number;
  medium_threats: number;
  low_threats: number;
  info_threats: number;
  avg_threat_score: number;
  total_attack_count: number;
  unique_attackers: number;
  unique_targets: number;
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
 * 分页响应
 */
export interface PaginatedResponse<T> {
  items: T[];
  total: number;
  page: number;
  page_size: number;
  total_pages: number;
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
