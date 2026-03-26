import { useRef, useState, useEffect } from 'react';
import { Canvas, useFrame, useThree, ThreeEvent } from '@react-three/fiber';
import { OrbitControls, Html, Edges, Line, Grid, MeshReflectorMaterial } from '@react-three/drei';
import { EffectComposer, Bloom } from '@react-three/postprocessing';
import * as THREE from 'three';
import { ArrowLeft, ChevronRight } from 'lucide-react';
import { AlarmLevel, ALARM_COLOR, ALARM_LABEL, Center, FloorData } from './types';

// ── 색상 ─────────────────────────────────────────────────
const C_BG          = '#020c1e';
const C_WIRE_BRIGHT = '#4fc3f7';
const C_WIRE_MID    = '#1565c0';

// ── 배경 텍스처 모듈 레벨 프리로드 ───────────────────────
// 컴포넌트 마운트 전에 미리 로드 시작 → Canvas 뜰 때 바로 적용
const BG_IMAGE = '/images/server-room-bg6.jpg';
let _bgTexCache: THREE.Texture | null = null;
new THREE.TextureLoader().load(BG_IMAGE, (tex) => {
  tex.colorSpace = THREE.SRGBColorSpace;
  _bgTexCache = tex;
});

// ── 디바이스 상태 ─────────────────────────────────────────
type DeviceStatus = 'normal' | 'minor' | 'warning' | 'critical';

interface Device {
  id:     string;        // MME#001 / SGW#001
  type:   'MME' | 'SGW';
  slot:   number;        // 0-3
  status: DeviceStatus;
}

interface Rack {
  id:      string;       // RACK#1801
  row:     number;
  col:     number;
  devices: Device[];
}

function deviceStatusColor(s: DeviceStatus): string {
  if (s === 'critical') return ALARM_COLOR['CR'];
  if (s === 'warning')  return ALARM_COLOR['MJ'];
  if (s === 'minor')    return ALARM_COLOR['MN'];
  return ALARM_COLOR['NR'];
}

function deviceStatusToAlarmLevel(s: DeviceStatus): AlarmLevel {
  if (s === 'critical') return 'CR';
  if (s === 'warning')  return 'MJ';
  if (s === 'minor')    return 'MN';
  return 'NR';
}

// ── 층 알람에 따른 디바이스 상태 결정 ─────────────────────
// 층 레벨 = 해당 층 장비 최고 등급. CR층은 CR/MJ/MN 혼재, MJ층은 MJ/MN 혼재.
function assignDeviceStatus(floorLevel: AlarmLevel, rackIdx: number, slotIdx: number): DeviceStatus {
  if (floorLevel === 'NR') return 'normal';
  const seed = rackIdx * 4 + slotIdx;
  if (floorLevel === 'CR') {
    if (seed % 20 === 3) return 'critical';  // CR: 소수 (~1-2건)
    if (seed % 8  === 1) return 'warning';   // MJ: 중간 (~3-4건)
    if (seed % 5  === 0) return 'minor';     // MN: 좀 더 많이 (~5-6건)
  }
  if (floorLevel === 'MJ') {
    if (seed % 11 === 2) return 'warning';   // MJ: 중간
    if (seed % 7  === 1) return 'minor';     // MN: 좀 더 많이
  }
  if (floorLevel === 'MN') {
    if (seed % 8  === 3) return 'minor';     // MN: 소수
  }
  return 'normal';
}

// ── 랙/디바이스 데이터 생성 ───────────────────────────────
const DEVICES_PER_RACK = 4;

function generateRacks(center: Center, floor: FloorData): { racks: Rack[]; cols: number } {
  const nocPrefix  = center.name.includes('品川') ? 1 : 2;
  const floorNum   = parseInt(floor.id);
  const isMME      = floorNum === 8;
  const devType    = (isMME ? 'MME' : 'SGW') as 'MME' | 'SGW';
  const totalDevs  = isMME ? 30 : 60;
  const totalRacks = Math.ceil(totalDevs / DEVICES_PER_RACK);
  const cols       = isMME ? 4 : 5;

  const racks: Rack[] = [];
  for (let i = 0; i < totalRacks; i++) {
    const row    = Math.floor(i / cols);
    const col    = i % cols;
    const seq    = String(i + 1).padStart(2, '0');
    const rackId = `RACK#${nocPrefix}${floorNum}${seq}`;

    const devices: Device[] = [];
    for (let s = 0; s < DEVICES_PER_RACK; s++) {
      const devNum = i * DEVICES_PER_RACK + s + 1;
      if (devNum > totalDevs) break;
      devices.push({
        id:     `${devType}#${String(devNum).padStart(3, '0')}`,
        type:   devType,
        slot:   s,
        status: assignDeviceStatus(floor.level, i, s),
      });
    }
    racks.push({ id: rackId, row, col, devices });
  }
  return { racks, cols };
}

// ── 3D 레이아웃 상수 ──────────────────────────────────────
const RACK_W        = 0.80;
const RACK_H        = 2.20;
const RACK_D        = 1.45;
const DEV_H         = 0.28;
const DEV_GAP       = 0.008;
const RACK_SPACING_X = 2.0;
const RACK_SPACING_Z = 2.6;

function rackPos(row: number, col: number, cols: number, rows: number): [number, number, number] {
  const ox = -((cols - 1) / 2) * RACK_SPACING_X;
  const oz = -((rows - 1) / 2) * RACK_SPACING_Z;
  return [col * RACK_SPACING_X + ox, 0, row * RACK_SPACING_Z + oz];
}

// ── 깜빡이는 경고등 ───────────────────────────────────────
function PulseLight({ color, intensity }: { color: string; intensity: number }) {
  const ref = useRef<THREE.PointLight>(null!);
  useFrame(({ clock }) => {
    if (ref.current)
      ref.current.intensity = intensity * (0.5 + 0.5 * Math.sin(clock.elapsedTime * 2.5));
  });
  return <pointLight ref={ref} color={color} intensity={intensity} distance={3} decay={2} />;
}

// ── 랙 3D — 투명 와이어프레임 (서버가 주인공) ───────────────
function RackFrame({ rack, totalCols, totalRows }: {
  rack:      Rack;
  totalCols: number;
  totalRows: number;
}) {
  const [px, , pz] = rackPos(rack.row, rack.col, totalCols, totalRows);

  return (
    <group position={[px, RACK_H / 2, pz]}>
      {/* 어두운 반투명 fill — 배경 대비 윤곽 강조 */}
      <mesh>
        <boxGeometry args={[RACK_W, RACK_H, RACK_D]} />
        <meshBasicMaterial color="#020810" transparent opacity={0.45} depthWrite={false} />
      </mesh>
      {/* 엣지 오버레이 */}
      <mesh>
        <boxGeometry args={[RACK_W, RACK_H, RACK_D]} />
        <meshBasicMaterial visible={false} />
        <Edges threshold={15} color="#0e3060" lineWidth={1.4} />
      </mesh>

      {rack.devices.some((d) => d.status === 'critical') && (
        <group position={[0, RACK_H / 2 + 0.3, 0]}>
          <PulseLight color={ALARM_COLOR['CR']} intensity={1.2} />
        </group>
      )}
      {rack.devices.some((d) => d.status === 'warning') &&
       !rack.devices.some((d) => d.status === 'critical') && (
        <group position={[0, RACK_H / 2 + 0.3, 0]}>
          <PulseLight color={ALARM_COLOR['MJ']} intensity={0.7} />
        </group>
      )}
      {rack.devices.some((d) => d.status === 'minor') &&
       !rack.devices.some((d) => d.status === 'warning') &&
       !rack.devices.some((d) => d.status === 'critical') && (
        <group position={[0, RACK_H / 2 + 0.3, 0]}>
          <PulseLight color={ALARM_COLOR['MN']} intensity={0.5} />
        </group>
      )}
    </group>
  );
}

// ── 알람 장비 펄스 fill ───────────────────────────────────
function DeviceAlarmPulse({ color }: { color: string }) {
  const ref = useRef<THREE.Mesh>(null!);
  useFrame(({ clock }) => {
    if (ref.current) {
      (ref.current.material as THREE.MeshBasicMaterial).opacity =
        0.10 + 0.12 * Math.sin(clock.elapsedTime * 2.5);
    }
  });
  return (
    <mesh ref={ref}>
      <boxGeometry args={[RACK_W * 0.86, DEV_H * 0.80, RACK_D * 0.68]} />
      <meshBasicMaterial color={color} transparent opacity={0.10} side={THREE.DoubleSide} />
    </mesh>
  );
}

// ── 디바이스 3D — 개별 선택 가능 ─────────────────────────
function DeviceSlot3D({ device, rackRow, rackCol, totalCols, totalRows, isSelected, onSelect, deviceTexture }: {
  device:        Device;
  rackRow:       number;
  rackCol:       number;
  totalCols:     number;
  totalRows:     number;
  isSelected:    boolean;
  onSelect:      () => void;
  deviceTexture: THREE.Texture | null;
}) {
  const [isHovered, setIsHovered] = useState(false);

  const [px, , pz] = rackPos(rackRow, rackCol, totalCols, totalRows);
  const slotY   = device.slot * (DEV_H + DEV_GAP) + DEV_GAP + DEV_H / 2 - RACK_H / 2;
  const isAlarm = device.status !== 'normal';
  const edgeCol = isAlarm ? deviceStatusColor(device.status) : C_WIRE_BRIGHT;
  const fillCol = isAlarm ? deviceStatusColor(device.status) : ALARM_COLOR['NR'];
  const level   = deviceStatusToAlarmLevel(device.status);

  // 호버 시 엣지: 알람은 흰색으로, NR은 밝은 시안으로
  const hoverEdgeCol = isAlarm ? '#ffffff' : '#80d8ff';

  // 패널 방향: 좌측 rack → 우측 상단, 우측 rack → 좌측 상단
  // (패널이 항상 화면 중앙 방향으로 확장 → 잘림 방지)
  const isRightHalf = rackCol >= totalCols / 2;
  const LINE_START: [number, number, number] = [isRightHalf ? -RACK_W * 0.44 : RACK_W * 0.44, DEV_H * 0.45, 0];
  const PANEL_POS:  [number, number, number] = [isRightHalf ? -1.4 : 1.4, 1.6, 0];
  // anchor: 우측 rack → 패널 우하단 기준(왼쪽 위로 확장), 좌측 rack → 패널 좌하단 기준(오른쪽 위로 확장)
  const panelTransform = isRightHalf ? 'translate(-100%, -100%)' : 'translateY(-100%)';

  const currentEdge  = isSelected ? fillCol : isHovered ? hoverEdgeCol : edgeCol;
  const currentWidth = isSelected || isHovered ? 1.4 : 0.6;

  return (
    <group
      position={[px, RACK_H / 2 + slotY, pz]}
      onClick={(e: ThreeEvent<MouseEvent>) => { e.stopPropagation(); onSelect(); }}
      onPointerOver={(e) => { e.stopPropagation(); setIsHovered(true); document.body.style.cursor = 'pointer'; }}
      onPointerOut={() =>  { setIsHovered(false); document.body.style.cursor = 'auto'; }}
    >
      {/* 알람 펄스 fill — 비선택 알람 장비 */}
      {isAlarm && !isSelected && (
        <DeviceAlarmPulse color={edgeCol} />
      )}

      {/* 선택 시 배경 fill */}
      {isSelected && (
        <mesh>
          <boxGeometry args={[RACK_W * 0.86, DEV_H * 0.80, RACK_D * 0.68]} />
          <meshBasicMaterial color={fillCol} transparent opacity={0.38} side={THREE.FrontSide} />
        </mesh>
      )}

      {/* 디바이스 — 텍스처 + 엣지 오버레이 */}
      <mesh>
        <boxGeometry args={[RACK_W * 0.86, DEV_H * 0.80, RACK_D * 0.68]} />
        {deviceTexture
          ? <meshStandardMaterial map={deviceTexture} roughness={0.70} metalness={0.20} />
          : <meshBasicMaterial color="#0a1828" />
        }
        <Edges threshold={15} color={currentEdge} lineWidth={currentWidth} />
      </mesh>

      {/* LED 도트 */}
      <mesh position={[RACK_W * 0.28, 0, RACK_D * 0.35]}>
        <circleGeometry args={[0.022, 8]} />
        <meshBasicMaterial color={currentEdge} />
      </mesh>

      {/* ── Hover: 플로팅 ID 라벨 ── */}
      {isHovered && !isSelected && (
        <Html position={[0, DEV_H * 0.65, RACK_D * 0.38]} center style={{ pointerEvents: 'none' }}>
          <div style={{
            fontFamily:   'monospace',
            fontSize:     '11px',
            fontWeight:   700,
            color:        hoverEdgeCol,
            background:   'rgba(2,12,30,0.90)',
            border:       `1px solid ${hoverEdgeCol}80`,
            borderRadius: '4px',
            padding:      '2px 8px',
            whiteSpace:   'nowrap',
            boxShadow:    `0 0 10px ${hoverEdgeCol}40`,
            letterSpacing: '0.5px',
          }}>
            {device.id}
          </div>
        </Html>
      )}

      {/* ── 선택: 글로우 도트 + 연결선 + 정보 패널 ── */}
      {isSelected && (
        <>
          {/* 연결 기점 글로우 도트 — 항상 앞에 보이도록 depthTest:false */}
          <mesh position={LINE_START} renderOrder={10}>
            <sphereGeometry args={[0.06, 10, 10]} />
            <meshBasicMaterial color={fillCol} depthTest={false} />
          </mesh>

          {/* 연결선 — depthTest:false로 다른 오브젝트에 가려지지 않게 */}
          <Line
            points={[LINE_START, PANEL_POS]}
            color={fillCol}
            lineWidth={0.9}
            transparent
            opacity={0.65}
            depthTest={false}
          />

          {/* 정보 패널 — center 제거, panelTransform으로 anchor 설정 */}
          <Html position={PANEL_POS} style={{ pointerEvents: 'none', transform: panelTransform }}>
            <div style={{
              background:   'rgba(2,10,28,0.94)',
              border:       `1px solid ${fillCol}60`,
              borderRadius: '6px',
              padding:      '10px 14px',
              minWidth:     '190px',
              boxShadow:    `0 0 20px ${fillCol}25, inset 0 0 20px rgba(0,0,0,0.5)`,
              userSelect:   'none',
            }}>
              {/* 헤더 */}
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '8px', borderBottom: `1px solid ${fillCol}30`, paddingBottom: '6px' }}>
                <span style={{ fontFamily: 'monospace', fontSize: '13px', fontWeight: 700, color: fillCol }}>
                  {device.id}
                </span>
                <span style={{
                  fontSize: '9px', fontWeight: 700,
                  padding: '1px 6px', borderRadius: '3px',
                  color: fillCol, background: `${fillCol}20`, border: `1px solid ${fillCol}40`,
                }}>
                  {ALARM_LABEL[level]}
                </span>
              </div>

              {/* 알람 정보 */}
              {isAlarm ? (
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '5px 14px' }}>
                  {([
                    { label: 'CALL TYPE',  value: 'ATTACH' },
                    { label: 'KPI',        value: '성공율'  },
                    { label: '상태',        value: '낮음'   },
                    { label: '값',          value: '40%'   },
                    { label: '연속발생',     value: '5회'   },
                  ] as { label: string; value: string }[]).map(({ label, value }) => (
                    <div key={label}>
                      <div style={{ fontSize: '8px', color: '#475569', marginBottom: '1px', fontFamily: 'monospace' }}>{label}</div>
                      <div style={{ fontSize: '10px', fontWeight: 600, color: fillCol, fontFamily: 'monospace' }}>{value}</div>
                    </div>
                  ))}
                </div>
              ) : (
                <div style={{ fontSize: '10px', color: ALARM_COLOR['NR'], fontFamily: 'monospace' }}>
                  ● 정상 운용 중
                </div>
              )}
            </div>
          </Html>
        </>
      )}
    </group>
  );
}

// ── 장비실 환경 ───────────────────────────────────────────
function RoomEnvironment({ roomW, roomD }: { roomW: number; roomD: number }) {
  const hw = roomW / 2, hd = roomD / 2;
  const ROOM_H = 4.0;
  const floor: [number,number,number][] = [[-hw,0,-hd],[hw,0,-hd],[hw,0,hd],[-hw,0,hd],[-hw,0,-hd]];
  const top:   [number,number,number][] = floor.map(([x,,z]) => [x, ROOM_H, z]);
  const pillars = [[-hw,-hd],[hw,-hd],[hw,hd],[-hw,hd]] as [number,number][];

  return (
    <group>
      {/* 반사 바닥 */}
      <mesh rotation={[-Math.PI / 2, 0, 0]} position={[0, -0.01, 0]}>
        <planeGeometry args={[roomW, roomD]} />
        <MeshReflectorMaterial
          blur={[400, 120]}
          resolution={512}
          mixBlur={1.2}
          mixStrength={55}
          roughness={0.85}
          depthScale={1.3}
          minDepthThreshold={0.3}
          maxDepthThreshold={1.5}
          color="#061828"
          metalness={0.6}
          mirror={0}
        />
      </mesh>
      <Grid
        position={[0, 0.002, 0]}
        cellSize={RACK_SPACING_X}
        cellThickness={0.4}
        cellColor="#0e4060"
        sectionSize={0}
        fadeDistance={35}
        fadeStrength={1.2}
        args={[roomW, roomD]}
      />
      <Line points={floor} color={C_WIRE_BRIGHT} lineWidth={1.2} transparent opacity={0.5} />
      <Line points={top}   color={C_WIRE_BRIGHT} lineWidth={0.8} transparent opacity={0.25} />
      {pillars.map(([x, z], i) => (
        <Line key={i} points={[[x,0,z],[x,ROOM_H,z]]} color={C_WIRE_BRIGHT} lineWidth={0.9} transparent opacity={0.38} />
      ))}
      <mesh position={[0, ROOM_H / 2, -hd]}>
        <planeGeometry args={[roomW, ROOM_H]} />
        <meshBasicMaterial color={C_WIRE_MID} transparent opacity={0.07} side={THREE.FrontSide} />
      </mesh>
    </group>
  );
}

// ── 서버룸 배경 이미지 ────────────────────────────────────
function RoomBackground() {
  const { scene } = useThree();
  useEffect(() => {
    const apply = (tex: THREE.Texture) => {
      scene.background = tex;
      scene.backgroundIntensity = 0.30;
    };
    // 이미 캐시된 경우 즉시 적용, 아니면 로드 완료 후 적용
    if (_bgTexCache) {
      apply(_bgTexCache);
    } else {
      new THREE.TextureLoader().load(BG_IMAGE, (tex) => {
        tex.colorSpace = THREE.SRGBColorSpace;
        _bgTexCache = tex;
        apply(tex);
      });
    }
    return () => {
      scene.background = null;
      scene.backgroundIntensity = 1;
    };
  }, [scene]);
  return null;
}

// ── 3D 씬 ─────────────────────────────────────────────────
function RoomScene({ racks, cols, rows, selectedDeviceId, onDeviceSelect }: {
  racks:           Rack[];
  cols:            number;
  rows:            number;
  selectedDeviceId: string | null;
  onDeviceSelect:  (id: string) => void;
}) {
  const roomW = cols * RACK_SPACING_X + 4;
  const roomD = rows * RACK_SPACING_Z + 4;

  const [deviceTexture, setDeviceTexture] = useState<THREE.Texture | null>(null);

  useEffect(() => {
    const loader = new THREE.TextureLoader();
    loader.load('/images/server-blade.jpg', (tex) => { tex.colorSpace = THREE.SRGBColorSpace; setDeviceTexture(tex); });
  }, []);

  return (
    <>
      <RoomBackground />
      <ambientLight intensity={0.35} color="#1a4a6a" />
      <pointLight position={[0, 6, 0]} color={C_WIRE_BRIGHT} intensity={0.5} distance={25} decay={1.5} />

      <RoomEnvironment roomW={roomW} roomD={roomD} />

      {racks.map((rack) => (
        <group key={rack.id}>
          <RackFrame rack={rack} totalCols={cols} totalRows={rows} />
          {/* 랙 내 디바이스 개별 */}
          {rack.devices.map((dev) => (
            <DeviceSlot3D
              key={dev.id}
              device={dev}
              rackRow={rack.row}
              rackCol={rack.col}
              totalCols={cols}
              totalRows={rows}
              isSelected={dev.id === selectedDeviceId}
              onSelect={() => onDeviceSelect(dev.id)}
              deviceTexture={deviceTexture}
            />
          ))}
        </group>
      ))}

      <OrbitControls
        target={[0, 1.1, 0]}
        minDistance={3}
        maxDistance={30}
        maxPolarAngle={Math.PI / 2.05}
        enablePan={false}
        enableDamping
        dampingFactor={0.06}
      />

      {/* Bloom 글로우 */}
      <EffectComposer>
        <Bloom
          luminanceThreshold={0.08}
          intensity={1.8}
          radius={0.9}
          mipmapBlur
        />
      </EffectComposer>
    </>
  );
}

// ── 하단 정보 패널 ────────────────────────────────────────
function DeviceInfoPanel({ device, rackId }: { device: Device | null; rackId: string | null }) {
  const level  = device ? deviceStatusToAlarmLevel(device.status) : 'NR';
  const col    = device ? deviceStatusColor(device.status) : '#1e293b';
  const border = device ? `${col}50` : '#1e293b';

  return (
    <div
      className="shrink-0 mx-4 mb-3 rounded-xl border px-4 py-2.5"
      style={{ borderColor: border, backgroundColor: device ? `${col}08` : 'transparent', height: '52px', overflow: 'hidden' }}
    >
      {device ? (
        <div className="flex items-center gap-3">
          {/* 디바이스 ID + 소속 랙 */}
          <div className="shrink-0">
            <p className="font-mono text-sm font-bold" style={{ color: col }}>{device.id}</p>
            <p className="font-mono text-[10px] text-slate-500 mt-0.5">{rackId}</p>
          </div>

          <div className="w-px h-8 bg-slate-700/60 shrink-0" />

          {/* 알람 등급 */}
          <span
            className="text-[10px] font-bold px-2 py-0.5 rounded shrink-0"
            style={{
              color:           col,
              backgroundColor: `${col}18`,
              border:          `1px solid ${col}40`,
            }}
          >
            {ALARM_LABEL[level]}
          </span>

          <div className="w-px h-8 bg-slate-700/60 shrink-0" />

          {/* 알람 상세 — 추후 SSE 연동 후 실데이터로 교체 */}
          <div className="flex-1 grid grid-cols-4 gap-2 min-w-0">
            {[
              { label: 'CALL TYPE', value: device.status !== 'normal' ? 'ATTACH' : '—' },
              { label: 'KPI',       value: device.status !== 'normal' ? '성공율' : '—' },
              { label: '상태',       value: device.status !== 'normal' ? '낮음'  : '—' },
              { label: '연속발생',   value: device.status !== 'normal' ? '5회'   : '—' },
            ].map(({ label, value }) => (
              <div key={label} className="flex flex-col gap-0.5">
                <span className="text-[9px] text-slate-500 uppercase tracking-wide">{label}</span>
                <span className="text-xs font-semibold" style={{ color: device.status !== 'normal' ? col : '#475569' }}>
                  {value}
                </span>
              </div>
            ))}
          </div>
        </div>
      ) : null}
    </div>
  );
}

// ── 메인 컴포넌트 ─────────────────────────────────────────
interface Props {
  center: Center;
  floor:  FloorData;
  onBack: () => void;
}

export default function NwDtRoomView({ center, floor, onBack }: Props) {
  const [selectedDeviceId, setSelectedDeviceId] = useState<string | null>(null);
  const { racks, cols } = generateRacks(center, floor);
  const rows = Math.ceil(racks.length / cols);

  const selectedDevice = racks.flatMap((r) => r.devices).find((d) => d.id === selectedDeviceId) ?? null;
  const selectedRackId = racks.find((r) => r.devices.some((d) => d.id === selectedDeviceId))?.id ?? null;

  const camDist = cols * 1.6;

  return (
    <div className="flex flex-col h-full overflow-hidden" style={{ background: C_BG }}>

      {/* 브레드크럼 */}
      <div className="shrink-0 flex items-center gap-1.5 px-4 py-2.5 border-b border-slate-800/60 text-xs">
        <button
          onClick={onBack}
          className="flex items-center gap-1.5 px-2.5 py-1 rounded-md bg-slate-800 border border-slate-600/70 text-slate-300 hover:text-cyan-300 hover:border-cyan-500/60 hover:bg-slate-700 transition-all"
        >
          <ArrowLeft className="size-3.5" />
          빌딩
        </button>
        <ChevronRight className="size-3 text-slate-700" />
        <span className="text-slate-400">{center.name}</span>
        <ChevronRight className="size-3 text-slate-700" />
        <span className="font-mono" style={{ color: C_WIRE_BRIGHT }}>{floor.id}</span>
        <ChevronRight className="size-3 text-slate-700" />
        <span className="text-slate-300 font-medium">{floor.name}</span>
        <span className="ml-auto font-mono text-[10px] text-slate-500">
          RACK × {racks.length}&nbsp;|&nbsp;{floor.id === '8F' ? 'MME' : 'SGW'} × {floor.id === '8F' ? 30 : 60}
        </span>
      </div>

      {/* 3D 캔버스 */}
      <div className="flex-1 min-h-0 mx-4 my-2 rounded-xl overflow-hidden border border-slate-800/40">
        <Canvas
          camera={{ position: [camDist * 0.25, camDist * 0.65, camDist * 1.15], fov: 50 }}
          gl={{ antialias: true, alpha: false, toneMapping: THREE.ACESFilmicToneMapping, toneMappingExposure: 1.2 }}
          style={{ background: C_BG }}
          onPointerMissed={() => setSelectedDeviceId(null)}
        >
          <RoomScene
            racks={racks}
            cols={cols}
            rows={rows}
            selectedDeviceId={selectedDeviceId}
            onDeviceSelect={(id) => setSelectedDeviceId((prev) => prev === id ? null : id)}
          />
        </Canvas>
      </div>

      {/* 디바이스 정보 패널 */}
      <DeviceInfoPanel device={selectedDevice} rackId={selectedRackId} />
    </div>
  );
}
