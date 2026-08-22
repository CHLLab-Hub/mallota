# 아키텍처

## 기본 흐름

```text
사용자 음성
  → STT
  → React
  → Spring Boot
      → watsonx.ai 조건 추출
      → TAGO 운행정보 조회
      → 규칙 기반 좌석 추천
      → Mock 예매
  → React
  → TTS와 화면 안내
```

## 책임 분리

### Frontend

- 음성 또는 텍스트 입력
- 인식된 문장의 수정 수단 제공
- 버스·추천 좌석·최종 조건 표시
- 로딩, 재시도, 오류 안내
- 대형 글씨, 충분한 대비, 키보드 접근성

### Backend

- AI 출력과 사용자 입력 검증
- 대화 상태 관리
- 터미널명과 TAGO 코드 매핑
- 외부 API 호출 및 Timeout 처리
- 좌석 추천 규칙 실행
- 사용자 확인 이후에만 Mock 예매 생성

## MVP 상태 흐름

```text
COLLECTING_CONDITIONS
  → READY_TO_SEARCH
  → BUS_SELECTED
  → SEAT_RECOMMENDED
  → AWAITING_CONFIRMATION
  → BOOKED
```

날짜, 버스 또는 좌석 조건이 변경되면 기존 확인 상태를 무효화하고 필요한 이전 상태로 돌아갑니다.
