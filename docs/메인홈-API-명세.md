# 메인 홈 API 명세

> 기준 문서: `메인 홈 정책 및 예외처리.pdf`, `Moive_ERD.sql` (2026-09-03 확정 스키마)
> 작성 범위: 홈 메인화면, 내 모임 목록/전체보기, 모임 상세, 모임 나가기
> 알림 아이콘/발송, 조건입력 제출, 투표 제출 API는 본 문서 범위 밖 (각 도메인 별도 문서 참고)
> **Required 컬럼 의미**: Request 테이블에서는 "필수 파라미터 여부"(없으면 400), Response 테이블에서는 "not-null 보장 필드 여부"(✅=항상 값 존재, -=null 가능)를 뜻함. DB 컬럼의 NOT NULL 제약과 1:1 매칭되도록 관리.

---

## 정책 반영 핵심 사항

- **상태(status) 4종 확정**: `CONDITION_INPUT`(조건입력중) → `VOTING`(투표진행중) → `CONFIRMED`(모임확정) → `COMPLETED`(완료/모임종료). `COMPLETED`는 "모임 시작일 다음날 00:00 자동 종료"와 "모임장 탈퇴로 참여자 0명" 두 케이스를 모두 포함.
- **홈 화면은 두 영역이 서로 다른 정렬 기준**: 확정된 모임 캐러셀(D-day 오름차순) vs 내 모임 리스트(참여 순서/가입순 정렬, 필터 적용). 이 때문에 엔드포인트를 `GET /api/home`과 `GET /api/meetings`(전체보기)로 분리함.
- **참여자 상태(`참여자.status`)는 서버가 능동적으로 관리**: 신규 참여자 join 시점의 `모임.status`를 보고 상태를 즉시 부여하고, 모임 phase 전환 시(예: VOTING→CONFIRMED) 서버가 기존 참여자들의 상태를 일괄 업데이트함. 별도의 `confirmed_at` 타임스탬프 컬럼 없이 처리 가능.
- **모임 phase 전환(CONDITION_INPUT→VOTING→CONFIRMED)은 서버가 참여자 액션을 집계해서 자동 처리**. 별도의 수동 "확정하기" API는 없음 (조건입력 제출 API·투표 제출 API 문서에서 다룰 내용).
- **장소 상세정보(이름/주소/좌표)와 이동시간(평균/최대/참여자별)은 DB 캐싱 없이 매번 알고리즘/외부 API로 실시간 계산**. `장소 마스터`, 이동시간 저장 테이블들은 팀 결정으로 삭제됨. API 응답 스키마 자체는 캐싱 여부와 무관하게 동일.

### `participantState` enum

| 값 | 라벨 | 설명 |
|---|---|---|
| `COND_PENDING` | 조건 입력 전 | |
| `COND_DONE` | 조건 입력 완료 | |
| `VOTE_PENDING` | 투표 전 | |
| `VOTE_DONE` | 투표 완료 | |
| `NEW_RESTRICTED` | 신규 참여 | 투표 시작/모임 확정 이후 참여한 신규 참여자, 조회만 가능 |
| `CONFIRMED` | 모임 확정 | |

---

## `GET /api/home`

### Description
홈 메인 화면 데이터 조회 (확정된 모임 캐러셀 + 내 모임 목록). 알림 아이콘 상태는 별도 알림 정책 문서를 따름 (본 API 범위 제외).
🔒 인증 필요

### Request

| Request Type | Name | Type | Required | Description |
|---|---|---|---|---|
| Query | filter | String | - | 내 모임 필터. `ALL`(전체) \| `UPCOMING`(예정) \| `PAST`(지난모임), 기본값 `ALL`. **확정 모임 캐러셀에는 영향 없음** |

<details>
<summary>Request Body Sample</summary>

없음

</details>

### Response

| Name | Type | Required | Description |
|---|---|---|---|
| confirmedMeetings | Object[] | ✅ | 확정된 모임 캐러셀. 없으면 `[]` (empty placeholder는 FE 처리) |
| confirmedMeetings[].meetingId | Long | ✅ | 모임 ID |
| confirmedMeetings[].name | String | ✅ | 모임명 |
| confirmedMeetings[].confirmedPlaceName | String | ✅ | 확정 장소명 (실시간 조회) |
| confirmedMeetings[].confirmedDate | String (date) | ✅ | 확정 일정 날짜 |
| confirmedMeetings[].confirmedTime | String (time) | ✅ | 확정 일정 시간 |
| confirmedMeetings[].participantProfileImages | String[] | ✅ | 참여자 프로필 이미지 (가입 순, 최대 3장). 배열 자체는 항상 존재하되 개별 URL은 미설정 시 `null` 포함 가능 |
| confirmedMeetings[].participantCnt | Integer | ✅ | 전체 참여자 수 (카드의 "+N" 표시는 `participantCnt - 3`으로 FE 계산) |
| confirmedMeetings[].dDay | Integer | ✅ | `confirmedDate` 기준 D-day. **정렬 기준 = dDay 오름차순** |
| myMeetings | Object[] | ✅ | 내 모임 목록. 참여 순서(가입 순) 정렬, `filter` 파라미터 적용 |
| myMeetings[].meetingId | Long | ✅ | 모임 ID |
| myMeetings[].name | String | ✅ | 모임명 |
| myMeetings[].purposeType | String | ✅ | 모임 목적 (`모임_목적.type`, 단일값. enum은 `모임생성-초대-API-명세.md` 참고) |
| myMeetings[].status | String | ✅ | `CONDITION_INPUT`\|`VOTING`\|`CONFIRMED`\|`COMPLETED` |
| myMeetings[].statusLabel | String | ✅ | 상태 뱃지 라벨 ("조건 입력중"/"투표 진행중"/"확정"/"완료") |
| myMeetings[].scheduledDate | String (date) | - | 확정 전 후보 일정 없으면 `null` |
| myMeetings[].scheduledTime | String (time) | - | 위와 동일 |
| myMeetings[].participantCnt | Integer | ✅ | 참여자 수 |
| myMeetings[].submittedCnt | Integer | ✅ | 조건 입력 완료 참여자 수 |

<details>
<summary>Response Body Sample</summary>

```json
{
  "code": 200,
  "message": "홈 데이터 조회에 성공했습니다.",
  "data": {
    "confirmedMeetings": [
      {
        "meetingId": 3,
        "name": "동아리 정기모임",
        "confirmedPlaceName": "홍대 OO카페",
        "confirmedDate": "2026-09-08",
        "confirmedTime": "18:00:00",
        "participantProfileImages": ["https://.../a.png", "https://.../b.png", "https://.../c.png"],
        "participantCnt": 6,
        "dDay": 5
      }
    ],
    "myMeetings": [
      {
        "meetingId": 1,
        "name": "대학 동기 모임",
        "purposeType": "NETWORKING",
        "status": "CONDITION_INPUT",
        "statusLabel": "조건 입력중",
        "scheduledDate": null,
        "scheduledTime": null,
        "participantCnt": 4,
        "submittedCnt": 2
      }
    ]
  }
}
```

</details>

### Error

| HTTP Status | Code | Message |
|---|---|---|
| 401 | 2011 | 유효하지 않은 Access Token입니다. ⚠️ 미구현 — 현재는 `CustomErrorCode` 형식이 아닌 Spring Security 기본 403 응답 (문서 하단 각주 참고) |

**⚠️ 확인 필요**: "내 모임 최대 노출 개수"가 정책문서상 `TODO, 보류(디자인 확인 후 결정)`으로 명시돼 있음. 지금은 제한 없이 전체 반환. 디자인 확정되면 `limit` 파라미터나 서버 측 상한 추가 검토.

---

## `GET /api/meetings`

### Description
모임 전체보기 목록 조회 (무한스크롤). 필터 분류 기준은 `GET /api/home`의 `myMeetings`와 동일.
🔒 인증 필요

### Request

| Request Type | Name | Type | Required | Description |
|---|---|---|---|---|
| Query | filter | String | - | `ALL`\|`UPCOMING`\|`PAST`, 기본값 `ALL` |
| Query | cursor | Long | - | 이전 응답의 `nextCursor` 값. 첫 페이지는 미전달 |
| Query | size | Integer | - | 페이지당 개수, 기본값 20 |

<details>
<summary>Request Body Sample</summary>

없음

</details>

### Response

| Name | Type | Required | Description |
|---|---|---|---|
| meetings | Object[] | ✅ | 모임 목록 (참여 순서 정렬), 필드는 `GET /api/home`의 `myMeetings[]`와 동일 |
| hasNext | Boolean | ✅ | 다음 페이지 존재 여부. `false`면 추가 요청 중단 |
| nextCursor | Long | - | 다음 요청 시 사용할 cursor, `hasNext=false`면 `null` |

<details>
<summary>Response Body Sample</summary>

```json
{
  "code": 200,
  "message": "모임 목록 조회에 성공했습니다.",
  "data": {
    "meetings": [
      {
        "meetingId": 1,
        "name": "대학 동기 모임",
        "purposeType": "NETWORKING",
        "status": "VOTING",
        "statusLabel": "투표 진행중",
        "scheduledDate": null,
        "scheduledTime": null,
        "participantCnt": 4,
        "submittedCnt": 4
      }
    ],
    "hasNext": true,
    "nextCursor": 1
  }
}
```

</details>

### Error

| HTTP Status | Code | Message |
|---|---|---|
| 401 | 2011 | 유효하지 않은 Access Token입니다. ⚠️ 미구현 — 현재는 `CustomErrorCode` 형식이 아닌 Spring Security 기본 403 응답 (문서 하단 각주 참고) |

**⚠️ 확인 필요**: cursor 기반 페이지네이션으로 가정함 (참여 순서가 불변이라 offset도 가능하지만, cursor가 중간 삽입/삭제에 더 안전). FE와 페이지네이션 방식(cursor vs page) 맞춰야 함.

---

## `GET /api/meetings/{meetingId}`

### Description
모임 상세 조회. 조건입력중/투표진행중/확정/완료 모든 phase를 하나의 응답으로 통합 제공하며, `status`에 따라 해당 없는 phase 필드는 `null`.
🔒 인증 필요

### Request

| Request Type | Name | Type | Required | Description |
|---|---|---|---|---|
| Path | meetingId | Long | ✅ | 모임 ID |

<details>
<summary>Request Body Sample</summary>

없음

</details>

### Response

| Name | Type | Required | Description |
|---|---|---|---|
| meetingId | Long | ✅ | 모임 ID |
| name | String | ✅ | 모임명 |
| purposeType | String | ✅ | 모임 목적 (단일값, 보조 텍스트로 표시) |
| status | String | ✅ | `CONDITION_INPUT`\|`VOTING`\|`CONFIRMED`\|`COMPLETED` |
| statusLabel | String | ✅ | 상태 라벨 |
| inviteCode | String | - | 초대 코드. `status=COMPLETED`면 `null` (친구초대 버튼 미노출) |
| inviteUrl | String | - | 초대 링크, 위와 동일 조건 |
| canShare | Boolean | ✅ | "친구에게 알려주기" 버튼 노출 여부 (`COMPLETED`면 `false`) |
| recommendationReady | Boolean | ✅ | `CONDITION_INPUT`에서만 유효. 제외 대상 제외한 전원 조건입력 완료 시 `true` (추천/투표 CTA 활성화 기준) |
| myParticipantState | String | ✅ | 내 참여자 상태 (`participantState` enum 참고) |
| myParticipantStateLabel | String | ✅ | 내 상태 표시 라벨 |
| participants | Object[] | ✅ | 참여자 목록 (가입 순 정렬) |
| participants[].participantId | Long | ✅ | 참여자 ID |
| participants[].userId | Long | ✅ | 사용자 ID |
| participants[].nickname | String | ✅ | 닉네임 (`사용자.nickname` NOT NULL) |
| participants[].profileImageUrl | String | - | 프로필 이미지. `사용자.profile_image_url`이 NULL 허용 컬럼이라 미설정 시 `null` |
| participants[].participantState | String | ✅ | `participantState` enum 참고 (`참여자.status` 값 그대로 매핑) |
| participants[].participantStateLabel | String | ✅ | 상태 표시 라벨 |
| participants[].excludedFromRecommendation | Boolean | ✅ | 3일 미입력으로 추천 계산에서 제외된 참여자인지 (참여자 목록엔 유지) |
| participants[].travelMinutes | Integer | - | 확정 장소까지 이동 소요시간(분, 실시간 계산). `CONFIRMED`\|`COMPLETED`에서만 값 존재, 신규참여자(출발지 없음)는 `null` |
| confirmedPlace | Object | - | 확정 장소 (실시간 조회). `CONFIRMED`\|`COMPLETED`에서만 존재, 그 외 `null` |
| confirmedPlace.placeId | Long | ✅ | 장소 ID (객체가 존재할 때는 항상 값 있음) |
| confirmedPlace.placeName | String | ✅ | 장소명 |
| confirmedPlace.category | String | ✅ | 장소 유형 |
| confirmedPlace.address | String | ✅ | 주소 |
| confirmedPlace.latitude | Decimal | ✅ | 지도뷰용 좌표 |
| confirmedPlace.longitude | Decimal | ✅ | 지도뷰용 좌표 |
| confirmedDate | String (date) | - | 확정 일정 날짜, 확정 전 `null` |
| confirmedTime | String (time) | - | 확정 일정 시간, 확정 전 `null` |
| travelSummary | Object | - | 이동 소요시간 요약 (실시간 계산). `CONFIRMED`\|`COMPLETED`에서만 존재 |
| travelSummary.avgMinutes | Integer | ✅ | 평균 이동시간 (신규참여자 제외 계산, 객체가 존재할 때는 항상 값 있음) |
| travelSummary.maxMinutes | Integer | ✅ | 최대 이동시간 |

<details>
<summary>Response Body Sample</summary>

```json
{
  "code": 200,
  "message": "모임 상세 조회에 성공했습니다.",
  "data": {
    "meetingId": 1,
    "name": "대학 동기 모임",
    "purposeType": "NETWORKING",
    "status": "CONDITION_INPUT",
    "statusLabel": "조건 입력중",
    "inviteCode": "ABC123XY",
    "inviteUrl": "https://moive.app/invite/ABC123XY",
    "canShare": true,
    "recommendationReady": false,
    "myParticipantState": "COND_DONE",
    "myParticipantStateLabel": "조건 입력 완료",
    "participants": [
      {
        "participantId": 1,
        "userId": 1,
        "nickname": "홍길동",
        "profileImageUrl": "https://.../a.png",
        "participantState": "COND_DONE",
        "participantStateLabel": "조건 입력 완료",
        "excludedFromRecommendation": false,
        "travelMinutes": null
      },
      {
        "participantId": 2,
        "userId": 2,
        "nickname": "김철수",
        "profileImageUrl": null,
        "participantState": "COND_PENDING",
        "participantStateLabel": "조건 입력 전",
        "excludedFromRecommendation": true,
        "travelMinutes": null
      }
    ],
    "confirmedPlace": null,
    "confirmedDate": null,
    "confirmedTime": null,
    "travelSummary": null
  }
}
```

</details>

### Error

| HTTP Status | Code | Message |
|---|---|---|
| 401 | 2011 | 유효하지 않은 Access Token입니다. ⚠️ 미구현 — 현재는 `CustomErrorCode` 형식이 아닌 Spring Security 기본 403 응답 (문서 하단 각주 참고) |
| 403 | 4045 | 해당 모임의 참여자가 아닙니다. ⚠️ CustomErrorCode 미구현 |
| 404 | 4042 | 존재하지 않는 모임이에요. |

**⚠️ 확인 필요**
1. 투표 시작 이후 참여한 신규 참여자(`NEW_RESTRICTED`)와 투표는 가능하지만 조건입력만 못하는 케이스 등 phase 경계의 세부 판정은 서버 로직으로 처리 — FE에는 `participantState`만 내려주면 되는지, "왜 제한되는지" 사유 텍스트도 필요한지 확인 필요.
2. `모임확정` 상태와 `완료` 상태의 `confirmedPlace`/`travelSummary` 스키마는 동일하게 재사용 (완료된 모임도 동일 정보를 조회만 가능하게 노출).

---

## `GET /api/meetings/{meetingId}/home`

### Description
모임 홈 화면(모임 상세 + 참여자별 진행 상태) 조회. `GET /api/meetings/{meetingId}`와 데이터 대부분이 겹치지만, 이 화면 전용으로 하단 배너 문구·CTA 버튼 문구를 서버가 완제품 텍스트로 내려준다 (와이어프레임 `[SCR-HOME-04-A]`/`[SCR-HOME-05-A]`/`[SCR-HOME-06]`/`신규 참여자 진입 시` 기준).
🔒 인증 필요

### Request

| Request Type | Name | Type | Required | Description |
|---|---|---|---|---|
| Path | meetingId | Long | ✅ | 모임 ID |

<details>
<summary>Request Body Sample</summary>

없음

</details>

### Response

| Name | Type | Description |
|---|---|---|
| meetingId | Long | 모임 ID |
| name | String | 모임명 |
| purposeType | String | 모임 목적 |
| status | String | 모임 상태 |
| inviteCode | String | 초대 코드 (`COMPLETED`면 `null`) |
| inviteUrl | String | 초대 링크 |
| participants | Object[] | 참여자 목록 |
| participants[].participantId | Long | 참여자 ID |
| participants[].nickname | String | 닉네임 |
| participants[].profileImageUrl | String | 프로필 이미지 |
| participants[].participantState | String | 참여자 상태 |
| participants[].participantStateLabel | String | 상태 라벨 |
| homeMessage | String | 배너 문구 |
| primaryActionLabel | String | CTA 버튼 문구 |
| primaryActionEnabled | Boolean | CTA 버튼 활성화 여부 |

`homeMessage`/`primaryActionLabel`/`primaryActionEnabled`는 `status`(및 본인이 `NEW_RESTRICTED`인지)에 따라 서버가 아래 표대로 조립해서 내려준다.

| status | homeMessage | primaryActionLabel | primaryActionEnabled |
|---|---|---|---|
| `CONDITION_INPUT` | 아직 조건 입력 중이에요! | 추천 장소 확인 | `false` |
| `VOTING` | 이미 조건 입력이 완료된 모임이에요! | 추천 장소 확인 및 투표 | `true` |
| `CONFIRMED` | 모임이 확정됐어요, 모임 정보를 확인해보세요! | 확정된 모임 보러 가기 | `true` |

<details>
<summary>Response Body Sample (투표 진행중)</summary>

```json
{
  "code": 200,
  "message": "모임 홈 조회에 성공했습니다.",
  "data": {
    "meetingId": 1,
    "name": "모임명",
    "purposeType": "NETWORKING",
    "status": "VOTING",
    "inviteCode": "ABC123XY",
    "inviteUrl": "https://moive.app/invite/ABC123XY",
    "participants": [
      { "participantId": 1, "nickname": "사용자", "profileImageUrl": null, "participantState": "VOTE_PENDING", "participantStateLabel": "투표 전" },
      { "participantId": 2, "nickname": "참여자1", "profileImageUrl": null, "participantState": "VOTE_DONE", "participantStateLabel": "투표 완료" },
      { "participantId": 3, "nickname": "참여자2", "profileImageUrl": null, "participantState": "VOTE_DONE", "participantStateLabel": "투표 완료" }
    ],
    "homeMessage": "이미 조건 입력이 완료된 모임이에요!",
    "primaryActionLabel": "추천 장소 확인 및 투표",
    "primaryActionEnabled": true
  }
}
```

</details>

### Error

| HTTP Status | Code | Message |
|---|---|---|
| 401 | 2011 | 유효하지 않은 Access Token입니다. ⚠️ 미구현 — 현재는 `CustomErrorCode` 형식이 아닌 Spring Security 기본 403 응답 (문서 하단 각주 참고) |
| 403 | 4045 | 해당 모임의 참여자가 아닙니다. ⚠️ CustomErrorCode 미구현 |
| 404 | 4042 | 존재하지 않는 모임이에요. |

**⚠️ 확인 필요**: `GET /api/meetings/{meetingId}`와 `participants`/`status` 등 필드가 대부분 중복됨. 두 API를 계속 별도로 유지할지, 아니면 `GET /api/meetings/{meetingId}` 응답에 `homeMessage`/`primaryActionLabel`/`primaryActionEnabled` 3개 필드만 얹어서 이 엔드포인트를 없앨지 팀 논의 필요.

---

## `DELETE /api/meetings/{meetingId}/leave`

### Description
모임 나가기. 서버가 내부적으로:
1. 조건입력중 나가면 해당 참여자를 추천 계산 대상에서 제외
2. 투표진행중 나가면 투표 대상/기존 투표 결과를 최종 집계에서 제외
3. 확정 후 나가면 장소·일정 유지 및 참여자만 제외
4. 모임장이 나가면 가입일시가 가장 빠른 잔여 참여자를 새 모임장으로 승계
5. 마지막 참여자(모임장)가 나가면 모임을 `COMPLETED`로 전환 (데이터 유지)

🔒 인증 필요

### Request

| Request Type | Name | Type | Required | Description |
|---|---|---|---|---|
| Path | meetingId | Long | ✅ | 모임 ID |

<details>
<summary>Request Body Sample</summary>

없음

</details>

### Response

| Name | Type | Required | Description |
|---|---|---|---|
| data | null | - | 성공 시 `null` |

<details>
<summary>Response Body Sample</summary>

```json
{
  "code": 200,
  "message": "모임에서 나갔습니다.",
  "data": null
}
```

</details>

### Error

| HTTP Status | Code | Message |
|---|---|---|
| 401 | 2011 | 유효하지 않은 Access Token입니다. ⚠️ 미구현 — 현재는 `CustomErrorCode` 형식이 아닌 Spring Security 기본 403 응답 (문서 하단 각주 참고) |
| 403 | 4045 | 해당 모임의 참여자가 아닙니다. ⚠️ CustomErrorCode 미구현 |
| 400 | 4046 | 종료된 모임은 나갈 수 없습니다. ⚠️ CustomErrorCode 미구현 |
| 404 | 4042 | 존재하지 않는 모임이에요. |

---

## ⚠️ 인증 실패(401) 관련 참고

이 문서의 "401 | 2011 | 유효하지 않은 Access Token입니다" 행은 **목표 사양**이고, 실제 현재 동작과는 다릅니다.

- `JwtAuthenticationFilter`는 토큰이 없거나 유효하지 않아도 예외를 던지지 않고 `SecurityContext`를 비워둔 채 다음 필터로 넘깁니다.
- `JwtTokenProvider.validateAccessToken()`은 만료/서명오류/형식오류를 전부 뭉개서 "없음"/"위조"/"만료"를 구분하지 않습니다.
- 커스텀 `AuthenticationEntryPoint`가 없어서, 미인증 요청은 Spring Security 기본 403 응답(앱 표준 `{code, message, data}` 형식 아님)으로 나갑니다.

**정리된 사용 방침** (클라이언트가 실제로 다르게 반응해야 하는지가 기준):
- 일반적인 인증 실패(토큰 없음/위조/형식오류)는 전부 `INVALID_ACCESS_TOKEN`(2011)로 통일해서 사용. `TOKEN_MISSING`(2010)은 별도로 쓰지 않음.
- `ACCESS_TOKEN_EXPIRED`(2012)만 별도로 구분해서 구현 — 클라이언트가 강제 로그아웃 대신 `/api/auth/token/refresh`로 조용히 갱신할 수 있어야 하기 때문.
- `REFRESH_TOKEN_EXPIRED`(2013)는 별도 코드 없이 `INVALID_REFRESH_TOKEN`(2006)으로 통합해서 사용(`AuthService`가 실제로 이렇게 동작 중).
- `USER_NOT_FOUND`(2007)는 이미 정상적으로 구현·사용 중이라 그대로 유지.

## 범위 밖 (별도 문서 참고)

- 알림 아이콘 상태/발송 → 알림 정책 및 예외처리 문서
- 조건입력 폼 제출 API, 투표 제출 API → 각 도메인 문서 (진행 중)
- 장소 추천 알고리즘, 이동시간 실시간 계산 로직 → 별도 알고리즘 문서
