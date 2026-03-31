# Frontend UI 개발 규칙 (Tailwind CSS + shadcn/ui)

## 컴포넌트 3계층 구조

```
shadcn/ui (ui/)          ← 기반: npx shadcn add로 추가하는 원시 컴포넌트
    ↓ 조합
common/                  ← 프로젝트 전용: 비즈니스 의미를 가진 컴포넌트
    ↓ 조합
pages/                   ← 레이아웃·데이터 흐름만, className 나열 금지
```

### ui/ — shadcn 기본 컴포넌트
- `npx shadcn add <컴포넌트명>` 으로만 추가 (직접 작성 금지)
- 현재 추가된 것: `button`, `input`
- shadcn 컴포넌트 내부 수정 최소화 — 커스텀은 `common/`에서 래핑으로 해결

### common/ — 프로젝트 전용 컴포넌트
- shadcn `ui/` 컴포넌트를 조합하거나 Tailwind 패턴을 캡슐화
- 비즈니스 의미 있는 이름 사용 (예: `AlarmBadge`, `StatusDot`, `KpiCard`)
- **신규 컴포넌트 작성 위치는 항상 `common/`**

### pages/ — 페이지 파일 규칙
- `common/` 컴포넌트를 조합하는 것이 주 역할
- 페이지 고유 레이아웃(grid/flex 구조)은 className 직접 사용 허용
- 동일 className 패턴이 페이지 내에서 2회 이상 반복되면 즉시 `common/`으로 추출

---

## Tailwind CSS 사용 원칙

### className 나열 위치
```tsx
// ❌ 페이지에서 raw Tailwind 나열
<span className="text-[10px] font-bold px-2 py-0.5 rounded text-red-400 bg-red-500/10 border border-red-500/30">
  CR
</span>

// ✅ common/ 컴포넌트로 추출 후 페이지에서 호출
<AlarmBadge level="CR" />
```

### 추출 기준
| 상황 | 처리 |
|------|------|
| 동일 패턴 2회 이상 반복 | `common/` 컴포넌트로 추출 |
| 비즈니스 의미 있는 UI 단위 | `common/` 컴포넌트로 추출 |
| 페이지 고유 1회성 레이아웃 | 페이지에 직접 작성 허용 |
| 단순 flex/gap 래퍼 | 추출 불필요 |

---

## CVA (class-variance-authority) 패턴

variant·size 분기가 있는 컴포넌트는 반드시 CVA 사용.

```tsx
// common/AlarmBadge.tsx
import { cva, type VariantProps } from 'class-variance-authority';
import { cn } from '@/lib/utils';

const badgeVariants = cva(
  'text-[10px] font-bold px-2 py-0.5 rounded border',
  {
    variants: {
      level: {
        CR: 'text-rose-400 bg-rose-500/10 border-rose-500/30',
        MJ: 'text-orange-400 bg-orange-500/10 border-orange-500/30',
        MN: 'text-yellow-400 bg-yellow-500/10 border-yellow-500/30',
        NR: 'text-green-400 bg-green-500/10 border-green-500/30',
      },
    },
    defaultVariants: { level: 'NR' },
  }
);

interface Props extends VariantProps<typeof badgeVariants> {
  className?: string;
}

export function AlarmBadge({ level, className }: Props) {
  return <span className={cn(badgeVariants({ level }), className)}>{level}</span>;
}
```

- 클래스 병합: `cn()` (`tailwind-merge` + `clsx` 조합, `@/lib/utils`에 정의)
- `className` prop 허용으로 외부 오버라이드 가능하게 유지

---

## shadcn/ui 예외 — 제3자 컴포넌트 허용 기준

shadcn/ui가 제공하지 않는 기능이 필요할 때 외부 라이브러리 사용 가능.

### 허용 조건
- shadcn/ui + Tailwind 조합으로 구현이 비효율적인 복잡한 UI 컴포넌트
- 예: `DatePicker`, `MultiSelectBox`, `ColorPicker`, `RichTextEditor`, `TreeView` 등

### 사용 시 원칙
- 외부 컴포넌트도 **`common/`에서 래핑하여 사용** — 페이지에서 직접 import 금지
- 래퍼 컴포넌트에서 props 인터페이스 통일 및 스타일 정합성 맞춤
- 라이브러리 교체 시 래퍼만 수정하면 되도록 추상화 유지

```tsx
// ✅ common/DateRangePicker.tsx — 외부 라이브러리를 래핑
import SomeExternalPicker from 'some-datepicker-lib';
import { cn } from '@/lib/utils';

interface Props {
  value: [Date, Date] | null;
  onChange: (range: [Date, Date]) => void;
  className?: string;
}

export function DateRangePicker({ value, onChange, className }: Props) {
  return (
    <div className={cn('...', className)}>
      <SomeExternalPicker ... />
    </div>
  );
}

// ✅ 페이지에서는 래퍼 컴포넌트만 사용
<DateRangePicker value={range} onChange={setRange} />
```

---

## 시각화 전용 라이브러리

일반 UI 컴포넌트 계층(shadcn/CVA)과 별개로, 아래 라이브러리는 해당 도메인 페이지에서 직접 사용.

### 확정 (설치 완료)

| 용도 | 라이브러리 | 버전 | 적용 영역 |
|------|-----------|------|----------|
| 3D Digital Twin | `@react-three/fiber` | 9.5.0 | NW Digital Twin 빌딩·장비실 |
| 3D 헬퍼 | `@react-three/drei` | 10.7.7 | OrbitControls, Html, Edges 등 |
| 3D 후처리 | `@react-three/postprocessing` | 3.0.4 | Bloom 글로우 효과 |
| 지도·차트 | `echarts` + `echarts-for-react` | 6.0.0 / 3.0.6 | NW Digital Twin 지도, KPI 차트 |
| 토폴로지·다이어그램 | `@xyflow/react` | 12.10.1 | NW Topology 페이지 |

### 검토 중 (미설치)

| 용도 | 라이브러리 | 비고 |
|------|-----------|------|
| 차트 (추가 검토) | `chart.js` | echarts와 중복 여부 판단 후 결정 |
| 차트 (추가 검토) | `@canvasjs/react-charts` | echarts와 중복 여부 판단 후 결정 |

### 시각화 컴포넌트 작성 규칙
- 3D·차트·토폴로지 컴포넌트는 `pages/{도메인}/` 하위에 위치 (범용 아님)
- 데이터 로직(API 호출, 가공)과 렌더링 컴포넌트 분리
- Canvas/ECharts 옵션 객체는 컴포넌트 파일 상단 또는 별도 `options.ts`로 분리

---

## 컴포넌트 파일 규칙

- 파일명: PascalCase (`AlarmBadge.tsx`, `KpiCard.tsx`)
- 1파일 1컴포넌트 원칙 (작은 내부 헬퍼 컴포넌트는 같은 파일 허용)
- props 타입은 파일 내 `interface Props` 로 정의 (export 불필요 시 비공개 유지)
- 스타일 전용 상수(`cva`, 색상 맵 등)는 컴포넌트 파일 상단에 정의
