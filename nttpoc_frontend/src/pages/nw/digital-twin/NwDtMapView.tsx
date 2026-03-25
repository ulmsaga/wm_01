import { useEffect, useState } from 'react';
import ReactECharts from 'echarts-for-react';
import * as echarts from 'echarts';
import { AlarmLevel, ALARM_COLOR, ALARM_LABEL, Center, CENTERS } from './types';

interface Props {
  onSelectCenter: (center: Center) => void;
}

export default function NwDtMapView({ onSelectCenter }: Props) {
  const [ready, setReady] = useState(false);

  useEffect(() => {
    fetch('/map/japan.json')
      .then((r) => r.json())
      .then((geoJson) => {
        echarts.registerMap('japan', geoJson);
        setReady(true);
      })
      .catch((e) => console.error('Japan GeoJSON 로드 실패:', e));
  }, []);

  const makeSeries = (level: AlarmLevel) => ({
    name: ALARM_LABEL[level],
    type: 'effectScatter' as const,
    coordinateSystem: 'geo' as const,
    cursor: 'pointer',
    data: CENTERS.filter((c) => c.level === level).map((c) => ({
      name: c.name,
      value: c.value,
      level: c.level,
      desc: c.desc,
    })),
    symbolSize: level === 'CR' ? 20 : level === 'MJ' ? 18 : 16,
    rippleEffect: {
      brushType: (level === 'CR' ? 'fill' : 'stroke') as 'fill' | 'stroke',
      scale: level === 'CR' ? 5 : 4,
      period: level === 'CR' ? 1.5 : level === 'MJ' ? 2 : 3,
    },
    itemStyle: {
      color: ALARM_COLOR[level],
      shadowBlur: level === 'CR' ? 24 : 16,
      shadowColor: ALARM_COLOR[level],
    },
    label: {
      show: true,
      formatter: '{b}',
      position: 'right' as const,
      color: ALARM_COLOR[level],
      fontSize: 12,
      fontWeight: 'bold' as const,
    },
  });

  const option: echarts.EChartsOption = {
    backgroundColor: '#0b1120',
    tooltip: {
      trigger: 'item',
      backgroundColor: '#1e293b',
      borderColor: '#334155',
      textStyle: { color: '#e2e8f0', fontSize: 12 },
      formatter: (params: unknown) => {
        const p = params as { name: string; data?: Center };
        if (!p.data?.level) return p.name;
        const { name, level, desc } = p.data;
        const color = ALARM_COLOR[level];
        return [
          `<span style="font-weight:600;color:${color}">${name}</span>`,
          `<span style="color:#94a3b8">${desc}</span>`,
          `<span style="color:${color}">● ${ALARM_LABEL[level]}</span>`,
          `<span style="color:#475569;font-size:10px">클릭하여 빌딩 보기</span>`,
        ].join('<br/>');
      },
    },
    legend: { show: false },
    geo: {
      map: 'japan',
      roam: true,
      center: [137.6, 35.2],
      zoom: 4.5,
      itemStyle: {
        areaColor: '#0f2744',
        borderColor: '#1e4976',
        borderWidth: 0.8,
      },
      emphasis: {
        itemStyle: { areaColor: '#1a3a5c', borderColor: '#38bdf8', borderWidth: 1.2 },
        label: { show: false },
      },
    },
    series: (['NR', 'MN', 'MJ', 'CR'] as AlarmLevel[]).map(makeSeries),
  };

  const handleClick = (params: unknown) => {
    const p = params as { componentType: string; seriesType: string; name: string };
    if (p.componentType === 'series' && p.seriesType === 'effectScatter') {
      const center = CENTERS.find((c) => c.name === p.name);
      if (center) onSelectCenter(center);
    }
  };

  if (!ready) {
    return (
      <div className="flex items-center justify-center h-full text-slate-600 text-sm">
        지도 로딩 중...
      </div>
    );
  }

  return (
    <div className="relative w-full h-full">
      {/* 지도 */}
      <ReactECharts
        option={option}
        style={{ width: '100%', height: '100%' }}
        opts={{ renderer: 'canvas' }}
        onEvents={{ click: handleClick }}
      />

      {/* NOC 선택 버튼 — 지도 하단 오버레이 */}
      <div className="absolute bottom-4 left-1/2 -translate-x-1/2 flex gap-3">
        {CENTERS.map((c) => {
          const color = ALARM_COLOR[c.level];
          return (
            <button
              key={c.name}
              onClick={() => onSelectCenter(c)}
              className="flex items-center gap-2 px-4 py-2 rounded-lg backdrop-blur-sm border transition-all hover:scale-105 active:scale-95"
              style={{
                backgroundColor: `${color}18`,
                borderColor: `${color}50`,
                boxShadow: `0 0 12px ${color}30`,
              }}
            >
              <span
                className="w-2 h-2 rounded-full shrink-0 animate-pulse"
                style={{ backgroundColor: color, boxShadow: `0 0 6px ${color}` }}
              />
              <span className="text-sm font-semibold text-slate-200 whitespace-nowrap">
                {c.name}
              </span>
              <span
                className="text-[10px] font-bold px-1.5 py-0.5 rounded"
                style={{ color, backgroundColor: `${color}20`, border: `1px solid ${color}40` }}
              >
                {c.level}
              </span>
            </button>
          );
        })}
      </div>
    </div>
  );
}
