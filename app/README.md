<!-- 화면·데모 버튼·SDK 판을 바꾸면 '화면', '데모 패널', 'Pleos SDK 연동' 표를 같이 고친다 -->
<div align="center">

# Driver Sense · 차량 앱

차량 신호를 모아 [판정 엔진](../engine/README.md)에 넣고, 엔진이 낸 안내와 판정을 **음성·코치 카드·리포트**로 보여주는 Pleos Connect 앱이다.<br>
패키지는 `kkam.app`이고, 담당은 앱 담당이다.

[![Android Automotive](https://img.shields.io/badge/Android_Automotive-minSdk_28-3DDC84?logo=android&logoColor=white)](#빌드와-실행)
[![Pleos SDK](https://img.shields.io/badge/Pleos_SDK-Vehicle_·_NaviHelper_·_TTS-002C5F)](#pleos-sdk-연동)
[![Naver Map](https://img.shields.io/badge/Naver_Map_SDK-3.24.1-03C75A)](#네이버-지도)

</div>

> [!NOTE]
> Pleos Connect Emulator의 내비는 위치가 움직이지 않는다. 주행 장면은 [데모 패널](#데모-패널)로 녹화된 신호를 재생해서 본다. 에뮬레이터에서 확인할 수 없는 것은 [에뮬레이터 제약](#에뮬레이터-제약)에 적었다.

저장소 전체 소개는 [루트 README](../README.md)에 있다. 처음 본다면 [화면](#화면) → [주행 중 안내](#주행-중-안내) → [목적지 경로 코치](#목적지-경로-코치) → [데모 패널](#데모-패널) 순서로 읽는다.

## 화면

기어가 P가 아니면 늘 주행 화면이고, P면 왼쪽 레일로 정차 화면을 오간다. 주행 중에는 터치할 것을 두지 않는다.

| 화면 | 언제 | 내용 | 레이아웃 |
|---|---|---|---|
| 주행 화면 (UI-1) | 기어 P가 아닐 때 | 코치 카드, 한 줄 문구, 오늘 성적(지킴 수와 링) | `screen_drive.xml` |
| 오늘 리포트 (UI-2) | 기어 P | 요약 한 줄, 수치 카드(지킴·놓침·아낀 범칙금), 판정 기록과 조항·처분 | `screen_report.xml` |
| 진단 (UI-3) | 기어 P | 상황별 준수율 막대와 전국 평균선 75.7% | `screen_diagnose.xml` |
| 교정 진행 (UI-4) | 기어 P | 습관별 졸업 진행, 7일 준수율 막대, 코칭 카드, 자주 놓친 곳 Top 3 | `screen_progress.xml` |
| 법규 Q&A (UI-5) | 기어 P | 질문 입력, 추천 질문 칩, 답변과 근거 카드(조항·처분·출처) | `screen_qna.xml` |
| 운전 코치 | 기어 P | 코치 켬/끔 스위치 카드 한 장 | `screen_mode.xml` |
| 경로 미리보기 | 경로가 시작됐을 때 기어 P | 코치할 곳 수, 좌·우회전 칩, 경로 순서, 자주 놓친 유형, "출발" 버튼 | `screen_route_preview.xml` |
| 경로 리포트 | 경로가 끝나고 기어 P | 그 경로의 판정만. 놓친 곳 먼저, 지킨 곳, 아낀 범칙금, "AI에게 묻기" | `screen_route_report.xml` |

- 왼쪽 레일 순서는 오늘 리포트 → 진단 → 교정 진행 → 법규 Q&A → 운전 코치다.
- 기어 P에서 이미 정차 화면(진단, Q&A 등)을 보고 있으면 리포트로 덮지 않는다(`Screen.kt`).
- 내비를 보다가 기어를 P로 바꾸면 앱이 앞으로 와서 리포트를 보여준다. P에서 출발해 처음 D가 되면 내비를 앞으로 띄운다. 경로 데모 중에는 앱 지도가 내비 역할이라 띄우지 않는다.
- 리포트의 조항·처분은 루트 `data/law_table.json`을 assets로 바로 읽는다. 룰 테이블에 룰 필드가 없어서 `DriveReport.kt`에서 룰과 행을 잇는다: R1→`LAW-01`, R2→`LAW-03`, R3→`LAW-12`, R4→`LAW-04`. R5~R7은 연결할 행이 없다.
- R6(와이퍼)·R7(김서림)은 법규 근거가 없는 안전 안내라서 준수율과 아낀 범칙금에서 뺀다.
- 오늘 판정 기록은 기기에 저장해(`RecordStore`) 앱을 다시 켜도 남고, 날짜가 바뀌면 비운다.

## 주행 중 안내

엔진이 GUIDE를 내면 Pleos TTS로 한 문장을 읽고, 같은 내용을 카드로 보여준다.

**카드와 팝업의 모습**

코치 카드(주행 화면)와 팝업은 흰 바탕에 검정 글자다. 상태 색은 굵은 테두리, 방향 그림 원, 행동 알약, 깜빡이 칩, 카드 밑 문구에 쓴다(`CoachLook.kt`).

| 상태 | 색 | 행동 알약 | 언제 |
|---|---|---|---|
| 대기 | 연회색 | 없음 | 안내할 것이 없을 때 |
| 켜세요 | 노랑 | "! 지금 켜세요" | 지금 켜야 할 때 |
| 좋아요 | 파랑 | "✓ 좋아요" | 켰을 때 |
| 놓침 | 빨강 | "X 다음엔 미리 켜요" | 꺼진 채 판정 지점을 지났을 때 |

- 색만으로 구분하지 않게 알약 아이콘도 상태마다 다르다.
- 안내 종류(`GuideKind`: 회전 깜빡이, 차선 변경, 전조등, 와이퍼, 적색 우회전, 김서림)마다 맞는 그림을 쓴다.
- 레버를 쓰는 안내(회전 깜빡이, 차선 변경, 전조등, 와이퍼)는 켜야 할 때 운전대와 레버 그림이 함께 움직인다. 레버 배치는 현대·기아 공통 기준이다(왼쪽: 깜빡이·전조등, 오른쪽: 와이퍼).
- 김서림은 레버 대신 누를 공조 버튼(① 성에 제거, ② 외기 순환) 그림을 보여준다. 적색 우회전은 지금 신호 색이 켜진 신호등 그림을 쓴다.

**내비가 앞에 있을 때**

주행 중에는 내비가 화면을 차지하고 앱은 뒤에서 돈다. 재생, 신호 수집, 엔진, 음성은 포그라운드 서비스 `CoachService`(알림 채널 "주행 코치")가 맡는다. 화면을 닫아도 주행 중이면 서비스가 계속 돌고, 주행이 끝나면 스스로 끝난다.

안내가 필요할 때만 내비 위쪽 가운데에 작은 팝업 카드(`CoachPopup`)가 뜬다. 언제 띄우고 숨길지는 `PopupPolicy.kt`가 정한다.

1. 음성 안내가 나오면 노랑 "지금 켜세요"가 뜬다.
2. 깜빡이를 켜면 파랑 "좋아요"로 바뀌고 3초 뒤 사라진다.
3. 판정이 나오거나, 회전이 끝나거나, 기어가 P면 숨긴다. 안내 전에는 먼저 띄우지 않는다.

- 앱 화면이 보이는 동안은 코치 카드가 같은 내용을 보여주므로 팝업을 띄우지 않는다.
- 팝업은 "다른 앱 위에 표시" 권한이 있어야 뜬다. 권한이 없으면 같은 내용을 차량 상단 알림(`CoachHeadsUp`)으로 보여준다.
- 알림 권한과 겹침 창 권한은 정차 중(주행 중 제한이 풀렸을 때) 한 번 묻는다. 주행 중에는 시스템이 권한 화면을 막는다.

## 목적지 경로 코치

목적지를 정하면 그 경로를 따라가며 코치하고, 도착하면 경로 리포트를 보여준다. 규칙은 `RouteCoach.kt`, 내비 이벤트 수신은 `RouteWatcher.kt`에 있다.

1. **경로 시작**: 내비에서 경로 안내가 시작되면 실제 차량 신호 코치를 자동으로 켠다. 코치가 켜져 있고 위치·속도 권한이 있을 때만이며, 시나리오 재생 중이면 건드리지 않는다.
2. **경로 미리보기**: 그때 기어가 P면 미리보기를 보여준다. 자주 놓친 유형은 오늘 놓친 판정 중 가장 많은 것이고, 없으면 진단 값에서 준수율이 가장 낮은 습관이다.
3. **주행**: 경로 위 회전·나들목·터널마다 안내하고 판정한다.
4. **경로 리포트**: 도착하거나 안내가 끝난 뒤 기어 P가 되면, 오늘 리포트 대신 그 경로의 판정만 묶은 "○○ 경로 리포트"를 보여준다. 놓친 곳을 먼저 보여준다.
5. **AI에게 묻기**: 경로 리포트의 버튼을 누르면 법규 Q&A로 가서 추천 질문("왜 우회전할 때 멈춰야 해?")을 바로 묻는다.

## 운전 코치 켬/끔

기획 초기의 모드 4개(진단·교정·연수·조용)를 스위치 하나로 줄였다. 값은 `DriveMode`(`ON`/`OFF`, 기본 켬)이고 `ModeStore`가 저장한다.

| 값 | 음성·카드·팝업 안내 | 판정 기록 |
|---|---|---|
| 켬 | 한다 | 한다 |
| 끔 | 안 한다 (카드는 대기로 둔다) | 한다 |

- 세션이 프레임마다 값을 읽으므로 주행 중에 바꿔도 다음 프레임부터 반영된다.
- 끔이어도 판정은 남겨서 진단·리포트가 끊기지 않게 한다.
- 예전에 저장된 모드 값은 교정·연수 → 켬, 진단·조용 → 끔으로 옮긴다.

## 데모 패널

debug 빌드에서만 오른쪽 위 "데모" 버튼으로 연다. release 빌드에는 버튼이 없고, 앱을 켜면 실제 차량 신호 코치를 바로 시작한다.

| 항목 | 하는 일 |
|---|---|
| 시나리오 버튼 | 루트 `scenarios/`의 녹화된 신호를 시간대로 재생해 엔진에 넣는다. 예: S2를 틀고 이어서 S8(9초에 기어 P)을 틀면 리포트까지 본다 |
| 실제 차량 신호 | SDK 신호를 1초마다 `SignalFrame`으로 묶어 엔진에 넣는다 |
| 경로 데모 (목적지 검색 → 도착) | 아래 [경로 데모](#경로-데모) |
| 진단 리포트 열기 (기어 P) | 재생을 멈추고 기어를 P로 세운 뒤 진단 리포트를 연다 |
| 재생하면 내비 띄우기 | 켜면 시나리오를 틀 때 내비가 앞으로 와서 내비 위 팝업을 볼 수 있다 |
| 안내 종류 미리보기 | 엔진 출력 없이 안내 종류별 카드·팝업·그림을 차례로 보여주고 문구를 읽는다. 차선 변경처럼 엔진 룰이 없는 종류도 여기서 본다 |
| 차량 신호·판정 기록 | 지금 프레임의 신호 값과 엔진이 낸 판정. 맨 위에는 지금 쓰는 내비 위 안내 방식(팝업/상단 알림)이 보인다 |

### 경로 데모

에뮬레이터 내비는 지도가 나오지 않고 위치도 움직이지 않아서, 경로 데모는 앱 안 네이버 지도를 내비로 쓴다. **시연용 경로 데이터**를 재생하며, 처음부터 끝까지 약 3분이다.

1. 버튼을 누르면 기어 P로 서고, 지도 위에 목적지 검색창(`DestinationSearch`)이 열린다. 목적지는 미리 계산한 경로가 있는 목록(`RouteDestinations.kt`)에서 찾는다.
2. 목적지를 고르면 지도(경로 전체)와 경로 미리보기를 반씩 보여주고, 미리보기의 "출발"을 누를 때까지 기다린다.
3. 출발하면 지도가 전체 화면이 되고 진행 방향이 위로 간다. 안내는 지도 위쪽 가운데 팝업 카드(`MapPopupCard`)로 뜬다. 내비 위 팝업과 같은 그리기 코드(`PopupCardPainter`)를 써서 모습이 같다.
4. 코치 지점 여섯 곳을 지난다.

   | 순서 | 지점 | 운전자 동작 (데모 값) | 결과 |
   |---|---|---|---|
   | 1 | 좌회전 | 안내 듣고 깜빡이 켬 | R1 지킴 |
   | 2 | 적색 신호 우회전 | 정지 안내 듣고 정지선에서 멈춤 | R3 지킴, R1 지킴 |
   | 3 | 차선 변경 | 팝업과 음성만 | 판정·리포트 없음 |
   | 4 | 터널 | 전조등 안내 듣고 켬 | R5 지킴 |
   | 5 | 나들목 | 깜빡이 안내 듣고 켬 | R4 지킴 |
   | 6 | 우회전 | 안내했지만 안 켬 | R1 놓침 |

5. 도착하면 기어 P → 경로 리포트 → "AI에게 묻기" → 법규 Q&A로 이어진다.

**만든 방법**

- 경로는 OpenStreetMap 도로를 OSRM으로 한 번 계산해 assets에 넣었다. 회전·나들목 위치는 OSRM 안내 지점, 터널은 OSM의 `tunnel=yes` 구간 입구다. 적색 신호와 운전자 동작은 데모 값이다. 네이버 Directions API는 Client Secret이 필요해서 쓰지 않는다.
- 시간 압축: 코치 지점 앞뒤(회전 120 m 전~40 m 뒤, 터널 260 m 전, 나들목 320 m 전)와 출발·도착만 실제 속도(시내 40, 자동차전용도로 80 km/h)로 1초씩 가고, 나머지는 1초에 최대 1.5 km씩 빨리 감는다. 지도의 속도 표시에 "▶▶ 빨리 감기"가 붙는다.
- 빨리 감는 구간은 30 km/h보다 빠르게 둬서 R2가 굽은 길을 회전으로 세지 않는다.
- 안내·판정 순서와 3분 안에 끝나는지는 `RouteDemoTest`가 확인한다. 같은 경로를 넣으면 늘 같은 프레임이 나온다.
- 기어는 에뮬레이터 Virtual Console에 보내 계기판도 같이 바뀐다(`VehicleConsole`). 깜빡이는 보내지 않는다. 차량에 깜빡이를 켜면 사각지대 카메라 화면이 떠서 앱 지도를 가리기 때문이다.
- 속도도 보내지 않는다(계기판은 늘 0과 브레이크). 차가 움직이면 차량 OS가 주행 중 제한으로 앱 화면을 막기 때문이다. 데모 속도는 지도 왼쪽 아래에 표시한다.

## 빌드와 실행

Android SDK가 필요하다. `ANDROID_HOME`을 지정하거나 루트 `local.properties`에 `sdk.dir`을 적는다. 이 설정이 없으면 `./gradlew test`도 실패한다.

```bash
./gradlew :app:testDebugUnitTest
```

→ 앱 JVM 테스트가 돈다.

```bash
./gradlew :app:installDebug
```

→ 켜져 있는 에뮬레이터에 debug 앱을 설치한다.

```bash
adb shell appops set --user 10 kkam.app SYSTEM_ALERT_WINDOW allow
```

→ 내비 위 팝업에 필요한 겹침 창 권한을 켠다. Pleos 에뮬레이터에는 이 권한을 켜는 설정 화면이 없다.

- 시나리오와 룰 테이블은 복사하지 않는다. 루트 `scenarios/`와 `data/`를 앱 assets 폴더로 바로 쓴다(`app/build.gradle.kts`).

## 네이버 지도

경로 데모 지도는 네이버 지도 Android SDK(`com.naver.maps:map-sdk:3.24.1`, Maven 저장소 `https://repository.map.naver.com/archive/maven`)를 쓴다.

1. 네이버 클라우드 플랫폼 콘솔의 Maps에서 Application을 등록한다. API는 Dynamic Map, Android 패키지 이름은 `kkam.app`이다.
2. 발급받은 Client ID를 루트 `local.properties`에 적는다. 이 파일은 커밋하지 않는다.
   ```properties
   NAVER_MAP_CLIENT_ID=발급받은_Client_ID
   ```
3. 빌드하면 `BuildConfig.NAVER_MAP_CLIENT_ID`로 들어가고, `RouteMap`이 `NaverMapSdk.NcpKeyClient`로 인증한다(새 Maps 콘솔 키 방식).

- 키가 없어도 빌드된다. 그때는 경로 데모에서 지도만 안 뜨고 속도와 안내 카드는 보인다.
- 인증이 실패하면 logcat의 `RouteMap` 태그에 오류 코드가 찍힌다. 패키지 이름 등록을 먼저 확인한다.

## Pleos SDK 연동

Maven 저장소 `https://nexus-playground.pleos.ai/repository/maven-releases/`를 추가한다.

| 입력 | 가져오는 방법 | 권한 |
|---|---|---|
| 방향지시등, 핸들, 기어, 브레이크, 전조등, 와이퍼, 공조 | `ai.pleos.playground:Vehicle:2.3.2` | `pleos.car.permission.CAR_LIGHTS`, `CAR_DRIVE`, `CAR_INFO`, `CAR_WINDOWS`, `CAR_CLIMATE` |
| 속도 | `android.car`의 `CarPropertyManager`, `PERF_VEHICLE_SPEED` | `android.car.permission.CAR_SPEED` |
| 위치, 진행 방향 | `ai.pleos.playground:FusedLocation:1.1.3` | `pleos.car.permission.FUSED_LOCATION` |
| 다음 안내(TBT), 경로 시작·도착 | `ai.pleos.playground:NaviHelper:3.0.5` | `pleos.car.permission.NAVI_ROUTE` |
| 음성 안내 | `ai.pleos.playground:TextToSpeech:3.3.1` | `pleos.car.permission.TTS_SERVICE` |

- 속도는 Vehicle SDK에 없다. 앱에서 `useLibrary("android.car")`를 선언한다.
- TTS는 `Mode.ON_DEVICE`로 만들면 앱 등록 없이 동작한다. 언어는 `Locale.KOREA`로 지정한다. `Locale.KOREAN`은 `ON_DEVICE_ENGINE_ERROR`로 실패한다.
- SDK 값을 엔진 값으로 바꾸는 규칙은 [spec 2.1](../docs/spec.md#21-입력-signalframe-차량-신호-한-프레임)을 따른다(`SdkMapping.kt`).
- Virtual Console은 `adb forward tcp:8088 tcp:8088` 뒤 `http://127.0.0.1:8088/`을 브라우저로 열어 둔 동안만 값을 바꿀 수 있다.

## 에뮬레이터 제약

- 내비는 위치와 진행 방향이 움직이지 않는다. GPS 문장 체크섬이 고정값(`*47`)이라 내비가 모든 위치를 버리기 때문이다. 그래서 실제 신호로는 30 m 판정과 회전 완료를 확인할 수 없다. 속도를 약 45 km/h 이상으로 올리면 80 m 앞 안내(GUIDE)까지는 확인할 수 있다.
- 차가 움직이면(속도 > 0, 브레이크를 뗀 D) 차량 OS가 앱 화면을 막는다("주행 중에는 사용할 수 없습니다").
- NaviHelper 3.0.5에는 경로 전체 회전 목록 API가 없어서, 경로 미리보기는 경로 시작 직후 받은 TBT 목록을 쓴다. 그 목록이 경로 전체인지는 에뮬레이터 경로(도착 1개뿐)로는 확인하지 못했다.

## 아직 안 된 것

| 항목 | 지금 | 남은 일 |
|---|---|---|
| 서버 저장 | 오늘 판정만 기기에 저장 | 서버 담당 API가 생기면 EVENT를 보낸다 |
| 법규 Q&A | 룰 테이블 키워드 매칭(`KeywordQnaAnswerer`) | Gleo AI 답변(`QnaAnswerer` 자리에 교체), 음성 질문(STT) |
| 오늘 리포트 "AI에게 묻기" | "준비 중" 표시 | 경로 리포트처럼 Q&A로 잇는다 |
| 진단·교정 진행 | 데모 고정값(`DemoDiagnosisSource`, `DemoProgressSource`) | 실제 판정 기록으로 계산한다 |
| 습관 선택 (기획서 4.1) | 화면 없음. 코치를 켜면 모든 상황을 안내한다 | 엔진에 모드 입력이 생기면 습관 선택 화면과 함께 넣는다 |
| 실제 신호의 신호등·비 정보 | `light`, `rain`, `fogReported`가 null | 서버 신호·날씨, STT를 잇는다 |
| 리포트 지도 핀 | 없음 | 지도 SDK로 판정 위치를 표시한다 |

## 폴더 구조

소스는 `app/src/main/java/kkam/app/`에 있다. "JVM 테스트"는 Android 없이 `app/src/test`에서 시험하는 파일이다.

```text
kkam/app/
├── 세션과 신호
│   ├── MainActivity.kt         화면. 서비스에 붙어 세션 상태를 그리기만 한다
│   ├── CoachService.kt         포그라운드 서비스. 내비가 앞에 있어도 세션이 돈다. 기어에 따라 앱·내비를 앞으로 띄운다
│   ├── CoachSession.kt         재생·실제 신호 → 엔진 → 음성 → CoachState 흐름
│   ├── CoachState.kt           프레임과 엔진 출력을 화면 값으로 (JVM 테스트)
│   ├── SignalCollector.kt      차량 신호 수집과 프레임 생성
│   ├── SdkMapping.kt           SDK 값을 엔진 값으로 (JVM 테스트)
│   ├── ScenarioPlayer.kt       scenarios/의 JSON을 시간대로 재생
│   ├── Speaker.kt              Pleos TTS 호출
│   ├── DriveMode.kt            운전 코치 켬/끔과 저장 ModeStore (JVM 테스트)
│   └── RecordStore.kt          오늘 판정 기록 저장·복원 (JVM 테스트)
├── 주행 화면과 안내
│   ├── DriveScreen.kt          주행 화면 코치 카드와 오늘 성적
│   ├── CoachLook.kt            카드·팝업 상태 색 규칙
│   ├── GuideKind.kt            안내 종류와 엔진 룰 연결 (JVM 테스트)
│   ├── GuideIcons.kt           안내 종류별 그림
│   ├── LeverMotion.kt          안내 종류별 레버 동작과 문구 (JVM 테스트)
│   ├── LeverGuideView.kt       운전대와 레버 그림. 움직일 레버만 반복해서 움직인다
│   ├── ButtonGuide.kt          김서림 안내의 공조 버튼 그림
│   ├── GuidePreview.kt         데모 "안내 종류 미리보기" 예시 값 (엔진 출력 아님)
│   ├── PopupPolicy.kt          팝업을 언제 띄우고 숨길지 (JVM 테스트)
│   ├── PopupCardPainter.kt     팝업 카드 그리기. 내비 위 팝업과 지도 위 팝업이 같이 쓴다
│   ├── CoachNotifier.kt        내비 위 안내 방식 (겹침 창 카드 / 상단 알림)
│   ├── CoachPopup.kt           내비 위 팝업 카드 (겹침 창)
│   ├── CoachHeadsUp.kt         차량 상단 알림 (겹침 창 권한이 없을 때)
│   └── NaviLauncher.kt         차량 기본 내비를 앞으로 띄움
├── 정차 화면
│   ├── Screen.kt               화면 목록과 기어에 따른 화면 전환 규칙 (JVM 테스트)
│   ├── ParkedNav.kt            기어 P 화면 왼쪽 레일
│   ├── ReportScreen.kt         오늘 리포트 화면
│   ├── DriveReport.kt          리포트 값 계산, 룰과 룰 테이블 행 연결 (JVM 테스트)
│   ├── DiagnoseScreen.kt       진단 리포트 화면
│   ├── Diagnosis.kt            진단 값 (JVM 테스트)
│   ├── ProgressScreen.kt       교정 진행 화면
│   ├── WeeklyProgress.kt       졸업 단계 계산 (JVM 테스트)
│   ├── QnaScreen.kt            법규 Q&A 화면
│   ├── LawQna.kt               답 만들기 (JVM 테스트)
│   ├── ModeScreen.kt           운전 코치 켬/끔 화면
│   └── Ui.kt                   화면이 같이 쓰는 그리기 도구
└── 경로 코치
    ├── RouteCoach.kt           경로 미리보기 값, 자주 놓친 유형, 경로 시작 전이표, 경로 판정 묶기 (JVM 테스트)
    ├── RouteWatcher.kt         NaviHelper 경로 시작·도착·종료 이벤트 수신
    ├── RoutePreviewScreen.kt   경로 미리보기 화면
    ├── RouteReportScreen.kt    경로 리포트 화면과 "AI에게 묻기"
    ├── RouteDemo.kt            경로 데모 주행 프레임 생성, 코치 지점·시간 압축 (JVM 테스트)
    ├── RouteDestinations.kt    경로 데모 목적지 목록과 검색 (JVM 테스트)
    ├── DestinationSearch.kt    지도 위 목적지 검색창
    ├── RouteMap.kt             경로 데모 앱 안 지도 (네이버 지도 SDK)
    ├── MapPopupCard.kt         경로 데모 지도 위 팝업 카드
    └── VehicleConsole.kt       에뮬레이터 Virtual Console에 기어 보내기 (보낼 값 규칙은 JVM 테스트)
```

- 묶음 이름(세션과 신호 등)은 읽기 쉽게 나눈 것이고, 실제 파일은 모두 한 폴더에 있다.
- 엔진 사용법은 [engine/README.md](../engine/README.md)에 있다.
