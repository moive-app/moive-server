# 알림 API 명세

> 기준 문서: `알림 정책 및 예외처리 (08.31 수정).pdf` + 피그마 프레임(`[SCR-NOTI-01] 알림함 (OS 시스템 권한 거부)`, `[SCR-NOTI-02] 알림 없음`, `[SCR-NOTI-03] 알림 존재`, `[SCR-NOTI-04] 예외_탈퇴한 모임의 알림 클릭 시`, `withdrawn_notification_modal`, 2026-09-07 조회)
> 작성 범위: 알림함 목록 조회, 읽음 처리, 안읽음 뱃지(홈 아이콘 dot), 멀티기기 푸시 토큰 등록/해제
> 실제 푸시 발송 스케줄링(재발송 배치, FCM 호출)·조건입력/투표 완료 시 자동 읽음 처리 트리거는 각 도메인 서버 내부 로직 — 본 문서는 클라이언트-서버 API 계약만 다룸
> **Required 컬럼 의미**: Request 테이블에서는 "필수 파라미터 여부"(없으면 400), Response 테이블에서는 "not-null 보장 필드 여부"(✅=항상 값 존재, -=null 가능)를 뜻함

---

## 시작 전에: 정책-스키마가 안 맞는 부분과 결정 사항

PDF·Figma·기존 스키마를 대조하다가 5가지가 서로 안 맞았습니다. 각각 어떻게 처리할지 제 나름대로 결론을 내리고 진행했으니, 아래 표만 훑어보고 동의 여부만 확인해주시면 됩니다.

| # | 무엇이 문제였나 | 이렇게 결정함 | 왜 |
|---|---|---|---|
| 1 | `알림.meeting_id`가 `NOT NULL`인데, 업데이트·장애점검 알림(NOTI-005/006)은 특정 모임에 속하지 않음 | **`meeting_id`를 nullable로 변경** | 시스템 알림은 넣을 모임 ID 자체가 없음. 이 컬럼 하나만 완화하면 나머지 구조는 그대로 재사용 가능 |
| 2 | `사용자_알림_설정.push_enabled` 컬럼이 있는데, PDF는 "앱 자체 알림 토글 없음, OS 권한만 기준"이라고 명시 | **이번 알림 기능 API에서는 이 컬럼을 아예 사용하지 않음** | 정책 문서와 정면으로 상충. 컬럼을 지우기보다는 일단 안 쓰는 걸로 두고, 나중에 마케팅 알림 같은 다른 용도로 필요하면 그때 재검토 |
| 3 | 여러 기기 로그인 시 기기별로 푸시를 보내야 하는데, 그 토큰을 저장할 테이블이 스키마에 없음 | **`기기_푸시_토큰` 테이블 신규 추가** (아래 DDL 제안) | §13 정책(기기별 발송, 로그아웃한 기기는 제외)을 구현하려면 필수. 없으면 이 정책 자체를 못 만듦 |
| 4 | `[SCR-NOTI-01]` 프레임만 디자인이 다르고(다크 헤더, 다른 버튼 스타일), 다른 3개 프레임과 문구가 겹침 | **구버전으로 보고 명세에서 제외**, `NOTI-02/03/04` 기준으로 작성 | node-id 대역도 다른 페이지 소속이라 최신 작업물이 아닐 가능성이 높음. 디자이너 확인 전까지는 최신 3개를 기준으로 삼는 게 안전 |
| 5 | PDF 안에서 NOTI-005(업데이트) 알림 대상이 "전체 사용자"(6p 표)와 "해당 모임 사용자"(9p 본문)로 서로 다르게 적혀 있음 | **"전체 사용자"로 채택** | 표가 더 명확하고, 모임 알림은 이미 001~004가 담당하므로 "전체 사용자"가 정책 의도에 맞음 |

### 3번 관련 — 신규 테이블 제안: `기기_푸시_토큰`

```sql
CREATE TABLE 기기_푸시_토큰 (
    device_token_id BIGINT       NOT NULL,
    user_id         BIGINT       NOT NULL,
    device_id       VARCHAR(100) NOT NULL,   -- 클라이언트가 발급하는 기기 고유값 (재설치해도 유지 가정)
    fcm_token       VARCHAR(255) NOT NULL,
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP    NOT NULL,
    PRIMARY KEY (device_token_id),
    UNIQUE (device_id)
);
```

- `device_id` 기준으로 upsert하면 앱 재실행마다 호출해도 안전(idempotent).
- 로그아웃 시 해당 `device_id` 행만 삭제 → 그 기기만 발송 대상에서 빠지고 다른 기기는 그대로 유지.
- 이 테이블은 아직 팀 확정 전 **제안**입니다. 엔티티 설계 들어가기 전에 최종 컬럼은 한 번 더 맞춰보는 걸 권장드려요.

---

## 정책 반영 핵심 사항

- **알림 유형 6종**: 서비스 알림 4종(NOTI-001~004, 모임 종속) + 시스템 알림 2종(NOTI-005/006, 전체 사용자 대상, 모임 비종속).
- **OS 권한과 알림 데이터는 독립적**: OS 알림 권한을 거부해도 알림 데이터는 항상 생성·저장된다. OS 권한은 "OS 푸시 표시 여부"만 결정하고 "알림함 목록"에는 영향 없음. 즉 `GET /api/notifications`는 OS 권한 상태와 무관하게 항상 동일한 결과를 반환.
- **읽음 처리 트리거 3가지**: ① OS 푸시 클릭 ② 알림함에서 알림 클릭 ③ 사용자가 알림 생성 이후 해당 행동(조건입력 완료·투표 완료)을 직접 완료한 경우 자동 읽음. ③은 조건입력/투표 제출 API 쪽에서 서버가 내부적으로 처리 — 본 문서 범위 밖.
- **재발송(NOTI-001, NOTI-003만 해당)**: 미완료 상태 지속 시 1시간마다 최대 3회, 09:00~22:00 사이에만 발송(시간대 벗어나면 다음 09:00로 이월). 재발송 횟수는 **사용자 단위**로 관리(여러 기기 로그인해도 합산). NOTI-002/004/005/006은 재발송 없음.
- **알림함 보관 기간**: 생성일 기준 30일 경과 시 알림함에서 제거. `GET /api/notifications` 응답에는 30일 이내 알림만 포함(서버가 필터링, FE는 별도 처리 불필요).
- **모임 탈퇴 후 알림 클릭**: 탈퇴 전 생성된 모임 종속 알림(NOTI-001~004)은 알림함에 남아있지만, 클릭 시 "탈퇴한 모임" 모달을 띄우고 실제 화면 이동은 막는다. 탈퇴 후 클릭이어도 읽음 처리는 정상 적용. 시스템 알림(005/006)은 모임 비종속이라 해당 없음.
  - **설계 결정**: 별도의 "접근 가능 여부" 필드를 목록 응답에 추가하지 않고, `메인홈-API-명세.md`에 이미 정의된 `GET /api/meetings/{meetingId}`의 `403 4045`(해당 모임의 참여자가 아닙니다, ⚠️ CustomErrorCode 미구현) 에러를 그대로 재사용한다. FE는 알림 클릭 시 항상 먼저 `PATCH /api/notifications/{id}/read`로 읽음 처리한 뒤, `meetingId` 기반으로 대상 화면(모임 상세 등) 진입을 시도하고 `403`을 받으면 탈퇴 모달을 띄운다.
- **여러 기기 로그인**: 로그인 기기 수 제한 없음. 동일 계정에 등록된 모든 기기로 푸시 발송하되, OS 알림 권한이 거부된 기기는 해당 기기만 미수신(다른 기기에는 영향 없음). 특정 기기에서 로그아웃하면 그 기기는 발송 대상에서 제외.
- **안 읽은 알림 표시(홈 아이콘 dot)**: 안 읽은 알림이 1개 이상 있으면 dot 표시, 모두 읽으면 제거. **사용자 계정 기준**으로 관리하며 로그인한 모든 기기에서 동일하게 동기화.
- **알림함 조회 실패 시** 별도 에러 문구·재시도 버튼 없이 로딩 인디케이터만 유지하는 정책이라, 별도의 "빈 에러 상태" 응답 스펙은 없음(표준 5xx로 충분).

### `notificationType` enum

| 값 | 제목(title) 예시 | 그룹 | 클릭 시 이동 | 재발송 |
|---|---|---|---|---|
| `COND_INPUT` | 조건 입력을 완료해주세요. | 서비스 | 조건 입력 화면 | O (최대 3회) |
| `PLACE_RECOMMEND` | 장소 추천이 완료됐어요. | 서비스 | 장소 추천 결과 화면 | X |
| `PLACE_VOTE` | 장소 투표를 완료해주세요. | 서비스 | 장소 투표 화면 | O (최대 3회) |
| `MEETING_CONFIRMED` | 모임 일정과 장소가 결정됐어요! | 서비스 | 모임 상세 | X |
| `APP_UPDATE` | 업데이트 | 시스템 | 이동 없음 | X |
| `SERVICE_INCIDENT` | 장애 및 점검 | 시스템 | 이동 없음 | X |

- 서비스 알림(`COND_INPUT`/`PLACE_RECOMMEND`/`PLACE_VOTE`/`MEETING_CONFIRMED`)은 `meetingId`가 항상 존재. 시스템 알림(`APP_UPDATE`/`SERVICE_INCIDENT`)은 `meetingId`가 `null` (위 결정 1번 참고).
- `title`/`content`는 서버가 `{모임명}` 치환을 끝낸 최종 문자열로 내려준다 (PDF 7p "푸시 알림 문구 정리" 표 기준, FE는 템플릿 처리 안 함).

---

## `GET /api/notifications`

### Description
알림함 목록 조회 (최신순, 무한스크롤). 생성일 기준 30일 경과 알림은 서버가 자동 제외.
🔒 인증 필요

### Request

| Request Type | Name | Type | Required | Description |
|---|---|---|---|---|
| Query | cursor | Long | - | 이전 응답의 `nextCursor` 값. 첫 페이지는 미전달 |
| Query | size | Integer | - | 페이지당 개수, 기본값 20 |

<details>
<summary>Request Body Sample</summary>

없음

</details>

### Response

| Name | Type | Required | Description |
|---|---|---|---|
| notifications | Object[] | ✅ | 알림 목록 |
| notifications[].notificationId | Long | ✅ | 알림 ID |
| notifications[].type | String | ✅ | `notificationType` enum |
| notifications[].title | String | ✅ | 제목 (치환 완료된 최종 문자열) |
| notifications[].content | String | ✅ | 본문 (치환 완료된 최종 문자열) |
| notifications[].meetingId | Long | - | 대상 모임 ID. 시스템 알림(`APP_UPDATE`\|`SERVICE_INCIDENT`)이면 `null` |
| notifications[].isRead | Boolean | ✅ | 읽음 여부 |
| notifications[].createdAt | String (datetime) | ✅ | 생성 시각. 상대 시간 표기("3시간 전")는 FE 계산 |
| hasNext | Boolean | ✅ | 다음 페이지 존재 여부 |
| nextCursor | Long | - | 다음 요청 시 사용할 cursor, `hasNext=false`면 `null` |

<details>
<summary>Response Body Sample</summary>

```json
{
  "code": 200,
  "message": "알림함 조회에 성공했습니다.",
  "data": {
    "notifications": [
      {
        "notificationId": 101,
        "type": "COND_INPUT",
        "title": "조건 입력을 완료해주세요",
        "content": "'주말 맛집 모임'의 조건을 아직 입력하지 않았어요.",
        "meetingId": 12,
        "isRead": false,
        "createdAt": "2026-09-07T09:20:00"
      },
      {
        "notificationId": 98,
        "type": "APP_UPDATE",
        "title": "업데이트",
        "content": "새로운 업데이트가 있어요.",
        "meetingId": null,
        "isRead": true,
        "createdAt": "2026-09-06T10:00:00"
      }
    ],
    "hasNext": false,
    "nextCursor": null
  }
}
```

</details>

### Error

| HTTP Status | Code | Message |
|---|---|---|
| 401 | 2011 | 유효하지 않은 Access Token입니다. ⚠️ 미구현 — 현재는 `CustomErrorCode` 형식이 아닌 Spring Security 기본 403 응답 (문서 하단 각주 참고) |

> 이 문서의 알림 도메인 전용 에러(`5001`~`5004`)는 `CustomErrorCode`의 도메인 규칙(`5xxx` = 알림)에 맞춘 신규 제안 코드이며, 실제 enum에는 아직 추가되지 않았습니다(⚠️ 표시).

---

## `PATCH /api/notifications/{notificationId}/read`

### Description
알림 읽음 처리. OS 푸시 클릭 또는 알림함에서 알림 클릭 시 호출한다. **화면 이동보다 먼저(또는 동시에) 호출**하며, 탈퇴한 모임에 대한 알림이라 화면 이동이 이어서 실패(403)하더라도 읽음 처리 자체는 정상 반영된다. 이미 읽은 알림에 재호출해도 에러 없이 idempotent하게 처리.
🔒 인증 필요

### Request

| Request Type | Name | Type | Required | Description |
|---|---|---|---|---|
| Path | notificationId | Long | ✅ | 알림 ID |

<details>
<summary>Request Body Sample</summary>

없음

</details>

### Response

| Name | Type | Required | Description |
|---|---|---|---|
| notificationId | Long | ✅ | 알림 ID |
| isRead | Boolean | ✅ | 처리 후 상태, 항상 `true` |
| hasUnreadRemaining | Boolean | ✅ | 이 처리 이후에도 계정에 안읽은 알림이 남아있는지 (홈 아이콘 dot 즉시 갱신용) |

<details>
<summary>Response Body Sample</summary>

```json
{
  "code": 200,
  "message": "알림을 읽음 처리했습니다.",
  "data": {
    "notificationId": 101,
    "isRead": true,
    "hasUnreadRemaining": false
  }
}
```

</details>

### Error

| HTTP Status | Code | Message |
|---|---|---|
| 401 | 2011 | 유효하지 않은 Access Token입니다. ⚠️ 미구현 — 현재는 `CustomErrorCode` 형식이 아닌 Spring Security 기본 403 응답 (문서 하단 각주 참고) |
| 403 | 5002 | 본인의 알림이 아닙니다. ⚠️ CustomErrorCode 미구현 |
| 404 | 5001 | 존재하지 않는 알림입니다. ⚠️ CustomErrorCode 미구현 |

---

## `GET /api/notifications/unread-status`

### Description
안읽은 알림 존재 여부만 가볍게 조회 (홈 화면 알림 아이콘 dot 표시용). 계정 기준으로 로그인한 모든 기기에서 동일하게 동기화됨. 홈 진입 시 또는 앱 포그라운드 복귀 시 호출을 가정.
🔒 인증 필요

**⚠️ 확인 필요**: `GET /api/home` 응답에 이 값을 필드로 함께 내려줄지, 별도 엔드포인트로 분리 유지할지 FE와 협의 필요. 지금은 알림함 정책 문서 담당 범위를 명확히 하기 위해 별도 엔드포인트로 설계함.

### Request

없음 (인증 토큰만)

### Response

| Name | Type | Required | Description |
|---|---|---|---|
| hasUnread | Boolean | ✅ | 안 읽은 알림 1개 이상 존재 여부 |

<details>
<summary>Response Body Sample</summary>

```json
{
  "code": 200,
  "message": "안읽음 상태 조회에 성공했습니다.",
  "data": {
    "hasUnread": true
  }
}
```

</details>

### Error

| HTTP Status | Code | Message |
|---|---|---|
| 401 | 2011 | 유효하지 않은 Access Token입니다. ⚠️ 미구현 — 현재는 `CustomErrorCode` 형식이 아닌 Spring Security 기본 403 응답 (문서 하단 각주 참고) |

---

## `PUT /api/devices/token`

### Description
현재 기기의 FCM 푸시 토큰을 등록/갱신한다 (idempotent upsert, `기기_푸시_토큰` 테이블 기준 — 위 결정 3번 참고). 앱 실행 시 OS 알림 권한 상태와 무관하게 항상 호출하는 것으로 가정(OS 권한은 표시 여부만 결정하므로 서버는 권한 상태를 별도로 추적하지 않음).

🔒 인증 필요

### Request

| Request Type | Name | Type | Required | Description |
|---|---|---|---|---|
| Body | fcmToken | String | ✅ | FCM 디바이스 토큰 |
| Body | deviceId | String | ✅ | 클라이언트가 생성하는 기기 고유 식별자 (재설치 시에도 동일 값 유지 가정) |

<details>
<summary>Request Body Sample</summary>

```json
{
  "fcmToken": "dXJ2...token...",
  "deviceId": "android-uuid-1234"
}
```

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
  "message": "기기 토큰이 등록되었습니다.",
  "data": null
}
```

</details>

### Error

| HTTP Status | Code | Message |
|---|---|---|
| 401 | 2011 | 유효하지 않은 Access Token입니다. ⚠️ 미구현 — 현재는 `CustomErrorCode` 형식이 아닌 Spring Security 기본 403 응답 (문서 하단 각주 참고) |
| 400 | 5003 | 기기 토큰을 입력해주세요. ⚠️ CustomErrorCode 미구현 |

---

## `DELETE /api/devices/token`

### Description
로그아웃 시 현재 기기를 푸시 발송 대상에서 제외한다 (§13: "사용자가 특정 기기에서 로그아웃한 경우 해당 기기의 푸시 알림 수신 대상에서 제외"). `기기_푸시_토큰`에서 해당 `device_id` 행만 삭제.

**⚠️ 확인 필요**: 로그아웃 API(인증 도메인) 내부에서 함께 처리할지, FE가 로그아웃 직전 별도 호출할지 확인 필요.

🔒 인증 필요

### Request

| Request Type | Name | Type | Required | Description |
|---|---|---|---|---|
| Query | deviceId | String | ✅ | 해제할 기기 식별자 |

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
  "message": "기기 토큰이 해제되었습니다.",
  "data": null
}
```

</details>

### Error

| HTTP Status | Code | Message |
|---|---|---|
| 401 | 2011 | 유효하지 않은 Access Token입니다. ⚠️ 미구현 — 현재는 `CustomErrorCode` 형식이 아닌 Spring Security 기본 403 응답 (문서 하단 각주 참고) |
| 404 | 5004 | 등록되지 않은 기기입니다. ⚠️ CustomErrorCode 미구현 |

---

## 예외처리 매핑

| ID | 상황 | 처리 |
|---|---|---|
| EX-NOTI-01 | 탈퇴한 모임에 대한 알림 클릭 | 읽음 처리는 정상 적용, 화면 이동 시도 시 대상 API가 `403 4045`(⚠️ CustomErrorCode 미구현) 반환 → FE가 "탈퇴한 모임" 모달 노출 |
| EX-NOTI-02 | 시스템 알림(NOTI-005/006) 클릭 | 이동 없음 (정상 동작, 예외 아님) |
| EX-NOTI-03 | 재발송 대상 상태가 완료로 바뀐 경우(조건입력/투표 완료) | 해당 알림 자동 읽음 처리 + 이후 재발송 중단 (조건입력·투표 제출 API 내부 로직, 본 문서 범위 밖) |
| EX-NOTI-04 | 09:00~22:00 시간대 밖에서 재발송 타이밍 도래 | 다음 발송 가능 시각(09:00)으로 이월 (서버 배치 로직, 본 문서 범위 밖) |
| EX-NOTI-05 | OS 알림 권한 거부 상태에서 알림 이벤트 발생 | 알림 데이터/알림함 목록은 정상 생성, OS 푸시 표시만 생략 |

---

## 남은 확인 필요 사항

이번 문서에서 못 정한 것들만 남겨뒀습니다 (위 5가지 이슈는 이미 결론 냄).

1. `GET /api/notifications/unread-status`를 `GET /api/home` 응답에 통합할지, 별도 엔드포인트로 둘지.
2. 기기 토큰 해제(`DELETE /api/devices/token`)를 로그아웃 API와 합칠지, FE가 따로 호출할지.
3. `기기_푸시_토큰` 신규 테이블 컬럼 최종 확정 (제안 DDL은 위 참고).
4. `[SCR-NOTI-01]` 구버전 프레임 실제 폐기 여부는 디자이너 확인 필요.

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

- 조건입력 완료/투표 완료 시 관련 알림 자동 읽음 처리 로직 → `조건입력-API-명세.md`, 장소 투표 API 문서(예정)
- 모임 상세/장소 추천 결과/장소 투표 화면 자체의 API → 각 도메인 문서
- 모임 탈퇴 시 403 처리 → `메인홈-API-명세.md`의 `GET /api/meetings/{meetingId}`
- 실제 FCM 발송, 재발송 배치 스케줄러 구현 → 별도 인프라/배치 설계 문서
