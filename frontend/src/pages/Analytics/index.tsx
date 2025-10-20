import { Card, Typography } from 'antd';

const { Title, Paragraph } = Typography;

/**
 * 数据分析页面 (待实现)
 */
const Analytics = () => {
  return (
    <Card>
      <Title level={3}>数据分析</Title>
      <Paragraph>
        此页面将提供:
        <ul>
          <li>威胁趋势分析</li>
          <li>攻击者行为分析</li>
          <li>端口攻击统计</li>
          <li>时间分布热力图</li>
          <li>设备横向移动分析</li>
        </ul>
      </Paragraph>
      <Paragraph type="secondary">功能开发中...</Paragraph>
    </Card>
  );
};

export default Analytics;
