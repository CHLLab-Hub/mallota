# 말로타 (Malrota)

> **말로 타는 고속버스**  
> Voice-first accessible intercity bus booking assistant for older adults and digitally vulnerable users.

말로타는 고령자 및 디지털 취약계층이 복잡한 예매 화면을 직접 탐색하지 않아도, 자연어 음성을 통해 고속버스를 검색하고 좌석을 추천받아 예매할 수 있도록 돕는 AI 기반 서비스입니다.

## Project Overview

기존 고속버스 예매 서비스는 출발지, 목적지, 날짜, 시간, 버스, 좌석 등을 사용자가 여러 화면에서 직접 선택해야 합니다.

말로타는 이 과정을 **폼 기반 예매에서 대화 기반 예매로 전환**합니다.

예시:

> "내일 오전에 서울에서 대전 가려고 하는데 다리가 불편해서 앞쪽 창가 자리로 잡아줘."

AI는 사용자의 발화를 분석해 다음과 같은 예매 조건으로 변환합니다.

```json
{
  "departure": "서울",
  "arrival": "대전",
  "date": "2026-08-22",
  "time_preference": "오전",
  "passengers": 1,
  "seat_preferences": ["front", "window"],
  "accessibility": ["walking_difficulty"]
}
```

## Core Features

- 🎙️ 음성 기반 고속버스 검색
- 💬 자연어 기반 예매 조건 추출
- 🪑 사용자 상황에 맞춘 좌석 추천
- 👴 고령자 친화적 대형 UI
- 🔊 음성 안내(STT/TTS)
- ✅ 결제·예매 전 최종 확인 단계
- 🧠 IBM watsonx.ai 기반 의도 및 조건 분석

## Seat Recommendation Examples

| 사용자 표현 | 추천 조건 |
|---|---|
| "창밖을 보고 싶어요" | Window |
| "멀미가 심해요" | Front |
| "다리가 불편해요" | Front / 접근성 우선 |
| "옆에 사람이 없는 자리가 좋아요" | Solo-friendly |
| "아내랑 같이 앉고 싶어요" | Adjacent seats |
| "통로 쪽이 편해요" | Aisle |

## Tech Stack

### Frontend
- React
- Responsive Web UI
- IBM Speech integration (STT/TTS)

### Backend
- Node.js + Express
- REST API

### AI
- IBM watsonx.ai
- IBM Speech to Text
- IBM Text to Speech

### External Data
- 고속버스 운행정보 API (TAGO)
- Mock Seat / Booking API for hackathon demonstration

## Suggested Architecture

```text
User Voice
   ↓
Speech to Text
   ↓
watsonx.ai
   ├─ Intent Detection
   ├─ Departure / Arrival Extraction
   ├─ Date / Time Extraction
   ├─ Seat Preference Extraction
   └─ Accessibility Need Extraction
   ↓
FastAPI Backend
   ├─ Bus Search Service
   ├─ Seat Recommendation Engine
   └─ Booking / Mock Booking Service
   ↓
Confirmation UI
   ↓
Text to Speech
```

## Repository Structure

```text
malrota/
├── frontend/
│   └── src/
├── backend/
│   ├── app/
│   ├── services/
│   └── tests/
├── docs/
│   ├── architecture/
│   └── api/
├── .github/
│   ├── ISSUE_TEMPLATE/
│   └── pull_request_template.md
├── .env.example
├── .gitignore
├── CONTRIBUTING.md
└── README.md
```

## Getting Started

### 1. Clone

```bash
git clone <YOUR_REPOSITORY_URL>
cd malrota
```

### 2. Environment Variables

`.env.example`을 복사해 `.env`를 생성합니다.

Windows PowerShell:

```powershell
Copy-Item .env.example .env
```

macOS / Linux:

```bash
cp .env.example .env
```

실제 API Key는 `.env`에만 작성하고 GitHub에 올리지 않습니다.

### 3. Branch

새 기능을 개발할 때는 `main`에서 기능 브랜치를 생성합니다.

```bash
git checkout main
git pull
git checkout -b feat/voice-input
```

### 4. Commit

```bash
git add .
git commit -m "feat: add voice input"
git push origin feat/voice-input
```

그 후 GitHub에서 Pull Request를 생성합니다.

## Development Rules

- `main` 브랜치에 직접 push하지 않습니다.
- 하나의 브랜치는 하나의 기능 또는 수정사항을 담당합니다.
- 작업 전 반드시 `main`의 최신 변경사항을 반영합니다.
- API Key, 비밀번호, 인증 토큰은 절대 commit하지 않습니다.
- Pull Request를 통해 코드를 검토한 뒤 `main`에 병합합니다.

자세한 규칙은 [CONTRIBUTING.md](CONTRIBUTING.md)를 참고하세요.

## MVP Flow

```text
Voice Input
   ↓
Speech to Text
   ↓
Natural Language Parsing
   ↓
Bus Search
   ↓
Seat Recommendation
   ↓
User Confirmation
   ↓
Mock Booking
   ↓
Voice Confirmation
```

## Team

**2026 IBM End-to-End Hackathon — Team 1**

팀원 정보는 아래 형식으로 추가하세요.

| Name | Role | GitHub |
|---|---|---|
| Member 1 | Frontend | @username |
| Member 2 | Backend | @username |
| Member 3 | AI / watsonx | @username |
| Member 4 | Voice / Integration | @username |
| Member 5 | Data / UX | @username |

## GitHub Description

> Malrota is a voice-first intercity bus booking assistant for older adults and vulnerable users. It understands natural-language requests, recommends accessible seats, and guides users through a simple booking flow powered by IBM AI.

## License

This project currently does not include an open-source license.
