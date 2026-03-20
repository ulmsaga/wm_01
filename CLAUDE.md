# CLAUDE.md

## 프로젝트 개요

`weekly_marking` — 주간 채점 관리 시스템.
RSA 암호화 → BCrypt 검증 → OTP 2차 인증 → JWT (HttpOnly 쿠키) 방식의 2단계 인증 구조.

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
| TypeScript | - |
| Vite | 8.0.0 |
| Tailwind CSS | 4.2.1 |
| Axios | 1.13.6 |
| Radix UI | 1.4.3 |
| React Router DOM | 7.13.1 |

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
│       │   ├── common/          # 범용 컴포넌트 (신규 작성 기준)
│       │   ├── layout/
│       │   └── ui/              # Radix UI 래퍼
│       ├── context/             # AuthContext, ThemeContext
│       ├── pages/
│       └── routes/              # AppRouter, ProtectedRoute
│
└── docs/
    └── auth-login-process.md    # 인증 전체 흐름 순서도
```

---

## 아키텍처 핵심 결정사항

### 인증 흐름
```
GET  /auth/rsa-public-key        → RSA 공개키 발급 (Caffeine TTL 5분)
POST /auth/login                 → 1차 인증 (RSA 암호화 credentials → BCrypt 검증)
POST /auth/verify-otp            → 2차 인증 (이메일 OTP)
                                   중복 로그인: forceLogin 플래그 → 기존 RT revoke + SSE SESSION_INVALIDATED push
                                   → JWT 발급: AT 5분(로컬)/30분(운영), RT 30일, HttpOnly 쿠키 Set
GET  /api/sse/session            → SSE 연결 (heartbeat 25초, 세션 무효화 감지)
POST /auth/refresh               → AT 만료 시 자동 갱신 (axiosInstance 401 인터셉터)
POST /auth/logout                → RT revoke + 쿠키 삭제
```

### API 응답 포맷 (ApiResponse<T> record)
```json
// 성공
{ "success": true, "data": { ... } }

// 실패
{ "success": false, "code": "TOKEN_EXPIRED", "message": "토큰이 만료되었습니다." }
```
- null 필드는 자동 제외 (`jackson.default-property-inclusion: non_null`)

---

## 개발 규칙

**Test 우선**: 테스트 커버리지 90% 이상유지

### [Backend] 어기면 버그나는 것

**1. MyBatis `mapUnderscoreToCamelCase: false`**
- DB 컬럼 → Java 필드 자동 변환 **없음**
- Mapper XML에서 `resultMap` alias를 직접 지정해야 함

**2. 에러 메시지 하드코딩 금지**
- 모든 에러 메시지는 `i18n/messages.properties`에 키로 등록
- 키 규칙: `error.{도메인}.{상세}` (예: `error.otp.mismatch`)
- 예외 발생: `throw new NttpocException(ErrorCode.OTP_MISMATCH)` — 메시지는 `GlobalExceptionHandler`가 `MessageSource`로 처리

**3. JWT는 HttpOnly 쿠키로만 전달**
- 로컬스토리지/sessionStorage 저장 절대 금지 (XSS 방지 의도적 결정)
- 쿠키 설정: `Secure=true`(운영), `SameSite=Lax`, `HttpOnly`

**4. 예외는 반드시 NttpocException으로**
- `throw new NttpocException(ErrorCode.XXX)` 또는 `new NttpocException(ErrorCode.XXX, args...)`
- `ResponseEntity` 직접 반환하거나 별도 예외 클래스 생성 금지
- `GlobalExceptionHandler`가 일괄 처리

### [Frontend] 어기면 버그나는 것

**5. HTTP 요청은 반드시 axiosInstance 사용**
- `withCredentials: true` 및 401 자동 refresh 인터셉터 포함
- 일반 `axios` 직접 사용 금지

**6. 신규 컴포넌트는 `common/` 기준으로 작성**
- `ui/` (Radix 래퍼)와 `common/` 혼용 중 — 신규 작성 시 `common/` 우선

---

## 개발 워크플로우 (증강 코딩 + TDD)

### 캔트 백의 증강 코딩 원칙
- **증강 코딩 vs 바이브 코딩**: 코드 품질, 테스트, 단순성을 중시하되 AI와 협업
- **중간 결과 관찰**: AI가 반복 동작, 요청하지 않은 기능 구현, 테스트 삭제 등의 신호를 보이면 즉시 개입
- **설계 주도권 유지**: AI가 너무 앞서가지 않도록 개발자가 설계 방향 제시

### 로컬 개발 환경 실행 순서
```bash
# 1. MySQL (Docker 또는 로컬, 포트 3307)
# 2. Mailpit 실행 (OTP 이메일 수신 확인용)
#    http://localhost:8025 — SMTP 포트 1025

# 3. Backend 실행
cd nttpoc_backend
mvn spring-boot:run          # 포트 8080, profile: local 자동 적용

# 4. Frontend 실행
cd nttpoc_frontend
npm run dev                  # 포트 5173
```

### 환경변수 (local 실행 필수)
```
NTTPOC_DB1_PASSWORD=...           # DB 비밀번호
NTTPOC_JWT_SECRET=...             # 최소 32바이트 Base64 인코딩 값
```

### 브랜치 전략
```
main      ← 배포 기준
feature/* ← 기능 개발
fix/*     ← 버그 수정
```

### 새 모듈 추가 절차
```
1. nttpoc_backend: module/{모듈명}/ 아래 controller / service / dao 구조 생성
2. Mapper XML: resources/mapper/mybatis/mysql/{모듈명}/ 에 추가
3. ErrorCode: 필요한 에러 코드 추가 → messages.properties에 키 등록
4. nttpoc_frontend: pages/{모듈명}/ + api/{모듈명}Api.ts 추가
5. AppRouter에 라우트 등록 (인증 필요 시 ProtectedRoute로 감싸기)
```

### 인증 관련 코드 수정 시 체크리스트
- [ ] `SecurityConfig` 변경 영향 확인 (permit/authenticated 경로)
- [ ] `JwtCookieAuthFilter` 필터 순서 확인
- [ ] 로컬 테스트: `node nttpoc_backend/http/test-login.mjs` (RSA 암호화 자동화 로그인)
- [ ] OTP 수신: Mailpit (`http://localhost:8025`) 확인

### TDD 워크플로우

#### 기본 사이클 (Red → Green → Refactor)
```
1. Red    : 실패하는 테스트 먼저 작성 (구현 없이 컴파일만 통과)
2. Green  : 테스트를 통과하는 최소 구현 작성
3. Refactor: 테스트가 통과된 상태에서 코드 정리
```
> 구현 코드 수정 전 반드시 테스트 파일을 먼저 생성한다.

#### 테스트 분류 및 위치
| 종류 | 어노테이션 | 위치 | 특징 |
|------|-----------|------|------|
| 단위 테스트 | 없음 (순수 JUnit5) | `test/.../module/{모듈명}/` | DB·Spring 컨텍스트 불필요, 빠름 |
| 슬라이스 테스트 | `@WebMvcTest` | `test/.../module/{모듈명}/controller/` | MockMvc, Security 포함 |
| 통합 테스트 | `@SpringBootTest` | `test/.../integration/` | 전체 컨텍스트, DB 필요 |

#### 테스트 명명 규칙
- 파일: `{대상클래스}Test.java`
- 메서드: `{상황}_{기대결과}` (예: `expiredToken_throwsExpiredJwtException`)
- `@DisplayName`: 한국어 서술형 (예: `"만료 토큰 → ExpiredJwtException"`)
- `@Nested` 클래스로 메서드(기능) 단위 그룹핑

#### 테스트 작성 원칙
- Spring 컨텍스트 없이 테스트 가능한 클래스는 순수 JUnit5로 작성 (`JwtProvider`, `RsaKeyStore` 등)
- AssertJ(`assertThat`, `assertThatThrownBy`, `assertThatCode`) 사용 — JUnit5 기본 assert 혼용 금지
- Given / When / Then 구조 준수
- 외부 의존성(DB, SMTP) → Mockito `@Mock` / `@InjectMocks` 처리

#### 커버리지 목표
- **라인 커버리지 90% 이상** (모듈 단위)
- 측정: `mvn test jacoco:report` → `target/site/jacoco/index.html`

---

### 빌드 및 기타 명령어
```bash
# Backend
mvn clean compile
mvn test
mvn test jacoco:report   # 커버리지 리포트 생성

# Frontend
npm run lint
npm run build
npm run preview
```
