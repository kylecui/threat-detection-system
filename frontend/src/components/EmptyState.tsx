import { Empty, Typography } from 'antd';

interface EmptyStateProps {
  description?: string;
  image?: 'default' | 'simple';
}

const EmptyState = ({ description = '暂无数据', image = 'default' }: EmptyStateProps) => (
  <div style={{ textAlign: 'center', padding: '60px 0' }}>
    <Empty
      image={image === 'simple' ? Empty.PRESENTED_IMAGE_SIMPLE : Empty.PRESENTED_IMAGE_DEFAULT}
      description={<Typography.Text type="secondary">{description}</Typography.Text>}
    />
  </div>
);

export default EmptyState;
