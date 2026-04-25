import { Empty, Typography } from 'antd';
import { useTranslation } from 'react-i18next';

interface EmptyStateProps {
  description?: string;
  image?: 'default' | 'simple';
}

const EmptyState = ({ description, image = 'default' }: EmptyStateProps) => {
  const { t } = useTranslation();
  const resolvedDescription = description ?? t('common.noData');

  return (
    <div style={{ textAlign: 'center', padding: '60px 0' }}>
      <Empty
        image={image === 'simple' ? Empty.PRESENTED_IMAGE_SIMPLE : Empty.PRESENTED_IMAGE_DEFAULT}
        description={<Typography.Text type="secondary">{resolvedDescription}</Typography.Text>}
      />
    </div>
  );
};

export default EmptyState;
