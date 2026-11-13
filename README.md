<div align="center">

<img src="docs/images/banner.svg" alt="Driver Sense — 차 안에 나만의 선생님." width="100%">

<br>

# Driver Sense

**차량 신호로 운전 법규 습관을 진단하고, 단속되기 전에 음성 한 문장과 카드로 미리 알려주는 Pleos Connect 차량용 앱**

[![Pleos Connect](https://img.shields.io/badge/Pleos_Connect-Emulator_2.1.0-002C5F?style=for-the-badge)](#개발-환경)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](#개발-환경)
[![Android Automotive](https://img.shields.io/badge/Android_Automotive-minSdk_28-3DDC84?style=for-the-badge&logo=android&logoColor=white)](#개발-환경)

</div>

현대자동차그룹 차량용 앱 개발 공모전(Pleos Playground) 출품작이다.
회전 전 깜빡이, 적색 신호 우회전 일시정지, 터널 전조등 같은 7가지 상황을 규칙(상태 머신)으로 판정하고, 결과를 코치 카드와 주행 리포트로 보여준다. 판정에 LLM을 쓰지 않아 같은 주행에는 언제나 같은 결과가 나온다.

## 목차

1. [APK 위치](#apk-위치)
2. [실행 방법](#실행-방법)
3. [개발 환경](#개발-환경)
4. [테스트 환경과 필요한 설정](#테스트-환경과-필요한-설정)
5. [소스에서 빌드](#소스에서-빌드)
6. [참고 문서](#참고-문서)

## APK 위치

APK는 용량이 100MB를 넘어 저장소 폴더에 넣지 못하고 **GitHub Releases**에 올렸다.

**[GitHub Release v0.1 페이지](https://github.com/Driver-sense-team/Driver-sense/releases/tag/v0.1)**

| 파일 | 용도 |
|---|---|
| `Driver-sense-team_DriverSense_release.apk` | **제출용 release APK.** 켜면 바로 실제 차량 신호로 주행 코치를 시작한다 |
| `Driver-sense-team_DriverSense_demo.apk` | 데모 패널이 있는 빌드. 오른쪽 위 "데모" 버튼으로 녹화된 주행 장면과 경로 데모를 재생한다 |

두 APK는 같은 키로 서명했다. 설치 방법은 [apk/README.md](apk/README.md)에도 있다.

## 실행 방법

1. Pleos Connect Emulator v2.1.0을 켠다.
2. [Release 페이지](https://github.com/Driver-sense-team/Driver-sense/releases/tag/v0.1)에서 APK를 받아 설치하고 실행한다.
   ```bash
   adb install -r --user 10 Driver-sense-team_DriverSense_release.apk
   ```
   ```bash
   adb shell am start --user 10 -n kkam.app/.MainActivity
   ```
   처음 켜면 위치·속도 권한을 묻는다. 허용하면 실제 차량 신호로 주행 코치를 시작한다.
3. 내비 위 팝업을 보려면 겹침 창 권한을 켠다. 에뮬레이터에는 이 설정 화면이 없다. 권한이 없으면 같은 내용을 상단 알림으로 보여준다.
   ```bash
   adb shell appops set --user 10 kkam.app SYSTEM_ALERT_WINDOW allow
   ```
4. 기어, 방향지시등, 속도는 에뮬레이터의 Virtual Console에서 바꾼다.
   ```bash
   adb forward tcp:8088 tcp:8088
   ```
   브라우저로 `http://127.0.0.1:8088/`을 연다. 기어를 P로 바꾸면 오늘 리포트가 나온다.

### 데모로 보기

에뮬레이터의 내비는 경로를 정해도 위치가 움직이지 않는다. 그래서 주행 장면은 **데모용 APK**(`..._demo.apk`)의 데모 패널로 녹화된 차량 신호를 재생해서 본다. 판정 엔진은 실제 신호와 재생 신호를 똑같이 처리한다.

| 시나리오 | 보이는 것 |
|---|---|
| S2 좌회전 안내후 켬 | 좌회전 60m 앞 "좌회전 곧이에요, 깜빡이 켜세요" → 깜빡이를 켜면 파랑 "좋아요" → 지킴 기록 |
| S3 좌회전 끝까지 안켬 | 안내 후에도 안 켜서 놓침 기록 |
| S8 판정후 주차 | 기어 P에서 오늘 리포트 (지킴·놓침, 조항·처분, 아낀 범칙금) |
| S11 터널 전조등 | 터널 187m 앞 "터널이 곧 나와요, 전조등 켜세요" → 켜면 지킴 기록 |
| 경로 데모 | 목적지 검색 → 출발 → 코치 지점 6곳 → 도착 후 경로 리포트 (약 3분) |

## 개발 환경

| 항목 | 내용 |
|---|---|
| 에뮬레이터 | **Pleos Connect Emulator v2.1.0** |
| 화면 규격 | **2560×1440, 160dpi** (차량 중앙 화면) |
| 앱 | Android Automotive, minSdk 28, targetSdk 34, compileSdk 36, 패키지 `kkam.app` |
| 빌드 | Gradle Wrapper (Kotlin DSL), Kotlin 2.4, JDK 17, Android SDK |
| Pleos SDK | Vehicle 2.3.2, NaviHelper 3.0.5, FusedLocation 1.1.3, TextToSpeech 3.3.1 |
| 지도 | 네이버 지도 Android SDK 3.24.1 (경로 데모의 앱 안 지도에만 사용) |
| 엔진 | 순수 Kotlin JVM 라이브러리 (`engine/`), 테스트는 JUnit 5 |

## 테스트 환경과 필요한 설정

- **권한:** 위치(`pleos.car.permission.FUSED_LOCATION`)와 속도(`android.car.permission.CAR_SPEED`)는 앱을 처음 켤 때 묻는다.
- **겹침 창 권한(선택):** 내비 위 팝업용. 위 [실행 방법](#실행-방법) 3번의 명령으로 켠다.
- **사용자:** 에뮬레이터에서 앱은 user 10에서 돈다. `adb` 명령에 `--user 10`을 붙인다.
- **`local.properties`(소스에서 빌드할 때만):** `sdk.dir`(Android SDK 위치)와 `NAVER_MAP_CLIENT_ID`(네이버 지도 Client ID)를 적는다. 개인 값이라 저장소에 없다. `NAVER_MAP_CLIENT_ID`가 없어도 빌드되고, 경로 데모에서 지도만 안 뜬다.
- **알려진 한계:** 에뮬레이터는 내비 위치가 움직이지 않아 실제 신호로는 회전 완료 판정을 확인할 수 없다. 신호등·비 정보도 실제 신호로는 들어오지 않아 R3(적색 우회전), R6(와이퍼), R7(김서림)은 시나리오 재생으로 확인한다. 법규 Q&A는 룰 테이블 키워드 매칭으로 답한다.

## 소스에서 빌드

```bash
./gradlew test :app:testDebugUnitTest
```

엔진 단위 테스트, 시나리오 테스트(S1~S29), 앱 JVM 테스트가 돈다.

```bash
./gradlew :app:installDebug
```

켜져 있는 에뮬레이터에 debug 앱을 설치한다. debug 앱에만 데모 패널이 있다.

## 참고 문서

| 문서 | 내용 |
|---|---|
| [docs/proposal.md](docs/proposal.md) | 서비스 기획서 |
| [docs/proposal-tech.md](docs/proposal-tech.md) | 기술·AI 활용 요약 |
| [docs/spec.md](docs/spec.md) | 판정 엔진 명세 (룰별 상태 머신, 판정 기준, 시나리오) |
| [docs/law_table.md](docs/law_table.md) | 법규 룰 테이블 |
| [engine/README.md](engine/README.md) · [app/README.md](app/README.md) · [server/README.md](server/README.md) | 폴더별 설명 |
