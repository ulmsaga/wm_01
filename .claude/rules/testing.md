# TDD 워크플로우

## 기본 사이클 (Red → Green → Refactor)
```
1. Red    : 실패하는 테스트 먼저 작성 (구현 없이 컴파일만 통과)
2. Green  : 테스트를 통과하는 최소 구현 작성
3. Refactor: 테스트가 통과된 상태에서 코드 정리
```
> 구현 코드 수정 전 반드시 테스트 파일을 먼저 생성한다.

## 테스트 분류 및 위치
| 종류 | 어노테이션 | 위치 | 특징 |
|------|-----------|------|------|
| 단위 테스트 | 없음 (순수 JUnit5) | `test/.../module/{모듈명}/` | DB·Spring 컨텍스트 불필요, 빠름 |
| 슬라이스 테스트 | `@WebMvcTest` | `test/.../module/{모듈명}/controller/` | MockMvc, Security 포함 |
| 통합 테스트 | `@SpringBootTest` | `test/.../integration/` | 전체 컨텍스트, DB 필요 |

## 테스트 명명 규칙
- 파일: `{대상클래스}Test.java`
- 메서드: `{상황}_{기대결과}` (예: `expiredToken_throwsExpiredJwtException`)
- `@DisplayName`: 한국어 서술형 (예: `"만료 토큰 → ExpiredJwtException"`)
- `@Nested` 클래스로 메서드(기능) 단위 그룹핑

## 테스트 작성 원칙
- Spring 컨텍스트 없이 테스트 가능한 클래스는 순수 JUnit5로 작성 (`JwtProvider`, `RsaKeyStore` 등)
- AssertJ(`assertThat`, `assertThatThrownBy`, `assertThatCode`) 사용 — JUnit5 기본 assert 혼용 금지
- Given / When / Then 구조 준수
- 외부 의존성(DB, SMTP) → Mockito `@Mock` / `@InjectMocks` 처리

## 커버리지 목표
- **라인 커버리지 90% 이상** (모듈 단위)
- 측정: `mvn test jacoco:report` → `target/site/jacoco/index.html`
