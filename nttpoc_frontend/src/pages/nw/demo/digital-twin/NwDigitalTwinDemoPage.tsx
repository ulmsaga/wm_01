import React, { useState, useEffect, useCallback, useRef, Suspense } from 'react';
import { useTranslation } from 'react-i18next';
import { Canvas, useFrame } from '@react-three/fiber';
import { OrbitControls, Html, Edges, Line, Grid } from '@react-three/drei';
import * as THREE from 'three';
import { Server, AlertTriangle, XCircle, Cpu, Activity, Zap, Clock, Thermometer } from 'lucide-react';

/* ═══════════════════════════════════════════════════════════
   Types
═══════════════════════════════════════════════════════════ */
type RackStatus = 'normal' | 'warning' | 'critical' | 'offline';

interface ServerRack {
  id: string; label: string;
  row: number; col: number;
  status: RackStatus;
  cpu: number; memory: number; temp: number; network: number;
  uptime: string; model: string; responsible: string;
  year: number; load: string;
}

/* ═══════════════════════════════════════════════════════════
   Static rack definitions
═══════════════════════════════════════════════════════════ */
const RACK_DEFS: Omit<ServerRack, 'cpu' | 'memory' | 'temp' | 'network'>[] = [
  { id: 'SVR-A', label: 'Server A', row: 0, col: 4, status: 'normal',   uptime: '72h',  model: 'Dell PowerEdge R750',  responsible: '山田 太郎', year: 2022, load: '0.45|0.52|0.48' },
  { id: 'SVR-B', label: 'Server B', row: 0, col: 2, status: 'warning',  uptime: '39h',  model: 'HP ProLiant DL380',    responsible: 'Nancy',     year: 2020, load: '0.01|0.01|0.12' },
  { id: 'SVR-C', label: 'Server C', row: 1, col: 0, status: 'normal',   uptime: '120h', model: 'Dell PowerEdge R650',  responsible: '鈴木 花子', year: 2021, load: '0.32|0.30|0.28' },
  { id: 'SVR-D', label: 'Server D', row: 0, col: 3, status: 'normal',   uptime: '88h',  model: 'Fujitsu PRIMERGY',     responsible: '田中 一郎', year: 2023, load: '0.61|0.58|0.55' },
  { id: 'SVR-E', label: 'Server E', row: 1, col: 2, status: 'normal',   uptime: '215h', model: 'HP ProLiant DL360',    responsible: 'Nancy',     year: 2020, load: '0.01|0.01|0.12' },
  { id: 'SVR-F', label: 'Server F', row: 1, col: 1, status: 'normal',   uptime: '96h',  model: 'Dell PowerEdge R740',  responsible: '佐藤 次郎', year: 2022, load: '0.44|0.40|0.38' },
  { id: 'SVR-G', label: 'Server G', row: 2, col: 0, status: 'critical', uptime: '0h',   model: 'Lenovo ThinkSystem',   responsible: '高橋 美咲', year: 2019, load: '0.00|0.00|0.00' },
  { id: 'SVR-H', label: 'Server H', row: 1, col: 3, status: 'normal',   uptime: '144h', model: 'Cisco UCS C240',       responsible: '伊藤 健太', year: 2021, load: '0.55|0.50|0.48' },
  { id: 'UPS-1', label: 'UPS-1',   row: 0, col: 5, status: 'normal',   uptime: '480h', model: 'APC Smart-UPS',        responsible: '山田 太郎', year: 2020, load: '0.20|0.20|0.20' },
  { id: 'UPS-2', label: 'UPS-2',   row: 1, col: 5, status: 'warning',  uptime: '480h', model: 'APC Smart-UPS',        responsible: '山田 太郎', year: 2020, load: '0.75|0.72|0.70' },
];

function fluctuate(val: number, range: number, min = 0, max = 100) {
  return Math.min(max, Math.max(min, val + (Math.random() - 0.5) * range));
}

function initRacks(): ServerRack[] {
  return RACK_DEFS.map(d => ({
    ...d,
    cpu:     d.status === 'critical' ? 0  : d.status === 'warning' ? 88 : Math.floor(Math.random() * 45) + 20,
    memory:  d.status === 'critical' ? 0  : d.status === 'warning' ? 78 : Math.floor(Math.random() * 40) + 30,
    temp:    d.status === 'critical' ? 0  : d.status === 'warning' ? 72 : Math.floor(Math.random() * 15) + 38,
    network: d.status === 'critical' ? 0  : parseFloat((Math.random() * 8 + 1).toFixed(1)),
  }));
}

/* ═══════════════════════════════════════════════════════════
   Status helpers
═══════════════════════════════════════════════════════════ */
function statusColor(s: RackStatus): string {
  return s === 'normal' ? '#22d3ee' : s === 'warning' ? '#f59e0b' : s === 'critical' ? '#ef4444' : '#475569';
}

/* ═══════════════════════════════════════════════════════════
   Scene layout constants
═══════════════════════════════════════════════════════════ */
const SPACING_X = 1.75;   // col spacing
const SPACING_Z = 2.30;   // row spacing
const OFFSET_X  = -4.375; // center 6 cols: -(5/2)*1.75
const OFFSET_Z  = -2.30;  // center 3 rows

function rackWorldPos(row: number, col: number): [number, number, number] {
  return [col * SPACING_X + OFFSET_X, 0, row * SPACING_Z + OFFSET_Z];
}

// Room bounding box (slightly larger than rack area)
const ROOM_W = 12.0;
const ROOM_D =  8.5;
const ROOM_H =  3.8;
const RX = 0, RZ = 0; // room center

/* ═══════════════════════════════════════════════════════════
   Three.js — Pulsing alert light
═══════════════════════════════════════════════════════════ */
function PulseLight({ color, baseIntensity }: { color: string; baseIntensity: number }) {
  const ref = useRef<THREE.PointLight>(null);
  useFrame(({ clock }) => {
    if (ref.current) {
      ref.current.intensity = baseIntensity * (0.6 + 0.4 * Math.sin(clock.getElapsedTime() * 2.5));
    }
  });
  return <pointLight ref={ref} color={color} intensity={baseIntensity} distance={3.5} decay={2} />;
}

/* ═══════════════════════════════════════════════════════════
   Three.js — Server Rack Mesh
═══════════════════════════════════════════════════════════ */
function ServerRackMesh({ rack, isSelected, onSelect }: {
  rack: ServerRack;
  isSelected: boolean;
  onSelect: () => void;
}) {
  const isUps    = rack.id.startsWith('UPS');
  const rackW    = isUps ? 1.05 : 0.82;
  const rackH    = isUps ? 1.40 : 2.05;
  const rackD    = isUps ? 0.75 : 0.58;
  const [px, , pz] = rackWorldPos(rack.row, rack.col);
  const col      = statusColor(rack.status);
  const emissive = new THREE.Color(col);

  const baseEmissive = rack.status === 'critical' ? 0.35
                     : rack.status === 'warning'  ? 0.25
                     : 0.08;
  const emissiveIntensity = isSelected ? 0.55 : baseEmissive;

  // Server unit divider lines on front face (decorative)
  const unitCount = isUps ? 3 : 8;
  const slotH = rackH / unitCount;

  return (
    <group
      position={[px, rackH / 2, pz]}
      onClick={(e) => { e.stopPropagation(); onSelect(); }}
      onPointerOver={() => { document.body.style.cursor = 'pointer'; }}
      onPointerOut={() =>  { document.body.style.cursor = 'auto'; }}
    >
      {/* Main body */}
      <mesh castShadow receiveShadow>
        <boxGeometry args={[rackW, rackH, rackD]} />
        <meshStandardMaterial
          color="#0a1828"
          emissive={emissive}
          emissiveIntensity={emissiveIntensity}
          roughness={0.55}
          metalness={0.80}
        />
        {/* Edge wireframe */}
        <Edges
          color={col}
          linewidth={isSelected ? 2.0 : 1.0}
          threshold={15}
        />
      </mesh>

      {/* Front-face server unit slots (thin quads, decorative) */}
      {Array.from({ length: unitCount - 1 }).map((_, i) => {
        const y = -rackH / 2 + (i + 1) * slotH;
        return (
          <mesh key={i} position={[0, y, rackD / 2 + 0.001]}>
            <planeGeometry args={[rackW * 0.95, 0.01]} />
            <meshBasicMaterial color={col} transparent opacity={0.25} />
          </mesh>
        );
      })}

      {/* Status LED dots on front face */}
      {Array.from({ length: Math.min(unitCount, 4) }).map((_, i) => {
        const y = -rackH / 2 + (i + 0.5) * slotH;
        const ledCol = rack.status === 'critical' ? '#ef4444'
                     : i === 0 && rack.status === 'warning' ? '#f59e0b'
                     : '#22d3ee';
        return (
          <mesh key={i} position={[rackW * 0.35, y, rackD / 2 + 0.003]}>
            <circleGeometry args={[0.025, 8]} />
            <meshBasicMaterial color={ledCol} />
          </mesh>
        );
      })}

      {/* Pulsing point light for alerting racks */}
      {rack.status === 'critical' && (
        <group position={[0, rackH / 2 + 0.3, 0]}>
          <PulseLight color="#ef4444" baseIntensity={1.8} />
        </group>
      )}
      {rack.status === 'warning' && (
        <group position={[0, rackH / 2 + 0.3, 0]}>
          <PulseLight color="#f59e0b" baseIntensity={0.9} />
        </group>
      )}

      {/* Selection halo ring */}
      {isSelected && (
        <mesh position={[0, -rackH / 2 + 0.01, 0]} rotation={[-Math.PI / 2, 0, 0]}>
          <ringGeometry args={[Math.max(rackW, rackD) * 0.65, Math.max(rackW, rackD) * 0.85, 32]} />
          <meshBasicMaterial color={col} transparent opacity={0.6} side={THREE.DoubleSide} />
        </mesh>
      )}

      {/* Floating label */}
      <Html
        position={[0, rackH / 2 + 0.55, 0]}
        center
        distanceFactor={12}
        style={{ pointerEvents: 'none' }}
      >
        <div style={{
          background: 'rgba(4,12,28,0.88)',
          border: `1px solid ${col}`,
          borderRadius: 3,
          padding: '1px 7px',
          color: isSelected ? '#f8fafc' : '#94a3b8',
          fontSize: 10,
          fontWeight: isSelected || rack.status !== 'normal' ? 700 : 400,
          whiteSpace: 'nowrap',
          fontFamily: "'Consolas','monospace'",
          boxShadow: `0 0 8px ${col}60`,
          userSelect: 'none',
        }}>
          {rack.label}
        </div>
      </Html>
    </group>
  );
}

/* ═══════════════════════════════════════════════════════════
   Three.js — Room floor + grid + boundary walls
═══════════════════════════════════════════════════════════ */
function RoomEnvironment() {
  const halfW = ROOM_W / 2, halfD = ROOM_D / 2;

  // Boundary corner points (bottom)
  const floorCorners: [number, number, number][] = [
    [RX - halfW, 0, RZ - halfD],
    [RX + halfW, 0, RZ - halfD],
    [RX + halfW, 0, RZ + halfD],
    [RX - halfW, 0, RZ + halfD],
    [RX - halfW, 0, RZ - halfD],
  ];
  // Top frame
  const topCorners = floorCorners.map(([x, , z]) => [x, ROOM_H, z] as [number, number, number]);
  // Vertical pillars at 4 corners
  const cornerPillars = [
    [RX - halfW, RZ - halfD],
    [RX + halfW, RZ - halfD],
    [RX + halfW, RZ + halfD],
    [RX - halfW, RZ + halfD],
  ];

  return (
    <group>
      {/* Floor plane */}
      <mesh rotation={[-Math.PI / 2, 0, 0]} position={[RX, -0.01, RZ]} receiveShadow>
        <planeGeometry args={[ROOM_W, ROOM_D]} />
        <meshStandardMaterial color="#020c1a" roughness={1} metalness={0} />
      </mesh>

      {/* Floor grid (cyan tile lines) */}
      <Grid
        position={[RX, 0.002, RZ]}
        cellSize={SPACING_X}
        cellThickness={0.4}
        cellColor="#0e4d6e"
        sectionSize={0}
        fadeDistance={28}
        fadeStrength={1.2}
        args={[ROOM_W, ROOM_D]}
      />

      {/* Floor boundary (bottom frame) */}
      <Line points={floorCorners} color="#22d3ee" lineWidth={1.2} transparent opacity={0.55} />

      {/* Top frame */}
      <Line points={topCorners} color="#22d3ee" lineWidth={0.8} transparent opacity={0.30} />

      {/* Corner pillars */}
      {cornerPillars.map(([x, z], i) => (
        <Line key={i}
          points={[[x, 0, z], [x, ROOM_H, z]]}
          color="#22d3ee" lineWidth={1.0} transparent opacity={0.45}
        />
      ))}

      {/* Back wall (semi-transparent) */}
      <mesh position={[RX, ROOM_H / 2, RZ - halfD]} receiveShadow>
        <planeGeometry args={[ROOM_W, ROOM_H]} />
        <meshStandardMaterial color="#0a1e36" transparent opacity={0.18} side={THREE.FrontSide} />
      </mesh>

      {/* Left wall (semi-transparent) */}
      <mesh position={[RX - halfW, ROOM_H / 2, RZ]} rotation={[0, Math.PI / 2, 0]}>
        <planeGeometry args={[ROOM_D, ROOM_H]} />
        <meshStandardMaterial color="#0a1e36" transparent opacity={0.14} side={THREE.FrontSide} />
      </mesh>
    </group>
  );
}

/* ═══════════════════════════════════════════════════════════
   Three.js — Full scene content
═══════════════════════════════════════════════════════════ */
function SceneContent({ racks, selected, onSelect }: {
  racks: ServerRack[];
  selected: string | null;
  onSelect: (id: string) => void;
}) {
  return (
    <>
      {/* Sky / scene background */}
      <color attach="background" args={['#020810']} />
      <fog attach="fog" args={['#020810', 18, 40]} />

      {/* Lighting */}
      <ambientLight intensity={0.20} color="#0e2d45" />
      <directionalLight
        position={[6, 12, 8]}
        intensity={0.90}
        color="#2a9fd6"
        castShadow
        shadow-mapSize-width={1024}
        shadow-mapSize-height={1024}
        shadow-camera-far={30}
        shadow-camera-left={-10}
        shadow-camera-right={10}
        shadow-camera-top={10}
        shadow-camera-bottom={-10}
      />
      <pointLight position={[-6, 8, -5]} intensity={0.4} color="#1a4d6e" />
      <pointLight position={[6,  5,  5]} intensity={0.3} color="#0e3a5c" />

      {/* Room */}
      <RoomEnvironment />

      {/* Server Racks */}
      {racks.map(rack => (
        <ServerRackMesh
          key={rack.id}
          rack={rack}
          isSelected={selected === rack.id}
          onSelect={() => onSelect(rack.id)}
        />
      ))}

      {/* Camera controls */}
      <OrbitControls
        target={[RX, 1.0, RZ]}
        minDistance={4}
        maxDistance={26}
        maxPolarAngle={Math.PI / 2.05}
        enablePan={false}
        enableDamping
        dampingFactor={0.06}
      />
    </>
  );
}

/* ═══════════════════════════════════════════════════════════
   2D Panel sub-components (unchanged from original)
═══════════════════════════════════════════════════════════ */
function GaugeRing({ value, max = 100, color, size = 48, label }: {
  value: number; max?: number; color: string; size?: number; label: string;
}) {
  const r    = size / 2 - 5;
  const circ = 2 * Math.PI * r;
  const pct  = Math.min(value / max, 1);
  return (
    <div className="flex flex-col items-center gap-0.5">
      <div style={{ position: 'relative', width: size, height: size }}>
        <svg width={size} height={size} style={{ transform: 'rotate(-90deg)' }}>
          <circle cx={size/2} cy={size/2} r={r} fill="none" stroke="#1e293b" strokeWidth="4" />
          <circle cx={size/2} cy={size/2} r={r} fill="none"
            stroke={color} strokeWidth="4"
            strokeDasharray={`${pct * circ} ${circ}`}
            strokeLinecap="round"
            style={{ filter: `drop-shadow(0 0 4px ${color}60)` }}
          />
        </svg>
        <span style={{
          position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center',
          fontSize: 11, fontWeight: 700, color,
        }}>
          {value.toFixed(0)}%
        </span>
      </div>
      <span style={{ fontSize: 9, color: '#64748b' }}>{label}</span>
    </div>
  );
}

function BarRow({ label, value, max = 100, color, unit = '%' }: {
  label: string; value: number; max?: number; color: string; unit?: string;
}) {
  const pct = Math.min((value / max) * 100, 100);
  return (
    <div style={{ marginBottom: 6 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 2 }}>
        <span style={{ fontSize: 10, color: '#64748b' }}>{label}</span>
        <span style={{ fontSize: 10, fontWeight: 600, color }}>{value.toFixed(unit === 'Gbps' ? 1 : 0)}{unit}</span>
      </div>
      <div style={{ height: 4, background: '#1e293b', borderRadius: 9999 }}>
        <div style={{ height: '100%', width: `${pct}%`, background: color, borderRadius: 9999,
          boxShadow: `0 0 6px ${color}60`, transition: 'width 0.7s ease' }} />
      </div>
    </div>
  );
}

function RankItem({ rank, label, value, color }: { rank: number; label: string; value: number; color: string }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
      <span style={{ width: 18, fontSize: 9, color: '#64748b', textAlign: 'right', flexShrink: 0 }}>
        {String(rank).padStart(2, '0')}
      </span>
      <div style={{ flex: 1 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 2 }}>
          <span style={{ fontSize: 10, color: '#94a3b8' }}>{label}</span>
          <span style={{ fontSize: 10, fontWeight: 600, color }}>{value.toFixed(0)}%</span>
        </div>
        <div style={{ height: 3, background: '#1e293b', borderRadius: 9999 }}>
          <div style={{ height: '100%', width: `${value}%`, background: color, borderRadius: 9999 }} />
        </div>
      </div>
    </div>
  );
}

/* ═══════════════════════════════════════════════════════════
   Main Page
═══════════════════════════════════════════════════════════ */
export default function NwDigitalTwinDemoPage() {
  const { t } = useTranslation('nw');

  const statusLabel = (s: RackStatus) =>
    s === 'normal'   ? t('status.normal')  :
    s === 'warning'  ? t('status.warning') :
    s === 'critical' ? t('status.critical') :
                       t('status.offline');

  const [racks,    setRacks   ] = useState<ServerRack[]>(initRacks);
  const [selected, setSelected] = useState<string | null>('SVR-B');
  const [now,      setNow     ] = useState(new Date());
  const [tab,      setTab     ] = useState<'overview' | 'security' | 'device' | 'energy'>('device');

  /* Live clock */
  useEffect(() => {
    const id = setInterval(() => setNow(new Date()), 1000);
    return () => clearInterval(id);
  }, []);

  /* Real-time simulation */
  useEffect(() => {
    const id = setInterval(() => {
      setRacks(prev => prev.map(r => ({
        ...r,
        cpu:     r.status === 'critical' ? 0 : fluctuate(r.cpu,     4, 0, 100),
        memory:  r.status === 'critical' ? 0 : fluctuate(r.memory,  2, 0, 100),
        temp:    r.status === 'critical' ? 0 : fluctuate(r.temp,    1, 20, 85),
        network: r.status === 'critical' ? 0 : fluctuate(r.network, 0.5, 0, 10),
      })));
    }, 2500);
    return () => clearInterval(id);
  }, []);

  const handleSelect = useCallback((id: string) => {
    setSelected(prev => prev === id ? null : id);
  }, []);

  /* Derived stats */
  const selectedRack = selected ? racks.find(r => r.id === selected) ?? null : null;
  const normalCnt    = racks.filter(r => r.status === 'normal').length;
  const warnCnt      = racks.filter(r => r.status === 'warning').length;
  const critCnt      = racks.filter(r => r.status === 'critical').length;
  const offlineCnt   = racks.filter(r => r.status === 'offline').length;
  const cpuRanking   = [...racks].filter(r => r.status !== 'critical').sort((a, b) => b.cpu - a.cpu).slice(0, 5);

  const timeStr = now.toLocaleString('ja-JP', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit',
  });

  const tabs: { key: typeof tab; label: string }[] = [
    { key: 'overview', label: t('digitalTwin.tab.overview') },
    { key: 'security', label: t('digitalTwin.tab.security') },
    { key: 'device',   label: t('digitalTwin.tab.device') },
    { key: 'energy',   label: t('digitalTwin.tab.energy') },
  ];

  /* ── Style tokens ── */
  const panelBg     = 'rgba(4, 15, 32, 0.88)';
  const panelBorder = '#0e4d6e';
  const textPrim    = '#e2e8f0';
  const textMuted   = '#64748b';
  const cyan        = '#22d3ee';
  const amber       = '#f59e0b';
  const red         = '#ef4444';
  const green       = '#22c55e';
  const panelStyle: React.CSSProperties = {
    background: panelBg, border: `1px solid ${panelBorder}`, borderRadius: 8, backdropFilter: 'blur(8px)',
  };

  return (
    <div style={{
      position: 'relative', width: '100%', height: 'calc(100vh - 3.5rem)',
      background: '#020810', overflow: 'hidden',
      fontFamily: "'JetBrains Mono','Consolas',monospace", color: textPrim,
      display: 'flex', flexDirection: 'column',
    }}>

      {/* ══ Header ══ */}
      <div style={{
        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        padding: '8px 16px', borderBottom: `1px solid ${panelBorder}`,
        background: 'rgba(4,15,32,0.95)', flexShrink: 0, zIndex: 10,
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <Server size={16} color={cyan} />
          <span style={{ fontSize: 13, fontWeight: 700, letterSpacing: 2, color: textPrim }}>
            {t('digitalTwin.title')}
          </span>
          <span style={{
            display: 'flex', alignItems: 'center', gap: 4,
            fontSize: 10, color: cyan, border: `1px solid ${cyan}40`,
            borderRadius: 9999, padding: '2px 8px', background: `${cyan}10`,
          }}>
            <span style={{ width: 6, height: 6, borderRadius: '50%', background: cyan, animation: 'pulse 1.5s infinite' }} />
            {t('live')}
          </span>
        </div>
        <div style={{ display: 'flex', gap: 20, alignItems: 'center' }}>
          {[
            { label: t('status.normal'),  val: normalCnt,  color: green },
            { label: t('status.warning'), val: warnCnt,    color: amber },
            { label: t('status.critical'),val: critCnt,    color: red },
            { label: t('status.offline'), val: offlineCnt, color: '#475569' },
          ].map(s => (
            <span key={s.label} style={{ display: 'flex', gap: 4, fontSize: 11, color: s.color }}>
              <span style={{ fontWeight: 700 }}>{s.val}</span>
              <span style={{ color: textMuted }}>{s.label}</span>
            </span>
          ))}
          <span style={{ fontSize: 11, color: textMuted, display: 'flex', alignItems: 'center', gap: 4 }}>
            <Clock size={11} color={textMuted} /> {timeStr}
          </span>
        </div>
      </div>

      {/* ══ Body ══ */}
      <div style={{ flex: 1, display: 'flex', minHeight: 0, overflow: 'hidden' }}>

        {/* ── Left panel ── */}
        <div style={{
          width: 220, flexShrink: 0, padding: '10px 10px 10px 12px',
          display: 'flex', flexDirection: 'column', gap: 8, overflowY: 'auto', zIndex: 5,
        }}>
          {/* Abnormal monitoring */}
          <div style={{ ...panelStyle, padding: '10px 12px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 8 }}>
              <AlertTriangle size={11} color={amber} />
              <span style={{ fontSize: 10, fontWeight: 600, color: textPrim }}>{t('digitalTwin.abnormal.panelTitle')}</span>
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
              {[
                { icon: <XCircle size={14} color={red} />,           label: t('digitalTwin.abnormal.lv1'),     val: critCnt },
                { icon: <AlertTriangle size={14} color={amber} />,   label: t('digitalTwin.abnormal.lv2'),     val: warnCnt },
                { icon: <Activity size={14} color={cyan} />,         label: t('digitalTwin.abnormal.lv3'),     val: 0 },
                { icon: <Server size={14} color="#475569" />,        label: t('digitalTwin.abnormal.offline'), val: offlineCnt },
              ].map(item => (
                <div key={item.label} style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 2 }}>
                  {item.icon}
                  <span style={{ fontSize: 16, fontWeight: 700, color: item.val > 0 ? red : textPrim }}>{item.val}</span>
                  <span style={{ fontSize: 8, color: textMuted, textAlign: 'center' }}>{item.label}</span>
                </div>
              ))}
            </div>
          </div>

          {/* Overall utilization */}
          <div style={{ ...panelStyle, padding: '10px 12px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 8 }}>
              <Cpu size={11} color={cyan} />
              <span style={{ fontSize: 10, fontWeight: 600, color: textPrim }}>{t('digitalTwin.overall.panelTitle')}</span>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-around' }}>
              <GaugeRing
                value={Math.round(racks.filter(r => r.status !== 'critical').reduce((a, b) => a + b.cpu, 0) / Math.max(1, racks.filter(r => r.status !== 'critical').length))}
                color={cyan} size={52} label={t('digitalTwin.overall.cpuAvg')}
              />
              <GaugeRing
                value={Math.round(racks.filter(r => r.status !== 'critical').reduce((a, b) => a + b.memory, 0) / Math.max(1, racks.filter(r => r.status !== 'critical').length))}
                color={amber} size={52} label={t('digitalTwin.overall.memAvg')}
              />
            </div>
          </div>

          {/* Device status table */}
          <div style={{ ...panelStyle, padding: '10px 12px', flex: 1 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 8 }}>
              <Activity size={11} color={cyan} />
              <span style={{ fontSize: 10, fontWeight: 600, color: textPrim }}>{t('digitalTwin.deviceList.panelTitle')}</span>
            </div>
            <div style={{ overflowY: 'auto' }}>
              <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 9 }}>
                <thead>
                  <tr style={{ color: textMuted }}>
                    <th style={{ textAlign: 'left',  paddingBottom: 4, fontWeight: 500 }}>{t('digitalTwin.deviceList.name')}</th>
                    <th style={{ textAlign: 'right', paddingBottom: 4, fontWeight: 500 }}>{t('digitalTwin.deviceList.status')}</th>
                    <th style={{ textAlign: 'right', paddingBottom: 4, fontWeight: 500 }}>{t('digitalTwin.deviceList.cpu')}</th>
                    <th style={{ textAlign: 'right', paddingBottom: 4, fontWeight: 500 }}>{t('digitalTwin.deviceList.uptime')}</th>
                  </tr>
                </thead>
                <tbody>
                  {racks.map(r => (
                    <tr key={r.id}
                      style={{ cursor: 'pointer', background: selected === r.id ? `${cyan}10` : 'transparent' }}
                      onClick={() => handleSelect(r.id)}
                    >
                      <td style={{ padding: '3px 0', color: selected === r.id ? cyan : textPrim }}>{r.label}</td>
                      <td style={{ textAlign: 'right', color: statusColor(r.status) }}>{statusLabel(r.status)}</td>
                      <td style={{ textAlign: 'right', color: r.cpu > 85 ? red : textPrim }}>
                        {r.status === 'critical' ? '-' : `${r.cpu.toFixed(0)}%`}
                      </td>
                      <td style={{ textAlign: 'right', color: textMuted }}>{r.uptime}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </div>

        {/* ── Center: Three.js Canvas ── */}
        <div style={{ flex: 1, position: 'relative', minWidth: 0 }}>

          {/* Scan-line overlay */}
          <div style={{
            position: 'absolute', inset: 0, pointerEvents: 'none', zIndex: 2,
            backgroundImage: 'repeating-linear-gradient(0deg,transparent,transparent 2px,rgba(0,200,255,0.012) 2px,rgba(0,200,255,0.012) 4px)',
          }} />

          {/* Corner HUD decorations */}
          {([
            { top: 8,     left:  8,    rotateDeg: 0   },
            { top: 8,     right: 8,    rotateDeg: 90  },
            { bottom: 48, right: 8,    rotateDeg: 180 },
            { bottom: 48, left:  8,    rotateDeg: 270 },
          ] as { top?: number; bottom?: number; left?: number; right?: number; rotateDeg: number }[]).map((pos, i) => {
            const { rotateDeg, ...rest } = pos;
            return (
              <svg key={i} width="20" height="20"
                style={{ position: 'absolute', ...rest, opacity: 0.5, zIndex: 3 } as React.CSSProperties}
                viewBox="0 0 20 20">
                <path d="M0,0 L0,8 M0,0 L8,0" stroke={cyan} strokeWidth="1.5" fill="none"
                  transform={`rotate(${rotateDeg} 10 10)`} />
              </svg>
            );
          })}

          {/* Title */}
          <div style={{
            position: 'absolute', top: 10, left: '50%', transform: 'translateX(-50%)',
            textAlign: 'center', zIndex: 3, pointerEvents: 'none',
          }}>
            <div style={{ fontSize: 11, fontWeight: 700, color: textPrim, letterSpacing: 3 }}>
              {t('digitalTwin.layoutTitle')}
            </div>
            <div style={{ width: 60, height: 1, background: `linear-gradient(90deg,transparent,${amber},transparent)`, margin: '4px auto 0' }} />
          </div>

          {/* Three.js Canvas */}
          <Canvas
            camera={{ position: [8, 11, 13], fov: 42 }}
            shadows
            gl={{ antialias: true, alpha: false }}
            style={{ width: '100%', height: '100%' }}
          >
            <Suspense fallback={null}>
              <SceneContent racks={racks} selected={selected} onSelect={handleSelect} />
            </Suspense>
          </Canvas>

          {/* Tab bar */}
          <div style={{
            position: 'absolute', bottom: 0, left: '50%', transform: 'translateX(-50%)',
            display: 'flex', gap: 2, zIndex: 5,
          }}>
            {tabs.map(tb => (
              <button key={tb.key} onClick={() => setTab(tb.key)} style={{
                padding: '6px 20px', fontSize: 11, cursor: 'pointer',
                background: tab === tb.key ? `${amber}20` : 'rgba(4,15,32,0.85)',
                border: `1px solid ${tab === tb.key ? amber : panelBorder}`,
                color: tab === tb.key ? amber : textMuted,
                borderRadius: '4px 4px 0 0', fontWeight: tab === tb.key ? 700 : 400,
                fontFamily: 'inherit', transition: 'all 0.2s',
              }}>
                {tb.label}
              </button>
            ))}
          </div>
        </div>

        {/* ── Right panel ── */}
        <div style={{
          width: 220, flexShrink: 0, padding: '10px 12px 10px 10px',
          display: 'flex', flexDirection: 'column', gap: 8, overflowY: 'auto', zIndex: 5,
        }}>
          {/* Selected device detail */}
          {selectedRack ? (
            <div style={{ ...panelStyle, padding: '10px 12px' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
                <span style={{ fontSize: 11, fontWeight: 700, color: textPrim }}>{selectedRack.label}</span>
                <span style={{
                  fontSize: 9, padding: '1px 6px', borderRadius: 9999,
                  color: statusColor(selectedRack.status),
                  border: `1px solid ${statusColor(selectedRack.status)}60`,
                  background: `${statusColor(selectedRack.status)}15`,
                }}>
                  {statusLabel(selectedRack.status)}
                </span>
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 4, marginBottom: 10, fontSize: 9 }}>
                {[
                  { k: t('digitalTwin.detail.deviceName'), v: selectedRack.label },
                  { k: t('digitalTwin.detail.year'),       v: `${selectedRack.year}` },
                  { k: t('digitalTwin.detail.responsible'),v: selectedRack.responsible },
                  { k: t('digitalTwin.detail.uptime'),     v: selectedRack.uptime },
                  { k: t('digitalTwin.detail.load'),       v: selectedRack.load },
                  { k: 'Network',                          v: `${selectedRack.network.toFixed(1)} GB` },
                ].map(row => (
                  <React.Fragment key={row.k}>
                    <span style={{ color: textMuted }}>{row.k}</span>
                    <span style={{ color: textPrim, textAlign: 'right' }}>{row.v}</span>
                  </React.Fragment>
                ))}
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-around', marginBottom: 8 }}>
                <GaugeRing value={selectedRack.memory} color={cyan}  size={48} label="Memory" />
                <GaugeRing value={selectedRack.cpu}    color={amber} size={48} label="CPU" />
              </div>
              <BarRow label="CPU"     value={selectedRack.cpu}     color={selectedRack.cpu > 85 ? red : amber} />
              <BarRow label="Memory"  value={selectedRack.memory}  color={cyan} />
              <BarRow label="Temp"    value={selectedRack.temp}    max={85} color={selectedRack.temp > 70 ? red : '#f97316'} unit="°C" />
              <BarRow label="Network" value={selectedRack.network} max={10} color={green} unit="Gbps" />
            </div>
          ) : (
            <div style={{
              ...panelStyle, padding: '12px',
              display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center',
              gap: 6, height: 100,
            }}>
              <Server size={20} color={textMuted} />
              <span style={{ fontSize: 10, color: textMuted }}>{t('digitalTwin.selectDevice')}</span>
            </div>
          )}

          {/* CPU ranking */}
          <div style={{ ...panelStyle, padding: '10px 12px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 10 }}>
              <Zap size={11} color={amber} />
              <span style={{ fontSize: 10, fontWeight: 600, color: textPrim }}>{t('digitalTwin.ranking.cpu')}</span>
            </div>
            {cpuRanking.map((r, i) => (
              <RankItem key={r.id} rank={i + 1} label={r.label}
                value={r.cpu} color={r.cpu > 85 ? red : r.cpu > 70 ? amber : cyan} />
            ))}
          </div>

          {/* Temperature ranking */}
          <div style={{ ...panelStyle, padding: '10px 12px', flex: 1 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 10 }}>
              <Thermometer size={11} color="#f97316" />
              <span style={{ fontSize: 10, fontWeight: 600, color: textPrim }}>{t('digitalTwin.ranking.temp')}</span>
            </div>
            {[...racks]
              .filter(r => r.status !== 'critical')
              .sort((a, b) => b.temp - a.temp)
              .slice(0, 5)
              .map((r, i) => (
                <RankItem key={r.id} rank={i + 1} label={r.label}
                  value={Math.round((r.temp / 85) * 100)}
                  color={r.temp > 70 ? red : r.temp > 55 ? amber : green} />
              ))}
          </div>
        </div>
      </div>

      <style>{`
        @keyframes pulse { 0%,100%{opacity:1} 50%{opacity:0.3} }
      `}</style>
    </div>
  );
}
