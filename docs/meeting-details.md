# 모임 상세 조회 API

## 개요
> 확정(CONFIRMED 이상) 모임의 상세 정보 (장소, 일정, 참여자별 출발지/이동 정보)를 조회한다.
> - `GET` /api/meetings/{meetingId}GET /api/meetings/{meetingId}/detail (MeetingRouteService.getMeetingDetail)

### 케이스별 응답 구조 구분

```text
- 기준 1: 모임 종료 여부      (status == COMPLETED 인지)
- 기준 2: 최종 확정 장소 존재  (confirmedPlaceId != null 인지, 전원 미투표면 null)
```

| | 장소 확정됨 | 전원 미투표 |
|---|---|---|
| **진행 전** (status=CONFIRMED) | place 있음, participants 있음, 이동정보 있음 | place=null, participants=[] |
| **종료** (status=COMPLETED) | place 있음, participants 있음, 이동정보=null | place=null, participants 있음(이동정보=null) |

```text
- "진행 전 + 전원 미투표"만 participants가 빈 배열([])이다.
  나머지 3케이스는 장소 확정 여부와 무관하게 participants 자체는 채워진다.

- "이동 정보(transferCnt/totalTime)"는 오직 "진행 전 + 장소 확정" 케이스에서만 채워진다.
  종료된 모임은 장소가 있어도 이동 정보를 다시 계산하지 않는다.
```

---

## 전체 흐름

```text
confirmedPlaceId == null (전원 미투표)
        ↓
    place = null   (Google 조회 자체를 시도하지 않음)

confirmedPlaceId != null
        ↓
RecommendedPlace 조회 (없으면 RECOMMENDED_PLACE_NOT_FOUND)
        ↓
GooglePlacesClient.getPlaceLocation() 호출
        ↓
   ┌────┴──────────────────────────┐
  성공              실패 (CustomException 또는 응답이 비어있음)
   ↓                               ↓
place 정상 채움           place.isFetchFailed = true
                 (name/address/category/location 모두 null)
```
- 구글 장소 조회가 실패해도 API 자체는 에러를 던지지 않고, `isFetchFailed: true`로 표시해 정상 응답한다. 프론트는 이 플래그로 "장소 정보를 못 불러왔다"는 UI를 그릴 수 있다.

---

## 참여자 목록 & 이동 정보 조회

```text
!isCompleted && confirmedPlaceId == null (진행 전 + 전원 미투표)
        ↓
participants = []  (참여자/선호조건 조회 자체를 생략)

그 외 모든 케이스
        ↓
참여자 목록 조회 (joinedAt 오름차순, IN 배치로 User/ParticipantPreference 조회)
        ↓
!isCompleted && place != null && !place.isFetchFailed  ?
        ↓ yes                              ↓ no
카카오맵 이동 정보 조회               이동 정보 없이 참여자만 반환
```

### 참여자별 이동 정보 조회 조건

```text
1. isCompleted == false   (모임이 아직 종료되지 않음)
2. placeInfo != null      (장소가 확정됨)
3. !placeInfo.isFetchFailed()  (구글 장소 조회에 성공함)
```

### 이동 정보 조회

- 참여자별 출발 위치 파악 후, `카카오맵 대중교통 경로 조회 API` 호출
  - 참여자 수만큼 반복 쿼리하지 않도록, User / ParticipantPreference는 IN 절로 한 번에 조회한다.
  - 경로 조회 특성상 카카오맵 API는 참여자 수만큼 호출된다.

```java
List<Long> userIds = participants.stream().map(Participant::getUserId).toList();
Map<Long, User> userById = userRepository.findAllById(userIds).stream()
        .collect(Collectors.toMap(User::getId, Function.identity()));

List<Long> participantIds = participants.stream().map(Participant::getId).toList();
Map<Long, ParticipantPreference> preferenceByParticipantId =
        participantPreferenceRepository.findAllByParticipantIdIn(participantIds).stream()
                .collect(Collectors.toMap(ParticipantPreference::getParticipantId, Function.identity()));
```

### 이동 정보 조회 실패 처리
- 카카오맵 대중교통 경로 조회는 참여자 한 명씩 개별 호출한다. 일부 참여자만 실패해도 나머지 참여자에는 영향이 없다.
- 조회에 실패한 참여자는 목록에서 빠지는 게 아니라, `transferCnt`/`totalTime`만 `null`로 채워져 응답에 포함된다.
```java
for (ParticipantDetail detail : participantDetails) {
    try {
        RouteDetail routeDetail = routeDetailService.getMyRouteDetail(...);
        routeByParticipantId.put(detail.participant().getId(), routeDetail);
    } catch (CustomException e) {
        log.info("[모임 상세] 참여자 이동 경로 조회 실패 => 이동 정보(transferCnt/totalTime) null ...");
        // put 하지 않음 → 해당 참여자만 transferCnt/totalTime null로 응답
    }
}
```
