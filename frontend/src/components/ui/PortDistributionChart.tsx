import {
  Bar,
  BarChart,
  CartesianGrid,
  LabelList,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';

export interface PortDistributionDatum {
  port: number;
  label: string;
  service?: string;
  count: number;
  percentage: number;
}

export interface PortDistributionChartLabels {
  port: string;
  service: string;
  attackCount: string;
  percentage: string;
  unknownService: string;
}

export interface PortDistributionChartProps {
  data: PortDistributionDatum[];
  labels: PortDistributionChartLabels;
  ariaLabel: string;
}

const formatCount = (value: unknown) => Number(value ?? 0).toLocaleString();

const PortDistributionChart = ({ data, labels, ariaLabel }: PortDistributionChartProps) => {
  const height = Math.max(240, Math.min(480, data.length * 30 + 52));

  return (
    <div
      className="tds-port-distribution-chart"
      style={{ height }}
      role="img"
      aria-label={ariaLabel}
    >
      <ResponsiveContainer width="100%" height="100%">
        <BarChart
          data={data}
          layout="vertical"
          margin={{ top: 6, right: 58, bottom: 12, left: 8 }}
        >
          <CartesianGrid horizontal={false} strokeDasharray="4 6" stroke="var(--chart-grid)" />
          <XAxis
            type="number"
            allowDecimals={false}
            axisLine={false}
            tickLine={false}
            tickMargin={10}
            tick={{ fill: 'var(--chart-axis)', fontSize: 11 }}
            tickFormatter={formatCount}
          />
          <YAxis
            type="category"
            dataKey="label"
            axisLine={false}
            tickLine={false}
            interval={0}
            width={112}
            tickMargin={10}
            tick={{ fill: 'var(--text-color)', fontSize: 12, fontWeight: 500 }}
          />
          <Tooltip
            cursor={{ fill: 'var(--chart-hover)' }}
            content={({ active, payload }) => {
              if (!active || !payload?.length) return null;
              const item = payload[0].payload as PortDistributionDatum;
              return (
                <div className="tds-port-chart__tooltip">
                  <div className="tds-port-chart__tooltip-title">{item.label}</div>
                  <dl>
                    <div>
                      <dt>{labels.port}</dt>
                      <dd>{item.port > 0 ? item.port : '-'}</dd>
                    </div>
                    <div>
                      <dt>{labels.service}</dt>
                      <dd>{item.service || labels.unknownService}</dd>
                    </div>
                    <div>
                      <dt>{labels.attackCount}</dt>
                      <dd>{formatCount(item.count)}</dd>
                    </div>
                    <div>
                      <dt>{labels.percentage}</dt>
                      <dd>{item.percentage.toFixed(2)}%</dd>
                    </div>
                  </dl>
                </div>
              );
            }}
          />
          <Bar dataKey="count" fill="var(--chart-primary)" radius={[0, 2, 2, 0]} barSize={14}>
            <LabelList
              dataKey="count"
              position="right"
              formatter={formatCount}
              fill="var(--chart-axis)"
              fontSize={11}
            />
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
};

export default PortDistributionChart;
