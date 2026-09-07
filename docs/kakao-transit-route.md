# 카카오맵 대중교통 경로 조회 API를 활용한 이동 시간 및 경로 조회

## 1. 개요

모임 참여자별로 각자의 출발지에서 추천 장소까지의 대중교통 경로 정보를 제공하기 위해 `카카오맵 대중교통 경로 조회 API`를 활용합니다.

### 특이사항
- 카카오 API는 약관상 응답 데이터 저장이 불가하여, 이동 시간 및 경로 정보가 필요한 화면별로 '실시간 호출'합니다.
- 대중교통 경로 조회 API는 대중교통 탑승~하차 구간만 제공하므로(출발지~첫 승차 지점, 마지막 하차 지점~도착지 도보 정보 제공 x),
이 부분은 서비스 레벨에서 직선 도보 구간으로 보완합니다. 
- 대중교통 경로 조회 API가 제공하는 이동 경로상의 좌표들은 매우 촘촘한 관계로 꺾이는 지점만 남기고 단순화하는 과정을 포함합니다.

---

## 2. 전체 흐름

```text
사용자 위치(userLocation), 추천 장소 위치(placeLocation)
        ↓
RouteDetailService.getMyRouteDetail()
        ↓
KakaoTransitClient.getTransitRoute() ─── GET dapi.kakao.com/v2/routing/publictraffic
        ↓
KakaoTransitRouteResponse (카카오 원본 응답)
        ↓
status 검사 (OK / 그 외)
        ↓ OK
routes[0](최적 경로) 선택
        ↓
이동 수단별(SUBWAY/BUS/WALKING) 시간 집계 + RouteStep 목록 구성 (좌표 단순화 포함)
        ↓
userLocation → 첫 탑승 지점, 마지막 하차 지점 → placeLocation 도보 구간 추가
        ↓
RouteDetailResponse (API 응답 규격)
```

---

## 3. 패키지 구조

```text
domain/route/
├── client/
│   ├── KakaoTransitClient.java       # 인터페이스 (Mock 가능하도록 추상화)
│   └── KakaoTransitClientImpl.java   # RestClient로 실제 카카오 API 호출 + 예외 처리
├── dto/
│   ├── KakaoTransitRouteResponse.java  # 카카오 원본 응답 1:1 매핑
│   ├── KakaoTransitErrorResponse.java  # 카카오 에러 응답
│   ├── RouteDetailResponse.java        # 추천 장소 상세 조회 (이동 경로) API 응답 DTO (*수정 필요)
│   └── Location.java                   # 공용 좌표 { latitude, longitude }
├── type/
│   ├── TransitType.java              # 이동 수단: SUBWAY, BUS, WALKING
│   └── KakaoTransitStatusType.java   # 대중교통 경로 조회 API 응답 status: OK, NO_RESULTS 등
├── service/
│   └── RouteDetailService.java       # 매핑/이동 시간 계산/도보 구간 보완 등 핵심 로직
└── util/
    └── PathPointsSimplifier.java     # 'Douglas-Peucker' 알고리즘 기반 좌표 단순화 (JTS 라이브러리 활용)
```

---

## 4. 카카오맵 대중교통 경로 조회 API 연동

### 요청
- `GET https://dapi.kakao.com/v2/routing/publictraffic`
- 인증 헤더: `Authorization: KakaoAK ${apiKey}`
- 좌표 파라미터: `start_x`, `start_y`, `end_x`, `end_y`
  - 카카오 좌표계 기준 'x = 경도(longitude), y = 위도(latitude)'
    → 반대로 넣기 쉬운 부분이라 'KakaoTransitClientImpl.buildTransitRouteUri()'에서 
    Location(latitude, longitude) → start_x = longitude, start_y = latitude로 명시적으로 매핑합니다.
- API 키: `application.yaml`의 `kakao.transit.api-key` (`${KAKAO_TRANSIT_API_KEY}` 환경변수)로 주입

### 인터페이스 추상화
외부 API 클라이언트는 테스트에서 Mock/직접 생성이 가능하도록 인터페이스로 분리했습니다.
```java
public interface KakaoTransitClient {
    KakaoTransitRouteResponse getTransitRoute(Location start, Location end);
}
```

### 응답
카카오 응답의 `status` 필드는 `KakaoTransitStatusType`(ENUM)으로 매핑해서 명시적으로 분기합니다.

| status | 의미 | 처리 |
| --- | --- | --- |
| `OK` | 정상 조회 | `routes[0]`(최적 경로)의 정보로 응답 구성 |
| `STARTNODES_NULL`, `ENDNODES_NULL`, `INVALID_REQUEST` | 좌표로 경로 탐색 자체가 불가능 | 요청 좌표 생성 로직 문제일 가능성이 높은 오류로 취급, `KAKAO_MAP_API_INVALID_REQUEST` 예외 |
| `EQUAL_POINTS`, `NO_RESULTS` | 좌표는 유효하지만 대중교통 경로가 없는 정상적인 비즈니스 결과 | 버그가 아니므로 별도 코드로 분리, `TRANSIT_ROUTE_NOT_FOUND` 예외 |

**`NO_RESULTS`(도보로만 이동 가능한 경우 등)는 필요 시 '도보 경로 API' 연동을 추가로 진행할 예정입니다.

---

## 5. 예외 처리
카카오가 HTTP 레벨에서 비정상 응답(4xx/5xx)을 내려줄 때는
KakaoTransitClientImpl 이를 잡아서 CustomErrorCode(3xxx 대)로 변환합니다. 
카카오 응답 바디의 `errorType`/`message`도 함께 로그에 남깁니다.

| Code | HTTP Status | 설명                                                                                |
| --- | --- |-----------------------------------------------------------------------------------|
| `3001` `KAKAO_MAP_API_CONFIG_ERROR` | 500 | 서버 내부 설정(앱키 등) 문제로 추정되는 카카오 401 응답, 또는 400 응답의 `errorType`이 `ValidationError`인 경우 |
| `3002` `KAKAO_MAP_API_QUOTA_EXCEEDED` | 500 | 400 응답 메시지에 호출 한도 초과로 보이는 키워드가 포함된 경우                                             |
| `3003` `KAKAO_MAP_API_SERVER_ERROR` | 503 | 카카오 서버 장애/점검으로 추정되는 503, 또는 그 외 400                                               |
| `3004` `KAKAO_MAP_API_CONNECTION_ERROR` | 502 | `RestClientResponseException`이 아닌 예외 (타임아웃, 네트워크 오류 등)                            |
| `3005` `KAKAO_MAP_API_INVALID_RESPONSE` | 500 | 카카오 응답 바디가 비어있거나 `status` 필드가 없는 경우                                               |
| `3006` `KAKAO_MAP_API_INVALID_REQUEST` | 500 | 카카오 응답 `status`가 `STARTNODES_NULL`/`ENDNODES_NULL`/`INVALID_REQUEST` (경로 탐색 불가)   |
| `3007` `TRANSIT_ROUTE_NOT_FOUND` | 404 | 카카오 응답 `status`가 `EQUAL_POINTS`/`NO_RESULTS` (경로 없음)                              |

---

## 6. 구현

### 이동 시간 계산
카카오 응답의 시간 단위는 '초', API 응답은 '분'(반올림) 단위입니다.
- `totalTime`: routes[0].properties.totalTime을 그대로 분으로 환산
- `busTime` / `subwayTime`: 각 step의 time을 이동 수단별로 합산 후 분으로 환산
- `walkTime`: 'totalTime - (busTime + subwayTime)' (음수 방지를 위해 'Math.max(0, ...)')

### 이동 경로 좌표 단순화
카카오가 제공하는 이동 경로 상의 좌표가 매우 촘촘해,
JTS 라이브러리의 `Douglas-Peucker 알고리즘`으로 각 step(구간)별 좌표를 단순화하여 응답합니다. (꺾이는 지점만 보존)

(Douglas-Peucker 알고리즘 특성상 각 구간의 시작점/끝점은 단순화 후에도 항상 보존됩니다. (구간 접점이 끊기지 않음))

### 처음/끝 도보 구간 보완
카카오 응답의 `routes[0].steps`는 실제 대중교통 탑승~하차 구간만 제공합니다. 
즉 `userLocation → 첫 탑승 지점`, `마지막 하차 지점 → placeLocation` 도보 구간은 제공하지 않습니다.
이 두 구간은 '직선 도보(WALKING)'로 `routeSteps`의 맨 앞/뒤에 추가해서, 지도에서 전체 이동 경로가 끊기지 않고 이어지도록 합니다.

```text
[추가] userLocation → 첫 탑승 지점      (WALKING)
[카카오 원본] 첫 탑승 지점 → ... → 마지막 하차 지점  (SUBWAY/BUS/WALKING)
[추가] 마지막 하차 지점 → placeLocation  (WALKING)
```

---

## 7. 응답 예시
> **'추천 장소 상세 조회 (이동 경로) API'** Response Body 일부 (타 도메인 제외)
```json
{
  "success": true,
  "code": 200,
  "message": "요청에 성공했습니다.",
  "data": {
    "userLocation": { "latitude": 37.5788132079661, "longitude": 126.901364655063 },
    "placeLocation": { "latitude": 37.5510324090502, "longitude": 126.91228338125131 },
    "routeSteps": [
      { "type": "WALKING", "path": [ { "latitude": 37.578813, "longitude": 126.901365 }, ... ] },
      { "type": "SUBWAY", "path": [ ... ] },
      { "type": "WALKING", "path": [ ... ] }
    ],
    "totalTime": 22,
    "walkTime": 14,
    "busTime": 0,
    "subwayTime": 7,
    "fare": 1550,
    "landingUrl": "https://map.kakao.com/link/by/traffic/..."
  }
}
```
