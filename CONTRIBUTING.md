# Malrota Contribution Guide

2026 IBM End-to-End Hackathon Team 1의 협업 규칙입니다.

## 1. 기본 원칙

1. `main` 브랜치에는 직접 push하지 않습니다.
2. 새로운 작업은 항상 별도의 브랜치에서 진행합니다.
3. 작업 시작 전 `main`을 최신 상태로 갱신합니다.
4. 하나의 Pull Request에는 가능한 한 하나의 목적만 포함합니다.
5. API Key와 비밀번호는 절대 Git에 commit하지 않습니다.

## 2. Branch Naming

### 기능 개발

```text
feat/<feature-name>
```

예:

```text
feat/voice-input
feat/watsonx-intent
feat/bus-search
feat/seat-recommendation
feat/senior-ui
```

### 버그 수정

```text
fix/<issue-name>
```

예:

```text
fix/stt-timeout
fix/mobile-layout
```

### 문서

```text
docs/<topic>
```

예:

```text
docs/readme
docs/api-spec
```

### 리팩터링

```text
refactor/<topic>
```

## 3. 작업 시작

```bash
git checkout main
git pull origin main
git checkout -b feat/my-feature
```

## 4. Commit Message

다음 prefix를 사용합니다.

| Prefix | 목적 |
|---|---|
| `feat` | 새로운 기능 |
| `fix` | 버그 수정 |
| `ui` | UI/UX 변경 |
| `refactor` | 코드 구조 개선 |
| `docs` | 문서 |
| `test` | 테스트 |
| `chore` | 환경설정 및 기타 작업 |

예:

```bash
git commit -m "feat: add watsonx intent extraction"
git commit -m "fix: handle empty destination"
git commit -m "ui: enlarge booking confirmation buttons"
```

## 5. Push

```bash
git push origin feat/my-feature
```

GitHub에서 Pull Request를 생성합니다.

## 6. Pull Request Rule

Pull Request에는 최소한 아래 내용을 작성합니다.

- 무엇을 변경했는지
- 왜 필요한지
- 어떻게 테스트했는지
- 관련 Issue 번호
- UI 변경이 있다면 스크린샷

가능하면 본인이 바로 Merge하지 않고 팀원 한 명 이상이 확인합니다.

## 7. Issue 연결

예를 들어 Issue #15를 해결하는 PR이라면 본문에 다음을 추가합니다.

```text
Closes #15
```

PR이 merge되면 Issue도 자동으로 닫힙니다.

## 8. Merge 후

```bash
git checkout main
git pull origin main
git branch -d feat/my-feature
```

원격 브랜치는 GitHub의 Delete branch 버튼으로 삭제할 수 있습니다.

## 9. 충돌을 줄이는 방법

- 같은 파일을 여러 명이 동시에 대규모 수정하지 않습니다.
- 담당 기능을 명확히 나눕니다.
- 작업 범위가 겹치면 개발 전에 팀 채널에 알립니다.
- 너무 오랫동안 브랜치를 유지하지 말고 작은 단위로 자주 merge합니다.

## 10. Recommended Ownership

예시:

```text
Frontend
- 화면
- 접근성 UI
- 좌석 선택 UI

Backend
- FastAPI
- API orchestration
- Booking service

AI
- watsonx.ai
- Prompt
- Intent / entity extraction

Voice
- Speech to Text
- Text to Speech

Data / Recommendation
- Bus API
- Seat recommendation logic
```

## 11. Secret Handling

다음과 같은 값은 절대로 commit하지 않습니다.

```text
IBM_CLOUD_API_KEY
WATSONX_PROJECT_ID
BUS_API_KEY
DATABASE_PASSWORD
ACCESS_TOKEN
```

실수로 GitHub에 API Key를 올렸다면 단순히 commit을 삭제하는 것으로 끝내지 말고 해당 Key를 폐기하고 새 Key를 발급하세요.
