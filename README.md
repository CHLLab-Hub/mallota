# 말로타 (Mallota)

말로 타는 고속버스. 고령자와 디지털 취약계층을 위한 음성 중심 고속버스 검색·좌석 추천 서비스입니다.

음성(또는 텍스트)으로 출발지·도착지·날짜·시간·인원·좌석 선호를 자연어로 말하면, 부족한 조건은
되물어 채우고 실제 TAGO 고속버스 운행정보를 조회해 규칙 기반으로 좌석을 추천한 뒤 예매까지
안내합니다. 좌석·예매는 Mock이지만 운행정보 조회는 실제 공공데이터를 사용합니다.

## 기술 구성

- Frontend: React, TypeScript, Create React App (`react-scripts`)
- Backend: Java 17, Spring Boot, Gradle
- 자연어 이해(NLU): 정규식 기반 룰 추출기(1차, 결정적) + IBM watsonx.ai(보조 — STT 오인식 교정, 화이트리스트 검증을 거친 보조 필드 추측)
- 음성 인식/합성: IBM Speech to Text/Text to Speech, Naver Clova Speech (환경변수로 공급자 전환)
- 운행정보: TAGO(국토교통부 공공데이터) 고속버스 실시간 조회
- 데이터베이스: PostgreSQL (Mock 예매 영속화), 대화 세션은 서버 메모리
- 좌석 추천: LLM이 아닌 백엔드 규칙 기반 엔진이 결정 ([정책](docs/recommendation-policy.md) 참고)
- 배포: 프론트엔드는 GitHub Pages, 백엔드는 Railway(Docker)

Node.js는 React 개발과 빌드에만 사용하며 별도의 Node.js 백엔드는 두지 않습니다.

## 저장소 구조

```text
mallota/
├─ frontend/   React 웹 애플리케이션
├─ backend/    Spring Boot API 서버
├─ ai/         NLU 프로토타입(Python FastAPI) — 백엔드와 별개로 운영되는 실험 코드
├─ docs/       API 계약, 아키텍처, 데모 시나리오, 좌석 추천 정책
└─ .github/    CI와 협업 템플릿
```

## 사전 준비

- Node.js 22 이상
- Java 17 이상
- PostgreSQL (로컬 실행 시 — 없으면 `backend/.env.example`을 참고해 접속 정보를 맞춰주세요)
- Git

## 로컬 실행

### Frontend

```bash
cd frontend
npm install
copy .env.example .env.local
npm start
```

기본 주소는 `http://localhost:3000`입니다.

### Backend

Windows:

```powershell
cd backend
copy .env.example .env
.\gradlew.bat bootRun
```

macOS/Linux:

```bash
cd backend
cp .env.example .env
./gradlew bootRun
```

기본 주소는 `http://localhost:8081`이며 상태 확인 API는 `GET /api/health`입니다. PostgreSQL이
실행 중이 아니면 부팅 자체가 실패하니, 먼저 로컬 DB를 띄우거나 `.env`의 `DB_URL`을 실제 접속
정보로 맞춰주세요.

TAGO·watsonx·STT/TTS 관련 환경변수(`TAGO_ENABLED`, `WATSONX_ENABLED` 등)가 비어 있거나
`false`이면 각 기능은 자동으로 Mock/룰베이스로 폴백되므로, 키가 없어도 로컬에서 전체 흐름을
확인할 수 있습니다.

## 환경변수

실제 키는 저장소에 커밋하지 않습니다. 필요한 변수명은 다음 예시 파일을 참고합니다.

- `frontend/.env.example`
- `backend/.env.example`

프론트에는 비밀 키를 두지 않습니다. 외부 서비스 키는 백엔드 환경변수로만 전달합니다.
개발 환경의 CORS 허용 출처는 기본적으로 `http://localhost:3000`이며 `CORS_ALLOWED_ORIGINS`로
변경할 수 있습니다.

## 배포

- Frontend: GitHub Pages (`npm run build` 결과를 `gh-pages` 브랜치에 배포). 저장소 이름에 맞춰
  빌드 시 `PUBLIC_URL`을 지정해야 정적 자산 경로가 올바르게 잡힙니다.
  ```bash
  cd frontend
  PUBLIC_URL=/mallota npm run build
  ```
- Backend: Railway(Docker)에 GitHub 연동으로 배포하며, main 브랜치 푸시 시 자동 재배포됩니다.
  Railway 프로젝트에 `backend/.env.example`의 환경변수를 동일하게 설정해야 합니다.

## 개발 원칙

- AI 출력은 백엔드에서 검증합니다. 특히 출발지·도착지는 룰베이스가 이미 검증한 값이나 세션에
  확정된 값만 신뢰하고, LLM의 추측은 지원 지역 화이트리스트로 재검증합니다.
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
