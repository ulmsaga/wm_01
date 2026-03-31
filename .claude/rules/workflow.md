# 개발 워크플로우

## 증강 코딩 원칙 (Kent Beck)
- **증강 코딩 vs 바이브 코딩**: 코드 품질, 테스트, 단순성을 중시하되 AI와 협업
- **중간 결과 관찰**: AI가 반복 동작, 요청하지 않은 기능 구현, 테스트 삭제 등의 신호를 보이면 즉시 개입
- **설계 주도권 유지**: AI가 너무 앞서가지 않도록 개발자가 설계 방향 제시

## 로컬 개발 환경 실행 순서
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

## 환경변수 (local 실행 필수)
```
NTTPOC_DB1_PASSWORD=...           # DB 비밀번호
NTTPOC_JWT_SECRET=...             # 최소 32바이트 Base64 인코딩 값
```

## 브랜치 전략
```
main      ← 배포 기준
feature/* ← 기능 개발
fix/*     ← 버그 수정
```

## 새 모듈 추가 절차
```
1. nttpoc_backend: module/{모듈명}/ 아래 controller / service / dao 구조 생성
2. Mapper XML: resources/mapper/mybatis/mysql/{모듈명}/ 에 추가
3. ErrorCode: 필요한 에러 코드 추가 → messages.properties에 키 등록
4. nttpoc_frontend: pages/{모듈명}/ + api/{모듈명}Api.ts 추가
5. AppRouter에 라우트 등록 (인증 필요 시 ProtectedRoute로 감싸기)
```

## 인증 관련 코드 수정 시 체크리스트
- [ ] `SecurityConfig` 변경 영향 확인 (permit/authenticated 경로)
- [ ] `JwtCookieAuthFilter` 필터 순서 확인
- [ ] 로컬 테스트: `node nttpoc_backend/http/test-login.mjs` (RSA 암호화 자동화 로그인)
- [ ] OTP 수신: Mailpit (`http://localhost:8025`) 확인

## 빌드 및 기타 명령어
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
