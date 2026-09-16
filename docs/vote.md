# 투표 기능 (일정 투표 & 장소 투표)
- 일정 투표 현황 조회:`GET` /api/meetings/{meetingId}/date-votes/result
- 장소 투표: `POST` /api/meetings/{meetingId}/place-votes
- 장소 투표 현황 조회: `GET` /api/meetings/{meetingId}/place-votes/result

## 1. 일정 투표 현황 조회
- 일정 투표는 참여자별 모임 조건 입력 시 진행된다.

```text
GET /api/meetings/{meetingId}/date-votes/result
        ↓
Meeting 조회 + 참여자 조회 권한 검증
        ↓
   ┌─────────────┴─────────────┐
   │                           │
모임 생성 시 일정 확정          일정 미확정
(scheduledDate/Time 있음)       ↓
   │                     DateVote 집계
   ↓                           │
isVoteSkipped = true      isVoteSkipped = false
  확정 일정 1건만 반환           후보 TOP 3 반환
```

### 랭킹 정렬 기준
- 일자별 총 득표수 기준으로 순위를 산출하며, 시간은 해당 일자에 투표된 시간대 중 가장 늦은 시간으로 결정한다.
```text
- 1순위: 날짜별 득표수 내림차순
- 2순위: 날짜 오름차순
```

---

## 2. 장소 투표
- 한번의 투표로 여러 장소 동시에 선택 가능(최소 1곳이상 선택), 장소 ID는 distinct 처리
- 마지막 투표자가 투표 시, 
```text
POST /api/meetings/{meetingId}/place-votes
        ↓
Meeting 조회
        ↓
모임 상태 검증 (VOTING 단계에서 가능)
   CONFIRMED, COMPLETED  → PLACE_VOTE_CLOSED
   CONDITION_INPUT       → PLACE_VOTE_NOT_STARTED
        ↓
완료된 추천(RecommendationRun) 존재 확인 → 없으면 PLACE_VOTE_NOT_STARTED
        ↓
요청 유저가 유효 참여자인지 확인 → 아니면 PLACE_VOTE_ACCESS_DENIED
        ↓
기투표 여부 확인 → 이미 투표했으면 PLACE_VOTE_ALREADY_DONE
        ↓
요청한 모든 장소 ID들이 이 모임의 추천 장소 목록에 속하는지 확인 → 아니면 PLACE_VOTE_INVALID_PLACE
        ↓
PlaceVote 저장
        ↓
투표자 수 == 참여자 수 ?
        ↓ yes
confirmMeetingPlace()  →  모임 장소 확정 + 모임 상태 전환 (CONFIRMED) + 전체 참여자 알림
```

---

## 3. 장소 투표 현황 조회

```text
GET /api/meetings/{meetingId}/place-votes/result
        ↓
Meeting 조회 + 참여자 투표 권한 검증
        ↓
rankCandidates(meetingId, 조회자 participantId)
        ↓
정렬된 후보 중 TOP 3 반환
```

### 랭킹 정렬 기준
```text
- 1순위: 장소별 득표수 내림차순
- 2순위: 참여자 출발지 <-> 장소 간 직선거리 평균값 오름차순
       (구글맵 장소 정보 조회 실패 시 거리 계산 불가 → 취향 일치 수(preferenceMatchCnt) 내림차순으로 대체)
- 3순위: recommendedPlaceId 오름차순
```

```java
private static final Comparator<CandidateDetail> CANDIDATE_COMPARATOR = Comparator
        .comparingInt(CandidateDetail::voterCnt).reversed()
        .thenComparing(VoteService::compareByDistanceOrPreferenceMatch)
        .thenComparing(CandidateDetail::recommendedPlaceId);
```
---

## 4. 에러 코드

| 코드 | 상황 |
|---|---|
| `PLACE_VOTE_ACCESS_DENIED` | 모임 참여자가 아닌 유저가 장소 투표 시도 |
| `VOTE_ACCESS_DENIED` | 모임 참여자가 아닌 유저가 투표 현황 조회 시도 |
| `PLACE_VOTE_ALREADY_DONE` | 이미 투표한 참여자가 재투표 시도 |
| `PLACE_VOTE_CLOSED` | 이미 장소가 확정된(CONFIRMED/COMPLETED) 모임에 투표 시도 |
| `PLACE_VOTE_NOT_STARTED` | 조건 입력 단계이거나, 추천이 아직 생성되지 않은 모임에 투표 시도 |
| `PLACE_VOTE_INVALID_PLACE` | 이 모임에 추천되지 않은 장소 id가 포함됨 |

---

