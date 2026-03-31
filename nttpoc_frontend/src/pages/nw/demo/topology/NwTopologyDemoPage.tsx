import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import {
  ReactFlow,
  Background,
  BackgroundVariant,
  Handle,
  Position,
  type NodeProps,
  type EdgeProps,
  type Node,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import { AlertTriangle, CheckCircle, XCircle, Activity, Clock, Wifi } from 'lucide-react';

/* ═══════════════════════════════════════════════════════════
   Types
═══════════════════════════════════════════════════════════ */
type NodeStatus = 'normal' | 'warning' | 'critical';
type Severity   = 'critical' | 'warning' | 'info';

interface NwNode {
  id: string; label: string; ip: string;
  x: number;  y: number;
  layer: 'core' | 'dist' | 'edge';
  status: NodeStatus; cpu: number;
}
interface NwLink  { from: string; to: string; util: number; speed: string; }
interface AlertItem { id: number; time: string; node: string; severity: Severity; message: string; }

interface NwNodeData extends Record<string, unknown> {
  label: string; ip: string;
  layer: 'core' | 'dist' | 'edge';
  status: NodeStatus; cpu: number;
  isSelected: boolean;
}
interface NwEdgeData extends Record<string, unknown> {
  util: number; speed: string;
}

/* ═══════════════════════════════════════════════════════════
   Node layout constants
═══════════════════════════════════════════════════════════ */
const NODE_W  = 100; // node container width
const NODE_H  = 90;  // node container height
const CX      = 50;  // circle center X within node
const CY      = 38;  // circle center Y within node

/* ═══════════════════════════════════════════════════════════
   Static data
═══════════════════════════════════════════════════════════ */
const NODE_DEFS: Omit<NwNode, 'status' | 'cpu'>[] = [
  { id: 'C1', label: 'Core-SW-01', ip: '10.0.0.1', x: 280, y: 75,  layer: 'core' },
  { id: 'C2', label: 'Core-SW-02', ip: '10.0.0.2', x: 580, y: 75,  layer: 'core' },
  { id: 'D1', label: 'Dist-01',    ip: '10.1.0.1', x: 110, y: 215, layer: 'dist' },
  { id: 'D2', label: 'Dist-02',    ip: '10.1.0.2', x: 430, y: 215, layer: 'dist' },
  { id: 'D3', label: 'Dist-03',    ip: '10.1.0.3', x: 750, y: 215, layer: 'dist' },
  { id: 'E1', label: 'Edge-01',    ip: '10.2.0.1', x: 40,  y: 365, layer: 'edge' },
  { id: 'E2', label: 'Edge-02',    ip: '10.2.0.2', x: 180, y: 365, layer: 'edge' },
  { id: 'E3', label: 'Edge-03',    ip: '10.2.0.3', x: 330, y: 365, layer: 'edge' },
  { id: 'E4', label: 'Edge-04',    ip: '10.2.0.4', x: 480, y: 365, layer: 'edge' },
  { id: 'E5', label: 'Edge-05',    ip: '10.2.0.5', x: 620, y: 365, layer: 'edge' },
  { id: 'E6', label: 'Edge-06',    ip: '10.2.0.6', x: 760, y: 365, layer: 'edge' },
];

const LINK_DEFS: { from: string; to: string; speed: string }[] = [
  { from: 'C1', to: 'C2', speed: '10G' },
  { from: 'C1', to: 'D1', speed: '10G' }, { from: 'C1', to: 'D2', speed: '10G' },
  { from: 'C2', to: 'D2', speed: '10G' }, { from: 'C2', to: 'D3', speed: '10G' },
  { from: 'D1', to: 'E1', speed: '1G'  }, { from: 'D1', to: 'E2', speed: '1G'  },
  { from: 'D2', to: 'E3', speed: '1G'  }, { from: 'D2', to: 'E4', speed: '1G'  },
  { from: 'D3', to: 'E5', speed: '1G'  }, { from: 'D3', to: 'E6', speed: '1G'  },
];

const ALERTS: AlertItem[] = [
  { id: 1, time: '14:31:05', node: 'Edge-03',    severity: 'critical', message: 'リンクダウン検出' },
  { id: 2, time: '14:28:42', node: 'Dist-02',    severity: 'warning',  message: 'CPU使用率 91% 超過' },
  { id: 3, time: '14:25:17', node: 'Edge-05',    severity: 'warning',  message: '遅延増加 (RTT > 50ms)' },
  { id: 4, time: '14:10:03', node: 'Core-SW-01', severity: 'info',     message: 'ポート再起動完了' },
];

/* ═══════════════════════════════════════════════════════════
   Helpers
═══════════════════════════════════════════════════════════ */
function fluctuate(val: number, range: number, min = 0, max = 100) {
  return Math.min(max, Math.max(min, val + (Math.random() - 0.5) * range));
}
function nodeColor(status: NodeStatus) {
  return status === 'normal' ? '#22c55e' : status === 'warning' ? '#f59e0b' : '#ef4444';
}
function linkColor(util: number) {
  return util >= 90 ? '#ef4444' : util >= 70 ? '#f59e0b' : '#22d3ee';
}
function nodeR(layer: NwNode['layer']) {
  return layer === 'core' ? 22 : layer === 'dist' ? 17 : 13;
}
function sparkPath(values: number[], w: number, h: number): string {
  if (values.length < 2) return '';
  const max = Math.max(...values), min = Math.min(...values);
  const range = max - min || 1;
  const step = w / (values.length - 1);
  return values
    .map((v, i) => `${i === 0 ? 'M' : 'L'} ${(i * step).toFixed(1)} ${(h - ((v - min) / range) * (h - 6) - 3).toFixed(1)}`)
    .join(' ');
}
function initNodes(): NwNode[] {
  return NODE_DEFS.map(d => ({
    ...d,
    status: d.id === 'E3' ? 'critical' : d.id === 'D2' || d.id === 'E5' ? 'warning' : 'normal',
    cpu: d.id === 'D2' ? 91 : d.id === 'E3' ? 0 : Math.floor(Math.random() * 45) + 15,
  }));
}
function initLinks(): NwLink[] {
  return LINK_DEFS.map(d => ({
    ...d,
    util: (d.from === 'D2' && d.to === 'E3') ? 0
        : (d.from === 'C1' && d.to === 'D2') ? 91
        : (d.from === 'D3' && d.to === 'E5') ? 77
        : Math.floor(Math.random() * 55) + 20,
  }));
}
function initSpark(len: number, base: number, range: number) {
  return Array.from({ length: len }, () => base + (Math.random() - 0.5) * range);
}

/* ═══════════════════════════════════════════════════════════
   Static sub-components (unchanged)
═══════════════════════════════════════════════════════════ */
function KpiCard({ label, value, sub, color }: { label: string; value: number | string; sub?: string; color: string }) {
  return (
    <div className="bg-slate-900 border border-slate-800 rounded-xl px-4 py-3 flex flex-col gap-0.5">
      <span className="text-xs text-slate-400">{label}</span>
      <span className="text-2xl font-bold tabular-nums" style={{ color }}>{value}</span>
      {sub && <span className="text-xs text-slate-500">{sub}</span>}
    </div>
  );
}

function SeverityIcon({ severity }: { severity: Severity }) {
  if (severity === 'critical') return <XCircle className="size-3.5 text-red-400 shrink-0" />;
  if (severity === 'warning')  return <AlertTriangle className="size-3.5 text-amber-400 shrink-0" />;
  return <CheckCircle className="size-3.5 text-cyan-400 shrink-0" />;
}

function SparkCard({ label, values, unit, color, latest }:
    { label: string; values: number[]; unit: string; color: string; latest: number }) {
  const W = 200, H = 44;
  const path = sparkPath(values, W, H);
  const fill = path ? `${path} L ${W} ${H} L 0 ${H} Z` : '';
  return (
    <div className="bg-slate-900 border border-slate-800 rounded-xl px-4 py-3">
      <div className="flex items-baseline justify-between mb-1.5">
        <span className="text-xs text-slate-400">{label}</span>
        <span className="text-sm font-semibold tabular-nums" style={{ color }}>
          {latest.toFixed(unit === 'Gbps' ? 1 : unit === '%' ? 2 : 0)}&nbsp;{unit}
        </span>
      </div>
      <svg viewBox={`0 0 ${W} ${H}`} className="w-full" style={{ height: H }}>
        <defs>
          <linearGradient id={`sg-${label}`} x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor={color} stopOpacity="0.25" />
            <stop offset="100%" stopColor={color} stopOpacity="0" />
          </linearGradient>
        </defs>
        {fill && <path d={fill} fill={`url(#sg-${label})`} />}
        {path && <path d={path} fill="none" stroke={color} strokeWidth="1.5" strokeLinejoin="round" />}
      </svg>
    </div>
  );
}

/* ═══════════════════════════════════════════════════════════
   ReactFlow — Custom Node
   Handle is invisible, positioned at circle center (CX, CY)
   so edges connect exactly at the node's circle center.
═══════════════════════════════════════════════════════════ */
const HANDLE_STYLE: React.CSSProperties = {
  opacity: 0, width: 1, height: 1,
  minWidth: 0, minHeight: 0,
  background: 'transparent', border: 'none',
  top: CY, left: CX,
  transform: 'translate(-50%, -50%)',
};

function NwNodeComponent({ id: nodeId, data }: NodeProps) {
  const d = data as NwNodeData;
  const r   = nodeR(d.layer);
  const col = d.status === 'critical' && d.cpu === 0 ? '#6b7280' : nodeColor(d.status);
  const filterId = `nglow-${nodeId}`;

  return (
    <>
      <Handle type="source" position={Position.Top}  style={HANDLE_STYLE} />
      <Handle type="target" position={Position.Top}  style={HANDLE_STYLE} />

      <svg width={NODE_W} height={NODE_H} style={{ overflow: 'visible', display: 'block' }}>
        <defs>
          <filter id={filterId} x="-60%" y="-60%" width="220%" height="220%">
            <feGaussianBlur stdDeviation="4" result="blur" />
            <feMerge><feMergeNode in="blur" /><feMergeNode in="SourceGraphic" /></feMerge>
          </filter>
        </defs>

        {/* Pulse ring (non-normal status) */}
        {d.status !== 'normal' && (
          <circle cx={CX} cy={CY} r={r + 8} fill="none" stroke={col} strokeWidth="1.5" strokeOpacity="0.4">
            <animate attributeName="r"
              values={`${r + 4};${r + 14};${r + 4}`} dur="2s" repeatCount="indefinite" />
            <animate attributeName="stroke-opacity"
              values="0.5;0;0.5" dur="2s" repeatCount="indefinite" />
          </circle>
        )}

        {/* Selection ring */}
        {d.isSelected && (
          <circle cx={CX} cy={CY} r={r + 6} fill="none"
            stroke="#f8fafc" strokeWidth="1.5" strokeDasharray="4 3" strokeOpacity="0.7" />
        )}

        {/* Glow halo */}
        <circle cx={CX} cy={CY} r={r + 4}
          fill={col} fillOpacity="0.12" filter={`url(#${filterId})`} />

        {/* Main circle */}
        <circle cx={CX} cy={CY} r={r}
          fill="#0f172a" stroke={col}
          strokeWidth={d.layer === 'core' ? 2.5 : 2}
          filter={`url(#${filterId})`} />

        {/* Inner dot */}
        <circle cx={CX} cy={CY} r={r * 0.35} fill={col} fillOpacity="0.9" />

        {/* Label */}
        <text x={CX} y={CY + r + 12}
          fontSize={d.layer === 'core' ? 10 : 9}
          fill={d.isSelected ? '#f8fafc' : '#cbd5e1'}
          textAnchor="middle"
          fontWeight={d.layer === 'core' ? 700 : 400}
          style={{ paintOrder: 'stroke', stroke: '#0f172a', strokeWidth: 3 }}>
          {d.label}
        </text>

        {/* IP */}
        <text x={CX} y={CY + r + 22}
          fontSize="7.5" fill="#475569" textAnchor="middle">
          {d.ip}
        </text>
      </svg>
    </>
  );
}

/* ═══════════════════════════════════════════════════════════
   ReactFlow — Layer Label Node (read-only text marker)
═══════════════════════════════════════════════════════════ */
function LayerLabelNode({ data }: NodeProps) {
  const d = data as { text: string };
  return (
    <div style={{ pointerEvents: 'none', userSelect: 'none' }}>
      <svg width={120} height={16}>
        <text x={2} y={12} fontSize={9} fill="#475569"
          letterSpacing={1} style={{ userSelect: 'none' }}>
          {d.text}
        </text>
      </svg>
    </div>
  );
}

/* ═══════════════════════════════════════════════════════════
   ReactFlow — Custom Edge
   Straight line with animated dashes + utilization label.
   No SVG filter (avoids cross-SVG scoping issues).
═══════════════════════════════════════════════════════════ */
function NwEdgeComponent({ id: _id, sourceX, sourceY, targetX, targetY, data }: EdgeProps) {
  const d    = data as NwEdgeData | undefined;
  const util = d?.util ?? 0;
  const col  = util === 0 ? '#475569' : linkColor(util);
  const dur  = Math.max(0.6, 2.0 - (util / 100) * 1.4);
  const midX = (sourceX + targetX) / 2;
  const midY = (sourceY + targetY) / 2 - 5;
  const line = `M ${sourceX} ${sourceY} L ${targetX} ${targetY}`;

  return (
    <g>
      {/* Shadow line */}
      <path d={line} stroke="#1e293b" strokeWidth={4} fill="none" />

      {/* Active flow line */}
      {util > 0 ? (
        <path d={line}
          stroke={col} strokeWidth={2} fill="none"
          strokeDasharray="10 6" strokeOpacity={0.85}>
          <animate attributeName="stroke-dashoffset"
            from="16" to="0" dur={`${dur}s`} repeatCount="indefinite" />
        </path>
      ) : (
        <path d={line}
          stroke={col} strokeWidth={1.5} fill="none"
          strokeDasharray="4 4" strokeOpacity={0.5} />
      )}

      {/* Utilization label */}
      {util > 0 && (
        <text x={midX} y={midY}
          fontSize="8.5" fill={col} textAnchor="middle" fontWeight={600}
          style={{ paintOrder: 'stroke', stroke: '#0f172a', strokeWidth: 3 }}>
          {util.toFixed(0)}%
        </text>
      )}
    </g>
  );
}

/* ═══════════════════════════════════════════════════════════
   ReactFlow type maps (defined outside component to be stable)
═══════════════════════════════════════════════════════════ */
const NODE_TYPES = {
  nwNode:     NwNodeComponent,
  layerLabel: LayerLabelNode,
};
const EDGE_TYPES = {
  nwEdge: NwEdgeComponent,
};

/* ═══════════════════════════════════════════════════════════
   Conversion: NwNode[] → ReactFlow Node[]
═══════════════════════════════════════════════════════════ */
function buildRfNodes(
  nodes: NwNode[],
  selected: string | null,
  layerLabels: { text: string; y: number }[],
): Node[] {
  const labelNodes: Node[] = layerLabels.map(ll => ({
    id: `layer-${ll.y}`,
    type: 'layerLabel',
    position: { x: -80, y: ll.y - 10 },
    data: { text: ll.text },
    selectable: false,
    draggable: false,
    style: { background: 'transparent', border: 'none', padding: 0 },
  }));

  const nwNodes: Node[] = nodes.map(node => ({
    id: node.id,
    type: 'nwNode',
    // Offset position so circle center = original x,y
    position: { x: node.x - CX, y: node.y - CY },
    data: {
      label: node.label, ip: node.ip,
      layer: node.layer, status: node.status,
      cpu: node.cpu, isSelected: selected === node.id,
    } satisfies NwNodeData,
    style: {
      background: 'transparent', border: 'none',
      boxShadow: 'none', padding: 0,
      width: NODE_W, height: NODE_H,
    },
    selectable: false,
    draggable: false,
  }));

  return [...labelNodes, ...nwNodes];
}

/* ═══════════════════════════════════════════════════════════
   Main Page
═══════════════════════════════════════════════════════════ */
export default function NwTopologyDemoPage() {
  const { t } = useTranslation('nw');

  const [nodes,    setNodes   ] = useState<NwNode[]>(initNodes);
  const [links,    setLinks   ] = useState<NwLink[]>(initLinks);
  const [now,      setNow     ] = useState(new Date());
  const [traffic,  setTraffic ] = useState(() => initSpark(30, 5.2, 2.0));
  const [latency,  setLatency ] = useState(() => initSpark(30, 12,  6.0));
  const [loss,     setLoss    ] = useState(() => initSpark(30, 0.3,  0.4).map(v => Math.max(0, v)));
  const [selected, setSelected] = useState<string | null>(null);

  /* Clock */
  useEffect(() => {
    const id = setInterval(() => setNow(new Date()), 1000);
    return () => clearInterval(id);
  }, []);

  /* Real-time simulation */
  useEffect(() => {
    const id = setInterval(() => {
      setLinks(prev => prev.map(lk => ({
        ...lk,
        util: (lk.from === 'D2' && lk.to === 'E3') ? 0 : fluctuate(lk.util, 6),
      })));
      setNodes(prev => prev.map(n => ({
        ...n,
        cpu: n.id === 'E3' ? 0 : fluctuate(n.cpu, 5),
      })));
      setTraffic(prev => [...prev.slice(1), fluctuate(prev[prev.length - 1], 1.2, 0.5, 12)]);
      setLatency(prev => [...prev.slice(1), fluctuate(prev[prev.length - 1], 4, 2, 80)]);
      setLoss(   prev => [...prev.slice(1), Math.max(0, fluctuate(prev[prev.length - 1], 0.15, 0, 5))]);
    }, 2000);
    return () => clearInterval(id);
  }, []);

  /* Derived */
  const nodeMap   = Object.fromEntries(nodes.map(n => [n.id, n]));
  const normalCnt = nodes.filter(n => n.status === 'normal').length;
  const warnCnt   = nodes.filter(n => n.status === 'warning').length;
  const critCnt   = nodes.filter(n => n.status === 'critical').length;
  const top5      = [...links].sort((a, b) => b.util - a.util).slice(0, 5);
  const selectedNode = selected ? nodeMap[selected] : null;

  const timeStr = now.toLocaleString('ja-JP', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit',
  });

  /* ReactFlow data (memoized) */
  const rfNodes = useMemo(() => {
    const layerLabels = [
      { text: t('topology.layer.core'), y: 75  },
      { text: t('topology.layer.dist'), y: 215 },
      { text: t('topology.layer.edge'), y: 365 },
    ];
    return buildRfNodes(nodes, selected, layerLabels);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [nodes, selected, t]);

  const rfEdges = useMemo(() =>
    links.map(lk => ({
      id: `${lk.from}-${lk.to}`,
      source: lk.from,
      target: lk.to,
      type: 'nwEdge',
      data: { util: lk.util, speed: lk.speed } satisfies NwEdgeData,
      selectable: false,
    })),
  [links]);

  const onNodeClick = useCallback((_: React.MouseEvent, node: Node) => {
    if (node.type === 'nwNode') {
      setSelected(prev => prev === node.id ? null : node.id);
    }
  }, []);

  return (
    <div
      className="flex flex-col bg-slate-950 text-slate-100 overflow-hidden"
      style={{ height: 'calc(100vh - 3.5rem)' }}
    >
      {/* ── Header ── */}
      <div className="flex items-center justify-between px-5 py-2.5 border-b border-slate-800 shrink-0">
        <div className="flex items-center gap-3">
          <Wifi className="size-4 text-cyan-400" />
          <h1 className="text-sm font-semibold tracking-wide">{t('topology.title')}</h1>
          <span className="flex items-center gap-1.5 text-[11px] bg-cyan-400/10 text-cyan-400
                           border border-cyan-400/30 rounded-full px-2.5 py-0.5 font-medium">
            <span className="size-1.5 rounded-full bg-cyan-400 animate-pulse inline-block" />
            {t('live')}
          </span>
        </div>
        <div className="flex items-center gap-1.5 text-xs text-slate-400">
          <Clock className="size-3" />
          {timeStr}
        </div>
      </div>

      {/* ── KPI Row ── */}
      <div className="grid grid-cols-4 gap-3 px-5 py-2.5 shrink-0">
        <KpiCard label={t('topology.kpi.total')}  value={nodes.length} sub={t('topology.kpi.totalSub')}  color="#94a3b8" />
        <KpiCard label={t('topology.kpi.normal')} value={normalCnt}    sub={t('topology.kpi.normalSub')} color="#22c55e" />
        <KpiCard label={t('topology.kpi.alert')}  value={warnCnt}      sub={t('topology.kpi.alertSub')}  color="#f59e0b" />
        <KpiCard label={t('topology.kpi.fault')}  value={critCnt}      sub={t('topology.kpi.faultSub')}  color="#ef4444" />
      </div>

      {/* ── Main Body ── */}
      <div className="flex-1 flex gap-3 px-5 pb-2 min-h-0">

        {/* ── ReactFlow Topology ── */}
        <div className="flex-1 bg-slate-900 rounded-xl border border-slate-800 overflow-hidden flex flex-col">
          <div className="flex items-center justify-between px-3 py-2 border-b border-slate-800 shrink-0">
            <span className="text-xs font-medium text-slate-300">{t('topology.subTitle')}</span>
            <span className="text-[10px] text-slate-500">{t('topology.clickHint')}</span>
          </div>

          <div className="flex-1 min-h-0">
            <ReactFlow
              nodes={rfNodes}
              edges={rfEdges}
              nodeTypes={NODE_TYPES}
              edgeTypes={EDGE_TYPES}
              onNodeClick={onNodeClick}
              nodesDraggable={false}
              nodesConnectable={false}
              elementsSelectable={false}
              panOnDrag
              zoomOnScroll
              fitView
              fitViewOptions={{ padding: 0.15 }}
              style={{ background: 'transparent' }}
              proOptions={{ hideAttribution: true }}
            >
              <Background
                variant={BackgroundVariant.Dots}
                color="#334155"
                gap={30}
                size={0.8}
              />
            </ReactFlow>
          </div>
        </div>

        {/* ── Right panel ── */}
        <div className="w-64 flex flex-col gap-3 shrink-0">

          {/* Node detail */}
          {selectedNode ? (
            <div className="bg-slate-900 rounded-xl border border-slate-800 p-3 shrink-0">
              <div className="flex items-center justify-between mb-2">
                <span className="text-xs font-semibold text-slate-200"
                      title={selectedNode.label}>{selectedNode.label}</span>
                <span className="text-[10px] font-semibold px-1.5 py-0.5 rounded-full"
                  style={{
                    color: nodeColor(selectedNode.status),
                    background: `${nodeColor(selectedNode.status)}20`,
                    border: `1px solid ${nodeColor(selectedNode.status)}50`,
                  }}>
                  {selectedNode.status.toUpperCase()}
                </span>
              </div>
              <div className="grid grid-cols-2 gap-1 text-[11px]">
                <span className="text-slate-500">{t('topology.detail.ip')}</span>
                <span className="text-slate-300 text-right font-mono">{selectedNode.ip}</span>
                <span className="text-slate-500">{t('topology.detail.layer')}</span>
                <span className="text-slate-300 text-right capitalize">{selectedNode.layer}</span>
                <span className="text-slate-500">{t('topology.detail.cpu')}</span>
                <span className="text-slate-300 text-right">{selectedNode.cpu.toFixed(0)}%</span>
              </div>
              <div className="mt-2 h-1.5 bg-slate-800 rounded-full overflow-hidden">
                <div className="h-full rounded-full transition-all duration-700"
                  style={{
                    width: `${selectedNode.cpu}%`,
                    background: selectedNode.cpu >= 90 ? '#ef4444'
                              : selectedNode.cpu >= 70 ? '#f59e0b' : '#22c55e',
                  }} />
              </div>
            </div>
          ) : (
            <div className="bg-slate-900 rounded-xl border border-slate-800 p-3 shrink-0
                            flex items-center justify-center text-[11px] text-slate-600 h-[88px]">
              {t('topology.selectNode')}
            </div>
          )}

          {/* Active alerts */}
          <div className="bg-slate-900 rounded-xl border border-slate-800 overflow-hidden flex flex-col"
               style={{ flex: '1 1 0' }}>
            <div className="px-3 py-2 border-b border-slate-800 shrink-0 flex items-center gap-1.5">
              <Activity className="size-3 text-red-400" />
              <span className="text-xs font-medium text-slate-300">{t('topology.alerts.panelTitle')}</span>
              <span className="ml-auto text-[10px] bg-red-400/20 text-red-400 rounded-full px-1.5 py-0.5">
                {ALERTS.filter(a => a.severity !== 'info').length}
              </span>
            </div>
            <div className="overflow-y-auto flex-1">
              {ALERTS.map(alert => (
                <div key={alert.id}
                  className="flex items-start gap-2 px-3 py-2 border-b border-slate-800/60 last:border-0
                             hover:bg-slate-800/40 transition-colors">
                  <SeverityIcon severity={alert.severity} />
                  <div className="flex-1 min-w-0">
                    <div className="text-[11px] font-medium text-slate-200 truncate">{alert.node}</div>
                    <div className="text-[10px] text-slate-400 truncate">{alert.message}</div>
                    <div className="text-[9px] text-slate-600 mt-0.5">{alert.time}</div>
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Link utilization TOP5 */}
          <div className="bg-slate-900 rounded-xl border border-slate-800 overflow-hidden flex flex-col shrink-0">
            <div className="px-3 py-2 border-b border-slate-800 shrink-0">
              <span className="text-xs font-medium text-slate-300">{t('topology.linkUtil.panelTitle')}</span>
            </div>
            <div className="p-3 flex flex-col gap-2">
              {top5.map(lk => {
                const col = lk.util === 0 ? '#475569' : linkColor(lk.util);
                return (
                  <div key={`${lk.from}-${lk.to}`}>
                    <div className="flex items-center justify-between mb-0.5">
                      <span className="text-[10px] text-slate-400 font-mono">{lk.from}→{lk.to}</span>
                      <span className="text-[10px] font-semibold tabular-nums" style={{ color: col }}>
                        {lk.util === 0 ? t('topology.linkUtil.down') : `${lk.util.toFixed(0)}%`}
                      </span>
                    </div>
                    <div className="h-1 bg-slate-800 rounded-full overflow-hidden">
                      <div className="h-full rounded-full transition-all duration-700"
                        style={{ width: `${lk.util}%`, background: col }} />
                    </div>
                  </div>
                );
              })}
            </div>
          </div>

        </div>
      </div>

      {/* ── Sparkline row ── */}
      <div className="grid grid-cols-3 gap-3 px-5 pb-4 shrink-0">
        <SparkCard label={t('topology.spark.traffic')} values={traffic} unit="Gbps"
          color="#22d3ee" latest={traffic[traffic.length - 1]} />
        <SparkCard label={t('topology.spark.latency')} values={latency} unit="ms"
          color="#a78bfa" latest={latency[latency.length - 1]} />
        <SparkCard label={t('topology.spark.loss')}    values={loss}    unit="%"
          color="#f87171" latest={loss[loss.length - 1]} />
      </div>
    </div>
  );
}
