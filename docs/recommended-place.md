# 추천 장소 기능

## 1. 개요

추천 장소 기능은 추천 지역(`RecommendedArea`)을 기준으로 참가자들의 활동 선호와 이동시간을 반영하여 실제 장소 후보를 생성하고, 최종 추천 장소 TOP 3를 선정하는 기능이다.

추천 장소 생성 시 Google Places API와 Google Routes Matrix API를 사용한다.

최종 추천 결과에는 참가자 선호 일치 수를 저장하며, 이동시간은 Google Routes API를 통해 조회 시점에 다시 계산한다.

---

## 2. 전체 흐름

```text
추천 지역 선택
    ↓
모임 참가자 조회
    ↓
참가자별 선호 조건 조회
    ↓
활동별 선택 횟수 계산
    ↓
장소 후보 개수 배분
    ↓
Google Places 장소 후보 검색
    ↓
참가자 → 장소 후보 이동시간 조회
    ↓
참가자 선호 + 최대 이동시간 일치 수 계산
    ↓
장소 후보 랭킹 계산
    ↓
TOP 3 선정
    ↓
RecommendedPlace 저장
    ↓
추천 장소 목록 / 상세 조회
```

---

## 3. 참가자 추천 조건 생성

각 참가자의 `ParticipantPreference`와 선택한 활동 정보를 이용하여 추천 계산에 사용할 조건을 생성한다.

```text
originIndex
preferenceTypes
maxTravelMinutes
```

`originIndex`는 Google Routes Matrix의 출발지 인덱스와 동일한 순서를 유지한다.

참가자의 활동 선호는 `ActivityType.getLabel()` 값을 사용한다.

예시:

```text
JAPANESE_FOOD → 일식
KARAOKE       → 노래방
SHOPPING      → 쇼핑
```

---

## 4. 장소 후보 개수 배분

전체 참가자가 선택한 활동별 선택 횟수를 계산한다.

예시:

```text
한식   3
볼링   2
노래방 1
```

최대 장소 후보 개수는 10개이며, 각 활동의 선택 비율에 따라 후보 개수를 배분한다.

기본 배분은 다음과 같이 계산한다.

```text
활동 선택 횟수 / 전체 선택 횟수 × 10
```

소수점 이하를 버린 뒤 남은 후보 수는 나머지가 큰 활동부터 추가 배분한다.

검색 순서는 다음 기준으로 고정한다.

```text
1. 선택 횟수 내림차순
2. 선택 횟수가 같으면 활동 이름 오름차순
```

이를 통해 동일한 조건에서는 장소 검색 순서가 항상 일정하도록 한다.

---

## 5. Google Places 장소 후보 생성

각 활동별로 다음 형태의 검색어를 생성한다.

```text
{추천 지역명} {활동명}
```

예시:

```text
홍대입구역 일식
홍대입구역 노래방
홍대입구역 쇼핑
```

Google Places Text Search 결과를 장소 후보로 변환한다.

장소 후보는 다음 정보를 가진다.

```text
googlePlaceId
latitude
longitude
candidateOrder
preferenceType
```

`preferenceType`은 Google이 반환하는 장소 카테고리가 아니라 해당 장소를 검색할 때 사용한 MOIVE 활동 타입이다.

예를 들어:

```text
검색어: 홍대입구역 쇼핑
검색 결과: 홍대거리

preferenceType = "쇼핑"
```

따라서 최종 추천 결과의 category 역시 `"쇼핑"`이 된다.

---

## 6. 중복 장소 처리

서로 다른 활동 검색에서 동일한 `googlePlaceId`가 반환될 수 있다.

이 경우 동일한 장소를 여러 후보로 추가하지 않는다.

```text
먼저 검색된 활동 타입을 해당 장소의 preferenceType으로 사용
이후 동일 googlePlaceId가 발견되면 제외
```

검색 순서는 활동 선택 횟수와 이름을 기준으로 고정되어 있으므로 동일 조건에서 중복 장소의 category 결정도 일정하게 유지된다.

---

## 7. 이동시간 계산

모든 참가자의 출발지에서 모든 장소 후보까지 Google Routes Matrix API를 이용해 대중교통 이동시간을 조회한다.

각 장소 후보별로 다음 값을 계산한다.

```text
averageTravelSeconds
maxTravelSeconds
```

- `averageTravelSeconds`: 참가자들의 평균 이동시간
- `maxTravelSeconds`: 참가자 중 가장 긴 이동시간

경로가 존재하지 않는 경우 해당 참가자와 장소 간 매칭에는 포함하지 않는다.

---

## 8. 선호 일치 수 계산

장소 후보마다 몇 명의 참가자 조건을 만족하는지 계산한다.

참가자 한 명이 장소 후보와 매칭되기 위한 조건은 다음과 같다.

```text
1. 참가자의 preferenceTypes에 장소의 preferenceType이 포함되어야 한다.
2. 해당 장소까지 이동 가능한 경로가 존재해야 한다.
3. 최대 이동시간 조건을 만족해야 한다.
```

예시:

```text
참가자 선호:
[한식, 카페]

장소:
preferenceType = 한식

→ 선호 조건 일치
```

최대 이동시간이 설정되어 있다면:

```text
travelSeconds <= maxTravelMinutes × 60
```

을 만족해야 한다.

`maxTravelMinutes == null`인 경우 이동시간 제한은 적용하지 않으며, 이동 가능한 경로만 존재하면 된다.

---

## 9. 장소 랭킹

장소 후보는 다음 순서로 정렬한다.

```text
1. preferenceMatchCnt 내림차순
2. averageTravelSeconds 오름차순
3. maxTravelSeconds 오름차순
4. candidateOrder 오름차순
```

즉,

```text
더 많은 참가자의 선호를 만족하는 장소
    ↓
평균 이동시간이 짧은 장소
    ↓
가장 오래 이동하는 참가자의 이동시간이 짧은 장소
    ↓
먼저 생성된 후보
```

순으로 우선순위를 결정한다.

최종적으로 상위 3개의 장소를 선정한다.

---

## 10. RecommendedPlace 저장

최종 TOP 3만 `RecommendedPlace`에 저장한다.

저장 정보:

```text
recommendedAreaId
googlePlaceId
category
preferenceMatchCnt
```

`category`에는 Google Places의 카테고리가 아닌 장소 후보 생성에 사용된 MOIVE 활동 타입을 저장한다.

예시:

```text
googlePlaceId       = Google Place ID
category            = "일식"
preferenceMatchCnt  = 2
```

Google Routes API를 통해 계산한 이동시간은 DB에 저장하지 않는다.

목록 또는 상세 조회 시 현재 참가자 출발지와 Google Routes API를 이용하여 다시 계산한다.

---

## 11. 추천 장소 목록 조회

추천 장소 목록 조회 시 해당 추천 지역에 저장된 `RecommendedPlace`가 없는 경우 장소 추천 생성을 수행한다.

```text
RecommendedPlace 존재
    ↓
바로 조회

RecommendedPlace 없음
    ↓
장소 추천 생성
    ↓
TOP 3 저장
    ↓
조회
```

목록 응답에는 다음 정보가 포함된다.

```text
recommendedPlaceId
name
category
preferenceMatchRate
averageTravelTime
maxTravelTime
preferenceMatchCnt
```

`preferenceMatchRate`는 다음과 같이 계산한다.

```text
preferenceMatchCnt / 현재 참가자 수 × 100
```

이동시간은 초 단위 결과를 분 단위로 변환하여 반환한다.

---

## 12. 추천 장소 상세 조회

추천 장소 상세 조회 시 Google Place Details API를 이용해 장소 상세 정보를 조회한다.

응답 정보:

```text
recommendedPlaceId
name
category
address
preferenceMatchCnt
averageTravelTime
imageUrls
```

장소 이름에 `|`가 포함된 경우 첫 번째 부분만 사용한다.

예시:

```text
다몽집 | damongzip
```

→

```text
다몽집
```

`category`는 Google Place Details의 장소 타입을 사용하지 않고 `RecommendedPlace`에 저장된 MOIVE 활동 타입을 사용한다.

---

## 13. 외부 API 사용

### Google Places API

사용 목적:

```text
장소 후보 검색
장소 이름 조회
주소 조회
장소 사진 조회
장소 위치 조회
```

장소 식별에는 `googlePlaceId`를 사용한다.

### Google Routes Matrix API

사용 목적:

```text
참가자 출발지 → 장소 후보 대중교통 이동시간 조회
```

Routes에서 계산한 이동시간은 영구 저장하지 않고 추천 계산 및 조회 시 사용한다.

---

## 14. 주요 서비스

```text
ParticipantRecommendationConditionService
- 참가자 선호조건을 추천 계산 데이터로 변환

PlacePreferenceAllocationService
- 활동별 선택 횟수 집계 및 검색 순서 결정

CandidateAllocationService
- 활동별 장소 후보 개수 배분

PlaceCandidateGenerationService
- Google Places를 이용한 장소 후보 생성

PlaceRouteService
- 참가자 → 장소 후보 Routes Matrix 조회

RouteMatrixService
- 이동시간 결과 계산

PlaceMatchService
- 참가자 선호 및 이동시간 조건 일치 계산

PlaceRankingService
- 장소 후보 정렬 및 TOP 3 선정

PlaceRecommendationGenerationService
- 전체 장소 추천 생성 흐름 통합

RecommendationService
- 추천 장소 목록 및 상세 조회
```

---

## 15. 최종 추천 생성 흐름

```text
RecommendedArea
      ↓
Participant
      ↓
ParticipantPreference
      ↓
ParticipantRecommendationCondition
      ↓
활동별 선택 횟수
      ↓
후보 개수 배분 (최대 10)
      ↓
Google Places Text Search
      ↓
PlaceCandidate
      ↓
Google Routes Matrix
      ↓
PlaceRouteResult
      +
PlaceMatchResult
      ↓
PlaceRankingService
      ↓
TOP 3
      ↓
RecommendedPlace
```