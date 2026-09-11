# 메인홈 API 개발 문서

> 기준 명세: `docs/메인홈-API-명세.md`
> 브랜치: `feat/#43`

---

## 구현 API 목록

| 메서드 | 엔드포인트 | 설명 |
|--------|-----------|------|
| GET | `/api/home` | 홈 메인화면 조회 |
| GET | `/api/meetings` | 모임 전체보기 (cursor 페이지네이션) |
| GET | `/api/meetings/{meetingId}` | 모임 상세 조회 |
| GET | `/api/meetings/{meetingId}/home` | 모임 홈 화면 조회 |
| DELETE | `/api/meetings/{meetingId}/leave` | 모임 나가기 |

---

## 추가/변경 파일

### Entity

| 파일 | 변경 내용 |
|------|----------|
| `MeetingStatus` | label 필드 추가 (`조건 입력중`, `투표 진행중`, `확정`, `완료`) |
| `ParticipantState` | `CONFIRMED("모임 확정")` enum 값 추가 |
| `Meeting` | `decrementParticipantCnt`, `decrementSubmittedCnt`, `updateCreator`, `complete` 메서드 추가 |
| `Participant` | `leave()` 메서드 추가 (leftAt 설정) |

### Repository

| 파일 | 추가 메서드 |
|------|-----------|
| `MeetingPurposeRepository` | `findByMeetingId`, `findAllByMeetingIdIn` |
| `ParticipantRepository` | `findAllByMeetingIdAndLeftAtIsNullOrderByJoinedAtAsc`, `findAllByUserIdAndLeftAtIsNullOrderByIdAsc`, `findAllByMeetingIdInAndLeftAtIsNullOrderByJoinedAtAsc` |

### DTO

| 파일 | 설명 |
|------|------|
| `MeetingDetailResponse` | 모임 상세 응답 (ConfirmedPlaceDto, TravelSummaryDto 포함) |
| `MeetingHomeResponse` | 모임 홈 화면 응답 (homeMessage, CTA 포함) |
| `MeetingItemDto` | 홈/전체보기 공용 모임 목록 아이템 |
| `HomeResponse` | 홈 메인화면 응답 (confirmedMeetings, myMeetings) |
| `MeetingListResponse` | 모임 전체보기 응답 (cursor 페이지네이션) |

### Service

| 파일 | 설명 |
|------|------|
| `MeetingDetailService` | 모임 상세 / 모임 홈 화면 조회 |
| `HomeService` | 홈 메인화면 / 모임 전체보기 조회 |
| `MeetingLeaveService` | 모임 나가기 |

### Controller

| 파일 | 추가 엔드포인트 |
|------|--------------|
| `HomeController` | `GET /api/home` |
| `MeetingController` | `GET /api/meetings`, `GET /api/meetings/{meetingId}`, `GET /api/meetings/{meetingId}/home`, `DELETE /api/meetings/{meetingId}/leave` |

### Global

| 파일 | 변경 내용 |
|------|----------|
| `CustomErrorCode` | `NOT_A_PARTICIPANT(4045)`, `CANNOT_LEAVE_COMPLETED_MEETING(4046)` 추가 |
| `GooglePlaceDetailsResponse` | `Location` 레코드 추가 (latitude, longitude) |
| `GooglePlacesClient` | `getPlaceDetails` field mask에 `location` 추가 |

---

## 핵심 로직

### 모임 상세 조회 (`MeetingDetailService.getMeetingDetail`)

```
1. 모임 조회 → 없으면 404(4042)
2. 현재 유저 참여 여부 확인 → 아니면 403(4045)
3. phase별 필드 분기
   - COMPLETED → inviteCode/inviteUrl null, canShare false
   - CONFIRMED|COMPLETED → confirmedPlace 실시간 조회 (Google API)
   - CONDITION_INPUT → recommendationReady 계산
4. 참여자 목록 가입 순 정렬, 유저 정보 배치 조회
```

### recommendationReady 계산

```
NEW_RESTRICTED 제외한 전체 참여자 수 == COND_DONE 참여자 수
```

### 모임 홈 화면 (`MeetingDetailService.getMeetingHome`)

서버가 status에 따라 배너 문구와 CTA를 조립해서 반환

| status | homeMessage | primaryActionLabel | primaryActionEnabled |
|--------|------------|-------------------|----------------------|
| CONDITION_INPUT | 아직 조건 입력 중이에요! | 추천 장소 확인 | false |
| VOTING | 이미 조건 입력이 완료된 모임이에요! | 추천 장소 확인 및 투표 | true |
| CONFIRMED | 모임이 확정됐어요, 모임 정보를 확인해보세요! | 확정된 모임 보러 가기 | true |

### 홈 메인화면 (`HomeService.getHome`)

```
confirmedMeetings: 참여 중인 CONFIRMED 모임, dDay 오름차순 정렬
myMeetings:        참여 중인 전체 모임, filter 적용 (ALL/UPCOMING/PAST), 가입 순
```

filter 기준:
- `ALL`: 전체
- `UPCOMING`: CONDITION_INPUT, VOTING, CONFIRMED
- `PAST`: COMPLETED

### 모임 전체보기 페이지네이션 (`HomeService.getMeetings`)

cursor = participant_id 기반. `id > cursor` 조건으로 다음 페이지 조회.

### 모임 나가기 (`MeetingLeaveService.leaveMeeting`)

```
1. COMPLETED 모임 → 400(4046)
2. 참여자 아님 → 403(4045)
3. 잔여 참여자 없으면 → meeting.complete()
4. 모임장이 나가면 → 가입일시 가장 빠른 잔여 참여자로 updateCreator
5. conditionCompleted=true이면 → submittedCnt 차감
```

---

## 미구현 사항 (추후 개발)

| 항목 | 사유 |
|------|------|
| `travelMinutes` (참여자별 이동시간) | `place_paths` 엔티티 미구현 |
| `travelSummary` (평균/최대 이동시간) | 동일 |
| `excludedFromRecommendation` | 3일 미입력 제외 로직 미구현, 현재 false 반환 |

---

## 테스트

| 테스트 파일 | 케이스 수 |
|------------|---------|
| `MeetingDetailServiceTest` | 10개 |
| `MeetingLeaveServiceTest` | 7개 |
| `HomeServiceTest` | 8개 |

**전체 테스트: 118개 / 실패: 0개**
