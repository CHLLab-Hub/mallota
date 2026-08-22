# 개발 및 협업 규칙

## 브랜치

`main`에 직접 Push하지 않습니다. 하나의 브랜치에는 하나의 기능만 포함합니다.

예시:

- `chore/project-setup`
- `feat/conversation-parse`
- `feat/tago-client`
- `feat/seat-recommendation`
- `fix/tago-timeout`
- `docs/api-contract`

## Pull Request

1. 작업 전 최신 `main`을 반영합니다.
2. API 계약을 변경한다면 프론트·백엔드 담당자와 먼저 공유합니다.
3. 로컬에서 프론트 빌드와 백엔드 테스트를 실행합니다.
4. 실제 API 키와 사용자 발화가 커밋되지 않았는지 확인합니다.
5. Pull Request 템플릿의 체크리스트를 작성합니다.

## 커밋 예시

```text
chore: initialize monorepo
feat(frontend): add conversation input screen
feat(backend): add bus search endpoint
test(backend): add seat recommendation tests
docs: define API contract
```

## 로컬 검증

```bash
cd frontend
npm run lint
npm run build
```

```bash
cd backend
./gradlew test
```
