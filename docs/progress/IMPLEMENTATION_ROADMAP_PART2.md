# 云原生威胁检测系统 - 完整实施路线图 (第2部分)

**接续**: Phase 2-3详细计划、测试策略、风险管理

---

## Phase 2: 智能增强 (Month 5-6, Week 17-24)

**目标**: 引入机器学习和高级威胁分析

### Week 17-18: ML误报过滤系统

#### 2.1 数据准备与标注

**任务清单**:

- [ ] **历史数据导出**
  
  ```sql
  -- 导出最近6个月的威胁告警数据
  COPY (
      SELECT 
          customer_id,
          attack_mac,
          attack_ip,
          unique_ips,
          unique_ports,
          attack_count,
          threat_score,
          threat_level,
          response_ports,  -- JSON数组
          created_at,
          -- 人工标注字段 (需要后续标注)
          NULL as is_false_positive,
          NULL as analyst_notes
      FROM threat_assessments
      WHERE created_at >= NOW() - INTERVAL '6 months'
      ORDER BY created_at DESC
  ) TO '/tmp/historical_alerts.csv' WITH CSV HEADER;
  ```

- [ ] **人工标注工具**
  
  ```python
  # scripts/tools/alert_labeling_tool.py
  """
  告警标注工具 - Web界面
  """
  from flask import Flask, render_template, request, jsonify
  import pandas as pd
  import json
  
  app = Flask(__name__)
  
  # 加载待标注数据
  df = pd.read_csv('/tmp/historical_alerts.csv')
  current_index = 0
  
  @app.route('/')
  def index():
      """显示标注界面"""
      global current_index
      
      if current_index >= len(df):
          return "✅ 标注完成!"
      
      alert = df.iloc[current_index].to_dict()
      
      return render_template('labeling.html', 
                           alert=alert,
                           progress=f"{current_index}/{len(df)}")
  
  @app.route('/label', methods=['POST'])
  def label():
      """保存标注结果"""
      global current_index
      
      is_fp = request.json.get('is_false_positive')
      notes = request.json.get('notes', '')
      
      df.at[current_index, 'is_false_positive'] = is_fp
      df.at[current_index, 'analyst_notes'] = notes
      
      current_index += 1
      
      # 每100条自动保存
      if current_index % 100 == 0:
          df.to_csv('/tmp/labeled_alerts.csv', index=False)
      
      return jsonify({'status': 'success', 'next_index': current_index})
  
  if __name__ == '__main__':
      app.run(host='0.0.0.0', port=5001, debug=True)
  ```
  
  ```html
  <!-- templates/labeling.html -->
  <!DOCTYPE html>
  <html>
  <head>
      <title>告警标注工具</title>
      <style>
          body { font-family: Arial; max-width: 800px; margin: 50px auto; }
          .alert-box { border: 1px solid #ccc; padding: 20px; margin: 20px 0; }
          .high { border-left: 5px solid red; }
          .medium { border-left: 5px solid orange; }
          .low { border-left: 5px solid yellow; }
          button { padding: 10px 20px; margin: 10px; font-size: 16px; }
          .true-positive { background-color: #4CAF50; color: white; }
          .false-positive { background-color: #f44336; color: white; }
      </style>
  </head>
  <body>
      <h1>威胁告警标注</h1>
      <p>进度: {{ progress }}</p>
      
      <div class="alert-box {{ alert.threat_level|lower }}">
          <h2>{{ alert.threat_level }} - 评分: {{ alert.threat_score }}</h2>
          <p><strong>攻击者MAC:</strong> {{ alert.attack_mac }}</p>
          <p><strong>攻击者IP:</strong> {{ alert.attack_ip }}</p>
          <p><strong>攻击次数:</strong> {{ alert.attack_count }}</p>
          <p><strong>唯一IP数:</strong> {{ alert.unique_ips }}</p>
          <p><strong>唯一端口数:</strong> {{ alert.unique_ports }}</p>
          <p><strong>端口列表:</strong> {{ alert.response_ports }}</p>
          <p><strong>时间:</strong> {{ alert.created_at }}</p>
      </div>
      
      <textarea id="notes" placeholder="备注 (可选)" style="width: 100%; height: 100px;"></textarea>
      
      <div>
          <button class="true-positive" onclick="label(false)">
              ✅ 真实威胁 (True Positive)
          </button>
          <button class="false-positive" onclick="label(true)">
              ❌ 误报 (False Positive)
          </button>
      </div>
      
      <script>
      function label(isFalsePositive) {
          fetch('/label', {
              method: 'POST',
              headers: {'Content-Type': 'application/json'},
              body: JSON.stringify({
                  is_false_positive: isFalsePositive,
                  notes: document.getElementById('notes').value
              })
          }).then(response => response.json())
            .then(data => {
                if (data.status === 'success') {
                    location.reload();
                }
            });
      }
      </script>
  </body>
  </html>
  ```

- [ ] **标注质量控制**
  
  ```python
  # 双盲标注 (2名分析师独立标注)
  # 计算标注一致性 (Cohen's Kappa)
  
  from sklearn.metrics import cohen_kappa_score
  
  analyst1_labels = [0, 0, 1, 0, 1, ...]
  analyst2_labels = [0, 0, 1, 0, 0, ...]
  
  kappa = cohen_kappa_score(analyst1_labels, analyst2_labels)
  print(f"标注一致性 (Kappa): {kappa:.3f}")
  # 目标: Kappa > 0.75 (良好一致性)
  ```

**预期标注量**:
- 最少2000条样本
- 真实威胁:误报 = 7:3 (处理样本不平衡)

---

#### 2.2 特征工程

```python
# ml-service/feature_engineering.py
"""
威胁告警特征工程
"""
import numpy as np
from scipy.stats import entropy
from sklearn.preprocessing import StandardScaler

class ThreatFeatureExtractor:
    
    def extract_features(self, alert_data):
        """
        提取16维特征向量
        """
        features = {}
        
        # 1. 统计特征 (5维)
        features['unique_ports'] = alert_data['unique_ports']
        features['unique_ips'] = alert_data['unique_ips']
        features['attack_count'] = alert_data['attack_count']
        features['duration_hours'] = self._calculate_duration(alert_data)
        features['avg_attacks_per_ip'] = (
            alert_data['attack_count'] / max(alert_data['unique_ips'], 1)
        )
        
        # 2. 熵特征 (2维) - 衡量多样性
        features['port_diversity_entropy'] = self._calculate_entropy(
            alert_data['response_ports']
        )
        features['ip_diversity_entropy'] = self._calculate_entropy(
            alert_data['response_ips']
        )
        
        # 3. 时间特征 (3维)
        features['is_night'] = self._is_night_time(alert_data['timestamp'])
        features['is_weekend'] = self._is_weekend(alert_data['timestamp'])
        features['hour_of_day'] = alert_data['timestamp'].hour
        
        # 4. 端口特征 (3维)
        features['has_high_risk_ports'] = self._has_high_risk_ports(
            alert_data['response_ports']
        )
        features['port_range_span'] = self._calculate_port_span(
            alert_data['response_ports']
        )
        features['avg_port_value'] = np.mean(alert_data['response_ports'])
        
        # 5. 行为特征 (3维)
        features['port_sequence_randomness'] = self._calculate_port_randomness(
            alert_data['response_ports']
        )
        features['ip_sequence_pattern'] = self._detect_ip_pattern(
            alert_data['response_ips']
        )
        features['unique_devices'] = alert_data.get('unique_devices', 1)
        
        return features
    
    def _calculate_entropy(self, items):
        """
        计算Shannon熵 (衡量多样性)
        
        高熵 (>3.0) = 高度随机/扫描行为
        低熵 (<1.0) = 集中攻击/针对性
        """
        if not items:
            return 0.0
        
        # 计算每个项目的频率
        unique, counts = np.unique(items, return_counts=True)
        probabilities = counts / counts.sum()
        
        return entropy(probabilities, base=2)
    
    def _calculate_duration(self, alert_data):
        """计算攻击持续时间 (小时)"""
        if 'window_start' in alert_data and 'window_end' in alert_data:
            duration_ms = alert_data['window_end'] - alert_data['window_start']
            return duration_ms / (1000 * 3600)  # 转为小时
        return 1.0  # 默认1小时
    
    def _is_night_time(self, timestamp):
        """是否深夜 (0:00-6:00)"""
        hour = timestamp.hour
        return 1 if 0 <= hour < 6 else 0
    
    def _is_weekend(self, timestamp):
        """是否周末"""
        return 1 if timestamp.weekday() >= 5 else 0
    
    def _has_high_risk_ports(self, ports):
        """是否包含高危端口"""
        high_risk = {445, 3389, 22, 1433, 3306, 5432}
        return 1 if any(p in high_risk for p in ports) else 0
    
    def _calculate_port_span(self, ports):
        """端口范围跨度"""
        if not ports:
            return 0
        return max(ports) - min(ports)
    
    def _calculate_port_randomness(self, ports):
        """
        端口序列随机性
        
        顺序扫描 (1,2,3,4) = 低随机性
        随机扫描 (1433,3389,22) = 高随机性
        """
        if len(ports) < 3:
            return 0.5
        
        sorted_ports = sorted(ports)
        
        # 计算相邻端口差的方差
        diffs = [sorted_ports[i+1] - sorted_ports[i] 
                 for i in range(len(sorted_ports)-1)]
        
        variance = np.var(diffs)
        
        # 归一化到0-1
        return min(variance / 1000, 1.0)
    
    def _detect_ip_pattern(self, ips):
        """
        检测IP序列模式
        
        0 = 随机
        1 = 顺序 (192.168.1.1, .2, .3)
        2 = 特定子网
        """
        if len(ips) < 3:
            return 0
        
        # 转换为整数
        ip_ints = [self._ip_to_int(ip) for ip in ips]
        ip_ints.sort()
        
        # 检查是否顺序
        is_sequential = all(
            ip_ints[i+1] - ip_ints[i] == 1
            for i in range(len(ip_ints)-1)
        )
        
        if is_sequential:
            return 1
        
        # 检查是否同一子网
        subnets = [ip.rsplit('.', 1)[0] for ip in ips]
        if len(set(subnets)) == 1:
            return 2
        
        return 0
    
    def _ip_to_int(self, ip):
        """IP转整数"""
        parts = ip.split('.')
        return (int(parts[0]) << 24) + (int(parts[1]) << 16) + \
               (int(parts[2]) << 8) + int(parts[3])
```

---

#### 2.3 模型训练

```python
# ml-service/model_training.py
"""
随机森林分类器训练
"""
import pandas as pd
import numpy as np
from sklearn.ensemble import RandomForestClassifier
from sklearn.model_selection import train_test_split, cross_val_score, GridSearchCV
from sklearn.metrics import classification_report, confusion_matrix, roc_auc_score
from sklearn.preprocessing import StandardScaler
import joblib
import json

class FalsePositiveClassifier:
    
    def __init__(self):
        self.model = None
        self.scaler = StandardScaler()
        self.feature_names = []
    
    def train(self, labeled_data_path='labeled_alerts.csv'):
        """
        训练随机森林分类器
        """
        print("📚 加载标注数据...")
        df = pd.read_csv(labeled_data_path)
        
        # 特征提取
        print("🔧 提取特征...")
        feature_extractor = ThreatFeatureExtractor()
        
        X = []
        y = []
        
        for idx, row in df.iterrows():
            features = feature_extractor.extract_features(row.to_dict())
            X.append(list(features.values()))
            y.append(row['is_false_positive'])
        
        X = np.array(X)
        y = np.array(y)
        
        self.feature_names = list(features.keys())
        
        print(f"✅ 特征提取完成: {X.shape}")
        print(f"   - 样本数: {len(X)}")
        print(f"   - 特征数: {X.shape[1]}")
        print(f"   - 真实威胁: {sum(y==0)}")
        print(f"   - 误报: {sum(y==1)}")
        
        # 数据归一化
        X_scaled = self.scaler.fit_transform(X)
        
        # 划分训练集和测试集
        X_train, X_test, y_train, y_test = train_test_split(
            X_scaled, y, 
            test_size=0.2, 
            random_state=42,
            stratify=y  # 保持类别比例
        )
        
        # 网格搜索最优参数
        print("🔍 网格搜索最优参数...")
        param_grid = {
            'n_estimators': [50, 100, 200],
            'max_depth': [10, 20, 30],
            'min_samples_split': [5, 10, 20],
            'min_samples_leaf': [2, 4, 8],
            'class_weight': ['balanced']  # 处理不平衡数据
        }
        
        grid_search = GridSearchCV(
            RandomForestClassifier(random_state=42),
            param_grid,
            cv=5,
            scoring='f1',
            n_jobs=-1,
            verbose=1
        )
        
        grid_search.fit(X_train, y_train)
        
        print(f"✅ 最优参数: {grid_search.best_params_}")
        
        # 使用最优参数训练最终模型
        self.model = grid_search.best_estimator_
        
        # 交叉验证
        print("🔄 5折交叉验证...")
        cv_scores = cross_val_score(
            self.model, X_scaled, y, 
            cv=5, 
            scoring='f1'
        )
        print(f"   - CV F1 Score: {cv_scores.mean():.3f} (±{cv_scores.std():.3f})")
        
        # 测试集评估
        print("📊 测试集评估...")
        y_pred = self.model.predict(X_test)
        y_prob = self.model.predict_proba(X_test)[:, 1]
        
        print("\n分类报告:")
        print(classification_report(
            y_test, y_pred,
            target_names=['真实威胁', '误报']
        ))
        
        print("\n混淆矩阵:")
        cm = confusion_matrix(y_test, y_pred)
        print(f"         预测:真实  预测:误报")
        print(f"实际:真实   {cm[0][0]:4d}      {cm[0][1]:4d}")
        print(f"实际:误报   {cm[1][0]:4d}      {cm[1][1]:4d}")
        
        auc = roc_auc_score(y_test, y_prob)
        print(f"\nROC-AUC: {auc:.3f}")
        
        # 特征重要性
        print("\n📈 特征重要性 (Top 10):")
        importances = self.model.feature_importances_
        indices = np.argsort(importances)[::-1]
        
        for i in range(min(10, len(indices))):
            idx = indices[i]
            print(f"   {i+1}. {self.feature_names[idx]}: {importances[idx]:.3f}")
        
        # 保存模型
        self._save_model()
        
        return {
            'cv_f1_score': cv_scores.mean(),
            'test_accuracy': (y_pred == y_test).mean(),
            'auc': auc,
            'confusion_matrix': cm.tolist()
        }
    
    def _save_model(self, model_dir='models'):
        """保存训练好的模型"""
        import os
        os.makedirs(model_dir, exist_ok=True)
        
        # 保存模型
        joblib.dump(self.model, f'{model_dir}/rf_classifier.pkl')
        joblib.dump(self.scaler, f'{model_dir}/scaler.pkl')
        
        # 保存特征名称
        with open(f'{model_dir}/feature_names.json', 'w') as f:
            json.dump(self.feature_names, f)
        
        print(f"\n✅ 模型已保存到 {model_dir}/")
    
    def load_model(self, model_dir='models'):
        """加载已训练的模型"""
        self.model = joblib.load(f'{model_dir}/rf_classifier.pkl')
        self.scaler = joblib.load(f'{model_dir}/scaler.pkl')
        
        with open(f'{model_dir}/feature_names.json', 'r') as f:
            self.feature_names = json.load(f)
        
        print("✅ 模型加载完成")
    
    def predict(self, alert_data):
        """
        预测单个告警是否为误报
        
        Returns:
            {
                'is_false_positive': bool,
                'confidence': float (0-1),
                'probabilities': [p_true_threat, p_false_positive]
            }
        """
        # 提取特征
        feature_extractor = ThreatFeatureExtractor()
        features = feature_extractor.extract_features(alert_data)
        X = np.array([list(features.values())])
        
        # 归一化
        X_scaled = self.scaler.transform(X)
        
        # 预测
        prediction = self.model.predict(X_scaled)[0]
        probabilities = self.model.predict_proba(X_scaled)[0]
        
        return {
            'is_false_positive': bool(prediction),
            'confidence': float(probabilities[prediction]),
            'probabilities': {
                'true_threat': float(probabilities[0]),
                'false_positive': float(probabilities[1])
            }
        }

if __name__ == '__main__':
    # 训练模型
    classifier = FalsePositiveClassifier()
    results = classifier.train('labeled_alerts.csv')
    
    print("\n🎉 训练完成!")
    print(json.dumps(results, indent=2))
```

**预期性能**:
- **准确率**: > 95%
- **精确率 (误报类)**: > 98%
- **召回率 (误报类)**: > 99%
- **F1-Score**: > 96%

---

#### 2.4 ML服务部署

```python
# ml-service/app.py
"""
Flask ML推理服务
"""
from flask import Flask, request, jsonify
from model_training import FalsePositiveClassifier
import logging

app = Flask(__name__)
logging.basicConfig(level=logging.INFO)

# 加载模型
classifier = FalsePositiveClassifier()
classifier.load_model('models')

@app.route('/health', methods=['GET'])
def health():
    """健康检查"""
    return jsonify({'status': 'healthy', 'model_loaded': classifier.model is not None})

@app.route('/predict', methods=['POST'])
def predict():
    """
    单个告警预测
    
    请求:
    {
        "unique_ports": 5,
        "unique_ips": 3,
        "attack_count": 50,
        "response_ports": [80, 443, 8080],
        "timestamp": "2025-10-11T10:00:00Z",
        ...
    }
    
    响应:
    {
        "is_false_positive": false,
        "confidence": 0.92,
        "probabilities": {
            "true_threat": 0.92,
            "false_positive": 0.08
        }
    }
    """
    try:
        alert_data = request.json
        result = classifier.predict(alert_data)
        
        logging.info(f"预测结果: {result}")
        
        return jsonify(result)
    
    except Exception as e:
        logging.error(f"预测失败: {str(e)}")
        return jsonify({'error': str(e)}), 500

@app.route('/batch-predict', methods=['POST'])
def batch_predict():
    """
    批量预测
    
    请求:
    {
        "alerts": [alert1, alert2, ...]
    }
    """
    try:
        alerts = request.json.get('alerts', [])
        results = []
        
        for alert in alerts:
            result = classifier.predict(alert)
            results.append({
                'alert_id': alert.get('id'),
                **result
            })
        
        return jsonify({'predictions': results})
    
    except Exception as e:
        return jsonify({'error': str(e)}), 500

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=False)
```

**Docker部署**:

```dockerfile
# ml-service/Dockerfile
FROM python:3.10-slim

WORKDIR /app

# 安装依赖
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# 复制代码和模型
COPY . .

# 暴露端口
EXPOSE 5000

# 启动服务
CMD ["python", "app.py"]
```

```yaml
# docker-compose.yml 添加ML服务
services:
  ml-service:
    build: ./ml-service
    container_name: ml-service
    ports:
      - "5000:5000"
    environment:
      MODEL_PATH: /app/models
    volumes:
      - ./ml-service/models:/app/models
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:5000/health"]
      interval: 30s
      timeout: 10s
      retries: 3
```

---

#### 2.5 与Flink集成

```java
// services/threat-assessment/src/main/java/com/threatdetection/ml/MLFalsePositiveFilter.java

@Service
@Slf4j
public class MLFalsePositiveFilter {
    
    @Value("${ml.service.url:http://ml-service:5000}")
    private String mlServiceUrl;
    
    private final RestTemplate restTemplate;
    
    public MLFalsePositiveFilter(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }
    
    /**
     * 使用ML模型预测是否为误报
     */
    public MLPredictionResult predict(ThreatAlert alert) {
        try {
            // 构建请求
            Map<String, Object> requestBody = buildFeatures(alert);
            
            // 调用ML服务
            ResponseEntity<Map> response = restTemplate.postForEntity(
                mlServiceUrl + "/predict",
                requestBody,
                Map.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK) {
                Map<String, Object> result = response.getBody();
                
                return MLPredictionResult.builder()
                    .isFalsePositive((Boolean) result.get("is_false_positive"))
                    .confidence((Double) result.get("confidence"))
                    .probabilities((Map<String, Double>) result.get("probabilities"))
                    .build();
            } else {
                log.error("ML服务返回错误: {}", response.getStatusCode());
                return null;
            }
            
        } catch (Exception e) {
            log.error("ML预测失败: customerId={}, error={}",
                     alert.getCustomerId(), e.getMessage(), e);
            return null;
        }
    }
    
    private Map<String, Object> buildFeatures(ThreatAlert alert) {
        Map<String, Object> features = new HashMap<>();
        
        features.put("unique_ports", alert.getUniquePorts());
        features.put("unique_ips", alert.getUniqueIps());
        features.put("attack_count", alert.getAttackCount());
        features.put("response_ports", alert.getResponsePorts());
        features.put("response_ips", alert.getResponseIps());
        features.put("timestamp", alert.getTimestamp());
        features.put("unique_devices", alert.getUniqueDevices());
        
        return features;
    }
    
    /**
     * 混合过滤策略
     * 
     * 1. 先应用规则白名单 (快速,确定性高)
     * 2. 不确定的交给ML模型 (慢,准确性高)
     */
    public boolean shouldSuppress(ThreatAlert alert) {
        // 阶段1: 规则白名单
        RuleBasedResult ruleResult = applyRuleWhitelist(alert);
        
        if (ruleResult.isHighConfidence()) {
            // 高置信度规则,直接决策
            return ruleResult.isSuppressed();
        }
        
        // 阶段2: ML模型 (不确定案例)
        MLPredictionResult mlResult = predict(alert);
        
        if (mlResult != null && mlResult.getConfidence() > 0.85) {
            return mlResult.isFalsePositive();
        }
        
        // 默认: 不抑制 (宁可误报,不可漏报)
        return false;
    }
}
```

**交付物**:
- ✅ 标注工具 (Web界面)
- ✅ 2000+标注样本
- ✅ 训练好的RF模型 (准确率 > 95%)
- ✅ ML推理服务 (Flask)
- ✅ Flink集成代码

---

### Week 19-20: 端口序列模式识别

*(实施细节略,参考analysis/03文档)*

**关键交付物**:
- ✅ 恶意软件端口指纹库 (50+家族)
- ✅ 序列匹配算法 (Jaccard + 编辑距离)
- ✅ 评分增强 (匹配 × 5.0)

---

### Week 21-22: 行为基线建立

*(实施细节参考analysis/05和06文档)*

**关键交付物**:
- ✅ 30天行为基线建模
- ✅ Z-score异常检测
- ✅ 时间模式分析

---

### Week 23-24: Phase 2集成测试与上线

**验收标准**:
- ✅ 误报率: 2% → 0.1% (-95%)
- ✅ ML模型准确率 > 95%
- ✅ 端口序列识别准确率 > 92%

**里程碑**: 🎉 **Phase 2智能增强完成**

---

## Phase 3: 高级威胁建模 (Month 7-8, Week 25-32)

### Week 25-27: APT状态机实现

*(详细实现参考analysis/04文档)*

**6阶段状态机**:
```
[初始侦察] → [网络扫描] → [漏洞利用] → [横向移动] → [权限提升] → [数据渗出]
```

**交付物**:
- ✅ APT状态机 (6阶段)
- ✅ 状态转移规则 (基于端口+行为)
- ✅ PostgreSQL状态存储
- ✅ Flink状态管理

---

### Week 28-29: 持续性评分模型

*(详细实现参考analysis/07文档)*

**持续性公式**:
```
persistenceScore = duration × 0.4
                 + activityFrequency × 0.3
                 + behaviorConsistency × 0.3
```

**交付物**:
- ✅ 持续性计算器
- ✅ 演化轨迹追踪
- ✅ 告警上下文增强

---

### Week 30-31: 洪泛数据集成

*(详细实现参考analysis/08文档)*

**交付物**:
- ✅ ARP流量分析
- ✅ 行为基线检测
- ✅ 与蜜罐数据融合

---

### Week 32: Phase 3验收与总结

**最终验收标准**:
- ✅ APT检出率: 60% → 95%
- ✅ APT检出时间: 30天 → 7天
- ✅ 误报率: 2% → 0.1%
- ✅ 系统稳定性: 99.9%+

**里程碑**: 🎉 **项目完成!**

---

**文档结束** (第2部分)

*下一部分: 测试策略、风险管理、团队配置*
