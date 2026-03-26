import { useRef, useState, useEffect } from 'react';
import { Canvas, useFrame, useThree, ThreeEvent } from '@react-three/fiber';
import { OrbitControls, Html, Edges, MeshReflectorMaterial } from '@react-three/drei';
import { EffectComposer, Bloom } from '@react-three/postprocessing';
import * as THREE from 'three';
import { ArrowLeft, ChevronRight } from 'lucide-react';
import { AlarmLevel, ALARM_COLOR, Center, FloorData } from './types';
import NwDtRoomView from './NwDtRoomView';

// ── 텍스처 모듈 레벨 프리로드 ─────────────────────────────
const BG_IMG       = '/images/server-room-bg4.jpg';
const BUILDING_IMG = '/images/server-room-bg6.jpg';
let _bgTex:  THREE.Texture | null = null;
let _bldTex: THREE.Texture | null = null;
const _loader = new THREE.TextureLoader();
_loader.load(BG_IMG,       (t) => { t.colorSpace = THREE.SRGBColorSpace; _bgTex  = t; });
_loader.load(BUILDING_IMG, (t) => { t.colorSpace = THREE.SRGBColorSpace; t.wrapS = t.wrapT = THREE.RepeatWrapping; t.repeat.set(2, 1); _bldTex = t; });

// ── 배경 컴포넌트 ─────────────────────────────────────────
function BuildingBackground() {
  const { scene } = useThree();
  useEffect(() => {
    const apply = (tex: THREE.Texture) => { scene.background = tex; scene.backgroundIntensity = 0.25; };
    if (_bgTex) apply(_bgTex);
    else _loader.load(BG_IMG, (t) => { t.colorSpace = THREE.SRGBColorSpace; _bgTex = t; apply(t); });
    return () => { scene.background = null; scene.backgroundIntensity = 1; };
  }, [scene]);
  return null;
}

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
  // 관리 대상 장비실은 8F·9F만 — 나머지 층은 NR
  // 층 레벨 = 해당 층 장비 중 최고 등급 (CR > MJ > MN)
  '大阪南港 NOC': makeFloors({
    '8F': 'CR',  // 장비실 A — CR/MJ/MN 장비 혼재, 최고등급 CR
    '9F': 'MJ',  // 장비실 B — MJ/MN 장비 혼재, 최고등급 MJ
  }),
};

// ── 색상 팔레트 ───────────────────────────────────────────
const C_BG          = '#020c1e';
const C_WIRE_BRIGHT = '#4fc3f7';   // 밝은 시안-블루
const C_WIRE_MID    = '#1565c0';   // 배경 빌딩 와이어
const C_WIRE_DIM    = '#0d2a5a';   // 건물 면 fill
const C_EDGE_MAIN   = '#7de8ff';   // 메인 빌딩 엣지

// ── 3D 상수 ───────────────────────────────────────────────
const BW    = 9.5;   // 메인 빌딩 폭
const BD    = 6.0;   // 메인 빌딩 깊이
const FH    = 0.75;  // 층 높이
const GAP   = 0.06;  // 층 간격
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
  const isAlarm  = data.level !== 'NR';
  const hexColor = ALARM_COLOR[data.level];

  const posY = index * FSTEP + FH / 2;

  const hoverEdge = isAlarm ? '#ffffff' : '#80d8ff';
  const fillColor = isAlarm ? hexColor : data.selectable ? C_WIRE_BRIGHT : C_WIRE_DIM;
  const edgeColor = isHovered ? hoverEdge
                  : isAlarm   ? hexColor
                  : data.selectable ? C_EDGE_MAIN : '#3a7abf';
  const edgeWidth = isHovered ? 2.5 : 1.2;

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

      {/* 솔리드 fill — NR 층 (알람은 AlarmPulseMesh 사용) */}
      {!isAlarm && (
        <mesh position={[0, posY, 0]}>
          <boxGeometry args={[BW, FH, BD]} />
          {_bldTex ? (
            <meshStandardMaterial
              map={_bldTex}
              color="#3a5a70"
              roughness={0.6}
              metalness={0.3}
            />
          ) : (
            <meshBasicMaterial color={fillColor} />
          )}
        </mesh>
      )}

      {/* 층 외곽 엣지 */}
      <mesh position={[0, posY, 0]}>
        <boxGeometry args={[BW, FH, BD]} />
        <meshBasicMaterial visible={false} />
        <Edges threshold={15} color={edgeColor} lineWidth={edgeWidth} />
      </mesh>

      {/* 앞면 유리창 엣지 (4칸) — 외곽(1·4번)은 좁게 */}
      {!isAlarm && (() => {
        const winH    = FH * 0.55;
        const zFront  = BD / 2 + 0.01;
        const winColor = isHovered ? '#b0efff' : '#4db8d4';
        const slots   = [-3, -1, 1, 3];
        const winW    = BW * 0.10;
        return slots.map((slot) => (
          <mesh key={slot} position={[slot * (BW / 4) * 0.58, posY, zFront]}>
            <boxGeometry args={[winW, winH, 0.01]} />
            <meshBasicMaterial visible={false} />
            <Edges threshold={1} color={winColor} lineWidth={0.5} />
          </mesh>
        ));
      })()}

      {/* 알람 포인트라이트 */}
      {isAlarm && (
        <pointLight position={[0, posY, BD + 0.5]} color={hexColor} intensity={0.8} distance={3} decay={2} />
      )}

      {/* 층수 레이블 — 활성 층만 표시 */}
      {data.selectable && (
        <Html position={[-BW / 2 - 0.6, posY, 0]} center>
          <span style={{
            fontFamily: 'monospace',
            fontSize:   '15px',
            fontWeight: 'bold',
            color:      isHovered ? '#80d8ff' : isAlarm ? hexColor : C_WIRE_BRIGHT,
            whiteSpace: 'nowrap',
            textShadow: `0 0 10px ${isAlarm ? hexColor : C_WIRE_BRIGHT}`,
            userSelect: 'none',
          }}>
            {data.id}
          </span>
        </Html>
      )}

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
      <mesh position={[0, topY + 0.14 + 0.40, 0]}>
        <cylinderGeometry args={[0.05, 0.05, 0.7, 8]} />
        <meshBasicMaterial color={C_WIRE_BRIGHT} wireframe />
      </mesh>
      <mesh position={[0, topY + 0.14 + 0.85, 0]}>
        <coneGeometry args={[0.14, 0.32, 8]} />
        <meshBasicMaterial color={C_WIRE_BRIGHT} wireframe />
      </mesh>
    </group>
  );
}

// ── 지면 ──────────────────────────────────────────────────
const GW = BW + 30;  // 바닥 폭
const GD = BD + 30;  // 바닥 깊이

function Ground() {
  return (
    <group>
      {/* 넓은 반사 바닥 */}
      <mesh rotation={[-Math.PI / 2, 0, 0]} position={[0, -0.01, 0]}>
        <planeGeometry args={[GW, GD]} />
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
      <BuildingBackground />
      <ambientLight intensity={1.1} color="#cce8ff" />
      <pointLight position={[0, buildingCenterY, 15]} color={C_WIRE_BRIGHT} intensity={2.5} distance={70} decay={1.0} />
      <pointLight position={[0, buildingCenterY, -15]} color="#ddeeff" intensity={1.8} distance={60} decay={1.2} />

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

      {/* Bloom 글로우 */}
      <EffectComposer>
        <Bloom
          luminanceThreshold={0.08}
          intensity={1.6}
          radius={0.88}
          mipmapBlur
        />
      </EffectComposer>
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

  // 장비실 전환 애니메이션 (building→room 방향만)
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
  const roomAnimClass = transDir === 'in' ? 'dt-anim-in' : 'dt-anim-back';

  const totalH = floors.length * FSTEP;
  const camY   = totalH * 0.75;
  const camZ   = totalH * 1.40;
  const camX   = totalH * 0.72;

  return (
    <div className="absolute inset-0 flex flex-col overflow-hidden" style={{ background: C_BG }}>

      {/* 브레드크럼 */}
      <div className="shrink-0 flex items-center gap-1.5 px-4 py-2.5 border-b border-slate-800/60 text-xs">
        <button
          onClick={onBack}
          className="flex items-center gap-1.5 px-2.5 py-1 rounded-md bg-slate-800 border border-slate-600/70 text-slate-300 hover:text-cyan-300 hover:border-cyan-500/60 hover:bg-slate-700 transition-all"
        >
          <ArrowLeft className="size-3.5" />
          지도
        </button>
        <ChevronRight className="size-3 text-slate-700" />
        <span className="text-slate-300 font-medium">{center.name}</span>
      </div>

      {/* 3D 빌딩 캔버스 — 항상 마운트 유지.
          room 뷰가 위에 올라갈 때 visibility:hidden으로 레이아웃을 보존해
          Canvas 크기가 깨지지 않게 한다. */}
      <div
        className="flex-1 min-h-0 mx-4 my-2 rounded-xl overflow-hidden border border-slate-800/40"
        style={{ visibility: view === 'room' ? 'hidden' : 'visible' }}
      >
        <Canvas
          camera={{ position: [camX, camY, camZ], fov: 55 }}
          gl={{ antialias: true, alpha: false, toneMapping: THREE.ACESFilmicToneMapping, toneMappingExposure: 1.2 }}
          style={{ background: C_BG }}
        >
          <BuildingScene floors={floors} onEnterRoom={handleEnterRoom} />
        </Canvas>
      </div>

      {/* 장비실 뷰 — absolute 오버레이 (Canvas를 대체하지 않고 위에 얹음) */}
      {view === 'room' && selectedData && (
        <div key={transKey} className={`absolute inset-0 ${roomAnimClass}`}>
          <NwDtRoomView
            center={center}
            floor={selectedData}
            onBack={handleRoomBack}
          />
        </div>
      )}
    </div>
  );
}
