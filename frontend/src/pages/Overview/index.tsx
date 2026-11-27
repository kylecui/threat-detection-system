import { useCallback, useEffect, useRef, useState } from 'react';
import type { CSSProperties } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Button,
  Divider,
  Segmented,
  Space,
  Table,
  Tag,
  Tooltip,
  message,
} from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import { Line, Pie } from '@ant-design/charts';
import { useTranslation } from 'react-i18next';
import type {
  Statistics,
  ThreatAssessment,
  ChartDataPoint,
  TopAttacker,
  NonPortProtocolDistribution,
} from '@/types';
import { ThreatLevel } from '@/types';
import threatService from '@/services/threat';
import { useScope } from '@/contexts/ScopeContext';
import EmptyState from '@/components/EmptyState';
import PortDistributionChart, {
  type PortDistributionDatum,
} from '@/components/ui/PortDistributionChart';
import NetworkIdentifier from '@/components/NetworkIdentifier';
import { formatDateTime } from '@/utils/formatDateTime';
import { toTrendChartData } from './trend';
import './overview.css';

const HOURS_MAP: Record<string, number> = {
  '24h': 24,
  '7d': 168,
  '30d': 720,
};

const Overview = () => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const {
    effectiveCustomerId: customerId,
    tenantId,
    initialized,
  } = useScope();
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [loadError, setLoadError] = useState(false);
  const [generatedAt, setGeneratedAt] = useState<string>();
  const [statistics, setStatistics] = useState<Statistics | null>(null);
  const [trendData, setTrendData] = useState<ChartDataPoint[]>([]);
  const [portData, setPortData] = useState<PortDistributionDatum[]>([]);
  const [nonPortProtocols, setNonPortProtocols] = useState<
    NonPortProtocolDistribution[]
  >([]);
  const [topAttackers, setTopAttackers] = useState<TopAttacker[]>([]);
  const [recentThreats, setRecentThreats] = useState<ThreatAssessment[]>([]);
  const [trendRange, setTrendRange] = useState<string>('24h');
  const requestGeneration = useRef(0);
  const hasLoaded = useRef(false);

  const loadData = useCallback(async () => {
    const generation = ++requestGeneration.current;
    try {
      const firstLoad = !hasLoaded.current;
      if (firstLoad) setLoading(true);
      else setRefreshing(true);
      const hours = HOURS_MAP[trendRange];

      if (!customerId && !tenantId) {
        setGeneratedAt(undefined);
        setStatistics(null);
        setTrendData([]);
        setPortData([]);
        setNonPortProtocols([]);
        setTopAttackers([]);
        setRecentThreats([]);
        setLoadError(false);
        return;
      }

      const snapshot = await threatService.getOverviewSnapshot({
        customerId,
        tenantId,
        hours,
      });
      if (generation !== requestGeneration.current) return;
      hasLoaded.current = true;
      const {
        generatedAt: snapshotGeneratedAt,
        statistics: stats,
        trend,
        portDistribution: ports,
        nonPortProtocols: protocols,
        topAttackers: attackers,
        recentThreats: recent,
      } = snapshot;

      setGeneratedAt(snapshotGeneratedAt);
      setStatistics(stats || null);
      setRecentThreats(recent || []);
      setLoadError(false);

      setTrendData(
        toTrendChartData(trend, hours, t('overview.threatCount')),
      );

      const formattedPorts = (ports || []).map((item) => {
        const port = Number(item.port);
        const validPort = Number.isInteger(port) && port >= 1 && port <= 65535;
        const service = validPort && item.portName ? item.portName : undefined;
        return {
          port: validPort ? port : 0,
          label: validPort
            ? `${port}${service ? ` / ${service}` : ''}`
            : t('overview.invalidPort'),
          service,
          count: Number(item.count || 0),
          percentage: Number(item.percentage || 0),
        };
      });
      setPortData(
        formattedPorts
          .sort((left, right) => right.count - left.count || left.port - right.port)
          .slice(0, 15),
      );
      setNonPortProtocols(protocols || []);
      setTopAttackers(attackers || []);
    } catch (error) {
      if (generation !== requestGeneration.current) return;
      setLoadError(true);
      console.error('Failed to load overview data:', error);
      message.error(t('overview.messageLoadFailed'));
    } finally {
      if (generation === requestGeneration.current) {
        setLoading(false);
        setRefreshing(false);
      }
    }
  }, [customerId, tenantId, trendRange, t]);

  useEffect(() => {
    if (!initialized) return;
    loadData();
    const interval = setInterval(loadData, 30000);
    return () => clearInterval(interval);
  }, [initialized, loadData]);

  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (
        event.key.toLowerCase() === 'r' &&
        !event.ctrlKey &&
        !event.metaKey &&
        !event.altKey
      ) {
        const target = event.target as HTMLElement;
        if (
          target.tagName === 'INPUT' ||
          target.tagName === 'TEXTAREA' ||
          target.isContentEditable
        ) {
          return;
        }
        void loadData();
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [loadData]);

  const getThreatLevelTag = (level: ThreatLevel | string) => {
    const normalizedLevel = String(level || ThreatLevel.INFO).toLowerCase();
    return (
      <span
        className={`tds-overview__threat-level tds-overview__threat-level--${normalizedLevel}`}
      >
        {level}
      </span>
    );
  };

  const levelDistData = statistics
    ? [
        { level: 'CRITICAL', count: statistics.criticalCount, color: '#cf1322' },
        { level: 'HIGH', count: statistics.highCount, color: '#d97706' },
        { level: 'MEDIUM', count: statistics.mediumCount, color: '#ca8a04' },
        { level: 'LOW', count: statistics.lowCount, color: '#168f83' },
        { level: 'INFO', count: statistics.infoCount, color: '#7b8792' },
      ].filter((item) => item.count > 0)
    : [];

  const rangeLabel =
    trendRange === '24h'
      ? t('overview.range24h')
      : trendRange === '7d'
        ? t('overview.range7d')
        : t('overview.range30d');

  const trendConfig = {
    data: trendData,
    xField: 'time',
    yField: 'value',
    smooth: false,
    point: false,
    color: '#168f83',
    lineStyle: { lineWidth: 2 },
    legend: false as const,
    xAxis: {
      title: null,
      tickCount: 6,
      tickLine: null,
      line: { style: { stroke: 'var(--line-color)' } },
      label: {
        autoHide: true,
        autoRotate: false,
        formatter: (text: string) => {
          if (trendData.length <= 6) return text;
          const index = trendData.findIndex((item) => item.time === text);
          const stride = Math.ceil(trendData.length / 6);
          return index === 0 || index === trendData.length - 1 || index % stride === 0
            ? text
            : '';
        },
        style: { fill: 'var(--chart-axis)', fontSize: 11 },
      },
    },
    yAxis: {
      title: null,
      tickCount: 5,
      grid: {
        line: {
          style: {
            stroke: 'var(--chart-grid)',
            lineDash: [3, 5],
          },
        },
      },
      label: { style: { fill: 'var(--chart-axis)', fontSize: 11 } },
    },
  };

  const levelPieConfig = {
    data: levelDistData,
    angleField: 'count',
    colorField: 'level',
    radius: 0.84,
    innerRadius: 0.72,
    label: false as const,
    legend: false as const,
    scale: {
      color: { range: levelDistData.map((item) => item.color) },
    },
  };

  const detectionWindowLabel = (tier?: number) => {
    if (tier === 1) return t('overview.tier1Window');
    if (tier === 2) return t('overview.tier2Window');
    if (tier === 3) return t('overview.tier3Window');
    return t('common.unknown');
  };

  const recentColumns = [
    {
      title: t('overview.assessmentTime'),
      dataIndex: 'assessmentTime',
      key: 'assessmentTime',
      render: (time: string) => formatDateTime(time),
      width: 180,
      align: 'center' as const,
    },
    {
      title: t('overview.attackMac'),
      dataIndex: 'attackMac',
      key: 'attackMac',
      width: 192,
      align: 'center' as const,
      render: (mac: string) => <NetworkIdentifier value={mac} kind="mac" />,
    },
    {
      title: t('overview.threatLevel'),
      dataIndex: 'threatLevel',
      key: 'threatLevel',
      render: (level: ThreatLevel) => getThreatLevelTag(level),
      width: 104,
      align: 'center' as const,
    },
    {
      title: t('overview.detectionWindow'),
      dataIndex: 'detectionTier',
      key: 'detectionTier',
      render: (tier?: number) => (
        <Tooltip title={t('overview.detectionWindowHelp')}>
          <Tag>{detectionWindowLabel(tier)}</Tag>
        </Tooltip>
      ),
      width: 164,
      align: 'center' as const,
    },
    {
      title: t('overview.threatScore'),
      dataIndex: 'threatScore',
      key: 'threatScore',
      render: (score: number) => score?.toFixed(2) || 'N/A',
      width: 104,
      align: 'center' as const,
    },
    {
      title: t('overview.attackCount'),
      dataIndex: 'attackCount',
      key: 'attackCount',
      width: 104,
      align: 'center' as const,
    },
    {
      title: t('overview.uniqueIps'),
      dataIndex: 'uniqueIps',
      key: 'uniqueIps',
      width: 96,
      align: 'center' as const,
    },
    {
      title: t('overview.uniquePorts'),
      dataIndex: 'uniquePorts',
      key: 'uniquePorts',
      width: 104,
      align: 'center' as const,
    },
  ];

  const attackerColumns = [
    {
      title: t('overview.rank'),
      key: 'rank',
      width: 56,
      align: 'center' as const,
      render: (_: unknown, __: unknown, index: number) => `#${index + 1}`,
    },
    {
      title: t('overview.attackMac'),
      dataIndex: 'attackMac',
      key: 'attackMac',
      width: 192,
      align: 'center' as const,
      render: (mac: string) => <NetworkIdentifier value={mac} kind="mac" />,
    },
    {
      title: t('overview.attackIp'),
      dataIndex: 'attackIp',
      key: 'attackIp',
      width: 148,
      align: 'center' as const,
      render: (ip: string) => <NetworkIdentifier value={ip} kind="ip" />,
    },
    {
      title: t('overview.attackCount'),
      dataIndex: 'totalCount',
      key: 'totalCount',
      width: 120,
      align: 'center' as const,
      sorter: (a: TopAttacker, b: TopAttacker) => a.totalCount - b.totalCount,
    },
    {
      title: t('overview.maxThreatScore'),
      dataIndex: 'maxThreatScore',
      key: 'maxThreatScore',
      width: 148,
      align: 'center' as const,
      render: (score: number) => (
        <span
          className={
            score > 200
              ? 'tds-overview__score tds-overview__score--critical'
              : score > 100
                ? 'tds-overview__score tds-overview__score--high'
                : 'tds-overview__score'
          }
        >
          {score?.toFixed(2)}
        </span>
      ),
      sorter: (a: TopAttacker, b: TopAttacker) =>
        a.maxThreatScore - b.maxThreatScore,
    },
    {
      title: t('overview.maxThreatLevel'),
      dataIndex: 'maxThreatLevel',
      key: 'maxThreatLevel',
      width: 128,
      align: 'center' as const,
      render: (level: string) => getThreatLevelTag(level),
    },
  ];

  if (loading) {
    return (
      <div
        className="tds-overview tds-overview--loading"
        aria-busy="true"
        aria-label={t('overview.loading')}
      >
        <div className="tds-overview__toolbar">
          <span className="tds-overview__skeleton tds-overview__skeleton--control" />
          <span className="tds-overview__skeleton tds-overview__skeleton--button" />
        </div>
        <div className="tds-overview__posture-strip">
          {[1, 2, 3, 4].map((item) => (
            <div className="tds-overview__posture-item" key={item}>
              <span className="tds-overview__skeleton tds-overview__skeleton--label" />
              <span className="tds-overview__skeleton tds-overview__skeleton--value" />
            </div>
          ))}
        </div>
        <div className="tds-overview__analysis-grid">
          <div className="tds-overview__panel tds-overview__loading-panel" />
          <div className="tds-overview__panel tds-overview__loading-panel" />
        </div>
      </div>
    );
  }

  return (
    <div className="tds-overview">
      <div className="tds-overview__toolbar">
        <Segmented
          className="tds-overview__range"
          value={trendRange}
          onChange={(value) => setTrendRange(value as string)}
          aria-label={t('overview.threatTrend')}
          options={[
            { label: t('overview.range24h'), value: '24h' },
            { label: t('overview.range7d'), value: '7d' },
            { label: t('overview.range30d'), value: '30d' },
          ]}
        />
        <div className="tds-overview__toolbar-actions">
          {generatedAt ? (
            <time dateTime={generatedAt} className="tds-overview__freshness">
              {t('overview.lastUpdated', {
                time: formatDateTime(generatedAt),
              })}
            </time>
          ) : null}
          <Button
            loading={refreshing}
            aria-label={t('overview.refreshAria')}
            icon={<ReloadOutlined />}
            onClick={loadData}
          >
            {t('common.refresh')}
          </Button>
        </div>
      </div>

      {loadError ? (
        <div className="tds-overview__error" role="alert">
          <span>{t('overview.inlineLoadFailed')}</span>
          <Button type="link" onClick={loadData}>
            {t('overview.retry')}
          </Button>
        </div>
      ) : null}

      <section
        className="tds-overview__posture-strip"
        aria-label={t('overview.threatLevelDistribution')}
      >
        <article className="tds-overview__posture-item tds-overview__posture-item--lead">
          <span>{t('overview.totalThreats')}</span>
          <strong>{Number(statistics?.totalCount || 0).toLocaleString()}</strong>
        </article>
        <article className="tds-overview__posture-item">
          <span>{t('overview.criticalThreats')}</span>
          <strong className="tds-overview__value--critical">
            {Number(statistics?.criticalCount || 0).toLocaleString()}
          </strong>
        </article>
        <article className="tds-overview__posture-item">
          <span>{t('overview.highThreats')}</span>
          <strong className="tds-overview__value--high">
            {Number(statistics?.highCount || 0).toLocaleString()}
          </strong>
        </article>
        <article className="tds-overview__posture-item">
          <span>{t('overview.mediumThreats')}</span>
          <strong className="tds-overview__value--medium">
            {Number(statistics?.mediumCount || 0).toLocaleString()}
          </strong>
        </article>
      </section>

      <div className="tds-overview__analysis-grid">
        <section
          className="tds-overview__panel tds-overview__panel--trend"
          aria-labelledby="overview-trend-title"
        >
          <header className="tds-overview__panel-header">
            <div>
              <h2 id="overview-trend-title">{t('overview.threatTrend')}</h2>
              <span>{rangeLabel}</span>
            </div>
          </header>
          <div className="tds-overview__panel-body tds-overview__chart-body">
            {trendData.length > 0 ? (
              <div role="img" aria-label={t('overview.trendChartAria')}>
                <Line {...trendConfig} height={240} />
              </div>
            ) : (
              <EmptyState
                description={t('overview.noTrendData')}
                image="simple"
              />
            )}
          </div>
        </section>

        <section
          className="tds-overview__panel tds-overview__panel--levels"
          aria-labelledby="overview-level-title"
        >
          <header className="tds-overview__panel-header">
            <h2 id="overview-level-title">
              {t('overview.threatLevelDistribution')}
            </h2>
          </header>
          <div className="tds-overview__level-layout">
            {levelDistData.length > 0 ? (
              <>
                <div
                  className="tds-overview__level-chart"
                  role="img"
                  aria-label={t('overview.levelChartAria')}
                >
                  <Pie {...levelPieConfig} height={190} />
                </div>
                <ul className="tds-overview__severity-list">
                  {levelDistData.map((item) => (
                    <li
                      key={item.level}
                      style={
                        {
                          '--severity-color': item.color,
                        } as CSSProperties
                      }
                    >
                      <span className="tds-overview__severity-name">
                        <i aria-hidden="true" />
                        {item.level}
                      </span>
                      <strong>{item.count.toLocaleString()}</strong>
                    </li>
                  ))}
                </ul>
              </>
            ) : (
              <EmptyState image="simple" />
            )}
          </div>
        </section>
      </div>

      <section
        className="tds-overview__panel tds-overview__panel--table"
        aria-labelledby="overview-latest-title"
      >
        <header className="tds-overview__panel-header">
          <h2 id="overview-latest-title">{t('overview.latestThreats')}</h2>
          <Button
            type="link"
            aria-label={t('overview.viewAllAria')}
            onClick={() => navigate('/investigate/threats')}
          >
            {t('overview.viewAll')} &rarr;
          </Button>
        </header>
        <div className="tds-overview__desktop-table">
          <Table
            columns={recentColumns}
            dataSource={recentThreats}
            rowKey="id"
            pagination={false}
            size="small"
            tableLayout="fixed"
            scroll={{ x: 1048 }}
          />
        </div>
        <div className="tds-overview__mobile-list">
          {recentThreats.length > 0 ? (
            recentThreats.map((threat) => (
              <article className="tds-overview__mobile-row" key={threat.id}>
                <div className="tds-overview__mobile-row-primary">
                  <NetworkIdentifier value={threat.attackMac} kind="mac" />
                  {getThreatLevelTag(threat.threatLevel)}
                </div>
                <time dateTime={threat.assessmentTime}>
                  {formatDateTime(threat.assessmentTime)}
                </time>
                <dl>
                  <div>
                    <dt>{t('overview.detectionWindow')}</dt>
                    <dd>{detectionWindowLabel(threat.detectionTier)}</dd>
                  </div>
                  <div>
                    <dt>{t('overview.threatScore')}</dt>
                    <dd>{threat.threatScore?.toFixed(2) || 'N/A'}</dd>
                  </div>
                  <div>
                    <dt>{t('overview.attackCount')}</dt>
                    <dd>{threat.attackCount}</dd>
                  </div>
                  <div>
                    <dt>{t('overview.uniqueIps')}</dt>
                    <dd>{threat.uniqueIps}</dd>
                  </div>
                  <div>
                    <dt>{t('overview.uniquePorts')}</dt>
                    <dd>{threat.uniquePorts}</dd>
                  </div>
                </dl>
              </article>
            ))
          ) : (
            <EmptyState image="simple" />
          )}
        </div>
      </section>

      <div className="tds-overview__secondary-grid">
        <section
          className="tds-overview__panel"
          aria-labelledby="overview-port-title"
        >
          <header className="tds-overview__panel-header">
            <h2 id="overview-port-title">
              {t('overview.portDistributionTop15')}
            </h2>
          </header>
          <div className="tds-overview__panel-body">
            {portData.length > 0 ? (
              <PortDistributionChart
                data={portData}
                labels={{
                  port: t('overview.port'),
                  service: t('overview.service'),
                  attackCount: t('overview.attackCount'),
                  percentage: t('overview.percentage'),
                  unknownService: t('overview.unknownService'),
                }}
                ariaLabel={t('overview.portChartAria')}
              />
            ) : (
              <EmptyState image="simple" />
            )}
            {nonPortProtocols.length > 0 ? (
              <>
                <Divider className="tds-overview__protocol-divider" />
                <Space wrap aria-label={t('overview.nonPortProtocols')}>
                  <Tooltip title={t('overview.nonPortProtocolsHelp')}>
                    <span className="tds-overview__protocol-label">
                      {t('overview.nonPortProtocols')}:
                    </span>
                  </Tooltip>
                  {nonPortProtocols.map((item) => (
                    <Tag
                      key={item.protocol}
                      color={
                        item.protocol === 'PORT_ANOMALY' ? 'warning' : 'default'
                      }
                    >
                      {t('overview.nonPortProtocolValue', {
                        protocol:
                          item.protocol === 'PORT_ANOMALY'
                            ? t('overview.portAnomaly')
                            : item.protocol === 'OTHER'
                              ? t('overview.otherProtocol')
                              : item.protocol,
                        count: Number(item.count).toLocaleString(),
                      })}
                    </Tag>
                  ))}
                </Space>
              </>
            ) : null}
          </div>
        </section>

        <section
          className="tds-overview__panel tds-overview__panel--table"
          aria-labelledby="overview-attackers-title"
        >
          <header className="tds-overview__panel-header">
            <h2 id="overview-attackers-title">{t('overview.topAttackers')}</h2>
          </header>
          <div className="tds-overview__desktop-table">
            <Table
              columns={attackerColumns}
              dataSource={topAttackers}
              rowKey="attackMac"
              pagination={false}
              size="small"
              tableLayout="fixed"
              scroll={{ x: 792 }}
            />
          </div>
          <div className="tds-overview__mobile-list">
            {topAttackers.length > 0 ? (
              topAttackers.map((attacker, index) => (
                <article
                  className="tds-overview__mobile-row"
                  key={attacker.attackMac}
                >
                  <div className="tds-overview__mobile-row-primary">
                    <span className="tds-overview__rank">#{index + 1}</span>
                    <NetworkIdentifier value={attacker.attackMac} kind="mac" />
                    {getThreatLevelTag(attacker.maxThreatLevel)}
                  </div>
                  <NetworkIdentifier value={attacker.attackIp} kind="ip" />
                  <dl>
                    <div>
                      <dt>{t('overview.attackCount')}</dt>
                      <dd>{attacker.totalCount.toLocaleString()}</dd>
                    </div>
                    <div>
                      <dt>{t('overview.maxThreatScore')}</dt>
                      <dd>{attacker.maxThreatScore.toFixed(2)}</dd>
                    </div>
                  </dl>
                </article>
              ))
            ) : (
              <EmptyState image="simple" />
            )}
          </div>
        </section>
      </div>
    </div>
  );
};

export default Overview;
