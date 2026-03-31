// ── 알람 등급 ─────────────────────────────────────────────
export type AlarmLevel = 'NR' | 'MN' | 'MJ' | 'CR';

export const ALARM_COLOR: Record<AlarmLevel, string> = {
  NR: '#22c55e',  // 초록       — Normal
  MN: '#facc15',  // 노랑       — Minor
  MJ: '#fb923c',  // 밝은 주황  — Major    (orange-400: 노랑과 빨강 사이, 명확한 주황)
  CR: '#f43f5e',  // 장밋빛 빨강 — Critical (rose-500: 주황과 확실히 구분되는 빨강-분홍)
};

export const ALARM_LABEL: Record<AlarmLevel, string> = {
  NR: 'NR (Normal)',
  MN: 'MN (Minor)',
  MJ: 'MJ (Major)',
  CR: 'CR (Critical)',
};

// ── 도코모 장비 운용 센터 ─────────────────────────────────
export interface Center {
  name: string;
  value: [number, number, number]; // [lng, lat, 0]
  level: AlarmLevel;
  desc: string; // 'ko설명 | ja説明' 형식
}

export const CENTERS: Center[] = [
  { name: '大阪南港 NOC', value: [135.4210, 34.6407, 0], level: 'CR', desc: '西日本 担当 大阪南港 NOC | 西日本 担当 · バックアップNOC (住之江区南港北1-9-9)' },
  { name: '品川 NOC',    value: [139.7372, 35.6333, 0], level: 'NR', desc: '東日本 担当 品川 NOC | 東日本 担当 · メインNOC (港区港南2-1-65)' },
];

// ── 빌딩 층 데이터 ───────────────────────────────────────
export interface FloorData {
  id:         string;
  name:       string;
  level:      AlarmLevel;
  selectable: boolean;
}

// ── 알람 데이터 구조 (TODO: SSE/API 연동 예정) ────────────
// 샘플 원본:
//   등급 | CALL TYPE | 지표  | 상태 | 값   | 연속발생
//   CR   | ATTACH    | 성공율 | 낮음 | 40%  | 5
//   MJ   | SRMO      | 성공율 | 낮음 | 60%  | 5
export interface AlarmRow {
  level:       AlarmLevel; // 등급
  callType:    string;     // CALL TYPE (ATTACH / SRMO / ...)
  metric:      string;     // 지표 (성공율 / ...)
  status:      string;     // 상태 (낮음 / 높음 / ...)
  value:       string;     // 값 (40% / ...)
  consecutive: number;     // 연속발생 횟수
}

export const SAMPLE_ALARMS: AlarmRow[] = [
  { level: 'CR', callType: 'ATTACH', metric: '성공율', status: '낮음', value: '40%', consecutive: 5 },
  { level: 'MJ', callType: 'SRMO',   metric: '성공율', status: '낮음', value: '60%', consecutive: 5 },
];
