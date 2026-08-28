# CI/CD 공부 자료

> 이 프로젝트에서 구축한 CI/CD를 처음부터 이해하기 위한 문서입니다.

---

## 1. CI/CD란?

| 용어 | 의미 |
|------|------|
| **CI** (Continuous Integration) | 코드를 push할 때마다 자동으로 빌드 + 테스트 실행 |
| **CD** (Continuous Deployment) | 테스트가 통과되면 자동으로 서버에 배포 |

**왜 쓰나요?**
- 매번 손으로 서버에 접속해서 배포하지 않아도 됨
- 테스트 실패하면 배포가 막혀서 버그가 운영 서버에 올라가는 사고를 방지

---

## 2. 전체 흐름

```
[개발자] main 브랜치에 push
           │
           ▼
   ┌─────────────────────────────────────────┐
   │           GitHub Actions                │
   │                                         │
   │  1단계 (test 잡)                         │
   │    └─ ./gradlew test  ──실패→  배포 중단 │
   │              │ 성공                      │
   │              ▼                          │
   │  2단계 (deploy 잡)                       │
   │    ├─ Docker 이미지 빌드                  │
   │    ├─ ECR에 이미지 업로드                 │
   │    ├─ deploy.zip 생성 → S3 업로드        │
   │    └─ CodeDeploy에 배포 명령             │
   └─────────────────────────────────────────┘
           │
           ▼
   ┌─────────────────────────────────────────┐
   │            AWS CodeDeploy               │
   │                                         │
   │  S3에서 deploy.zip 수신                  │
   │    ├─ stop.sh   → 기존 컨테이너 종료      │
   │    ├─ 파일 복사  → scripts/ EC2에 배치    │
   │    └─ start.sh  → 새 컨테이너 실행        │
   └─────────────────────────────────────────┘
           │
           ▼
   [EC2] ECR에서 새 이미지 pull → 앱 실행
```

---

## 3. 각 구성요소 설명

### GitHub Actions

`.github/workflows/deploy.yml` 파일에 정의된 자동화 도구입니다.
main에 push되거나 PR이 올라오면 자동으로 실행됩니다.

**이 프로젝트의 Job 구성:**

```
┌────────────────┐         ┌──────────────────────────────┐
│   test 잡      │  성공 시 │        deploy 잡              │
│                │ ───────▶│                              │
│ ./gradlew test │         │ Docker 빌드 → ECR 업로드       │
│ ./gradlew jar  │         │ zip 생성 → S3 업로드           │
│                │         │ CodeDeploy 배포 명령           │
└────────────────┘         └──────────────────────────────┘
  PR / main push 모두 실행      main push 시에만 실행
```

---

### Docker

애플리케이션과 실행 환경을 하나의 **이미지**로 묶어서 어디서든 동일하게 실행하는 기술입니다.

```
Dockerfile  →  이미지 빌드  →  컨테이너 실행
 (레시피)       (요리 완성본)    (실제로 돌아가는 것)
```

**이 프로젝트의 Dockerfile 구조 (2단계 빌드):**

```
1단계 (builder)          2단계 (실행)
─────────────────        ──────────────────────────
JDK 이미지 사용          JRE 이미지 사용 (더 가벼움)
gradlew bootJar 실행  →  JAR 파일만 가져와서 실행
```

> 왜 2단계로 나누냐? → JDK(빌드 도구)는 실행할 때 필요 없어서 JRE만 포함해 이미지 크기를 최소화합니다.

> `RUN ./gradlew bootJar -x test` 에서 `-x test`로 테스트를 생략하는 이유:
> CI/CD 파이프라인에서 `./gradlew test`로 이미 검증을 마친 후 Dockerfile을 실행하기 때문에 중복 실행을 방지하고 속도를 높이기 위해 생략합니다.

---

### ECR (Amazon Elastic Container Registry)

GitHub Actions에서 빌드한 Docker 이미지를 EC2가 받으려면 **중간 저장소**가 필요합니다. 그 역할을 ECR이 합니다.

```
GitHub Actions ──이미지 push──▶ ECR ──이미지 pull──▶ EC2
```

**ECR을 선택한 이유:**
- AWS 인프라(EC2, CodeDeploy, S3)와 IAM으로 통합 → 별도 토큰 관리 불필요
- EC2에서 이미지 pull 시 IAM 역할로 자동 인증
- 소규모 앱 기준 비용 거의 무료 수준

---

### AWS S3

배포 묶음 파일(`deploy.zip`)을 임시로 저장하는 용도로 사용합니다.
CodeDeploy는 S3에서 zip을 받아와야만 EC2에 배포할 수 있습니다.

```
GitHub Actions
  └─ deploy.zip 생성 (appspec.yml + scripts/)
       └─ S3 업로드
            └─ CodeDeploy가 S3에서 받아서 EC2에 전달
```

**deploy.zip 안에 들어있는 것:**
```
deploy.zip
├── appspec.yml      # CodeDeploy 배포 지시서
└── scripts/
    ├── start.sh     # 컨테이너 시작 스크립트
    └── stop.sh      # 컨테이너 중단 스크립트
```

---

### AWS CodeDeploy

S3에서 zip을 받아 `appspec.yml`에 적힌 순서대로 EC2에서 실행하는 배포 자동화 서비스입니다.

**배포 순서 (Lifecycle):**

```
ApplicationStop  →  stop.sh 실행   →  기존 컨테이너 종료
      │
      ▼
Files            →  scripts/ 복사  →  EC2에 새 스크립트 배치
      │
      ▼
ApplicationStart →  start.sh 실행  →  ECR에서 새 이미지 pull + 실행
```

> **첫 배포 시 주의:** `ApplicationStop`은 이전 배포의 `stop.sh`를 실행하므로 첫 배포에서는 스킵됩니다. 정상 동작입니다.

---

### appspec.yml

CodeDeploy에게 주는 배포 지시서입니다.

```yaml
files:
  - source: scripts                              # zip 안의 scripts/ 폴더를
    destination: /home/ec2-user/app/scripts      # EC2 이 경로에 복사

hooks:
  ApplicationStop:
    - location: scripts/stop.sh                  # 배포 전 기존 컨테이너 종료
  ApplicationStart:
    - location: scripts/start.sh                 # 배포 후 새 컨테이너 시작
```

---

## 4. Spring Boot 프로파일

환경마다 다른 설정을 분리해서 관리하는 기능입니다.

```
application.yaml          →  공통 설정 (기본 프로파일: local)
application-local.yaml    →  로컬 개발용 (H2 인메모리 DB, SQL 로그 출력)
application-prod.yaml     →  운영 서버용 (PostgreSQL, 환경변수로 주입)
```

**운영 서버 환경변수 구성 (EC2의 .env 파일):**

```
SPRING_PROFILES_ACTIVE=prod   ← prod 프로파일 활성화
DB_URL=jdbc:postgresql://...  ← application-prod.yaml의 ${DB_URL}에 주입
DB_USERNAME=...
DB_PASSWORD=...
```

---

## 5. 파일 구조 한눈에 보기

```
moive-server/
├── Dockerfile                          # Docker 이미지 빌드 레시피
├── appspec.yml                         # CodeDeploy 배포 지시서
├── scripts/
│   ├── start.sh                        # EC2에서 컨테이너 시작
│   └── stop.sh                         # EC2에서 컨테이너 중단
├── .github/
│   └── workflows/
│       └── deploy.yml                  # GitHub Actions CI/CD 파이프라인 정의
├── src/main/resources/
│   ├── application.yaml                # 공통 설정
│   ├── application-local.yaml          # 로컬 H2 설정
│   └── application-prod.yaml           # 운영 PostgreSQL 설정
└── docs/
    ├── cicd-setup.md                   # AWS/EC2 세팅 가이드 (실습용)
    └── cicd-study.md                   # 이 파일 (개념 학습용)
```

---

## 6. AWS 세팅 순서 (최초 1회)

main 옮기고 배포 전 진행해야할 설정
```
1. ECR 레포지토리 생성
2. S3 버킷 생성
3. IAM 사용자 생성 (GitHub Actions용 액세스 키 발급)
4. EC2 IAM 역할 생성 (ECR pull + CodeDeploy 권한)
5. CodeDeploy 애플리케이션 + 배포 그룹 생성
6. GitHub Secrets 등록 (아래 5개)
7. EC2에 Docker + CodeDeploy Agent 설치
8. EC2에 .env 파일 생성
```

**GitHub Secrets 목록:**
```
AWS_ACCESS_KEY_ID
AWS_SECRET_ACCESS_KEY
ECR_REPOSITORY
S3_BUCKET
CODEDEPLOY_APP_NAME
CODEDEPLOY_DEPLOYMENT_GROUP
```

