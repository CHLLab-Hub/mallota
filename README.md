# 말로타 (Malrota)

말로 타는 고속버스. 고령자와 디지털 취약계층을 위한 음성 중심 고속버스 검색·좌석 추천 서비스입니다.

> 현재 저장소는 **개발 환경과 프로젝트 골격만 구성한 단계**입니다. 실제 TAGO 조회, watsonx 조건 추출, STT/TTS, 예매 기능은 아직 구현하지 않았습니다.

## 기술 구성

- Frontend: React, TypeScript, Vite
- Backend: Java 17, Spring Boot, Gradle
- Planned integrations: TAGO, IBM watsonx.ai, IBM Speech to Text/Text to Speech
- Booking and seat availability: MVP에서는 Mock 사용

Node.js는 React 개발과 빌드에만 사용하며 별도의 Node.js 백엔드는 두지 않습니다.

## 저장소 구조

```text
malrota/
├─ frontend/   React 웹 애플리케이션
├─ backend/    Spring Boot API 서버
├─ docs/       API 계약, 아키텍처, 데모 시나리오
└─ .github/    CI와 협업 템플릿
```

## 사전 준비

- Node.js 22 이상
- Java 17 이상
- Git

## 로컬 실행

### Frontend

```bash
cd frontend
npm install
copy .env.example .env.local
npm run dev
```

기본 주소는 `http://localhost:5173`입니다.

### Backend

Windows:

```powershell
cd backend
.\gradlew.bat bootRun
```

macOS/Linux:

```bash
cd backend
./gradlew bootRun
```

기본 주소는 `http://localhost:8080`이며 상태 확인 API는 `GET /api/health`입니다.

## 환경변수

실제 키는 저장소에 커밋하지 않습니다. 필요한 변수명은 다음 예시 파일을 참고합니다.

- `frontend/.env.example`
- `backend/.env.example`

프론트에는 비밀 키를 두지 않습니다. 외부 서비스 키는 백엔드 환경변수로만 전달합니다.

## 개발 원칙

- AI 출력은 백엔드에서 검증합니다.
- 출발지, 도착지, 날짜가 없으면 임의로 추정하지 않고 추가 질문합니다.
- 운행정보는 TAGO, 좌석과 예매는 Mock임을 코드와 화면에서 구분합니다.
- 조건 변경 후에는 기존 최종 확인 상태를 무효화합니다.
- 접근성 관련 발화는 추천에 필요한 동안만 사용하고 불필요하게 저장하지 않습니다.

## 문서

- [아키텍처](docs/architecture.md)
- [API 계약 초안](docs/api-contract.md)
- [데모 시나리오](docs/demo-scenarios.md)
- [좌석 추천 정책](docs/recommendation-policy.md)
- [기여 방법](CONTRIBUTING.md)
