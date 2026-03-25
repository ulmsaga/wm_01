import { useRef, useState } from 'react';
import { Canvas, useFrame, ThreeEvent } from '@react-three/fiber';
import { OrbitControls, Html, Edges } from '@react-three/drei';
import * as THREE from 'three';
import { ArrowLeft, ChevronRight } from 'lucide-react';
import { AlarmLevel, ALARM_COLOR, Center, FloorData } from './types';
import NwDtRoomView from './NwDtRoomView';

const SELECTABLE = new Set(['8F', '9F']);

const FLOOR_NAMES: Record<string, string> = {
  '1F':  '로비·안내',
  '2F':  '관리·운용실',
  '3F':  '통신기기실',
  '4F':  '백업센터',
  '5F':  '데이터센터',
  '6F':  '전기통신설비실',
  '7F':  '네트워크기기실',
  '8F':  '장비실 A',
  '9F':  '장비실 B',
  '10F': '네트워크 감시센터',
  '11F': '회의실 A',
  '12F': '회의실 B',
  '13F': '연수실',
  '14F': '정보시스템부',
  '15F': '기술통괄부',
};

function makeFloors(alarmOverrides: Partial<Record<string, AlarmLevel>> = {}): FloorData[] {
  return Array.from({ length: 15 }, (_, i) => {
    const id = `${i + 1}F`;
    return {
      id,
      name:       FLOOR_NAMES[id] ?? `${i + 1}층`,
      level:      alarmOverrides[id] ?? 'NR',
      selectable: SELECTABLE.has(id),
    };
  });
}

const BUILDING_FLOORS: Record<string, FloorData[]> = {
  '品川 NOC':    makeFloors(),
  '大阪南港 NOC': makeFloors({ '8F': 'MJ' }),
};

// ── 색상 팔레트 ───────────────────────────────────────────
const C_BG          = '#020c1e';
const C_WIRE_BRIGHT = '#4fc3f7';   // 밝은 시안-블루
const C_WIRE_MID    = '#1565c0';   // 배경 빌딩 와이어
const C_WIRE_DIM    = '#0d2a5a';   // 건물 면 fill
const C_EDGE_MAIN   = '#29b6f6';   // 메인 빌딩 엣지

// ── 3D 상수 ───────────────────────────────────────────────
const BW    = 7.0;   // 메인 빌딩 폭
const BD    = 3.0;   // 메인 빌딩 깊이
const FH    = 0.35;  // 층 높이
const GAP   = 0.05;  // 층 간격
const FSTEP = FH + GAP;

// ── 알람 fill 펄스 ───────────────────────────────────────
function AlarmPulseMesh({ color, w, h, d }: { color: string; w: number; h: number; d: number }) {
  const ref = useRef<THREE.Mesh>(null!);
  useFrame(({ clock }) => {
    if (ref.current) {
      (ref.current.material as THREE.MeshBasicMaterial).opacity =
        0.18 + 0.17 * Math.sin(clock.elapsedTime * 2.5);
    }
  });
  return (
    <mesh ref={ref}>
      <boxGeometry args={[w, h, d]} />
      <meshBasicMaterial color={color} transparent opacity={0.18} side={THREE.DoubleSide} />
    </mesh>
  );
}

// ── 메인 빌딩 층 ─────────────────────────────────────────
function Floor3D({
  data,
  index,
  onClick,
}: {
  data:    FloorData;
  index:   number;
  onClick: () => void;
}) {
  const [isHovered, setIsHovered] = useState(false);
  const fillRef  = useRef<THREE.Mesh>(null!);
  const isAlarm  = data.level !== 'NR';
  const hexColor = ALARM_COLOR[data.level];

  useFrame((_, delta) => {
    if (fillRef.current && !isAlarm) {
      const mat = fillRef.current.material as THREE.MeshBasicMaterial;
      const target = data.selectable ? 0.22 : 0.08;
      mat.opacity = THREE.MathUtils.lerp(mat.opacity, target, delta * 6);
    }
  });

  const posY = index * FSTEP + FH / 2;

  const hoverEdge = isAlarm ? '#ffffff' : '#80d8ff';
  const fillColor = isAlarm ? hexColor : data.selectable ? C_WIRE_BRIGHT : C_WIRE_DIM;
  const edgeColor = isHovered ? hoverEdge
                  : isAlarm   ? hexColor
                  : data.selectable ? C_EDGE_MAIN : '#1a4a80';
  const edgeWidth = isHovered ? 2.0 : 0.7;

  return (
    <group>
      {/* 알람 층: 펄스 fill 애니메이션 */}
      {isAlarm && (
        <group position={[0, posY, 0]}>
          <AlarmPulseMesh color={hexColor} w={BW} h={FH} d={BD} />
        </group>
      )}

      {/* 이벤트 수신용 투명 mesh (사선 없음) */}
      <mesh
        position={[0, posY, 0]}
        onClick={(e: ThreeEvent<MouseEvent>) => {
          if (!data.selectable) return;
          e.stopPropagation();
          onClick();
        }}
        onPointerOver={(e) => {
          e.stopPropagation();
          if (data.selectable) { setIsHovered(true); document.body.style.cursor = 'pointer'; }
        }}
        onPointerOut={() => { setIsHovered(false); document.body.style.cursor = 'auto'; }}
      >
        <boxGeometry args={[BW, FH, BD]} />
        <meshBasicMaterial visible={false} />
      </mesh>

      {/* 반투명 fill — NR 층만 (알람은 AlarmPulseMesh 사용) */}
      {!isAlarm && (
        <mesh ref={fillRef} position={[0, posY, 0]}>
          <boxGeometry args={[BW, FH, BD]} />
          <meshBasicMaterial
            color={fillColor}
            transparent
            opacity={data.selectable ? 0.22 : 0.08}
            side={THREE.FrontSide}
          />
        </mesh>
      )}

      {/* 엣지 아웃라인 */}
      <mesh position={[0, posY, 0]}>
        <boxGeometry args={[BW, FH, BD]} />
        <meshBasicMaterial visible={false} />
        <Edges threshold={15} color={edgeColor} lineWidth={edgeWidth} />
      </mesh>

      {/* 알람 포인트라이트 */}
      {isAlarm && (
        <pointLight position={[0, posY, BD + 0.5]} color={hexColor} intensity={0.8} distance={3} decay={2} />
      )}

      {/* 층수 레이블 */}
      <Html position={[-BW / 2 - 0.5, posY, 0]} center>
        <span style={{
          fontFamily: 'monospace',
          fontSize:   data.selectable ? '11px' : '9px',
          fontWeight: data.selectable ? 'bold' : 'normal',
          color:      isHovered ? '#80d8ff' : data.selectable ? C_WIRE_BRIGHT : '#1a4a70',
          whiteSpace: 'nowrap',
          textShadow: isHovered ? `0 0 8px ${isAlarm ? hexColor : '#80d8ff'}` : 'none',
          userSelect: 'none',
        }}>
          {data.id}
        </span>
      </Html>

      {/* Hover: 플로팅 층 정보 라벨 */}
      {isHovered && (
        <Html position={[BW / 2 + 0.8, posY, 0]} center style={{ pointerEvents: 'none' }}>
          <div style={{
            display:      'flex',
            alignItems:   'center',
            gap:          '7px',
            background:   'rgba(2,12,30,0.93)',
            border:       `1px solid ${isAlarm ? hexColor : C_WIRE_BRIGHT}70`,
            borderRadius: '5px',
            padding:      '4px 10px',
            whiteSpace:   'nowrap',
            boxShadow:    `0 0 14px ${isAlarm ? hexColor : C_WIRE_BRIGHT}30`,
            fontFamily:   'monospace',
          }}>
            <span style={{ fontSize: '12px', fontWeight: 700, color: isAlarm ? hexColor : '#80d8ff' }}>
              {data.id}
            </span>
            <span style={{ fontSize: '10px', color: '#94a3b8', fontFamily: 'sans-serif' }}>
              {data.name}
            </span>
            <span style={{
              fontSize: '9px', fontWeight: 700,
              padding: '1px 5px', borderRadius: '3px',
              color: isAlarm ? hexColor : ALARM_COLOR['NR'],
              background: `${isAlarm ? hexColor : ALARM_COLOR['NR']}20`,
              border: `1px solid ${isAlarm ? hexColor : ALARM_COLOR['NR']}50`,
            }}>
              {data.level}
            </span>
          </div>
        </Html>
      )}
    </group>
  );
}

// ── 메인 빌딩 지붕 ───────────────────────────────────────
function MainRoof({ nFloors }: { nFloors: number }) {
  const topY = nFloors * FSTEP;
  return (
    <group>
      <mesh position={[0, topY + 0.07, 0]}>
        <boxGeometry args={[BW + 0.1, 0.13, BD + 0.1, 4, 1, 2]} />
        <meshBasicMaterial color={C_WIRE_BRIGHT} wireframe transparent opacity={0.6} />
      </mesh>
      {/* 스파이어 */}
      <mesh position={[0, topY + 0.14 + 0.9, 0]}>
        <cylinderGeometry args={[0.05, 0.05, 1.6, 8]} />
        <meshBasicMaterial color={C_WIRE_BRIGHT} wireframe />
      </mesh>
      <mesh position={[0, topY + 0.14 + 1.85, 0]}>
        <coneGeometry args={[0.18, 0.45, 8]} />
        <meshBasicMaterial color={C_WIRE_BRIGHT} wireframe />
      </mesh>
    </group>
  );
}

// ── 지면 ──────────────────────────────────────────────────
function Ground() {
  return (
    <group>
      {/* 건물 주변 플랫폼 — 빌딩 주위로만 */}
      <mesh rotation={[-Math.PI / 2, 0, 0]} position={[0, -0.01, 0]}>
        <planeGeometry args={[BW + 6, BD + 6]} />
        {/* <meshBasicMaterial color="#0a2a4a" transparent opacity={0.75} /> */}
        <meshBasicMaterial color="#006dc6" transparent opacity={0.2} />
      </mesh>
      {/* 그리드 — 가로 */}
      {Array.from({ length: 7 }).map((_, i) => (
        <mesh key={`h${i}`} rotation={[-Math.PI / 2, 0, 0]} position={[0, 0, -(BD + 6) / 2 + i * ((BD + 6) / 6)]}>
          <planeGeometry args={[BW + 6, 0.02]} />
          <meshBasicMaterial color={C_WIRE_MID} transparent opacity={0.45} />
        </mesh>
      ))}
      {/* 그리드 — 세로 */}
      {Array.from({ length: 7 }).map((_, i) => (
        <mesh key={`v${i}`} rotation={[-Math.PI / 2, 0, 0]} position={[-(BW + 6) / 2 + i * ((BW + 6) / 6), 0, 0]}>
          <planeGeometry args={[0.02, BD + 6]} />
          <meshBasicMaterial color={C_WIRE_MID} transparent opacity={0.45} />
        </mesh>
      ))}
    </group>
  );
}

// ── 3D 씬 ─────────────────────────────────────────────────
function BuildingScene({
  floors,
  onEnterRoom,
}: {
  floors:      FloorData[];
  onEnterRoom: (id: string) => void;
}) {
  const buildingCenterY = (floors.length * FSTEP) / 2;

  return (
    <>
      <ambientLight intensity={0.1} />
      <pointLight position={[0, buildingCenterY, 15]} color={C_WIRE_BRIGHT} intensity={0.8} distance={40} decay={1.5} />

      <OrbitControls
        target={[0, buildingCenterY * 0.7, 0]}
        minDistance={3}
        maxDistance={80}
        minPolarAngle={Math.PI / 10}
        maxPolarAngle={Math.PI / 2.2}
        enablePan={false}
      />

      <Ground />

      <group>
        {floors.map((floor, i) => (
          <Floor3D
            key={floor.id}
            data={floor}
            index={i}
            onClick={() => onEnterRoom(floor.id)}
          />
        ))}
        <MainRoof nFloors={floors.length} />
      </group>
    </>
  );
}

// ── 메인 컴포넌트 ─────────────────────────────────────────
interface Props {
  center: Center;
  onBack: () => void;
}

export default function NwDtBuildingView({ center, onBack }: Props) {
  const [selectedFloor, setSelectedFloor] = useState<string | null>(null);
  const [view, setView] = useState<'building' | 'room'>('building');

  // 빌딩↔장비실 전환 애니메이션
  const [transKey, setTransKey] = useState(0);
  const [transDir, setTransDir] = useState<'in' | 'back'>('in');

  const floors = BUILDING_FLOORS[center.name] ?? makeFloors();

  const handleEnterRoom = (id: string) => {
    setTransDir('in');
    setTransKey((k) => k + 1);
    setSelectedFloor(id);
    setView('room');
  };

  const handleRoomBack = () => {
    setTransDir('back');
    setTransKey((k) => k + 1);
    setView('building');
  };

  const selectedData = floors.find((f) => f.id === selectedFloor);
  const animClass = transKey === 0 ? '' : transDir === 'in' ? 'dt-anim-in' : 'dt-anim-back';

  if (view === 'room' && selectedData) {
    return (
      <div key={transKey} className={`w-full h-full ${animClass}`}>
        <NwDtRoomView
          center={center}
          floor={selectedData}
          onBack={handleRoomBack}
        />
      </div>
    );
  }

  const totalH = floors.length * FSTEP;
  const camY = totalH * 0.5;
  const camZ = totalH * 1.1;
  const camX = totalH * 0.75;

  return (
    <div key={transKey} className={`flex flex-col h-full overflow-hidden ${animClass}`} style={{ background: C_BG }}>

      {/* 브레드크럼 */}
      <div className="shrink-0 flex items-center gap-1.5 px-4 py-2.5 border-b border-slate-800/60 text-xs">
        <button
          onClick={onBack}
          className="flex items-center gap-1 text-slate-500 hover:text-cyan-400 transition-colors"
        >
          <ArrowLeft className="size-3.5" />
          지도
        </button>
        <ChevronRight className="size-3 text-slate-700" />
        <span className="text-slate-300 font-medium">{center.name}</span>
      </div>

      {/* 3D 캔버스 */}
      <div className="flex-1 min-h-0 mx-4 my-2 rounded-xl overflow-hidden border border-slate-800/40">
        <Canvas
          camera={{ position: [camX, camY, camZ], fov: 55 }}
          gl={{ antialias: true, alpha: false }}
          style={{ background: C_BG }}
        >
          <BuildingScene
            floors={floors}
            onEnterRoom={handleEnterRoom}
          />
        </Canvas>
      </div>
    </div>
  );
}
