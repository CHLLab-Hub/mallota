# API 계약 초안

이 문서는 프론트엔드와 백엔드 사이의 임시 단일 기준입니다. 구현 전 요청·응답 필드명과 enum을 확정합니다.

## 상태 확인

### `GET /api/health`

```json
{
  "status": "UP",
  "service": "malrota-backend"
}
```

## 조건 추출

### `POST /api/conversation/parse`

```json
{
  "text": "내일 오전 서울에서 대전 가는데 다리가 불편하고 창가가 좋아요"
}
```

```json
{
  "intent": "BUS_SEARCH",
  "departure": "서울",
  "arrival": "대전",
  "date": "2026-08-23",
  "timePreference": "MORNING",
  "passengers": 1,
  "seatPreferences": ["FRONT", "WINDOW"],
  "accessibilityNeeds": ["WALKING_DIFFICULTY"],
  "missingFields": []
}
```

필수값은 `departure`, `arrival`, `date`입니다. 누락 여부는 AI 응답과 별개로 백엔드에서 다시 계산합니다.

## 운행편 검색

### `POST /api/buses/search`

구조화된 조건으로 TAGO 운행정보를 조회합니다. TAGO 응답 DTO를 프론트에 직접 노출하지 않고 내부 `BusSchedule` 형식으로 변환합니다.

## 좌석 추천

### `POST /api/seats/recommend`

Mock 좌석과 사용자 조건을 입력받아 추천 좌석 및 설명 가능한 추천 이유를 반환합니다.

## Mock 예매

### `POST /api/bookings/confirm`

`AWAITING_CONFIRMATION` 상태에서 사용자의 명시적인 확인이 있을 때만 Mock 예매를 생성합니다.
