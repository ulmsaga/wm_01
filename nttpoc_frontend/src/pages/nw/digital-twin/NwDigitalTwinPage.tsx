import { useState } from 'react';
import { Clock, Map } from 'lucide-react';
import { Center, CENTERS, ALARM_COLOR, ALARM_LABEL } from './types';
import NwDtMapView from './NwDtMapView';
import NwDtBuildingView from './NwDtBuildingView';

// ── KPI 데이터 (TODO: SSE 연동 후 서버값으로 교체) ────────
interface KpiItem {
  label: string;
  value: number;
}

const KPI_DATA: KpiItem[] = [
  { label: '시도호',        value: 6_600_371 },
  { label: '성공호',        value: 6_516_457 },
  { label: '시도호 이용자', value: 7_017_834 },
];

function fmtNumber(n: number) {
  return n.toLocaleString('ja-JP');
}

function fmtEventTime(d: Date) {
  const yy = d.getFullYear();
  const mm = (d.getMonth() + 1).toString().padStart(2, '0');
  const dd = d.getDate().toString().padStart(2, '0');
  const hh = d.getHours().toString().padStart(2, '0');
  const mi = d.getMinutes().toString().padStart(2, '0');
  return `${yy}-${mm}-${dd} ${hh}:${mi}`;
}

// ── NOC 패널 카드 ─────────────────────────────────────────
function NocCard({ center }: { center: Center }) {
  const [ko, ja] = center.desc.split(' | ');
  return (
    <div className="flex flex-col gap-3 bg-slate-900/80 rounded-xl border border-slate-700/60 p-4 h-full">
      {/* 이름 + 알람 뱃지 */}
      <div className="flex items-start justify-between gap-2">
        <p className="text-sm font-semibold text-slate-100 leading-tight">{center.name}</p>
        <span
          className="shrink-0 text-[10px] font-bold px-2 py-0.5 rounded"
          style={{
            color: ALARM_COLOR[center.level],
            backgroundColor: `${ALARM_COLOR[center.level]}18`,
            border: `1px solid ${ALARM_COLOR[center.level]}40`,
          }}
        >
          {center.level}
        </span>
      </div>

      {/* 알람 상태 인디케이터 */}
      <div className="flex items-center gap-2">
        <span
          className="w-2.5 h-2.5 rounded-full shrink-0"
          style={{
            backgroundColor: ALARM_COLOR[center.level],
            boxShadow: `0 0 8px ${ALARM_COLOR[center.level]}`,
          }}
        />
        <span className="text-xs" style={{ color: ALARM_COLOR[center.level] }}>
          {ALARM_LABEL[center.level]}
        </span>
      </div>

      {/* 설명 */}
      <div className="border-t border-slate-700/50 pt-3 space-y-1">
        <p className="text-xs text-slate-300">{ko}</p>
        <p className="text-[11px] text-slate-500">{ja}</p>
      </div>

      {/* 빈 공간 — 추후 콘텐츠 영역 */}
      <div className="flex-1 rounded-lg border border-slate-700/30 border-dashed bg-slate-800/20 min-h-20" />
    </div>
  );
}

// ── 메인 컴포넌트 ─────────────────────────────────────────
type View = 'map' | 'building';

export default function NwDigitalTwinPage() {
  const [view, setView]                   = useState<View>('map');
  const [selectedCenter, setSelectedCenter] = useState<Center | null>(null);
  // TODO: SSE 연동 후 서버에서 받은 EVENT_TIME으로 교체
  const [eventTime] = useState(new Date());

  // 화면 전환 애니메이션 제어
  const [transKey, setTransKey]   = useState(0);
  const [transDir, setTransDir]   = useState<'in' | 'back'>('in');

  const handleSelectCenter = (center: Center) => {
    setTransDir('in');
    setTransKey((k) => k + 1);
    setSelectedCenter(center);
    setView('building');
  };

  const handleBack = () => {
    setTransDir('back');
    setTransKey((k) => k + 1);
    setView('map');
    setSelectedCenter(null);
  };

  return (
    <div className="flex flex-col h-full bg-[#0b1120] overflow-hidden">

      {/* ── 헤더 ── */}
      <div className="shrink-0 flex items-center gap-3 px-5 py-2 border-b border-slate-800">
        <span className="flex items-center gap-1.5 bg-red-500/10 border border-red-500/30 rounded px-2 py-0.5">
          <span className="w-1.5 h-1.5 rounded-full bg-red-500 animate-pulse" />
          <span className="text-[10px] font-bold tracking-widest text-red-400">LIVE</span>
        </span>
        <span className="flex items-center gap-1.5 text-xs font-semibold tracking-widest text-cyan-400 uppercase">
          <Map className="size-3.5" />
          NW Digital Twin / {view === 'map' ? '지도' : '빌딩'}
        </span>
        <div className="ml-auto flex items-center gap-1.5 text-xs text-slate-400">
          <Clock className="size-3.5 text-slate-500" />
          <span>{fmtEventTime(eventTime)}</span>
        </div>
      </div>

      {/* ── 좌우 분할 본문 ── */}
      <div className="flex flex-1 min-h-0">

        {/* ── 좌: KPI + 지도/빌딩 ── */}
        <div className="flex-1 min-w-0 flex flex-col">

          {/* 상단: KPI 카드 */}
          <div className="shrink-0 flex gap-3 p-3 border-b border-slate-800">
            {KPI_DATA.map((kpi) => (
              <div
                key={kpi.label}
                className="flex-1 flex items-center justify-between gap-3 bg-slate-900/80 rounded-xl border border-slate-700/60 px-4 py-3"
              >
                <span className="text-base font-semibold text-slate-300 tracking-wide whitespace-nowrap">
                  {kpi.label}
                </span>
                <span className="text-2xl font-bold text-slate-100 tabular-nums">
                  {fmtNumber(kpi.value)}
                </span>
              </div>
            ))}
          </div>

          {/* 하단: 지도 또는 빌딩 (전환 애니메이션) */}
          <div className="flex-1 min-h-0 relative overflow-hidden">
            <div
              key={transKey}
              className={transKey === 0 ? 'w-full h-full' : `w-full h-full ${transDir === 'in' ? 'dt-anim-in' : 'dt-anim-back'}`}
            >
              {view === 'map' ? (
                <NwDtMapView onSelectCenter={handleSelectCenter} />
              ) : selectedCenter ? (
                <NwDtBuildingView center={selectedCenter} onBack={handleBack} />
              ) : null}
            </div>
          </div>
        </div>

        {/* ── 우: NOC 패널 ── */}
        <div className="w-135 shrink-0 flex flex-col gap-3 p-3 border-l border-slate-800 overflow-y-auto">
          {CENTERS.map((c) => (
            <div key={c.name} className="flex-1 min-h-0">
              <NocCard center={c} />
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
