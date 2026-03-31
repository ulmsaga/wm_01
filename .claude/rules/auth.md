# 로그인 프로세스 상세 문서

> **Stack**: Spring Boot 3.x (백엔드) + React 18 (프론트엔드) + MySQL
> **작성일**: 2026-03-18

---

## 목차

1. [전체 흐름 개요](#1-전체-흐름-개요)
2. [사전 단계: RSA 공개키 발급](#2-사전-단계-rsa-공개키-발급)
3. [1차 인증 (비밀번호 검증)](#3-1차-인증-비밀번호-검증)
4. [중복 로그인 처리 (1차 인증 내)](#4-중복-로그인-처리-1차-인증-내)
5. [2차 인증 (OTP)](#5-2차-인증-otp)
6. [JWT 발급 및 SSE 연결](#6-jwt-발급-및-sse-연결)
7. [세션 유지: Refresh Token 로테이션](#7-세션-유지-refresh-token-로테이션)
8. [중복 로그인 강제 로그아웃 흐름](#8-중복-로그인-강제-로그아웃-흐름)
9. [로그아웃](#9-로그아웃)
10. [DB 테이블 역할 요약](#10-db-테이블-역할-요약)
11. [에러 코드 정리](#11-에러-코드-정리)

---

## 1. 전체 흐름 개요

```
[앱 시작]
   │
   ▼
GET /auth/me  ──────────────────────────────────────────────────────────────────────────────────────────────
   │  (HttpOnly 쿠키의 AT로 세션 복원)                                                                      │
   ├─ 유효한 AT 존재 ──► 사용자 상태 복원 → SSE 연결 → [메인 화면]                                         │
   └─ AT 없음/만료    ──► [로그인 화면]                                                                      │
                                                                                                             │
[로그인 화면]                                                                                                │
   │                                                                                                         │
   ▼                                                                                                         │
GET /auth/rsa-public-key  (RSA 공개키 발급, TTL 5분)                                                        │
   │                                                                                                         │
   ▼                                                                                                         │
[ID/PW 입력] → Web Crypto API로 RSA 암호화                                                                  │
   │                                                                                                         │
   ▼                                                                                                         │
POST /auth/login  (1차 인증)                                                                                 │
   │                                                                                                         │
   ├─ require_second_auth = N                                                                                │
   │     └─ allow_duplicate_login = N 이고 기존 세션 있음                                                   │
   │           ├─ forceLogin = false ──► requireDuplicateConfirm: true 반환 → [중복확인 다이얼로그]          │
   │           │                               │                                                             │
   │           │                               └─ 확인(forceLogin=true 재요청)                              │
   │           │                                       │                                                     │
   │           └─ forceLogin = true  ──► 기존 RT 전체 revoke + SSE SESSION_INVALIDATED push                 │
   │                 └─────────────────────────────────────────────────────────────────────────────────────►│
   │                                                                                                         │
   │   → JWT(AT+RT) 발급 → 쿠키 Set → SSE 연결 → [메인 화면]                                               │
   │                                                                                                         │
   └─ require_second_auth = Y                                                                                │
         └─ OTP 생성·발송(이메일/SMS) → otpSeq 반환 → [OTP 입력 화면]                                      │
               │                                                                                             │
               ▼                                                                                             │
         POST /auth/verify-otp  (2차 인증)                                                                  │
               │                                                                                             │
               └─ OTP 검증 성공                                                                              │
                     └─ allow_duplicate_login = N 이면 기존 RT 전체 revoke + SESSION_INVALIDATED push ──────►│
                           └─ JWT(AT+RT) 발급 → 쿠키 Set → SSE 연결 → [메인 화면]
```

---

## 2. 사전 단계: RSA 공개키 발급

```
Frontend                                    Backend (AuthController)
   │                                              │
   │── GET /api/auth/rsa-public-key ─────────────►│
   │                                              │
   │                                    RsaKeyUtil.generateKeyPair()
   │                                       - RSA-2048 키쌍 생성
   │                                       - keyId(UUID) 생성
   │                                       - Caffeine 캐시에 저장 (TTL: 5분)
   │                                              │
   │◄── { keyId, publicKey(Base64) } ────────────│
   │                                              │
   │  Web Crypto API (브라우저)                    │
   │  - SubtleCrypto.importKey(RSA-OAEP)          │
   │  - SubtleCrypto.encrypt({loginId, password}) │
   │  → encryptedCredentials(Base64)              │
   │                                              │
```

**포인트**
- 공개키는 로그인 화면 진입 시 1회 발급
- `keyId` + `encryptedCredentials`를 묶어 로그인 요청으로 전송
- 캐시 TTL(5분) 초과 시 `INVALID_CREDENTIALS_FORMAT` 에러 → 공개키 재발급 필요

---

## 3. 1차 인증 (비밀번호 검증)

```
Frontend                         Backend (AuthController → AuthServiceImpl)
   │                                          │
   │── POST /api/auth/login ─────────────────►│
   │   { keyId, encryptedCredentials,         │
   │     forceLogin: false }                  │
   │                                          │
   │                              ┌─────────────────────────────────────┐
   │                              │ 1. RSA 복호화                        │
   │                              │    rsaKeyUtil.decrypt(keyId, enc)   │
   │                              │    → { loginId, password } JSON     │
   │                              │    ※ keyId 없거나 만료 → INVALID    │
   │                              └─────────────────────────────────────┘
   │                                          │
   │                              ┌─────────────────────────────────────┐
   │                              │ 2. 사용자 조회                       │
   │                              │    wm_user_auth JOIN wm_user        │
   │                              │    WHERE auth_type='LOCAL'          │
   │                              │      AND auth_identifier=loginId    │
   │                              │    ※ 없으면 → LOGIN_FAILED          │
   │                              └─────────────────────────────────────┘
   │                                          │
   │                              ┌─────────────────────────────────────┐
   │                              │ 3. 계정/인증수단 상태 확인            │
   │                              │    wm_user.user_status = 'ACTIVE'   │
   │                              │    ※ 아니면 → ACCOUNT_INACTIVE      │
   │                              │    wm_user_auth.auth_status='ACTIVE'│
   │                              │    ※ 아니면 → AUTH_LOCKED           │
   │                              └─────────────────────────────────────┘
   │                                          │
   │                              ┌─────────────────────────────────────┐
   │                              │ 4. 비밀번호 검증                     │
   │                              │    BCrypt.matches(pw, hash)         │
   │                              │    ※ 불일치 → failed_login_count++  │
   │                              │             → LOGIN_FAILED          │
   │                              └─────────────────────────────────────┘
   │                                          │
   │                              ┌─────────────────────────────────────┐
   │                              │ 5. 로그인 성공 기록                  │
   │                              │    failed_login_count = 0           │
   │                              │    last_login_at = NOW()            │
   │                              └─────────────────────────────────────┘
   │                                          │
   │                              ┌─────────────────────────────────────┐
   │                              │ 6. 이후 분기                         │
   │                              │    ┌─ require_second_auth = Y       │
   │                              │    │   → [2차 인증 흐름] (섹션 5)    │
   │                              │    │                                 │
   │                              │    └─ require_second_auth = N       │
   │                              │        → [중복 로그인 처리] (섹션 4) │
   │                              │        → [JWT 발급] (섹션 6)        │
   │                              └─────────────────────────────────────┘
```

---

## 4. 중복 로그인 처리 (1차 인증 내)

> `wm_user.allow_duplicate_login = 'N'` 인 사용자에게만 적용

```
AuthServiceImpl.login()
        │
        │  allow_duplicate_login = 'N' ?
        ├─ N (Y) ──► 중복 처리 없이 바로 진행
        │
        └─ Y (N)
              │
              │  기존 활성 세션 존재?
              │  (wm_refresh_token WHERE user_seq=? AND revoked_at IS NULL AND expires_at > NOW())
              │
              ├─ 없음 ──► 최초 로그인 → 바로 JWT 발급
              │
              └─ 있음
                    │
                    │  request.forceLogin = true ?
                    │
                    ├─ false ──────────────────────────────────────────────────────────────────►
                    │          응답: { requireDuplicateConfirm: true,                           │
                    │                  message: "현재 다른 기기에서..." }                        │
                    │                                                                            ▼
                    │                                                               [프론트: 다이얼로그 표시]
                    │                                                                  "기존 세션 종료 후
                    │                                                                   로그인하시겠습니까?"
                    │                                                                      │       │
                    │                                                                    취소    확인
                    │                                                                      │       │
                    │                                                                   [취소]    POST /auth/login
                    │                                                                            { forceLogin: true }
                    │                                                                                   │
                    └─ true ◄──────────────────────────────────────────────────────────────────────────┘
                          │
                          ▼
                    doKickIfDuplicateNotAllowed(userSeq)
                       ├─ refreshTokenService.revokeAllSessions(userSeq)
                       │    → UPDATE wm_refresh_token SET revoked_at=NOW()
                       │      WHERE user_seq=? AND revoked_at IS NULL
                       │
                       ├─ sseSessionRegistry.markRevoked(userId)
                       │    → Caffeine 캐시에 revokedAt 기록 (TTL: 10분)
                       │
                       └─ sseSessionRegistry.send(userId, "SESSION_INVALIDATED", "duplicate_login")
                            → 기존 브라우저의 SSE emitter에 이벤트 push
                            → [기존 브라우저: 강제 로그아웃] (섹션 8)
                                   │
                                   ▼
                          신규 브라우저: JWT 발급 → 로그인 성공
```

---

## 5. 2차 인증 (OTP)

```
Backend (AuthServiceImpl)               OtpService               Frontend
        │                                    │                       │
        │  require_second_auth = 'Y'         │                       │
        │                                    │                       │
        │── createAndSendOtp(userSeq) ──────►│                       │
        │                                    │  OTP 6자리 생성        │
        │                                    │  wm_otp_verification  │
        │                                    │  INSERT (ttl: 5분)    │
        │                                    │                       │
        │                                    │  이메일 우선, 없으면 SMS│
        │                                    │  JavaMailSender 발송   │
        │                                    │  (SMS는 현재 log만)    │
        │◄── otpSeq ────────────────────────│                       │
        │                                    │                       │
        │── { requireSecondAuth: true,       │                       │
        │    otpSeq,                         │                       │
        │    sendType: "EMAIL" | "SMS",      │                       │
        │    sendTarget: "ab***@domain.com"} │                       │
        │                                    ──────────────────────►│
        │                                                            │
        │                                                   [OTP 입력 화면]
        │                                                   6자리 숫자 입력
        │                                                            │
        │◄───────────── POST /api/auth/verify-otp ─────────────────│
        │               { otpSeq, otpCode }                          │
        │                                                            │
        │  OtpService.verifyOtp(otpSeq, otpCode)                    │
        │  ┌──────────────────────────────────────────────────────┐  │
        │  │ 1. wm_otp_verification 조회 (otpSeq로)              │  │
        │  │    - 없으면 → INVALID_OTP                           │  │
        │  │    - verified_yn = 'Y' → 이미 사용됨 INVALID_OTP   │  │
        │  │    - expire_at < NOW() → OTP_EXPIRED               │  │
        │  │                                                      │  │
        │  │ 2. OTP 코드 일치 여부 확인                          │  │
        │  │    - 불일치 → fail_count++                          │  │
        │  │              fail_count >= max_fail_count           │  │
        │  │              → INVALID_OTP (만료 처리)              │  │
        │  │    - 일치 → verified_yn = 'Y' 업데이트             │  │
        │  │                                                      │  │
        │  │ @Transactional(noRollbackFor = WmException.class)  │  │
        │  │ → OTP 실패 카운트 롤백 방지                         │  │
        │  └──────────────────────────────────────────────────────┘  │
        │                                                            │
        │  검증 성공 → userSeq 반환                                  │
        │                                                            │
        │  kickIfDuplicateNotAllowed(userSeq)  ← [섹션 4와 동일]     │
        │                                                            │
        │  JWT 발급 → 쿠키 Set → [섹션 6]                           │
```

**OTP 발송 채널 우선순위**

| 우선순위 | 조건 | 발송 방법 |
|--------|------|---------|
| 1순위 | `wm_user.email` 존재 | JavaMailSender (SMTP) |
| 2순위 | `wm_user.phone` 존재 | SMS (현재 log만 출력, 미구현) |
| 오류 | 둘 다 없음 | `NO_SECOND_AUTH_TARGET` 에러 |

---

## 6. JWT 발급 및 SSE 연결

```
Backend (RefreshTokenServiceImpl)                         Frontend (AuthContext)
        │                                                       │
        │  jwtProvider.issueTokenPair(userSeq)                  │
        │  ┌──────────────────────────────────────────┐         │
        │  │ Access Token (AT)                        │         │
        │  │   - alg: HS256                           │         │
        │  │   - sub: userSeq                         │         │
        │  │   - typ: access                          │         │
        │  │   - iat: 발급시각                         │         │
        │  │   - exp: 발급 + 5분                      │         │
        │  │   - jti: UUID                            │         │
        │  │                                          │         │
        │  │ Refresh Token (RT)                       │         │
        │  │   - alg: HS256                           │         │
        │  │   - sub: userSeq                         │         │
        │  │   - typ: refresh                         │         │
        │  │   - iat: 발급시각                         │         │
        │  │   - exp: 발급 + 30일                     │         │
        │  │   - jti: UUID                            │         │
        │  └──────────────────────────────────────────┘         │
        │                                                       │
        │  wm_refresh_token INSERT                              │
        │  { jti_hash: SHA-256(rt.jti),                        │
        │    user_seq, issued_at, expires_at,                   │
        │    user_agent, ip_addr }                              │
        │                                                       │
        │  HTTP Response                                        │
        │  Set-Cookie: wm_at=<AT>; HttpOnly; Secure; SameSite=Strict; Max-Age=300
        │  Set-Cookie: wm_rt=<RT>; HttpOnly; Secure; SameSite=Strict; Max-Age=2592000
        │                                                       │
        │── { userSeq, userId, userName } ────────────────────►│
        │   (or { authenticated, ... } from /auth/me)           │
        │                                                       │
        │                                              loginUser(userData) 호출
        │                                                setUser(userData)
        │                                                       │
        │                                              connectSse(onInvalidated, onError)
        │◄── GET /api/sse/session ──────────────────────────────│
        │    (Cookie: wm_at=<AT>)                               │
        │                                                       │
        │  JwtCookieAuthFilter: AT 검증 → claims를 details에 저장
        │                                                       │
        │  SseController.session()                              │
        │  ┌─────────────────────────────────────────────────┐  │
        │  │ 1. isRevokedAfter(userId, at.iat) 체크          │  │
        │  │    → true: SESSION_INVALIDATED 즉시 전송 후 종료│  │
        │  │    → false: 정상 등록                           │  │
        │  │                                                  │  │
        │  │ 2. SseSessionRegistry.register(userId, emitter) │  │
        │  │                                                  │  │
        │  │ 3. CONNECTED 이벤트 전송                        │  │
        │  └─────────────────────────────────────────────────┘  │
        │                                                       │
        │── event: CONNECTED, data: ok ───────────────────────►│
        │                                                       │
        │  (이후 25초마다 heartbeat 전송, proxy timeout 방지)   │
        │                                                       │
        │                                              [메인 화면 진입]
```

---

## 7. 세션 유지: Refresh Token 로테이션

```
Frontend (axiosInstance 인터셉터)                Backend
        │                                           │
        │  API 요청 (AT 만료 5분 후)                │
        │── GET /api/some/protected ───────────────►│
        │   (Cookie: wm_at=만료된AT)                │
        │                                 JwtCookieAuthFilter
        │                                 AT 검증 실패 → 401
        │◄── 401 Unauthorized ──────────────────────│
        │                                           │
        │  인터셉터: /auth/refresh 시도             │
        │── POST /api/auth/refresh ────────────────►│
        │   (Cookie: wm_rt=<RT>)                    │
        │                                 RefreshTokenServiceImpl.refreshRotate()
        │                                 ┌──────────────────────────────────────────┐
        │                                 │ 1. RT JWT 파싱 및 검증                    │
        │                                 │    - 만료 → TOKEN_EXPIRED (401)          │
        │                                 │    - 형식 오류 → TOKEN_INVALID (401)     │
        │                                 │                                          │
        │                                 │ 2. jti_hash로 DB 조회                    │
        │                                 │    - 없으면 → "not recognized" (401)     │
        │                                 │                                          │
        │                                 │ 3. revoked_at IS NOT NULL ?              │
        │                                 │    → Reuse Detection!                   │
        │                                 │      revokeAllByUserSeq() 전체 무효화    │
        │                                 │      → 401 (모든 세션 강제 로그아웃)     │
        │                                 │                                          │
        │                                 │ 4. 로테이션                              │
        │                                 │    기존 RT: replaced_by=새jtiHash,       │
        │                                 │             revoked_at=NOW() 업데이트    │
        │                                 │    신규 RT: wm_refresh_token INSERT      │
        │                                 │    신규 AT 발급                          │
        │                                 └──────────────────────────────────────────┘
        │                                           │
        │◄── 200 OK ────────────────────────────────│
        │    Set-Cookie: wm_at=<신규AT>; Max-Age=300
        │    Set-Cookie: wm_rt=<신규RT>; Max-Age=2592000
        │                                           │
        │  원래 실패했던 요청 재시도               │
        │── GET /api/some/protected ───────────────►│
        │◄── 200 OK ────────────────────────────────│

[동시 다중 요청 처리]
  - isRefreshing 플래그 + 대기 큐(failedQueue)로 중복 refresh 방지
  - 첫 번째 401 요청이 refresh 수행하는 동안 나머지는 큐에서 대기
  - refresh 성공 시 큐의 모든 요청 재시도
  - refresh 실패 시 큐 전체 reject → /login 리다이렉트
```

---

## 8. 중복 로그인 강제 로그아웃 흐름

> 신규 기기에서 로그인(forceLogin=true 또는 2FA 완료) → 기존 기기 강제 로그아웃

```
기존 브라우저 (피해자)                  서버                    신규 브라우저 (공격자?)
        │                                │                              │
        │  SSE 연결 유지 중              │                              │
        │◄── heartbeat (25초마다) ───────│                              │
        │                                │                              │
        │                                │◄── POST /auth/login ────────│
        │                                │    { forceLogin: true }      │
        │                                │                              │
        │                    doKickIfDuplicateNotAllowed()              │
        │                    revokeAllSessions(userSeq)                 │
        │                    markRevoked(userId)  ← Caffeine 캐시       │
        │                                │                              │
        │                    sseRegistry.send(SESSION_INVALIDATED)     │
        │◄── event: SESSION_INVALIDATED ─│                              │
        │    data: "duplicate_login"     │                              │
        │                                │                              │
        │  sseService.js:                │                              │
        │  eventSource.close()           │                              │
        │  AuthContext.handleSessionInvalidated()                        │
        │    disconnectSse()             │                              │
        │    setLoginAlert("다른 기기에서 로그인하여...")               │
        │    setUser(null)               │                              │
        │    → ProtectedRoute            │                              │
        │      <Navigate to="/login" />  │                              │
        │                                │                              │
        │  [로그인 화면]                 │                              │
        │  loginAlert 메시지 표시        │                              │
        │                                │      JWT 발급 완료           │
        │                                │◄─────────────────────────────│
        │                                │── Set-Cookie: wm_at, wm_rt ─►│
        │                                │                              │
        │                                │              SSE 연결 ──────►│
        │                                │◄── GET /api/sse/session ─────│
        │                                │    isRevokedAfter 체크       │
        │                                │    → 신규 AT는 revokedAt 이후 발급
        │                                │    → false (정상 연결)       │
        │                                │── event: CONNECTED ─────────►│

[SSE 연결이 이미 끊긴 경우 - 3중 안전망]

  안전망 1: SSE emitter 살아있을 때 → SESSION_INVALIDATED push (위 흐름)

  안전망 2: SSE 재연결 시 revoke 체크
    기존 브라우저의 AT로 /api/sse/session 재연결 시도
    → SseController.isRevokedAfter(userId, at.iat)
    → revokedAt > at.iat → SESSION_INVALIDATED 즉시 전송 후 complete()

  안전망 3: AT 만료 (5분)
    AT 만료 → /api/sse/session 401
    → sseService.onerror: CLOSED 상태 → handleSseError() 호출
    → getMe() 시도 → RT도 revoked → 401
    → axiosInstance 인터셉터: /auth/refresh 시도
    → refresh RT도 revoked → 실패 → /login 리다이렉트
```

---

## 9. 로그아웃

```
Frontend                                    Backend
   │                                           │
   │  logoutUser() 호출                        │
   │  disconnectSse()  ← SSE 연결 먼저 종료    │
   │  setUser(null)                            │
   │                                           │
   │── POST /api/auth/logout ─────────────────►│
   │   (Cookie: wm_rt=<RT>)                    │
   │                                RefreshTokenServiceImpl.logout()
   │                                  RT JWT 파싱
   │                                  jti_hash로 wm_refresh_token
   │                                  UPDATE SET revoked_at=NOW()
   │                                           │
   │◄── 200 OK ────────────────────────────────│
   │    Set-Cookie: wm_at=; Max-Age=0 (삭제)
   │    Set-Cookie: wm_rt=; Max-Age=0 (삭제)
   │                                           │
   │  ProtectedRoute → /login 리다이렉트       │

※ RT가 null이거나 만료여도 정상 처리 (서버 에러 없음)
※ 로그아웃 시 AT는 DB 무효화 없음 (만료 5분 이내 자연 소멸)
```

---

## 10. DB 테이블 역할 요약

| 테이블 | 역할 | 주요 컬럼 |
|-------|------|---------|
| `wm_user` | 사용자 기본 정보 | `user_status`, `require_second_auth`, `allow_duplicate_login`, `last_login_at` |
| `wm_user_auth` | 인증 수단 (LOCAL/OAuth) | `auth_type`, `auth_identifier`, `password_hash`, `auth_status`, `failed_login_count`, `locked_at` |
| `wm_refresh_token` | JWT RT 관리 | `jti_hash(SHA-256)`, `revoked_at`, `replaced_by`, `user_agent`, `ip_addr`, `expires_at` |
| `wm_otp_verification` | OTP 관리 | `otp_code`, `expire_at`, `verified_yn`, `fail_count`, `max_fail_count` |
| `app_event_log` | 이벤트 로그 | `event_type`, `message`, `payload_json` |

---

## 11. 에러 코드 정리

| ErrorCode | HTTP | 발생 시점 |
|-----------|------|---------|
| `INVALID_CREDENTIALS_FORMAT` | 400 | RSA keyId 만료 또는 복호화 실패 |
| `LOGIN_FAILED` | 401 | 사용자 없음 또는 비밀번호 불일치 |
| `ACCOUNT_INACTIVE` | 401 | `wm_user.user_status != 'ACTIVE'` |
| `AUTH_LOCKED` | 401 | `wm_user_auth.auth_status != 'ACTIVE'` (실패 횟수 초과 등) |
| `NO_SECOND_AUTH_TARGET` | 400 | 이메일/폰 모두 없어 OTP 발송 불가 |
| `INVALID_OTP` | 401 | OTP 코드 불일치 또는 만료 |
| `OTP_EXPIRED` | 401 | OTP TTL 초과 |
| `TOKEN_EXPIRED` | 401 | RT 만료 |
| `TOKEN_INVALID` | 401 | RT 파싱 실패 또는 reuse detection |
| `UNAUTHORIZED` | 401 | 인증 없이 보호 리소스 접근 |

---

## 보안 설계 포인트 요약

| 항목 | 방식 | 이유 |
|-----|------|------|
| 비밀번호 전송 | RSA-OAEP 암호화 (Web Crypto API) | HTTPS 외 추가 암호화 계층 |
| 비밀번호 저장 | BCrypt 해시 | Rainbow table 방어 |
| JWT 전달 | HttpOnly + Secure + SameSite=Strict 쿠키 | XSS로 토큰 탈취 불가 |
| RT 저장 | SHA-256(jti) 해시만 DB 저장 | DB 유출 시 RT 원본 보호 |
| RT 재사용 방어 | Reuse Detection → 전체 세션 revoke | 탈취 토큰 재사용 즉시 무력화 |
| 중복 로그인 | SSE push + Caffeine revoke 캐시 (3중 안전망) | 실시간 강제 로그아웃 |
| SSE 유지 | 25초 heartbeat | Nginx/ALB proxy timeout 방지 |
| OTP 실패 | `@Transactional(noRollbackFor)` | 실패 카운트 롤백 버그 방지 |
