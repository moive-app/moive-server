# 카카오 인증 및 회원가입

## 1. 개요

MOIVE는 카카오 로그인을 통해 사용자를 인증합니다.

클라이언트에서 Kakao SDK를 통해 발급받은 Access Token을 백엔드로 전달하면,
백엔드는 카카오 사용자 정보 API를 호출하여 사용자를 검증합니다.

카카오 사용자 인증 후 Kakao Member ID를 기준으로 기존 회원과 신규 회원을 구분하며,
신규 회원은 필수 약관 동의 후 회원가입을 진행합니다.

---

## 2. 카카오 로그인 흐름

1. 클라이언트에서 Kakao SDK 로그인
2. Kakao Access Token 발급
3. `POST /api/auth/kakao` 호출
4. 백엔드에서 Kakao 사용자 정보 API 호출
5. Kakao Member ID를 기준으로 기존 활성 회원 조회
6. 기존 회원이면 `registered=true` 반환
7. 신규 회원이면 `registered=false` 반환

카카오에서 사용하는 사용자 정보:

- Kakao Member ID
- 이메일
- 닉네임
- 프로필 이미지

---

## 3. 회원가입 흐름

1. 카카오 로그인 결과 `registered=false`
2. 클라이언트에서 약관 동의 화면 표시
3. 사용자가 약관 동의
4. `POST /api/auth/signup` 호출
5. 백엔드에서 Kakao Access Token으로 사용자 정보 재검증
6. 필수 약관 동의 여부 확인
7. User 생성 및 저장
8. UserAgreement 저장
9. 회원가입 완료

회원가입 과정에서 User와 UserAgreement 저장은 하나의 트랜잭션으로 처리합니다.

---

## 4. 회원가입 요청

### POST `/api/auth/signup`

```json
{
  "accessToken": "KAKAO_ACCESS_TOKEN",
  "agreements": [
    {
      "type": "SERVICE",
      "version": "1.0",
      "agreed": true
    },
    {
      "type": "PRIVACY",
      "version": "1.0",
      "agreed": true
    },
    {
      "type": "MARKETING",
      "version": "1.0",
      "agreed": false
    }
  ]
}
```

---

## 5. 약관 종류

| Type | 설명 | 필수 여부 |
| --- | --- | --- |
| `SERVICE` | 서비스 이용약관 | 필수 |
| `PRIVACY` | 개인정보 처리방침 | 필수 |
| `MARKETING` | 마케팅 수신 동의 | 선택 |

`SERVICE` 또는 `PRIVACY` 약관에 동의하지 않은 경우 회원가입을 진행하지 않습니다.

---

## 6. 회원 정보 저장

회원가입 성공 시 카카오 사용자 정보를 기반으로 다음 정보를 저장합니다.

- Kakao Member ID
- Social Type (`KAKAO`)
- 이메일
- 닉네임
- 프로필 이미지
- 회원 상태 (`ACTIVE`)

카카오 인증 단계에서는 신규 사용자를 DB에 저장하지 않고,
약관 동의 후 회원가입이 완료되는 시점에 User를 생성합니다.

---

## 7. 예외 처리

| Code | HTTP Status | 설명 |
| --- | --- | --- |
| `2001` | 401 Unauthorized | 유효하지 않은 카카오 Access Token |
| `2002` | 502 Bad Gateway | 카카오 API 통신 실패 |
| `2003` | 400 Bad Request | 카카오 필수 사용자 정보 누락 |
| `2004` | 409 Conflict | 이미 가입된 회원 |
| `2005` | 400 Bad Request | 필수 약관 미동의 |

---

## 8. 참고사항

- 회원가입 시 카카오 로그인에서 사용한 Kakao Access Token을 다시 전달받습니다.
- 백엔드는 해당 Access Token으로 카카오 사용자 정보를 다시 검증합니다.
- `SERVICE`, `PRIVACY`는 필수 약관입니다.
- `MARKETING`은 선택 약관입니다.
- User와 UserAgreement 저장은 `@Transactional`로 처리합니다.
- 서비스 Access/Refresh Token(JWT) 발급 및 재발급은 별도 기능에서 구현합니다.