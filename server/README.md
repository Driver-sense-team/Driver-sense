<div align="center">

# 서버

</div>

> 담당: 서버 · 상태: 시작 전

Spring 서버를 이 폴더에 만듭니다. 판정 이벤트 저장, 리포트 API, 공공 신호 API 프록시를 맡습니다.

## 프로젝트 설정

`server/`는 Android 앱과 분리된 독립 Gradle 빌드입니다 (Spring Boot 4.1, Kotlin, JDK 17).
엔진은 `server/settings.gradle.kts`에서 `../engine`을 하위 프로젝트로 포함해 참조합니다.
`engine/`과 루트 빌드 파일은 수정하지 않습니다.

실행:
- IntelliJ: `ServerApplication.kt` 실행
- 명령어 (`server/` 폴더에서):
  - Windows: `.\gradlew.bat bootRun`
  - Mac/Linux: `./gradlew bootRun`

실행 후 `localhost:8080`으로 접속합니다.

테스트: Windows `.\gradlew.bat test`, Mac/Linux `./gradlew test`

## 먼저 정할 것

- 이벤트 저장 키: 엔진의 `Event.id`는 엔진 객체마다 `evt-1`부터 다시 시작하므로 그대로 키로 쓸 수 없습니다.
- 습관 인사이트 계산 위치: AI 담당이 라이브러리로 만들고 서버에서 호출하는 안을 제안합니다.
