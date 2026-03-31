# CLAUDE.md

## 프로젝트 개요

`NTT POC` - EPC 통신 장비 품질 모니터링 및 분석, APP 품질 모니터링 및 분석
**로그인 및 인증 방식**: RSA 암호화 → BCrypt 검증 → OTP 2차 인증 → JWT (HttpOnly 쿠키) 방식의 2단계 인증 구조.

> 미완성 항목 및 작업 현황은 memory를 참조할 것.

---

## 기술 스택

### Backend
| 항목 | 버전 |
|------|------|
| Spring Boot | 4.0.3 |
| Java | 17 |
| MyBatis Spring Boot Starter | 4.0.1 |
| JJWT | 0.11.5 |
| P6Spy | 3.9.1 |
| MySQL Connector/J | Spring Boot managed |
| Caffeine | Spring Boot managed |
| Build | Maven |

### Frontend
| 항목 | 버전 |
|------|------|
| React | 19.2.4 |
| TypeScript | 5.8.3 |
| Vite | 8.0.0 |
| Tailwind CSS | 4.2.1 |
| Axios | 1.13.6 |
| Radix UI | 1.4.3 |
| React Router DOM | 7.13.1 |
| i18next | 25.8.20 |
| react-i18next | 16.5.8 |
| Three.js | 0.183.2 |
| @react-three/fiber | 9.5.0 |
| @react-three/drei | 10.7.7 |
| @react-three/postprocessing | 3.0.4 |
| @xyflow/react | 12.10.1 |
| ECharts | 6.0.0 |
| echarts-for-react | 3.0.6 |
| lucide-react | 0.577.0 |
| clsx / tailwind-merge | 2.1.1 / 3.5.0 |

---

## 디렉토리 구조

```
source/
├── nttpoc_backend/
│   ├── src/main/java/com/mobigen/aiop/nttpoc/
│   │   ├── core/                # 공용: ApiResponse, ErrorCode, NttpocException, GlobalExceptionHandler
│   │   └── module/              # 기능별 모듈
│   │       └── {모듈명}/
│   │           ├── controller/
│   │           ├── service/     # interface + impl/
│   │           └── dao/         # interface + impl/
│   └── src/main/resources/
│       ├── config/              # application.yml, application-local.yml, application-prod.yml
│       ├── i18n/                # messages.properties (ko 기본, _en, _ja)
│       ├── logging/             # logback 설정
│       └── mapper/mybatis/mysql/{모듈명}/  # SQL Mapper XML
│
├── nttpoc_frontend/
│   └── src/
│       ├── api/                 # axiosInstance.ts, authApi.ts, sseService.ts
│       ├── types/               # index.ts — 공용 타입 정의 (User, ApiResponse 등)
│       ├── components/
│       │   ├── common/          # 프로젝트 전용 컴포넌트 (shadcn 조합, 신규 작성 기준)
│       │   ├── layout/
│       │   └── ui/              # shadcn/ui 컴포넌트 (CLI로 추가)
│       ├── context/             # AuthContext, ThemeContext
│       ├── pages/
│       └── routes/              # AppRouter, ProtectedRoute
│
```

## docu 디렉토리 구조 (source/ 외부)
```
docu/
├── design/
│   └── nw/
│       └── nw-digital-twin.md        # NW Digital Twin 화면 설계 문서
└── docker/mysql/initdb/
    ├── 00_grant.sql                  # DB 권한
    ├── 01_schema.sql                 # 전체 스키마 (auth + menu/RBAC)
    ├── 02_data.sql                   # 전체 초기 데이터 (TRUNCATE 포함, 멱등성 보장)
    └── modules/                      # 모듈별 증분 DB 스크립트
        └── nw/
            └── digital-twin/
                ├── README.md         # 변경 이력
                ├── schema.sql        # nw_site / nw_building / nw_room / nw_device
                └── data.sql          # 샘플 데이터
```

---

## 개발 규칙

**Test 우선**: 테스트 커버리지 90% 이상 유지

### [Backend] 어기면 버그나는 것

**1. MyBatis `mapUnderscoreToCamelCase: false`**
- DB 컬럼 → Java 필드 자동 변환 **없음**
- Mapper XML에서 `resultMap` alias를 직접 지정해야 함

**2. 에러 메시지 하드코딩 금지**
- 모든 에러 메시지는 `i18n/messages.properties`에 키로 등록
- 키 규칙: `error.{도메인}.{상세}` (예: `error.otp.mismatch`)
- 예외 발생: `throw new NttpocException(ErrorCode.OTP_MISMATCH)`

**3. JWT는 HttpOnly 쿠키로만 전달**
- 로컬스토리지/sessionStorage 저장 절대 금지 (XSS 방지 의도적 결정)
- 쿠키 설정: `Secure=true`(운영), `SameSite=Lax`, `HttpOnly`

**4. 예외는 반드시 NttpocException으로**
- `throw new NttpocException(ErrorCode.XXX)` 또는 `new NttpocException(ErrorCode.XXX, args...)`
- `ResponseEntity` 직접 반환하거나 별도 예외 클래스 생성 금지

### [Frontend] 어기면 버그나는 것

**5. HTTP 요청은 반드시 axiosInstance 사용**
- `withCredentials: true` 및 401 자동 refresh 인터셉터 포함
- 일반 `axios` 직접 사용 금지

**6. 컴포넌트 계층 구조 준수** (`ui/` → `common/` → `pages/`)
- 페이지에서 raw Tailwind className 나열 금지 — 상세 규칙은 `.claude/rules/ui.md` 참조

---

@.claude/rules/architecture.md
@.claude/rules/auth.md
@.claude/rules/i18n.md
@.claude/rules/testing.md
@.claude/rules/workflow.md
@.claude/rules/ui.md
