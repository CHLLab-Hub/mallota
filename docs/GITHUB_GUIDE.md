# GitHub Quick Guide

## 작업 시작

```bash
git checkout main
git pull
git checkout -b feat/feature-name
```

## 작업 저장

```bash
git status
git add .
git commit -m "feat: describe change"
```

## GitHub에 업로드

```bash
git push origin feat/feature-name
```

이후 GitHub에서 Pull Request를 생성합니다.

## PR Merge 후

```bash
git checkout main
git pull
```

## 자주 쓰는 명령어

현재 상태:

```bash
git status
```

브랜치 목록:

```bash
git branch
```

브랜치 이동:

```bash
git checkout branch-name
```

최근 commit:

```bash
git log --oneline -10
```

원격 저장소 확인:

```bash
git remote -v
```

## Conflict가 발생한 경우

1. 충돌난 파일을 VS Code에서 엽니다.
2. `Current Change`와 `Incoming Change`를 확인합니다.
3. 필요한 코드만 남깁니다.
4. 충돌 표시를 모두 제거합니다.
5. 저장 후:

```bash
git add .
git commit
git push
```

혼자 판단하기 어려운 충돌은 강제로 덮어쓰지 말고 담당 팀원과 함께 해결합니다.
