# 前端集成调试总结

**文档版本**: 1.0  
**最后更新**: 2025-10-20  
**状态**: ✅ 完成

---

## 一、调试成果总结

### 1.1 完成的任务

#### ✅ 后端服务重构 (Threat Assessment Service)
- 创建4个DTO类 (ThreatStatisticsResponse, TrendDataPoint, PortDistribution, ThreatAssessmentDetailResponse)
- 重写Repository层，添加12个查询方法
- 创建ThreatQueryService (280行业务逻辑)
- 重写AssessmentController，实现5个REST端点
- 修复技术问题 (Lombok依赖、Double字段映射、服务端口)

#### ✅ 前后端数据对齐
- **字段命名统一**: 后端使用驼峰命名 (camelCase)，前端类型定义完全匹配
- **分页系统对齐**: Spring Data Page从0开始，前端正确转换页码
- **响应格式标准化**: 后端直接返回数据对象，前端移除多余的包装层

#### ✅ 前端页面修复
- Dashboard页面: 统计卡片、趋势图、威胁列表、端口分布全部正常显示
- ThreatList页面: 分页、排序、删除功能正常工作
- 图表配置优化: 修复@ant-design/charts兼容性问题

#### ✅ Docker部署优化
- 后端: 正确编译jar包并构建镜像
- 前端: 卷挂载支持热更新
- 网络配置: API Gateway正确转发到后端服务

---

## 二、API标准规范

### 2.1 API端点定义

| 端点 | 方法 | 描述 | 参数 | 响应类型 |
|------|------|------|------|---------|
| `/api/v1/assessment/statistics` | GET | 获取威胁统计 | `customer_id` | ThreatStatisticsResponse |
| `/api/v1/assessment/trend` | GET | 获取24小时趋势 | `customer_id`, `hours` | TrendDataPoint[] |
| `/api/v1/assessment/port-distribution` | GET | 获取端口分布 | `customer_id` | PortDistribution[] |
| `/api/v1/assessment/assessments` | GET | 分页查询评估列表 | `customer_id`, `page`, `size` | Page\<ThreatAssessmentDetailResponse\> |
| `/api/v1/assessment/{id}` | GET | 获取评估详情 | `id` (路径参数) | ThreatAssessmentDetailResponse |
| `/api/v1/assessment/health` | GET | 健康检查 | 无 | Map\<String, String\> |

### 2.2 请求参数标准

#### 通用参数
```typescript
customer_id: string;  // 必需，客户ID (多租户隔离)
```

#### 分页参数
```typescript
page: number;        // 页码，从0开始 (Spring Data标准)
size: number;        // 每页大小，默认20
sort_by?: string;    // 排序字段，默认 'assessmentTime'
sort_order?: 'asc' | 'desc';  // 排序方向，默认 'desc'
```

#### 趋势查询参数
```typescript
customer_id: string;  // 必需
hours?: number;       // 小时数，默认24
```

### 2.3 响应数据结构

#### 统计响应 (ThreatStatisticsResponse)
```json
{
  "customerId": "demo-customer",
  "totalCount": 16,
  "criticalCount": 2,
  "highCount": 4,
  "mediumCount": 4,
  "lowCount": 4,
  "infoCount": 2,
  "averageThreatScore": 1045.625,
  "maxThreatScore": 7290.0,
  "minThreatScore": 5.0,
  "levelDistribution": {
    "CRITICAL": 2,
    "HIGH": 4,
    "MEDIUM": 4,
    "LOW": 4,
    "INFO": 2
  }
}
```

#### 趋势数据点 (TrendDataPoint)
```json
{
  "timestamp": "2025-10-20T02:00:00Z",
  "count": 2,
  "averageScore": 7290.0,
  "maxScore": 7290.0,
  "criticalCount": 2,
  "highCount": 0,
  "mediumCount": 0
}
```

#### 端口分布 (PortDistribution)
```json
{
  "port": 445,
  "portName": "445-SMB",
  "count": 8,
  "percentage": 33.33
}
```

#### 威胁评估详情 (ThreatAssessmentDetailResponse)
```json
{
  "id": 1,
  "customerId": "demo-customer",
  "attackMac": "04:42:1a:8e:e3:65",
  "attackIp": "N/A",
  "threatScore": 7290.0,
  "threatLevel": "CRITICAL",
  "attackCount": 150,
  "uniqueIps": 5,
  "uniquePorts": 3,
  "uniqueDevices": 2,
  "assessmentTime": "2025-10-20T02:02:50.632377Z",
  "createdAt": "2025-10-20T03:02:50.632377Z",
  "portList": "3389,445,22",
  "portRiskScore": 85.5,
  "detectionTier": 2,
  "mitigationRecommendations": [
    "立即隔离攻击源 04:42:1a:8e:e3:65",
    "检查同网段其他主机是否被攻陷",
    "启动应急响应流程"
  ]
}
```

#### 分页响应 (Spring Data Page)
```json
{
  "content": [/* ThreatAssessmentDetailResponse数组 */],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20,
    "sort": { "sorted": false, "unsorted": true, "empty": true },
    "offset": 0,
    "paged": true,
    "unpaged": false
  },
  "totalPages": 1,
  "totalElements": 16,
  "last": true,
  "first": true,
  "size": 20,
  "number": 0,
  "numberOfElements": 16,
  "empty": false
}
```

---

## 三、字段命名标准

### 3.1 前后端统一命名规则

**✅ 标准: 驼峰命名 (camelCase)**

#### 威胁评估字段
| 字段名 | 类型 | 说明 | 示例值 |
|--------|------|------|--------|
| `id` | number | 评估记录ID | 1 |
| `customerId` | string | 客户ID | "demo-customer" |
| `attackMac` | string | 被诱捕者MAC地址 | "04:42:1a:8e:e3:65" |
| `attackIp` | string | 被诱捕者IP (可选) | "192.168.1.100" 或 "N/A" |
| `threatScore` | number | 威胁分数 | 7290.0 |
| `threatLevel` | string | 威胁等级 | "CRITICAL" / "HIGH" / "MEDIUM" / "LOW" / "INFO" |
| `attackCount` | number | 攻击次数 | 150 |
| `uniqueIps` | number | 访问的诱饵IP数量 | 5 |
| `uniquePorts` | number | 尝试的端口种类 | 3 |
| `uniqueDevices` | number | 检测到的设备数 | 2 |
| `assessmentTime` | string | 评估时间 (ISO 8601) | "2025-10-20T02:02:50.632377Z" |
| `createdAt` | string | 创建时间 (ISO 8601) | "2025-10-20T03:02:50.632377Z" |
| `portList` | string | 端口列表 (逗号分隔) | "3389,445,22" |
| `portRiskScore` | number | 端口风险分数 | 85.5 |
| `detectionTier` | number | 检测层级 | 2 |
| `mitigationRecommendations` | string[] | 缓解建议 | ["立即隔离...", "检查..."] |

#### 统计字段
| 字段名 | 类型 | 说明 |
|--------|------|------|
| `totalCount` | number | 总威胁数量 |
| `criticalCount` | number | 严重威胁数量 |
| `highCount` | number | 高危威胁数量 |
| `mediumCount` | number | 中危威胁数量 |
| `lowCount` | number | 低危威胁数量 |
| `infoCount` | number | 信息级威胁数量 |
| `averageThreatScore` | number | 平均威胁分数 |
| `maxThreatScore` | number | 最大威胁分数 |
| `minThreatScore` | number | 最小威胁分数 |
| `levelDistribution` | Record<string, number> | 等级分布 |

#### 趋势数据字段
| 字段名 | 类型 | 说明 |
|--------|------|------|
| `timestamp` | string | 时间戳 (ISO 8601) |
| `count` | number | 威胁数量 |
| `averageScore` | number | 平均分数 |
| `maxScore` | number | 最大分数 |
| `criticalCount` | number | 严重威胁数 |
| `highCount` | number | 高危威胁数 |
| `mediumCount` | number | 中危威胁数 |

#### 分页字段
| 字段名 | 类型 | 说明 |
|--------|------|------|
| `content` | array | 数据内容 |
| `totalElements` | number | 总记录数 |
| `totalPages` | number | 总页数 |
| `size` | number | 每页大小 |
| `number` | number | 当前页码 (从0开始) |
| `first` | boolean | 是否首页 |
| `last` | boolean | 是否末页 |
| `empty` | boolean | 是否为空 |

### 3.2 前端TypeScript类型定义

```typescript
// frontend/src/types/index.ts

/**
 * 威胁等级枚举
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
 * 分页响应 (Spring Data Page)
 */
export interface PaginatedResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

/**
 * 查询过滤器
 */
export interface ThreatQueryFilter {
  page: number;           // 从0开始
  page_size: number;
  sort_by?: string;
  sort_order?: 'asc' | 'desc';
  customer_id?: string;
  threat_level?: ThreatLevel;
  start_time?: string;
  end_time?: string;
  attack_mac?: string;
}

/**
 * 图表数据点
 */
export interface ChartDataPoint {
  time: string;
  value: number;
  category?: string;
  averageScore?: number;
}
```

---

## 四、前端调用示例

### 4.1 获取统计数据

```typescript
// frontend/src/services/threat.ts
async getStatistics(customerId: string): Promise<Statistics> {
  const response = await apiClient.get<Statistics>(
    '/api/v1/assessment/statistics',
    { params: { customer_id: customerId } }
  );
  return response.data;
}
```

**使用示例**:
```typescript
const stats = await threatService.getStatistics('demo-customer');
console.log(`总威胁数: ${stats.totalCount}`);
console.log(`平均分数: ${stats.averageThreatScore.toFixed(2)}`);
```

### 4.2 获取分页列表

```typescript
async getThreatList(
  filter: ThreatQueryFilter
): Promise<PaginatedResponse<ThreatAssessment>> {
  const response = await apiClient.get<PaginatedResponse<ThreatAssessment>>(
    '/api/v1/assessment/assessments',
    { params: filter }
  );
  return response.data;
}
```

**使用示例**:
```typescript
const result = await threatService.getThreatList({
  page: 0,              // 页码从0开始 ⚠️
  page_size: 20,
  sort_by: 'assessmentTime',
  sort_order: 'desc',
});
console.log(`总数: ${result.totalElements}, 当前页: ${result.content.length}`);
```

### 4.3 获取趋势数据

```typescript
async getThreatTrend(customerId: string): Promise<ChartDataPoint[]> {
  const response = await apiClient.get<any[]>(
    '/api/v1/assessment/trend',
    { params: { customer_id: customerId, hours: 24 } }
  );
  
  // 转换为图表数据格式
  return response.data.map((item: any) => ({
    time: dayjs(item.timestamp).format('HH:mm'),
    value: item.count,
    averageScore: item.averageScore,
  }));
}
```

**使用示例**:
```typescript
const trendData = await threatService.getThreatTrend('demo-customer');
// trendData = [{ time: '02:00', value: 2, averageScore: 7290.0 }, ...]
```

### 4.4 获取端口分布

```typescript
async getPortDistribution(customerId: string): Promise<ChartDataPoint[]> {
  const response = await apiClient.get<any[]>(
    '/api/v1/assessment/port-distribution',
    { params: { customer_id: customerId } }
  );
  
  // 转换为图表数据格式
  return response.data.map((item: any) => ({
    category: `${item.portName}`,
    value: item.count,
  }));
}
```

**使用示例**:
```typescript
const portData = await threatService.getPortDistribution('demo-customer');
// portData = [{ category: '445-SMB', value: 8 }, ...]
```

---

## 五、图表配置标准

### 5.1 趋势图 (Line Chart)

```typescript
const trendConfig = {
  data: trendData,
  xField: 'time',           // 必须: 时间字段
  yField: 'value',          // 必须: 数值字段
  smooth: true,             // 平滑曲线
  point: {
    size: 3,
    shape: 'circle',
  },
  tooltip: {
    customContent: (title: string, items: any[]) => {
      if (!items || items.length === 0) return '';
      const item = items[0];
      return `<div style="padding: 8px;">
        <div>${title}</div>
        <div>威胁数量: ${item.value}</div>
      </div>`;
    },
  },
};
```

**使用**:
```tsx
<Line {...trendConfig} height={300} />
```

### 5.2 饼图 (Pie Chart)

```typescript
const portConfig = {
  data: portData.slice(0, 10),
  angleField: 'value',       // 必须: 数值字段
  colorField: 'category',    // 必须: 分类字段
  radius: 0.8,
  label: {
    text: (item: any) => `${item.category}: ${item.value}`,
  },
  legend: {
    position: 'bottom' as const,
  },
};
```

**使用**:
```tsx
<Pie {...portConfig} height={300} />
```

### 5.3 表格 (Table)

```typescript
const columns = [
  {
    title: '评估时间',
    dataIndex: 'assessmentTime',
    key: 'assessmentTime',
    render: (time: string) => dayjs(time).format('YYYY-MM-DD HH:mm:ss'),
    width: 160,
  },
  {
    title: '攻击者MAC',
    dataIndex: 'attackMac',
    key: 'attackMac',
    render: (mac: string) => <code>{mac}</code>,
  },
  {
    title: '威胁等级',
    dataIndex: 'threatLevel',
    key: 'threatLevel',
    render: (level: ThreatLevel) => getThreatLevelTag(level),
    width: 100,
  },
  {
    title: '威胁分数',
    dataIndex: 'threatScore',
    key: 'threatScore',
    render: (score: number) => score?.toFixed(2) || 'N/A',  // ⚠️ 空值保护
    width: 100,
  },
];
```

**使用**:
```tsx
<Table
  columns={columns}
  dataSource={threats}
  rowKey="id"
  pagination={{
    current: page,
    pageSize,
    total,
    showSizeChanger: true,
    showQuickJumper: true,
    showTotal: (total) => `共 ${total} 条`,
    onChange: (newPage, newPageSize) => {
      setPage(newPage);
      setPageSize(newPageSize);
    },
  }}
/>
```

---

## 六、关键问题与解决方案

### 6.1 页码问题

**问题**: Spring Data分页从0开始，但Ant Design Table从1开始

**解决方案**:
```typescript
// 发送请求时: UI页码 - 1
const response = await threatService.getThreatList({
  page: page - 1,  // ⚠️ 转换为0基索引
  page_size: pageSize,
});

// 显示时: 后端页码 + 1
<Table pagination={{ current: page, pageSize, total }} />
```

### 6.2 字段命名问题

**问题**: 前端期望下划线命名，后端返回驼峰命名

**解决方案**: 统一使用驼峰命名
```typescript
// ❌ 错误
dataIndex: 'threat_score',

// ✅ 正确
dataIndex: 'threatScore',
```

### 6.3 响应格式问题

**问题**: 前端期望 `response.data.data`，后端直接返回数据

**解决方案**: 前端直接使用 `response.data`
```typescript
// ❌ 错误
return response.data.data;

// ✅ 正确
return response.data;
```

### 6.4 空值处理问题

**问题**: `score.toFixed(2)` 在 `score` 为 `undefined` 时报错

**解决方案**: 添加空值保护
```typescript
// ❌ 错误
render: (score: number) => score.toFixed(2),

// ✅ 正确
render: (score: number) => score?.toFixed(2) || 'N/A',
```

### 6.5 图表配置问题

**问题**: `@ant-design/charts` 模板字符串语法不被支持

**解决方案**: 使用回调函数
```typescript
// ❌ 错误
label: {
  content: '{name} {percentage}',
}

// ✅ 正确
label: {
  text: (item: any) => `${item.category}: ${item.value}`,
}
```

### 6.6 时间格式转换

**后端返回**: ISO 8601格式 `"2025-10-20T02:02:50.632377Z"`

**前端显示**: 使用 dayjs 格式化
```typescript
import dayjs from 'dayjs';

// 表格显示
render: (time: string) => dayjs(time).format('YYYY-MM-DD HH:mm:ss'),

// 图表X轴
const formattedTrend = trend.map(item => ({
  time: dayjs(item.timestamp).format('HH:mm'),
  value: item.count,
}));
```

---

## 七、Docker部署要点

### 7.1 后端部署流程

```bash
# 1. Maven构建 (必须先执行)
cd services/threat-assessment
mvn clean package -DskipTests

# 2. Docker构建 (使用--no-cache确保使用新jar)
docker build --no-cache -t docker_threat-assessment .

# 3. 删除旧容器并重新运行
docker rm -f threat-assessment-service

docker run -d \
  --name threat-assessment-service \
  --network threat-detection-network \
  --network-alias threat-assessment \
  -p 8081:8081 \
  -e SPRING_DATASOURCE_JDBC_URL=jdbc:postgresql://postgres:5432/threat_detection \
  -e SPRING_DATASOURCE_USERNAME=threat_user \
  -e SPRING_DATASOURCE_PASSWORD=threat_password \
  -e SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:29092 \
  docker_threat-assessment
```

### 7.2 前端部署

```bash
# 前端使用卷挂载，修改代码后只需重启容器
docker restart threat-detection-ui-dev

# 等待容器启动
sleep 10

# 验证服务
curl http://localhost:3000
```

### 7.3 网络配置

- API Gateway: `http://localhost:8888`
- 前端服务: `http://localhost:3000`
- 后端服务: `http://localhost:8081`
- 容器网络别名: `threat-assessment` (重要!)

---

## 八、测试验证清单

### 8.1 后端API测试

```bash
# 1. 健康检查
curl http://localhost:8888/api/v1/assessment/health

# 2. 统计API
curl "http://localhost:8888/api/v1/assessment/statistics?customer_id=demo-customer"

# 3. 趋势API
curl "http://localhost:8888/api/v1/assessment/trend?customer_id=demo-customer"

# 4. 端口分布API
curl "http://localhost:8888/api/v1/assessment/port-distribution?customer_id=demo-customer"

# 5. 分页查询API
curl "http://localhost:8888/api/v1/assessment/assessments?customer_id=demo-customer&page=0&size=10"
```

### 8.2 前端功能测试

**Dashboard页面** (`http://localhost:3000`):
- ✅ 统计卡片显示正确数据
- ✅ 趋势图渲染8个数据点
- ✅ 最新威胁列表显示10条记录
- ✅ 端口分布饼图显示4个端口
- ✅ 无控制台错误 (除了无害的警告)

**ThreatList页面** (`http://localhost:3000/threats`):
- ✅ 列表显示所有16条记录
- ✅ 分页功能正常 (切换页码、修改页大小)
- ✅ 排序功能正常
- ✅ 删除按钮正常 (模态框确认)

### 8.3 浏览器控制台检查

**✅ 正常日志**:
```
[API Request] GET /api/v1/assessment/statistics
[API Response] /api/v1/assessment/statistics { totalCount: 16, ... }
```

**⚠️ 无害警告** (可忽略):
```
Warning: [antd: Spin] `tip` only work in nest or fullscreen pattern.
Warning: [antd: Card] `bordered` is deprecated. Please use `variant` instead.
React Router Future Flag Warning: ...
```

**❌ 不应出现的错误**:
```
✅ 无 ExpressionError
✅ 无 Unknown Component
✅ 无 Cannot read properties of undefined
✅ 无 ClassCastException
```

---

## 九、文件清单

### 9.1 后端文件 (已修改/新建)

```
services/threat-assessment/
├── src/main/java/com/threatdetection/assessment/
│   ├── dto/
│   │   ├── ThreatStatisticsResponse.java        (新建)
│   │   ├── TrendDataPoint.java                  (新建)
│   │   ├── PortDistribution.java                (新建)
│   │   └── ThreatAssessmentDetailResponse.java  (新建)
│   ├── repository/
│   │   └── ThreatAssessmentRepository.java      (重写)
│   ├── service/
│   │   ├── ThreatQueryService.java              (新建)
│   │   ├── RiskAssessmentService.java.bak       (备份)
│   │   └── ThreatAlertConsumer.java.bak         (备份)
│   ├── controller/
│   │   └── AssessmentController.java            (重写)
│   └── model/
│       └── ThreatAssessment.java                (修复)
├── src/main/resources/
│   └── application.properties                   (修改端口为8081)
├── pom.xml                                      (添加Lombok)
└── Dockerfile                                   (修改EXPOSE为8081)
```

### 9.2 前端文件 (已修改)

```
frontend/
├── src/
│   ├── types/
│   │   └── index.ts                    (重写类型定义为驼峰命名)
│   ├── services/
│   │   ├── api.ts                      (已有)
│   │   └── threat.ts                   (修改响应解析)
│   └── pages/
│       ├── Dashboard/
│       │   └── index.tsx               (修改字段名、图表配置、数据转换)
│       └── ThreatList/
│           └── index.tsx               (修改字段名、页码转换)
```

---

## 十、后续改进建议

### 10.1 功能增强
- [ ] 实时数据推送 (WebSocket)
- [ ] 导出功能 (Excel/CSV)
- [ ] 高级筛选 (多条件组合)
- [ ] 威胁详情页面 (点击查看完整信息)
- [ ] 用户权限管理

### 10.2 性能优化
- [ ] 前端数据缓存 (React Query)
- [ ] 虚拟滚动 (大数据量列表)
- [ ] 图表懒加载
- [ ] API请求防抖/节流

### 10.3 用户体验
- [ ] 深色模式支持
- [ ] 响应式布局优化
- [ ] 国际化 (i18n)
- [ ] 加载骨架屏
- [ ] 错误边界处理

### 10.4 代码质量
- [ ] 单元测试覆盖
- [ ] E2E测试
- [ ] TypeScript严格模式
- [ ] ESLint/Prettier配置
- [ ] Git提交规范

---

## 附录A: 完整的前端Service示例

```typescript
// frontend/src/services/threat.ts
import apiClient from './api';
import type {
  ThreatAssessment,
  Statistics,
  PaginatedResponse,
  ThreatQueryFilter,
  ChartDataPoint,
} from '@/types';
import dayjs from 'dayjs';

class ThreatService {
  /**
   * 获取威胁列表 (分页)
   */
  async getThreatList(
    filter: ThreatQueryFilter
  ): Promise<PaginatedResponse<ThreatAssessment>> {
    const response = await apiClient.get<PaginatedResponse<ThreatAssessment>>(
      '/api/v1/assessment/assessments',
      { params: filter }
    );
    return response.data;
  }

  /**
   * 获取统计数据
   */
  async getStatistics(customerId: string): Promise<Statistics> {
    const response = await apiClient.get<Statistics>(
      '/api/v1/assessment/statistics',
      { params: { customer_id: customerId } }
    );
    return response.data;
  }

  /**
   * 获取威胁趋势数据 (24小时)
   */
  async getThreatTrend(customerId: string): Promise<ChartDataPoint[]> {
    const response = await apiClient.get<any[]>(
      '/api/v1/assessment/trend',
      { params: { customer_id: customerId, hours: 24 } }
    );
    
    // 转换为图表数据格式
    return response.data.map((item: any) => ({
      time: dayjs(item.timestamp).format('HH:mm'),
      value: item.count,
      averageScore: item.averageScore,
    }));
  }

  /**
   * 获取端口分布
   */
  async getPortDistribution(customerId: string): Promise<ChartDataPoint[]> {
    const response = await apiClient.get<any[]>(
      '/api/v1/assessment/port-distribution',
      { params: { customer_id: customerId } }
    );
    
    // 转换为图表数据格式
    return response.data.map((item: any) => ({
      category: item.portName || `Port ${item.port}`,
      value: item.count,
    }));
  }

  /**
   * 删除威胁记录
   */
  async deleteThreat(id: number): Promise<void> {
    await apiClient.delete(`/api/v1/assessment/assessments/${id}`);
  }
}

export default new ThreatService();
```

---

## 附录B: 数据库测试数据

```sql
-- 插入16条测试数据 (已执行)
INSERT INTO threat_assessments (
  customer_id, attack_mac, attack_ip, threat_score, threat_level,
  attack_count, unique_ips, unique_ports, unique_devices,
  assessment_time, created_at, port_list, port_risk_score, detection_tier
) VALUES
  ('demo-customer', '04:42:1a:8e:e3:65', 'N/A', 7290.0, 'CRITICAL', 150, 5, 3, 2, 
   NOW() - INTERVAL '2 hours', NOW() - INTERVAL '1 hour', '3389,445,22', 85.5, 2),
  -- ... (共16条)
;
```

**查询验证**:
```sql
SELECT customer_id, COUNT(*) as total, 
       COUNT(CASE WHEN threat_level = 'CRITICAL' THEN 1 END) as critical_count
FROM threat_assessments
WHERE customer_id = 'demo-customer'
GROUP BY customer_id;
```

---

**文档结束**

*本文档记录了前端集成调试的完整过程和标准规范，可作为后续开发的参考手册。*
