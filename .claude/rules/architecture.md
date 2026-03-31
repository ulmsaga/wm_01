# 아키텍처 핵심 결정사항

## 인증 흐름
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

## API 응답 포맷 (`ApiResponse<T>` record)
```json
// 성공
{ "success": true, "data": { ... } }

// 실패
{ "success": false, "code": "TOKEN_EXPIRED", "message": "토큰이 만료되었습니다." }
```
- null 필드는 자동 제외 (`jackson.default-property-inclusion: non_null`)

## DB 스크립트 관리 원칙
- `initdb/01_schema.sql`, `02_data.sql` — 전체 재초기화용 (Docker init 시 자동 실행)
- `initdb/modules/{모듈명}/` — 모듈 개발 중 증분 DDL·데이터 관리 (개발자 수동 적용)
- 새 모듈 추가 시 `modules/{도메인}/{기능명}/` 디렉토리 생성 후 README.md 포함

## DB 문서 경로
```
/Users/sclee1115/Project/Dev/nttpoc/docu/docker/mysql/initdb/
```
