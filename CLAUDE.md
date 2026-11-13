# Driver Sense 판정 엔진

## 이 프로젝트는

- 저장소는 모노레포다. `engine/`(AI 담당), `app/`(앱 담당), `server/`(서버 담당)를 함께 둔다. 이 문서는 `engine/` 작업 기준이고, 담당자 동의 없이 다른 폴더를 고치지 않는다.
- 현대차·기아 차량용 앱 공모전(Pleos Playground) 출품작 "Driver Sense"의 **판정 엔진**이다.
- Driver Sense는 차량 중앙 화면에서 돌아가는 운전 법규 습관 코치 앱이다. 깜빡이, 진행 방향, 속도, 내비의 다음 회전 정보 같은 차량 신호로 운전 습관을 진단하고, 단속되기 전에 음성으로 미리 알려준다.
- 이 엔진은 차량 신호를 한 프레임씩 받아서 두 가지를 내보낸다.
  1. 안내 요청 (GUIDE): 지금 운전자에게 음성으로 말할 문구
  2. 판정 이벤트 (EVENT): 법규를 지켰는지(KEPT), 놓쳤는지(MISSED)
- 팀 구성
  - AI 담당: 이 엔진을 만든다.
  - 앱 담당 (프론트·팀장): 차량 앱(Android Automotive, Kotlin). 이 엔진을 앱에 붙인다.
  - 서버 담당 (백엔드): Spring 서버. 판정 이벤트를 저장하고 리포트를 집계한다.
  - PM: 기획서와 PT.
- 일정: 11/10 엔진 모듈을 앱 담당에게 전달, 11/14 기획서 제출, 12/3~4 PT.

## 팀 공통 규칙 (모든 폴더)

- 코드, 주석, TODO, 문서, 앱 화면 문구에 팀원 실명, GitHub 계정 이름, 개인 이메일을 쓰지 않는다. 역할명(AI 담당, 앱 담당, 서버 담당, PM)을 쓴다. 공모전 제출 자료에 개발자 실명이 보이면 안 되고, 이 저장소의 파일이 그대로 제출본이 되기 때문이다. 자세한 내용은 README의 "실명 쓰지 않기".
- 제출용 저장소에는 커밋하지 않는다. 제출 직전에 이 저장소의 `main`을 복사해 올린다.

## 기준 문서

- `docs/proposal.md`: 서비스 기획서. 무엇을 왜 만드는지, 기능 ID(F1~F12, AI-1~AI-6), 내 작업 목록(12장, P1~P13)이 있다. 기능의 목적이 헷갈리면 여기를 본다.
- `docs/spec.md`: 엔진의 입출력 형식, 룰, 시나리오의 **기준**이다.
- 코드와 spec이 다르면 코드를 바로 고치지 말고, spec을 어떻게 고칠지 먼저 제안하고 내 확인을 받는다.
- `docs/tasks.md`: 단계별 작업 순서다. 지금 진행 중인 단계의 범위만 작업한다.
- LLM(Gleo AI) 호출은 차량 앱에서 한다. 이 저장소에는 LLM에 보낼 프롬프트를 만드는 코드까지만 둔다.

## 기술 제약

- `engine/`은 **순수 Kotlin JVM 라이브러리**. Android 의존성 금지. 에뮬레이터 없이 개발·테스트하기 위해서다. 나중에 앱에서 `implementation(project(":engine"))`로 가져다 쓴다.
- Gradle Kotlin DSL, `jvmToolchain(17)`.
- 의존성은 `kotlinx-serialization-json`과 `kotlin-test`(JUnit 5)만 쓴다. 다른 라이브러리가 필요하면 추가하기 전에 먼저 물어본다.
- 엔진은 현재 시각, 랜덤, 파일, 네트워크에 의존하지 않는다. 같은 프레임을 넣으면 항상 같은 출력이 나와야 한다. (시나리오 테스트와 PT 데모 재현을 위해)

## 목표 구조

```
settings.gradle.kts
build.gradle.kts
engine/
  build.gradle.kts
  src/main/kotlin/kkam/engine/
    model/      입출력 데이터 클래스
    rules/      룰별 상태 머신 (R1Rule.kt, R2Rule.kt, R3Rule.kt)
    policy/     모드·적응형 안내·졸업 판정 (GuidancePolicy.kt)
    insights/   습관 인사이트 계산 (준수율, 놓친 곳, 아낀 범칙금)
    ai/         LLM 프롬프트 빌더 (법규 Q&A, 주간 코칭)
    Engine.kt   onFrame(frame): List<EngineOutput>
  src/test/kotlin/kkam/engine/
    ScenarioTest.kt
scenarios/      시나리오 JSON (테스트와 PT 데모 공용)
data/           법규 룰 테이블, Q&A 평가셋
app/            차량 앱 (앱 담당)
server/         Spring 서버 (서버 담당)
apk/            제출용 APK
docs/
  proposal.md
  spec.md
  tasks.md
```

## 작업 방식 (중요)

- 나는 Kotlin이 거의 처음이고 Java·Spring 경험이 있다. 설명은 **한국어**로, 필요하면 Java와 비교해서 해준다.
- **한 번에 한 단계씩.** 지금 단계 범위를 넘는 기능(R2, R3, LLM 등)을 미리 만들지 않는다.
- 이 엔진은 PT 질의응답에서 **내가 직접 설명해야 한다.** 영리한 코드보다 읽기 쉬운 코드를 쓴다.
  - 상태는 enum이나 sealed class로 이름을 붙인다.
  - 상태 전이는 한 함수 안에 모으고, spec의 전이표 행과 1:1로 대응되게 주석을 단다.
  - 매직 넘버 대신 spec에 나온 파라미터 이름을 상수로 쓴다.
- 코드를 바꾸면 무엇을 왜 바꿨는지 3~5줄로 설명한다. 상태 머신을 바꾸면 전이를 표로 보여준다.
- 판정 기준이 애매하면 임의로 정하지 말고 질문한다.
- 변경 후에는 항상 `./gradlew test`를 실행하고 결과를 보고한다.
- 단계가 끝나면 git 커밋한다. 커밋 메시지는 한국어로, 무엇을 했는지 한 줄.

## 명령어

- 전체 테스트: `./gradlew test`
- 시나리오 테스트만: `./gradlew test --tests "*ScenarioTest*"`
