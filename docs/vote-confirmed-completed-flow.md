# 장소 투표 마감 및 모임 자동 종료 처리

## 1. 개요
> 하나의 배치(`MeetingLifecycleScheduler`)가 매일 자정에 실행되어 처리된다.
```text
1. 장소 투표 마감 처리
   - 전원 투표 완료 시 즉시 확정
   - 마감 기한(N일)이 지나도록 투표가 안 끝나면 강제 확정

2. 모임 자동 종료 처리
   - 확정된 모임의 일정(scheduledDate)이 지나면 자동으로 종료 처리
```

### 배경
```text
문제 1) 장소 투표
  전원이 투표를 완료해야만 장소가 확정됨
  → 한 명이라도 투표를 안 하면 모임이 VOTING 상태에 영원히 머무름
  => 세부 정책 반영

문제 2) 모임 종료 여부
  MeetingRouteService는 LocalDateTime.now()로 실시간 비교해서 "종료 여부"를 판단했음
  HomeService / MeetingLeaveService는 저장된 Meeting.status(COMPLETED)를 신뢰
  → 같은 "종료"를 서로 다른 기준으로 판단하는 불일치 존재
  
해결)
- 날짜 단위 배치로 통일
- 모임 시간(scheduledTime)은 "몇 시부터 만나는지" 보여주기 위한 용도일 뿐,
  그 시각을 넘겼다고 즉시 종료 처리할 필요는 없다고 판단
  → 그 날짜(scheduledDate)가 지나 다음날 자정이 되면 그때 한 번에 종료 처리
```

---

## 2. 전체 흐름 
> 장소 투표 ~ 모임 확정 및 종료 처리
```text
전원 조건 입력 완료
        ↓
      VOTING
        ↓
   ┌────┴────┐
   │         │
전원 투표    마감 기한 경과 (배치)
   │         │
   └────┬────┘
        ↓
  confirmMeetingPlace()
        ↓
     CONFIRMED  ──(전체 참여자 알림)
        ↓
scheduledDate 경과 (배치)
        ↓
     COMPLETED
```

## 3. 구조
```text
매일 자정 (meeting.daily-batch-cron)
        ↓
MeetingLifecycleScheduler.run()
        ↓
① VoteService.finalizeExpiredPlaceVotes()   → 장소 투표 마감 기한 경과 건 처리
        ↓
② MeetingService.completeElapsedMeetings()  → 일정 경과한 CONFIRMED 모임 종료 처리
```

> **①을 먼저 실행하는 이유**
> - ①에서 VOTING → CONFIRMED로 전환된 모임이, 같은 배치 사이클 안에서 바로 ②의 종료 대상(scheduledDate가 이미 지난 CONFIRMED 모임)이 될 수도 있기 때문이다. 순서를 바꾸면 그 모임은 다음날 배치까지 하루 더 기다려야 한다.

```java
// MeetingLifecycleScheduler
@Scheduled(cron = "${meeting.daily-batch-cron}")
public void run() {
    ...
    voteService.finalizeExpiredPlaceVotes();
    meetingService.completeElapsedMeetings();
    ...
}
```

### 설정값
```yaml
place-vote:
  deadline-days: ${PLACE_VOTE_DEADLINE_DAYS:1}      # 장소 투표 마감 기한 (일)

meeting:
  daily-batch-cron: ${MEETING_DAILY_BATCH_CRON:0 0 0 * * *}   # 매일 자정
```

---

## 투표 마감
- "투표 시작 시점"은 별도 컬럼이 없고, 해당 모임의 장소 추천이 완료된 시각(`RecommendationRun.updatedAt`, status=COMPLETED)으로 판단한다.
```text
deadline = 오늘 - place-vote.deadline-days

각 VOTING 모임에 대해:
  placeVoteStartedAt = 그 모임의 추천 완료 시각
  placeVoteStartedAt이 없음         → 스킵 (아직 투표 시작도 안 함)
  placeVoteStartedAt > deadline   → 스킵 (아직 마감 기한 안 지남)
  placeVoteStartedAt <= deadline  → confirmMeetingPlace() 실행
```

```java
Map<Long, LocalDateTime> placeVoteStartedAtByMeetingId = recommendationRunRepository
        .findAllByMeetingIdInAndStatus(meetingIds, RecommendationStatus.COMPLETED)
        .stream()
        .collect(Collectors.toMap(
                RecommendationRun::getMeetingId,
                RecommendationRun::getUpdatedAt,
                (earlier, later) -> earlier.isAfter(later) ? earlier : later
        ));
```

## 장소 확정
- 장소가 확정되는 경로는 두 경우이나, 확정 로직 자체(`confirmMeetingPlace`)는 하나를 공유한다.
- 경로 A는 사람이 직접 트리거하고, 경로 B는 배치가 트리거한다는 차이만 있을 뿐, `득표 1위 집계 → 확정 → 전체 참여자 알림` 흐름은 동일하게 동작한다.
```text
[경로 A] 마지막 투표자가 투표
    createPlaceVote()
        ↓ (전원 투표 완료 감지, 같은 요청 트랜잭션 안에서 즉시 실행)
    confirmMeetingPlace(meeting)

[경로 B] 마감 기한 경과, 미완료 투표 존재
    MeetingLifecycleScheduler (매일 자정)
        ↓
    VoteService.finalizeExpiredPlaceVotes()
        ↓ (마감 기한 지난 VOTING 모임 순회)
    confirmMeetingPlace(meeting)
```

```java
private void confirmMeetingPlace(Meeting meeting) {
    List<CandidateDetail> ranked = rankCandidates(meeting.getId(), NO_VIEWER_PARTICIPANT_ID);
    Long confirmedPlaceId = ranked.isEmpty() ? null : ranked.get(0).recommendedPlaceId();
    meeting.confirmPlace(confirmedPlaceId);   // status = CONFIRMED

    // NOTI-004: 장소 확정 알림 (전체 참여자)
    // ...
}
```

### `NO_VIEWER_PARTICIPANT_ID`(-1L)를 쓰는 이유
- `confirmMeetingPlace()`는 특정 조회자에게 응답을 내려주는 게 아니라 시스템이 알아서 1위 장소를 뽑아 확정하는 것이라 "조회하는 유저" 자체가 없다. 그래서 실제로 존재할 수 없는 `-1L`을 넘기고, 결과에서도 `recommendedPlaceId()`만 사용한다 (`isVotedByMe`는 계산되지만 사용하지 않음)
- 두 경로(A/B)를 하나의 메소드로 합치기 전에는 경로 A가 마지막 투표자의 실제 `participantId`를 넘겼었는데, 경로 B(배치)는 애초에 "투표한 사람"이라는 개념이 없어 통일할 수 없었다. 그래서 파라미터 자체를 없애고 `NO_VIEWER_PARTICIPANT_ID`로 고정했다.

### 아무도 투표하지 않은 경우
- `confirmMeetingPlace()`는 득표 집계 결과가 비어 있어도 상태 전환은 항상 수행한다.
```text
- 집계 결과 있음  → 1위 장소로 confirmedPlaceId 지정 + status = CONFIRMED
- 집계 결과 없음  → confirmedPlaceId = null       + status = CONFIRMED
```

---

## 모임 자동 종료 처리
- 날짜만 비교하고 시각(`scheduledTime`)은 보지 않는다.
```text
MeetingService.completeElapsedMeetings()
        ↓
meetingRepository.findAllByStatusAndScheduledDateBefore(CONFIRMED, 오늘)
        ↓
각 모임: meeting.complete()   →  status = COMPLETED
```


