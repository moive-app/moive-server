# JWT 인증 구조

## 1. 개요

MOIVE는 카카오 로그인을 통해 사용자를 인증한 후,
서비스 내부 인증을 위해 JWT 기반 Access Token / Refresh Token을 사용한다.

카카오 Access Token은 카카오 사용자 인증에만 사용하며,
MOIVE 서비스 API 인증에는 MOIVE에서 발급한 Access Token을 사용한다.

---

## 2. 전체 인증 흐름

### 신규 회원

```text
카카오 SDK 로그인
    ↓
카카오 Access Token 발급
    ↓
POST /api/auth/kakao
    ↓
카카오 사용자 정보 조회
    ↓
Kakao Member ID로 회원 조회
    ↓
신규 회원 (registered = false)
    ↓
약관 동의
    ↓
POST /api/auth/signup
    ↓
회원 및 약관 동의 정보 저장
    ↓
MOIVE Access Token / Refresh Token 발급
```

### 기존 회원

```text
카카오 SDK 로그인
    ↓
카카오 Access Token 발급
    ↓
POST /api/auth/kakao
    ↓
카카오 사용자 정보 조회
    ↓
Kakao Member ID로 기존 ACTIVE 회원 조회
    ↓
MOIVE Access Token / Refresh Token 발급
```

---

## 3. Token 정책

### Access Token

- MOIVE API 인증에 사용
- 만료 시간: 1시간
- 클라이언트는 인증이 필요한 API 요청 시 HTTP Authorization 헤더에 전달

```http
Authorization: Bearer {accessToken}
```

### Refresh Token

- Access Token 재발급에 사용
- 만료 시간: 14일
- 발급된 Refresh Token은 User에 저장
- 새로운 Refresh Token 발급 시 기존 Refresh Token을 교체
- 로그아웃 시 저장된 Refresh Token 제거

현재 설정:

```yaml
jwt:
  secret: ${JWT_SECRET}
  access-token-expiration: 3600000
  refresh-token-expiration: 1209600000
```

---

## 4. JWT Payload

현재 JWT의 subject에는 MOIVE User ID를 저장한다.

```text
subject = userId
```

JWT 인증 성공 후 해당 userId를 Spring Security 인증 정보의 principal로 사용한다.

---

## 5. Access Token 인증

인증이 필요한 API 요청:

```http
Authorization: Bearer {MOIVE_ACCESS_TOKEN}
```

`JwtAuthenticationFilter`에서 다음 과정을 수행한다.

```text
HTTP Request
    ↓
Authorization Header 확인
    ↓
Bearer Token 추출
    ↓
JWT 유효성 검증
    ↓
userId 추출
    ↓
Authentication 생성
    ↓
SecurityContext에 저장
    ↓
Controller 접근
```

---

## 6. 토큰 재발급

### API

```http
POST /api/auth/reissue
```

Refresh Token을 이용하여 새로운 Access Token과 Refresh Token을 발급한다.

처리 과정:

```text
Refresh Token 수신
    ↓
JWT 유효성 검증
    ↓
JWT에서 userId 추출
    ↓
User 조회
    ↓
DB에 저장된 Refresh Token과 비교
    ↓
Access Token 재발급
    ↓
Refresh Token 재발급
    ↓
새 Refresh Token DB 저장
```

Refresh Token Rotation 방식을 사용하여 재발급할 때 Refresh Token도 함께 교체한다.

---

## 7. 로그아웃

### API

```http
POST /api/auth/logout
```

처리 과정:

```text
Refresh Token 수신
    ↓
JWT 유효성 검증
    ↓
userId 추출
    ↓
User 조회
    ↓
DB Refresh Token과 비교
    ↓
저장된 Refresh Token 제거
```

로그아웃 이후 기존 Refresh Token을 이용한 토큰 재발급은 불가능하다.

단, 이미 발급된 Access Token은 자체 만료 시간까지 유효할 수 있다.

---

## 8. Security 설정

다음 경로는 인증 없이 접근할 수 있다.

```text
/api/auth/**
/swagger-ui/**
/swagger-ui.html
/v3/api-docs/**
/h2-console/**
```

그 외 API는 인증이 필요하다.

Spring Security는 Session을 사용하지 않고 Stateless 방식으로 동작한다.

```text
SessionCreationPolicy.STATELESS
```

---

## 9. JWT Secret 관리

JWT Secret은 코드 또는 Git 저장소에 직접 저장하지 않는다.

`application.yaml`:

```yaml
jwt:
  secret: ${JWT_SECRET}
```

각 개발자는 로컬 환경에 `JWT_SECRET` 환경변수를 설정한다.

실제 JWT Secret 값은 Git에 커밋하지 않는다.

테스트 환경에서는 실제 Secret 대신 테스트 전용 Secret을 사용한다.

---
