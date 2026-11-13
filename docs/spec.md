# Driver Sense 판정 엔진 명세

v0.3.3 · 2026-11-13 R8 회전교차로 진입·진출 깜빡이 추가 · 담당 AI

| 버전 | 바뀐 것 |
| --- | --- |
| v0.1 (11-06) | R1 상태 머신, S1~S10 |
| v0.2 (11-09) | R4·R5 추가(시연용 최소 범위), 입력 `headlight`, tbt.turn에 `RAMP_LEFT`·`RAMP_RIGHT`·`TUNNEL`, GUIDE에 `action`, EVENT의 `turn`은 null 허용, S11·S12 |
| v0.3 (11-11) | R2·R3·R6 추가, 입력 `wiper`·`rain`, action `STOP`·`WIPER_ON`, S13~S21, 보조 지표(급정거·급출발) 자리. 모드는 v0.4로 미룬다 |
| v0.3.1 (11-11) | R7 앞유리 김서림 추가(13장), 입력 `defroster`·`recirc`·`fogReported`, action `DEFOG`, S22~S24 |
| v0.3.2 (11-11) | R1이 유턴(`UTURN`)도 감시, EVENT의 `turn`에 `UTURN`. R1 연속 회전 보완(전이표 #1a·#2a, `TBT_JUMP_M`). S25~S29 |
| v0.3.3 (11-13) | R8 회전교차로 진입·진출 깜빡이 추가(14장). tbt.turn·EVENT turn에 `ROUNDABOUT_ENTER`·`ROUNDABOUT_EXIT`, 법규표 LAW-17·18·19. R2가 회전교차로를 회전으로 세지 않음. S30~S32 |

이 문서가 기준이다. 코드와 다르면 이 문서를 먼저 고친다.

---

## 1. 엔진이 하는 일

차량 신호를 한 프레임씩 받아서 두 가지를 내보낸다.

- **안내 요청 (GUIDE)**: 지금 운전자에게 음성으로 말할 문구. 앱이 받아서 음성(TTS)으로 읽고 코치 카드에 표시한다.
- **판정 이벤트 (EVENT)**: 법규를 지켰는지(KEPT) 놓쳤는지(MISSED). 앱은 리포트 화면에 쓰고, 서버는 저장한다.

| 룰 | 내용 | 구현 시기 |
| --- | --- | --- |
| R1 | 회전·유턴 전 깜빡이 | 지금 (11/10까지). 유턴·연속 회전은 v0.3.2 |
| R2 | 깜빡이 없이 회전 (내비 없이, 기록만) | v0.3 (11장) |
| R3 | 적색 신호 우회전 일시정지 | v0.3 (9장) |
| R4 | 나들목 진출입 전 깜빡이 (안내 중심) | v0.2 (7장) |
| R5 | 터널 진입 전 전조등 (안내 중심) | v0.2 (8장) |
| R6 | 비 올 때 와이퍼 (안내 중심, 법규 근거 없음) | v0.3 (10장) |
| R7 | 앞유리 김서림 (안내 중심, 법규 근거 없음) | v0.3.1 (13장) |
| R8 | 회전교차로 진입·진출 깜빡이 | v0.3.3 (14장) |

급정거·급출발은 룰이 아니라 보조 지표다. 지킴·놓침, 범칙금, 음성 안내 없이 정차 리포트에 횟수만 보여준다(12장).

R4·R5는 PT 시연용 최소 범위다. 놓침(MISSED)은 내지 않고, 안내 후 운전자가 따르면 지킴(KEPT)만 기록한다. 이유는 각 룰의 "MVP 단순화"에 적는다.

**원칙**

- 판정은 규칙(상태 머신)으로만 한다. LLM은 엔진에 넣지 않는다. (설명과 코칭 문장은 앱에서 Gleo AI로 따로 만든다.)
- 엔진은 현재 시각, 랜덤에 의존하지 않는다. 프레임의 `t`만 시간으로 쓴다. 같은 프레임 순서를 넣으면 항상 같은 출력이 나온다.

---

## 2. 입출력 형식

### 2.1 입력: SignalFrame (차량 신호 한 프레임)

```json
{
  "t": 5000,
  "speedKph": 28.0,
  "gear": "D",
  "steeringDeg": -8.0,
  "turnSignal": "OFF",
  "brake": false,
  "lat": 37.5501,
  "lon": 127.0732,
  "headingDeg": 90.0,
  "tbt": { "turn": "LEFT", "distanceM": 48 },
  "light": null,
  "headlight": "OFF"
}
```

| 필드 | 타입 | 뜻 | 앱에서 가져오는 곳 | 없을 때 |
| --- | --- | --- | --- | --- |
| t | Long | 시각 (ms). 시나리오는 0부터 | 앱 | 항상 있음 |
| speedKph | Double | 속도 (km/h) | CarPropertyManager `PERF_VEHICLE_SPEED` (m/s) × 3.6 | 직전 값 유지 |
| gear | enum | 기어 | Vehicle SDK Gear | 직전 값 유지 |
| steeringDeg | Double? | 핸들 각도. 좌우 부호는 미정(5장) | Vehicle SDK SteeringWheel | SDK에서 안 나오면 null |
| turnSignal | enum | 방향지시등 상태 | Vehicle SDK TurnSignal. 앱은 당분간 HAZARD를 보내지 않는다(5장) | 직전 값 유지 |
| brake | Boolean | 브레이크 페달 밟음 | Vehicle SDK Brake | 직전 값 유지 |
| lat, lon | Double | 위치 | Fused Location | 직전 값 유지 |
| headingDeg | Double | 진행 방향 (북쪽 0°, 시계 방향, 0~360) | Fused Location | 직전 값 유지 |
| tbt | Tbt? | 내비의 다음 안내와 남은 거리 | NaviHelper getTBTInfo 목록의 첫 항목 | 목록이 비면 null |
| light | TrafficLight? | 전방 차량 신호 | 서버(공공 신호 API) → 앱 | 정보 없으면 null. R3에서만 사용 |
| headlight | enum? | 전조등(하향등) 상태 | Vehicle SDK Light (아래 변환표) | 직전 값 유지. 한 번도 값이 없으면 null. R5에서만 사용 |
| wiper | enum? | 앞 유리 와이퍼 상태 | Vehicle SDK Wiper (아래 변환표) | 직전 값 유지. 한 번도 값이 없으면 null. R6에서만 사용 |
| rain | Boolean? | 지금 비가 오는가 | 시연은 시나리오 값. 실제 주행은 서버 날씨(서버 담당)가 붙으면 앱이 넣는다 | 모르면 null. R6·R7에서 사용 |
| defroster | Boolean? | 앞유리 성에 제거가 켜져 있는가 | Vehicle SDK HVAC `getHvacDefrosterState`·`registerHvacDefrosterState` (앞 유리) | 직전 값 유지. 한 번도 값이 없으면 null. R7에서만 사용 |
| recirc | Boolean? | 내기 순환이 켜져 있는가 (false면 외기) | Vehicle SDK HVAC `getHvacRecirculationState`·`registerHvacRecirculationState` (좌석 묶음 영역) | 직전 값 유지. 한 번도 값이 없으면 null. R7에서만 사용 |
| fogReported | Boolean? | 운전자가 이 프레임에 "앞이 뿌옇다"고 말했는가 | 앱 음성 인식(STT). 말한 프레임 하나에만 true | 그 밖에는 null. R7에서만 사용 |

- headlight, wiper, rain, defroster, recirc, fogReported는 Kotlin에서 기본값 `= null`을 준다. 이 필드가 없는 시나리오도 그대로 읽히게 하기 위해서다.
- 앱은 이 값들이 없다는 이유로 프레임을 막지 않는다. null이면 그 값을 쓰는 룰만 아무것도 하지 않는다.

**enum 값**

- gear: `P`, `R`, `N`, `D`
- turnSignal: `OFF`, `LEFT`, `RIGHT`, `HAZARD`
- tbt.turn: `LEFT`, `RIGHT`, `STRAIGHT`, `UTURN`, `RAMP_LEFT`, `RAMP_RIGHT`, `TUNNEL`, `ROUNDABOUT_ENTER`, `ROUNDABOUT_EXIT`, `OTHER`
- light.phase: `RED`, `YELLOW`, `GREEN`, `UNKNOWN`
- headlight: `OFF`, `ON`, `AUTO`
- wiper: `OFF`, `ON`, `AUTO`

Tbt: `{ "turn": "LEFT", "distanceM": 48 }` (distanceM은 Int, 미터. SDK의 distance를 반올림)

**앱이 SDK 값을 엔진 값으로 바꾸는 규칙**

| 필드 | SDK 값 | 엔진 값 |
| --- | --- | --- |
| speedKph | `PERF_VEHICLE_SPEED` (m/s) | 3.6을 곱한 값 |
| gear | `GEAR_PARK`, `GEAR_REVERSE`, `GEAR_NEUTRAL`, `GEAR_DRIVE` | `P`, `R`, `N`, `D` |
| gear | `GEAR_1`~`GEAR_9` | `D` |
| gear | `GEAR_UNKNOWN` | 직전 값 유지 |
| turnSignal | `OFF`, `LEFT`, `RIGHT` | 같은 이름. 콘솔의 Blink와 Flash는 둘 다 `LEFT`/`RIGHT`로 나온다 |
| tbt.turn | `TURN_LEFT`, `TURN_SHARP_L` | `LEFT` |
| tbt.turn | `TURN_RIGHT`, `TURN_SHARP_R` | `RIGHT` |
| tbt.turn | `TURN_UTURN` | `UTURN` |
| tbt.turn | `TURN_STRAIGHT` | `STRAIGHT` |
| tbt.turn | `TURN_ENTER_IC_L`, `TURN_EXIT_IC_L` | `RAMP_LEFT` |
| tbt.turn | `TURN_ENTER_IC_R`, `TURN_EXIT_IC_R` | `RAMP_RIGHT` |
| tbt.turn | `TURN_TUNNEL` | `TUNNEL` |
| tbt.turn | `TURN_ENTER_ROUNDABOUT_02` ~ `_12` (숫자는 나갈 출구의 시계 방향) | `ROUNDABOUT_ENTER` |
| tbt.turn | `TURN_EXIT_ROUNDABOUT` | `ROUNDABOUT_EXIT` |
| tbt.turn | 그 밖의 값 (`TURN_KEEP_*`, `TURN_ENTER_IC_C`, `TURN_EXIT_IC_C`, 고가, 지하차도 등) | `OTHER` |
| headlight | 하향등 상태(`getCurrentLowBeamLightState`)가 `ON` | `ON` |
| headlight | 위가 아니고, 하향등 스위치(`getLowBeamLightSwitch`)가 `AUTOMATIC` | `AUTO` |
| headlight | 그 밖의 값 (상태 `OFF`, `DAYTIME_RUNNING`) | `OFF` |
| wiper | 와이퍼 스위치(`getCurrentWiperSwitch`, 앞 유리)가 `AUTO` | `AUTO` |
| wiper | 위가 아니고, 와이퍼 상태(`registerWiperState`)가 `ON` | `ON` |
| wiper | 와이퍼 상태가 `OFF` | `OFF` |
| wiper | 와이퍼 상태가 `OTHER`, `SERVICE` | 직전 값 유지 |

- tbt는 NaviHelper 목록의 첫 항목만 쓴다. 터널 앞에 회전 안내가 먼저 있으면 그 회전이 끝날 때까지 터널은 보이지 않는다.
- R1은 `LEFT`, `RIGHT`만 보므로 새 값이 생겨도 R1 판정은 그대로다. 앱의 `TurnType`을 쓰는 `when`은 새 값을 처리해야 컴파일된다.
- `DAYTIME_RUNNING`(주간주행등)은 터널 안을 밝히는 등화로 보지 않고 `OFF`로 둔다. 실제 차량 동작은 확인하지 못했다(5장).
- Light API 권한은 `pleos.car.permission.CAR_LIGHTS`이며 앱 매니페스트에 이미 있다.
- Wiper API 권한은 `pleos.car.permission.CAR_WINDOWS`이며 앱 매니페스트에 있다.
- HVAC API 권한은 `pleos.car.permission.CAR_CLIMATE`이며 앱 매니페스트에 추가해야 한다.
- 내기 순환은 좌석 영역으로 읽는다. 에뮬레이터는 1열 좌·우와 2열 좌·중·우를 묶은 영역(area ID 117)만 지원한다. 운전석 하나만 지정하면 오류가 난다 (11/11 확인).

TrafficLight: `{ "phase": "RED", "remainSec": 12 }` (remainSec는 Int?, 모르면 null)

### 2.2 출력: GUIDE (안내 요청)

```json
{ "type": "GUIDE", "t": 5000, "rule": "R1", "text": "좌회전 곧이에요, 깜빡이 켜세요", "action": "SIGNAL_LEFT" }
```

| 필드 | 뜻 |
| --- | --- |
| action | 안내가 운전자에게 바라는 동작. 앱이 "따랐는지"를 프레임으로 확인할 때 쓴다 |

| action | 나오는 룰 | 따른 것으로 보는 프레임 |
| --- | --- | --- |
| `SIGNAL_LEFT` | R1, R4 | turnSignal == LEFT |
| `SIGNAL_RIGHT` | R1, R4 | turnSignal == RIGHT |
| `HEADLIGHT_ON` | R5 | headlight가 ON 또는 AUTO |
| `STOP` | R3 | speedKph ≤ STOP_KPH (9.1) |
| `WIPER_ON` | R6 | wiper가 ON 또는 AUTO |
| `DEFOG` | R7 | defroster == true, 또는 안내 때 켜져 있던 내기 순환이 꺼짐(외기) |

- action이 있으면 앱은 재탐색으로 tbt가 바뀌어도 안내한 방향을 잃지 않는다.
- 시나리오 비교 항목에는 넣지 않는다(4.1).

### 2.3 출력: EVENT (판정 이벤트)

```json
{
  "type": "EVENT",
  "id": "evt-1",
  "t": 11000,
  "rule": "R1",
  "result": "KEPT",
  "guided": false,
  "turn": "LEFT",
  "lat": 37.5503,
  "lon": 127.0712
}
```

| 필드 | 뜻 |
| --- | --- |
| id | 엔진 인스턴스 안에서 순서대로 `evt-1`, `evt-2` … (테스트 재현성 때문에 UUID를 쓰지 않는다) |
| t | 이벤트가 나온 프레임의 시각 |
| rule | `R1` ~ `R8` |
| result | `KEPT`(지킴), `MISSED`(놓침). R4·R5·R6·R7은 `KEPT`만 낸다 |
| guided | 이 판정 전에 예방 안내(GUIDE)가 나갔는지. 앱의 "아낀 범칙금" 계산에 쓴다. R2는 안내가 없어 항상 false |
| turn | `LEFT`, `RIGHT`, `UTURN`, `ROUNDABOUT_ENTER`, `ROUNDABOUT_EXIT`, 또는 null. R1은 `LEFT`·`RIGHT`·`UTURN`, R2·R4는 회전 방향, R3는 `RIGHT`, R8은 `ROUNDABOUT_ENTER`·`ROUNDABOUT_EXIT`, R5·R6·R7은 null |
| lat, lon | 이벤트가 나온 프레임의 위치 |

- `turn`이 null일 수 있으므로 앱과 서버는 null을 처리해야 한다. R1의 유턴은 `UTURN`으로 온다(v0.3.2).
- R6·R7은 법규 근거가 없어 리포트의 준수율과 아낀 범칙금에서는 뺀다. 기록은 남긴다.

### 2.4 앱에서 쓰는 방법

```kotlin
val engine = Engine()

// 차량 신호가 들어올 때마다
val outputs: List<EngineOutput> = engine.onFrame(frame)
for (out in outputs) {
    when (out) {
        is Guide -> speak(out.text)          // 앱 담당: TTS + 코치 카드
        is Event -> saveAndShow(out)         // 앱 담당: 리포트, 서버 담당: 서버 저장
    }
}
```

- 엔진은 한 스레드에서만 호출한다고 가정한다.
- 한 프레임에서 출력이 0개, 1개, 여러 개 나올 수 있다. 여러 룰이 한 프레임에 출력하면 룰 번호 순서(R1, R2, R3, R4, R5, R6, R7, R8)로 담는다.
- 적색 우회전에서는 R1(깜빡이)과 R3(멈춤) 안내가 같은 회전에 나올 수 있다. 엔진은 둘 다 내고, 앱은 R3 문장을 먼저 읽는다.
- R5는 안내 직후 운전자가 전조등을 켜면 그 프레임에 EVENT가 바로 나온다. 앱이 "EVENT가 오면 안내 표시를 숨김"으로 처리하면 따랐다는 표시가 보이지 않을 수 있다. 표시 유지 시간은 앱(앱 담당)이 정한다.

---

## 3. 룰 R1: 회전·유턴 전 깜빡이

**법 근거**: 도로교통법 제38조. 좌·우회전할 때 일반도로는 회전 지점 30m 전부터(고속도로는 100m 전부터), 동작이 끝날 때까지 방향지시등을 켜야 한다. 유턴도 같은 조항이며 왼쪽 방향지시등을 30m 전부터 켠다(시행령 별표 2 1호, 법규표 LAW-03). 승용차 기준 현장 적발 범칙금 3만 원, 공익신고 과태료 4만 원.

유턴을 새 룰로 두지 않고 R1에 넣은 이유: 판정 방식(30m 지점, 회전 완료)이 좌회전과 같다. 유턴만 따로 집계하려면 EVENT의 `turn == UTURN`으로 나눈다(v0.3.2).

**MVP 단순화**

- 30m 지점을 지나는 순간 해당 방향 깜빡이가 켜져 있으면 지킴(KEPT), 아니면 놓침(MISSED).
- 고속도로 100m 기준은 MVP에서 뺀다.

### 3.1 파라미터

| 이름 | 값 | 뜻 |
| --- | --- | --- |
| APPROACH_START_M | 100 | 다음 회전까지 이 거리 안이면 감시를 시작한다 |
| GUIDE_LEAD_MS | 4000 | 판정 지점에 도착하기 이만큼 전에 안내한다. 음성 재생과 반응 시간을 합한 초기값이며 TTS 실측 후 조정한다 |
| GUIDE_MIN_M | 50 | 안내 거리의 최솟값 |
| GUIDE_MAX_M | 100 | 안내 거리의 최댓값 |
| CHECK_AT_M | 30 | 판정 지점 |
| TURN_DONE_DEG | 60 | 진행 방향이 이만큼 바뀌면 회전이 끝난 것으로 본다 |
| TURN_TIMEOUT_MS | 30000 | 판정 후 주행 시간(speedKph > 0)이 이만큼 쌓여도 회전이 안 끝나면 기록 없이 취소한다 |
| TBT_JUMP_M | 20 | 다가가는 중 TBT 거리가 직전보다 이만큼 넘게 커지면 다음 회전으로 본다 (v0.3.2). 초기값이며 실차 측정 후 조정한다 |

안내 거리: guideDistance(speedKph) = CHECK_AT_M + speedKph ÷ 3.6 × GUIDE_LEAD_MS ÷ 1000 을 GUIDE_MIN_M 이상 GUIDE_MAX_M 이하로 자른 값.

| 속도 | 안내 거리 |
| --- | --- |
| 15km/h | 50m (최솟값) |
| 30km/h | 약 63m |
| 40km/h | 약 74m |
| 60km/h | 약 97m |
| 80km/h | 100m (최댓값) |

### 3.2 상태

| 상태 | 뜻 | 기억하는 값 |
| --- | --- | --- |
| IDLE | 대기 | 없음 |
| APPROACHING | 회전 지점에 다가가는 중 | targetTurn(LEFT/RIGHT/UTURN), guided, lastDistanceM |
| TURNING | 판정을 마치고 회전이 끝나길 기다리는 중 | targetTurn, guided, signalOk, startHeading, movingMs, lastT |

- signalOk: 30m 지점에서 해당 방향 깜빡이가 켜져 있었는가
- startHeading: 30m 판정을 한 프레임의 headingDeg. 회전 전 곡선 구간의 영향을 줄이기 위해 판정 시점 값을 쓴다
- movingMs: 판정 후 쌓인 주행 시간. 신호 대기 시간은 세지 않는다
- lastT: 직전 프레임의 t
- lastDistanceM: APPROACHING에서 직전 프레임의 tbt.distanceM. APPROACHING에 머무는 모든 프레임(#4와 맞는 행이 없을 때)에서 이번 값으로 바꾼다

TURNING에서는 전이표를 검사하기 전에 프레임마다 시간을 먼저 갱신한다. speedKph > 0이면 movingMs에 (t − lastT)를 더하고, 속도와 상관없이 lastT = t로 바꾼다.

### 3.3 전이표

한 프레임마다 현재 상태의 행을 **위에서부터** 검사하고, **처음 맞는 행 하나만** 적용한다. 맞는 행이 없으면 상태를 유지하고 출력도 없다.

| # | 현재 | 조건 | 다음 | 출력 |
| --- | --- | --- | --- | --- |
| 0 | 모든 상태 | gear가 P 또는 R | IDLE | 없음 (주차·후진으로 보고 취소) |
| 1a | IDLE | tbt.turn이 LEFT, RIGHT, UTURN 중 하나이고 tbt.distanceM ≤ 30 | TURNING (#3과 같은 값, guided = false) | 없음 (처음 본 회전이 이미 판정 지점 안. 연속 회전) |
| 1 | IDLE | tbt.turn이 LEFT, RIGHT, UTURN 중 하나이고 tbt.distanceM ≤ 100 | APPROACHING (targetTurn = tbt.turn, guided = false, lastDistanceM = tbt.distanceM) | 없음 |
| 2 | APPROACHING | tbt가 null이거나 tbt.turn ≠ targetTurn | IDLE | 없음 (경로 재탐색으로 보고 취소) |
| 2a | APPROACHING | tbt.distanceM > lastDistanceM + 20 | APPROACHING (targetTurn = tbt.turn, guided = false, lastDistanceM = tbt.distanceM) | 없음 (같은 방향 다음 회전. 앞 회전은 30m 프레임을 못 봐서 기록하지 않음) |
| 3 | APPROACHING | tbt.distanceM ≤ 30 | TURNING (signalOk = turnSignal가 targetTurn과 같은 방향, startHeading = headingDeg, movingMs = 0, lastT = t) | 없음 |
| 4 | APPROACHING | 30 < tbt.distanceM ≤ guideDistance(speedKph) 이고 turnSignal이 targetTurn과 다르고 guided == false | APPROACHING (guided = true) | GUIDE |
| 5 | TURNING | headingDiff(startHeading, headingDeg) ≥ 60 | IDLE | EVENT (result = signalOk면 KEPT 아니면 MISSED, guided 그대로) |
| 6 | TURNING | movingMs > 30000 | IDLE | 없음 (회전하지 않은 것으로 보고 취소) |

- "turnSignal이 targetTurn과 같은 방향": targetTurn이 LEFT 또는 UTURN이면 turnSignal == LEFT, RIGHT면 turnSignal == RIGHT. HAZARD(비상등)는 같은 방향으로 치지 않는다.
- 유턴은 진행 방향이 180° 바뀌므로 #5의 60° 기준을 반드시 넘는다.
- #1a, #2a는 연속 회전 때문에 넣었다(v0.3.2). #1a가 없으면 앞 회전이 끝난 프레임에 다음 회전이 이미 30m 안일 때 다음 회전을 놓친다(S28). #2a가 없으면 같은 방향 회전이 이어질 때 앞 회전의 guided가 남아 다음 회전 안내가 나가지 않는다(S29). #1a는 안내 기회 없이 MISSED가 나올 수 있다. 법 기준(30m 전 점등)에는 맞는다.
- TURNING에서는 tbt를 보지 않는다. 회전 지점을 지나면 내비가 다음 안내로 바뀌기 때문이다.
- headingDiff(a, b) = | ((b − a + 540) mod 360) − 180 |. 예: 90 → 30 이면 60, 350 → 10 이면 20.
- GUIDE 문구: LEFT면 `좌회전 곧이에요, 깜빡이 켜세요`, RIGHT면 `우회전 곧이에요, 깜빡이 켜세요`, UTURN이면 `유턴 곧이에요, 왼쪽 깜빡이 켜세요`.
- GUIDE action: LEFT·UTURN이면 `SIGNAL_LEFT`, RIGHT면 `SIGNAL_RIGHT`.

### 3.4 예시 흐름 (시나리오 S2)

1. t=2000: 좌회전 90m → APPROACHING (38km/h의 안내 거리는 약 72m라 아직 안내하지 않음)
2. t=4000: 60m, 32km/h의 안내 거리는 약 66m, 깜빡이 꺼짐 → GUIDE (guided = true)
3. t=6000: 운전자가 왼쪽 깜빡이를 켬
4. t=7000: 28m, 깜빡이 켜짐 → TURNING (signalOk = true, startHeading = 90)
5. t=11000: 진행 방향 90° → 30°, 60° 바뀜 → EVENT (KEPT, guided = true) → IDLE

---

## 4. 시나리오

### 4.1 파일 형식

```json
{
  "name": "S1_좌회전_처음부터_켬",
  "description": "왼쪽 깜빡이를 미리 켜고 좌회전. 안내 없이 지킴.",
  "expect": [
    { "type": "EVENT", "t": 11000, "rule": "R1", "result": "KEPT", "guided": false }
  ],
  "frames": [ { "t": 0, "...": "SignalFrame" } ]
}
```

- `expect`에는 엔진이 내보내야 하는 출력 **전체**를 순서대로 적는다. 개수가 다르면 실패다.
- 비교 항목: `type`, `t`, `rule`, 그리고 EVENT면 `result`, `guided`. `id`, `text`, 위치는 비교하지 않는다.
- 표에 없는 필드의 공통값: `gear` = D, `brake` = false, `steeringDeg` = 0.0, `light` = null, `headlight`·`wiper`·`rain`·`defroster`·`recirc`·`fogReported`는 적지 않음(null), `lat`/`lon` = 37.5500/127.0700 근처에서 조금씩 움직이는 값.
- `steeringDeg` = 0.0은 회전 중인 프레임에도 그대로 쓴다. R1은 핸들 각도를 쓰지 않고 좌우 부호도 미정이다. 실제 값은 R2 시나리오부터 넣는다.
- 표의 "다음 안내"는 `tbt = { "turn": "STRAIGHT", "distanceM": 500 }`, 그 다음 프레임은 `distanceM: 480`.

### 4.2 S1: 좌회전, 처음부터 깜빡이 켬

| t (ms) | tbt | speedKph | turnSignal | headingDeg |
| --- | --- | --- | --- | --- |
| 0 | LEFT 120m | 40 | OFF | 90 |
| 2000 | LEFT 90m | 38 | LEFT | 90 |
| 4000 | LEFT 60m | 32 | LEFT | 90 |
| 6000 | LEFT 40m | 26 | LEFT | 90 |
| 7000 | LEFT 28m | 22 | LEFT | 90 |
| 9000 | LEFT 10m | 15 | LEFT | 80 |
| 11000 | 다음 안내 | 15 | LEFT | 30 |
| 13000 | 다음 안내 (480m) | 25 | OFF | 0 |

기대 출력: EVENT t=11000, R1, KEPT, guided = false

### 4.3 S2: 좌회전, 안내 듣고 켬

| t (ms) | tbt | speedKph | turnSignal | headingDeg |
| --- | --- | --- | --- | --- |
| 0 | LEFT 120m | 40 | OFF | 90 |
| 2000 | LEFT 90m | 38 | OFF | 90 |
| 4000 | LEFT 60m | 32 | OFF | 90 |
| 5000 | LEFT 48m | 28 | OFF | 90 |
| 6000 | LEFT 40m | 26 | LEFT | 90 |
| 7000 | LEFT 28m | 22 | LEFT | 90 |
| 9000 | LEFT 10m | 15 | LEFT | 80 |
| 11000 | 다음 안내 | 15 | LEFT | 30 |
| 13000 | 다음 안내 (480m) | 25 | OFF | 0 |

기대 출력

1. GUIDE t=4000, R1
2. EVENT t=11000, R1, KEPT, guided = true

### 4.4 S3: 좌회전, 끝까지 안 켬

S2와 같고, turnSignal만 모든 프레임에서 OFF.

기대 출력

1. GUIDE t=4000, R1
2. EVENT t=11000, R1, MISSED, guided = true

### 4.5 추가 시나리오 (tasks 단계 6)

| 이름 | 내용 | 기대 출력 |
| --- | --- | --- |
| S4_우회전_처음부터_켬 | S1과 같되 tbt는 RIGHT, turnSignal은 RIGHT, headingDeg는 90 → 100 → 150 → 180 (우회전이라 커짐) | EVENT R1 KEPT guided=false (진행 방향이 60° 바뀌는 프레임에서) |
| S5_좌회전_반대쪽_깜빡이 | S2와 같되 깜빡이를 LEFT가 아니라 RIGHT로 켬 | GUIDE t=4000, EVENT R1 MISSED guided=true |
| S6_접근중_재탐색 | S1처럼 다가가다 t=4000(60m) 다음 프레임에서 tbt가 null이 되고, 이후 직진 | 출력 없음 |
| S7_신호대기_후_좌회전 | 4.6 표 | GUIDE t=4000, EVENT t=72000 R1 MISSED guided=true |
| S8_판정후_주차 | 4.7 표 | 출력 없음 |
| S9_재탐색후_우회전 | 4.8 표 | EVENT t=12000 R1 KEPT guided=false |
| S10_판정후_회전없이_주행 | 4.9 표 | 출력 없음 |
| S11_터널_전조등 | 4.10 표 | GUIDE t=11000 R5, EVENT t=14000 R5 KEPT guided=true |
| S12_나들목_안내후_켬 | 4.11 표 | GUIDE t=15000 R4, EVENT t=18000 R4 KEPT guided=true |
| S13_적색우회전_멈춤 | 4.12 표 | GUIDE t=4000 R3, EVENT t=14000 R1 KEPT guided=false, EVENT t=14000 R3 KEPT guided=true |
| S14_적색우회전_서행만 | 4.13 표 | GUIDE t=4000 R3, EVENT t=14000 R1 KEPT guided=false, EVENT t=14000 R3 MISSED guided=true |
| S15_적색우회전_접근중_녹색 | 4.14 표 | EVENT t=11000 R1 KEPT guided=false (R3 출력 없음) |
| S16_우회전_신호정보_없음 | S15와 같되 light는 모든 프레임에서 null | EVENT t=11000 R1 KEPT guided=false (R3 출력 없음) |
| S17_비_와이퍼 | 4.15 표 | GUIDE t=10000 R6, EVENT t=13000 R6 KEPT guided=true |
| S18_내비없이_좌회전_안켬 | 4.16 표 | EVENT t=8000 R2 MISSED guided=false |
| S19_내비없이_좌회전_켬 | S18과 같되 turnSignal은 t=2000~8000에 LEFT | EVENT t=8000 R2 KEPT guided=false |
| S20_완만한_커브 | 4.17 표 | 출력 없음 |
| S21_후진후_회전 | 4.18 표 | 출력 없음 |
| S22_비_내기순환_김서림 | 4.19 표 | GUIDE t=10000 R7, EVENT t=14000 R7 KEPT guided=true |
| S23_음성_앞이_뿌옇다 | 4.20 표 | GUIDE t=3000 R7, EVENT t=6000 R7 KEPT guided=true |
| S24_비_외기순환 | S22와 같되 recirc는 모든 프레임에서 false | 출력 없음 |
| S25_유턴_안내후_켬 | S2와 같되 tbt는 UTURN, headingDeg는 90(6프레임) → 80 → 30 → 270 | GUIDE t=4000 R1, EVENT t=11000 R1 KEPT guided=true |
| S26_유턴_반대쪽_깜빡이 | S25와 같되 t=6000~11000에 RIGHT를 켬 | GUIDE t=4000 R1, EVENT t=11000 R1 MISSED guided=true |
| S27_연속회전_좌후우_여유 | 4.21 표 | EVENT t=11000 R1 KEPT guided=false, GUIDE t=15000 R1, EVENT t=21000 R1 KEPT guided=true |
| S28_연속회전_좌후우_근접 | 4.21 표 | EVENT t=11000 R1 KEPT guided=false, EVENT t=15000 R1 KEPT guided=false |
| S29_같은방향_연속_30m놓침 | 4.21 표 | GUIDE t=4000 R1, GUIDE t=10000 R1, EVENT t=14000 R1 MISSED guided=true |

R2를 넣어도 S1~S12의 기대 출력은 바뀌지 않아야 한다. 이 시나리오들의 회전은 모두 R1이 판정하므로 R2는 쉰다(11.3 #2).

### 4.6 S7: 판정 후 신호 대기

30m에서 깜빡이가 꺼져 있었으므로, 대기 중에 켜도 MISSED다. 대기 시간은 타임아웃에 세지 않고 guided도 유지된다.

| t (ms) | tbt | speedKph | turnSignal | headingDeg |
| --- | --- | --- | --- | --- |
| 0 | LEFT 120m | 40 | OFF | 90 |
| 2000 | LEFT 90m | 38 | OFF | 90 |
| 4000 | LEFT 60m | 32 | OFF | 90 |
| 7000 | LEFT 28m | 22 | OFF | 90 |
| 8000 | LEFT 20m | 0 | OFF | 90 |
| 38000 | LEFT 20m | 0 | LEFT | 90 |
| 68000 | LEFT 20m | 0 | LEFT | 90 |
| 70000 | LEFT 10m | 15 | LEFT | 80 |
| 72000 | 다음 안내 | 15 | LEFT | 30 |

### 4.7 S8: 판정 후 주차

회전 지점 앞에서 주차하면 기록 없이 취소한다. 다시 출발해 방향이 달라져도 EVENT가 나오지 않아야 한다.

| t (ms) | tbt | speedKph | gear | turnSignal | headingDeg |
| --- | --- | --- | --- | --- | --- |
| 0 | LEFT 120m | 40 | D | OFF | 90 |
| 2000 | LEFT 90m | 38 | D | LEFT | 90 |
| 4000 | LEFT 60m | 32 | D | LEFT | 90 |
| 7000 | LEFT 28m | 22 | D | LEFT | 90 |
| 9000 | LEFT 25m | 0 | P | LEFT | 90 |
| 60000 | 다음 안내 | 10 | D | OFF | 30 |

### 4.8 S9: 재탐색 후 다른 방향 회전

전이표 #2를 검증한다. 좌회전 대상이 취소되어야 우회전을 새로 감시하고 오른쪽 깜빡이를 KEPT로 판정한다.

| t (ms) | tbt | speedKph | turnSignal | headingDeg |
| --- | --- | --- | --- | --- |
| 0 | LEFT 120m | 40 | OFF | 90 |
| 2000 | LEFT 90m | 38 | OFF | 90 |
| 4000 | RIGHT 80m | 35 | RIGHT | 90 |
| 6000 | RIGHT 60m | 32 | RIGHT | 90 |
| 8000 | RIGHT 28m | 22 | RIGHT | 90 |
| 10000 | RIGHT 10m | 15 | RIGHT | 100 |
| 12000 | 다음 안내 | 15 | RIGHT | 150 |

### 4.9 S10: 판정 후 회전 없이 계속 주행

전이표 #6을 검증한다. 주행 시간이 t=38000에 31000ms가 되어 취소된다. 그 뒤 방향이 바뀌어도 EVENT가 나오지 않아야 한다.

| t (ms) | tbt | speedKph | turnSignal | headingDeg |
| --- | --- | --- | --- | --- |
| 0 | LEFT 120m | 40 | OFF | 90 |
| 2000 | LEFT 90m | 38 | LEFT | 90 |
| 4000 | LEFT 60m | 32 | LEFT | 90 |
| 7000 | LEFT 28m | 22 | LEFT | 90 |
| 37000 | 다음 안내 | 40 | OFF | 90 |
| 38000 | 다음 안내 (480m) | 40 | OFF | 90 |
| 40000 | 다음 안내 (480m) | 30 | OFF | 20 |

### 4.10 S11: 터널 전조등 (R5)

70km/h로 직진하며 터널에 다가간다. 200m 안의 첫 프레임(187m)에서 안내하고, 운전자가 전조등을 켠 프레임에서 KEPT. turnSignal은 모두 OFF, headingDeg는 모두 90.

| t (ms) | tbt | speedKph | headlight |
| --- | --- | --- | --- |
| 0 | TUNNEL 400m | 70 | OFF |
| 5000 | TUNNEL 303m | 70 | OFF |
| 10000 | TUNNEL 206m | 70 | OFF |
| 11000 | TUNNEL 187m | 70 | OFF |
| 14000 | TUNNEL 129m | 70 | ON |
| 20000 | TUNNEL 12m | 70 | ON |
| 22000 | 다음 안내 | 70 | ON |

기대 출력

1. GUIDE t=11000, R5
2. EVENT t=14000, R5, KEPT, guided = true

### 4.11 S12: 나들목, 안내 듣고 깜빡이 (R4)

80km/h로 오른쪽 나들목에 다가간다. 안내 거리는 약 189m라 167m 프레임에서 안내하고, 100m 지점에서 오른쪽 깜빡이가 켜져 있어 KEPT. headingDeg는 모두 90.

| t (ms) | tbt | speedKph | turnSignal |
| --- | --- | --- | --- |
| 0 | RAMP_RIGHT 500m | 80 | OFF |
| 5000 | RAMP_RIGHT 389m | 80 | OFF |
| 9000 | RAMP_RIGHT 300m | 80 | OFF |
| 15000 | RAMP_RIGHT 167m | 80 | OFF |
| 17000 | RAMP_RIGHT 122m | 80 | RIGHT |
| 18000 | RAMP_RIGHT 100m | 80 | RIGHT |
| 20000 | 다음 안내 | 80 | RIGHT |

기대 출력

1. GUIDE t=15000, R4
2. EVENT t=18000, R4, KEPT, guided = true

---

### 4.12 S13: 적색 우회전, 안내 듣고 멈춤 (R3)

오른쪽 깜빡이는 처음부터 켜 두었다(R1은 안내 없이 KEPT). 적색이라 R3가 60m에서 안내하고, 25m에서 멈췄다가 우회전해 KEPT. 진행 방향 90 → 150.

| t (ms) | tbt | speedKph | turnSignal | light | headingDeg |
| --- | --- | --- | --- | --- | --- |
| 0 | RIGHT 120m | 40 | RIGHT | RED | 90 |
| 2000 | RIGHT 90m | 38 | RIGHT | RED | 90 |
| 4000 | RIGHT 60m | 32 | RIGHT | RED | 90 |
| 6000 | RIGHT 40m | 20 | RIGHT | RED | 90 |
| 7000 | RIGHT 28m | 10 | RIGHT | RED | 90 |
| 8000 | RIGHT 25m | 0 | RIGHT | RED | 90 |
| 10000 | RIGHT 25m | 0 | RIGHT | RED | 90 |
| 12000 | RIGHT 15m | 10 | RIGHT | RED | 100 |
| 14000 | 다음 안내 | 15 | RIGHT | null | 150 |

기대 출력

1. GUIDE t=4000, R3
2. EVENT t=14000, R1, KEPT, guided = false
3. EVENT t=14000, R3, KEPT, guided = true

### 4.13 S14: 적색 우회전, 서행만 (R3)

S13과 같되 멈추지 않는다. t=8000은 RIGHT 25m·5km/h, t=10000은 RIGHT 20m·5km/h, t=12000은 RIGHT 15m·8km/h.

기대 출력: GUIDE t=4000 R3, EVENT t=14000 R1 KEPT guided=false, EVENT t=14000 R3 MISSED guided=true

### 4.14 S15: 적색 우회전, 다가가는 중 녹색 (R3)

90m에서 적색으로 감시를 시작했지만 75m에서 녹색으로 바뀌어 멈출 의무가 사라진다(9.3 #3). 오른쪽 깜빡이는 계속 켜져 있다.

| t (ms) | tbt | speedKph | turnSignal | light | headingDeg |
| --- | --- | --- | --- | --- | --- |
| 0 | RIGHT 120m | 40 | RIGHT | RED | 90 |
| 2000 | RIGHT 90m | 38 | RIGHT | RED | 90 |
| 3000 | RIGHT 75m | 36 | RIGHT | GREEN | 90 |
| 5000 | RIGHT 50m | 30 | RIGHT | GREEN | 90 |
| 7000 | RIGHT 28m | 22 | RIGHT | GREEN | 90 |
| 9000 | RIGHT 10m | 15 | RIGHT | GREEN | 100 |
| 11000 | 다음 안내 | 15 | RIGHT | null | 150 |

### 4.15 S17: 비 오는데 와이퍼 (R6)

50km/h로 직진(tbt = STRAIGHT 900m, headingDeg 90, turnSignal OFF). 비가 5초 계속된 t=10000에 안내하고, t=13000에 와이퍼를 켜 KEPT.

| t (ms) | rain | wiper |
| --- | --- | --- |
| 0 | false | OFF |
| 5000 | true | OFF |
| 8000 | true | OFF |
| 10000 | true | OFF |
| 13000 | true | ON |
| 20000 | true | ON |
| 25000 | false | ON |

### 4.16 S18: 내비 없이 좌회전, 깜빡이 안 켬 (R2)

tbt는 모든 프레임에서 null, turnSignal은 OFF. t=8000에 t=0(90°)부터 60° 바뀌었고 그동안 속도가 30km/h 이하라 회전으로 잡힌다.

| t (ms) | speedKph | headingDeg |
| --- | --- | --- |
| 0 | 30 | 90 |
| 2000 | 25 | 90 |
| 4000 | 20 | 90 |
| 5000 | 15 | 80 |
| 6000 | 12 | 60 |
| 7000 | 12 | 40 |
| 8000 | 15 | 30 |
| 10000 | 25 | 30 |

### 4.17 S20: 완만한 커브 (R2 오탐 방지)

tbt는 null, turnSignal은 OFF, 25km/h. 20초 동안 60° 바뀌지만 10초 안의 변화는 24°를 넘지 않는다.

| t (ms) | 0 | 4000 | 8000 | 12000 | 16000 | 20000 |
| --- | --- | --- | --- | --- | --- | --- |
| headingDeg | 90 | 78 | 66 | 54 | 42 | 30 |

### 4.18 S21: 후진 후 회전 (R2 주차장 제외)

tbt는 null, turnSignal은 OFF. 후진(t=0) 뒤 30초가 안 지나서 회전해도 판정하지 않는다(11.3 #1).

| t (ms) | gear | speedKph | headingDeg |
| --- | --- | --- | --- |
| 0 | R | 5 | 90 |
| 4000 | D | 10 | 90 |
| 6000 | D | 12 | 60 |
| 8000 | D | 10 | 20 |

### 4.19 S22: 비 오는데 내기 순환, 김서림 안내 (R7)

40km/h로 직진(tbt = STRAIGHT 900m, headingDeg 90, turnSignal OFF). 비가 오지만 와이퍼는 처음부터 켜 두어 R6는 조용하다. 내기 순환 + 성에 제거 꺼짐이 10초 이어진 t=10000에 안내하고, t=14000에 성에 제거를 켜 KEPT.

| t (ms) | rain | wiper | recirc | defroster |
| --- | --- | --- | --- | --- |
| 0 | true | ON | true | false |
| 5000 | true | ON | true | false |
| 10000 | true | ON | true | false |
| 14000 | true | ON | true | true |
| 20000 | true | ON | true | true |

### 4.20 S23: 음성으로 "앞이 뿌옇다" (R7)

40km/h로 직진, 비 없음(rain false), 외기 순환(recirc false), 성에 제거 꺼짐. t=3000에 운전자가 말해 바로 안내하고, t=6000에 성에 제거를 켜 KEPT.

| t (ms) | fogReported | defroster |
| --- | --- | --- |
| 0 | null | false |
| 3000 | true | false |
| 6000 | null | true |

### 4.21 S27~S29: 연속 회전 (R1, v0.3.2)

S27·S28은 t=0~9000이 같다: LEFT 120·90·60·28·10m, 속도 40·38·32·22·15, turnSignal LEFT, headingDeg 90(4프레임) → 80.

S27 (다음 우회전까지 여유)

| t (ms) | tbt | speedKph | turnSignal | headingDeg |
| --- | --- | --- | --- | --- |
| 11000 | RIGHT 90m | 15 | LEFT | 30 |
| 13000 | RIGHT 70m | 25 | OFF | 0 |
| 15000 | RIGHT 50m | 25 | OFF | 0 |
| 16000 | RIGHT 40m | 22 | RIGHT | 0 |
| 17000 | RIGHT 28m | 20 | RIGHT | 0 |
| 19000 | RIGHT 10m | 15 | RIGHT | 20 |
| 21000 | 다음 안내 | 15 | RIGHT | 70 |

S28 (좌회전이 끝나는 프레임에 우회전이 이미 25m 앞. 그 프레임은 #5만 적용되고, 다음 프레임(8m)에서 #1a로 바로 TURNING. #1a 검증)

| t (ms) | tbt | speedKph | turnSignal | headingDeg |
| --- | --- | --- | --- | --- |
| 11000 | RIGHT 25m | 15 | RIGHT | 30 |
| 13000 | RIGHT 8m | 12 | RIGHT | 0 |
| 15000 | 다음 안내 | 15 | RIGHT | 70 |
| 17000 | 다음 안내 (480m) | 25 | OFF | 90 |

S29 (같은 방향 좌회전 두 번, 첫 회전의 30m 프레임 없음. #2a 검증)

| t (ms) | tbt | speedKph | turnSignal | headingDeg |
| --- | --- | --- | --- | --- |
| 0 | LEFT 120m | 40 | OFF | 90 |
| 2000 | LEFT 90m | 38 | OFF | 90 |
| 4000 | LEFT 60m | 32 | OFF | 90 |
| 6000 | LEFT 35m | 26 | OFF | 90 |
| 8000 | LEFT 90m | 25 | OFF | 30 |
| 10000 | LEFT 60m | 30 | OFF | 30 |
| 12000 | LEFT 28m | 22 | OFF | 30 |
| 14000 | 다음 안내 | 15 | OFF | 330 |

---

## 5. 아직 정하지 않은 것

공모전은 실차 시연을 지원하지 않고 앱 검증은 에뮬레이터로 한다 (2026-10-28 온라인 설명회). 아래 값은 실제 도로에서 잴 수 없으니 에뮬레이터에서 재현할 방법을 찾거나 값을 미정으로 둔 채 진행한다.

- [ ] 30m 단순화(30m 지점 한 번만 판정)를 팀이 받아들이는가
- [x] 핸들 각도가 SDK에서 숫자로 나오는가: 에뮬레이터에서 Float로 나온다
- [ ] 핸들 각도의 좌우 부호. 콘솔에 -90을 넣으면 SDK는 +90을 준다. 실제 왼쪽이 어느 쪽인지 확인 필요. R1은 진행 방향만 쓰므로 영향 없음. R2 설계 전에 정한다
- [ ] TBT 남은 거리가 몇 초마다 갱신되는가. 에뮬레이터 내비는 위치가 움직이지 않아 측정 불가. 재현 방법을 주최측에 문의한다. 너무 드물면 50m·30m 지점을 건너뛸 수 있음
- [ ] 회전 완료를 진행 방향 변화 대신 TBT 변화로 볼지. 비스듬한 교차로(진행 방향이 60°까지 안 바뀜) 직후 다음 회전이 오면 R1이 TURNING에 묶여 두 회전 모두 기록하지 않는다 (v0.3.2. 시나리오는 이 질문이 정해진 뒤 만든다)
- [ ] `TBT_JUMP_M`(20m)이 실제 내비 거리에서 맞는가. 내비 거리가 GPS 오차로 20m 넘게 튀면 같은 회전을 새 회전으로 보고 안내가 한 번 더 나갈 수 있다. 실차 측정 후 조정한다 (v0.3.2)
- [ ] 실제 headingDeg 기준(북쪽 0°, 시계 방향)이 Fused Location과 같은가. 에뮬레이터에서는 위치를 움직여도 0.0으로만 나와 측정 불가. 재현 방법을 주최측에 문의한다
- [ ] TBT 남은 거리의 기준점이 무엇인가. 에뮬레이터에서 측정 불가, 공개 문서에도 없다. 제출과 시연에 꼭 필요하지 않아 주최측 문의에서는 뺐다. 법령의 30m 기준점은 교차로 가장자리(시행령 별표 2)라서 기준점이 교차로 중심이면 판정이 늦어진다
- [ ] 비상등 입력원. Vehicle SDK에서 찾지 못했다. R1은 HAZARD와 OFF를 똑같이 처리하므로 영향 없음
- [x] 에뮬레이터에서 하향등 상태·스위치 값이 실제로 나오는가 (11/11 확인). Virtual Console의 Exterior Lights Mode를 Low beam으로 두면 앱에 `ON`, Off·Tail lamp(미등)는 `OFF`로 들어온다
- [ ] 전조등 자동(`AUTO`)이 들어오는가. 에뮬레이터에서는 확인할 수 없다. 에뮬레이터의 `HEADLIGHTS_SWITCH`는 OFF·ON만 지원하고, 콘솔의 Auto는 제조사 속성 `EXTERIOR_LIGHTS_MODE`(0x21400754)만 바꾼다. 이 속성은 Pleos SDK에 읽는 함수가 없고, 직접 읽으려면 일반 앱이 받을 수 없는 `CAR_VENDOR_EXTENSION` 권한이 필요하다. 그래서 자동으로 둔 운전자도 터널 200m 앞에서 아직 꺼져 있으면 R5 안내를 한 번 듣고, 터널에서 켜지면 KEPT로 기록된다. 실제 차량의 스위치가 `AUTOMATIC`을 지원하면 지금 변환표대로 `AUTO`가 된다
- [ ] `DAYTIME_RUNNING`을 OFF로 보는 것이 맞는가. 주간주행등이 터널에서 어떻게 동작하는지 확인 필요 (v0.2)
- [ ] R4 판정 지점을 100m 하나로 두는 것. 일반도로 나들목은 30m 기준이라 더 엄격하게 본다 (v0.2)
- [ ] R4·R5에서 MISSED를 낼 조건. 차로 번호, 터널 통과 여부를 알 수 있는 입력이 생기면 다시 본다 (v0.2)
- [ ] R3 멈춤 기준(STOP_KPH 2.0)과 멈춤 인정 구간(TURN_START_DEG 15°)이 실제 차량에서 맞는가 (v0.3)
- [ ] R2 회전 기준(10초 안 60°, 30km/h 이하)이 실제 도로에서 오탐이 없는가. 굽은 도로, 주차장 (v0.3). 회전교차로는 내비 안내가 있으면 R8이 본다 (v0.3.3)
- [ ] 회전교차로에서 내비 TBT 목록이 실제로 "진입(ENTER) → 진출(EXIT)" 순서로 오는가, 진출 안내의 남은 거리가 출구까지인가. 에뮬레이터 내비는 위치가 움직이지 않아 확인할 수 없다. 다르면 R8 판정 지점을 다시 정한다 (v0.3.3)
- [ ] R8 진출 판정 지점 `RB_EXIT_CHECK_M`(10m)이 회전교차로 크기에 맞는가. 법령은 30m 전을 말하지만 회전교차로 안은 30m가 안 되는 경우가 많다 (v0.3.3)
- [ ] 실제 주행의 `rain` 출처. 서버 날씨 API(서버 담당)가 붙기 전까지 R6은 시나리오에서만 동작한다 (v0.3)
- [ ] 보조 지표(12장): 급정거·급출발 기준 가속도(m/s²). 근거 자료를 찾아 정한다
- [ ] 보조 지표(12장): 한 번 센 뒤 다시 세지 않는 시간
- [ ] 보조 지표(12장): 계산 위치. 엔진에 룰과 분리된 계산기를 둘지, 앱이나 습관 인사이트 라이브러리에서 할지. EVENT 형식과 시나리오 기대값은 바꾸지 않는다
- [ ] 보조 지표(12장): R3 리포트에 "적색 신호 앞 급정거"를 함께 보여줄지
- [ ] R7 FOG_HOLD_MS 10초는 시연용 초안이다. 실제로 김이 서리기 시작하는 시간과 맞는 근거가 없다 (v0.3.1)
- [ ] R7 조건에 바깥 기온(겨울철)을 넣을지. AAOS 속성 `ENV_OUTSIDE_TEMPERATURE`는 에뮬레이터 차량 시스템에 있지만 앱에서 읽히는지 확인하지 않았다 (v0.3.1)
- [ ] R7 음성 요청(`fogReported`)을 앱 STT에서 어떤 문장으로 알아들을지. 앱의 STT 연결이 먼저 필요하다 (v0.3.1)

---

## 6. 나중 룰 메모 (지금 구현하지 않음)

R2·R3·R6은 v0.3에서 각각 11장, 9장, 10장으로 옮겼다.

- **아직 뺀 것**
  - R2에서 핸들 각도를 보조로 쓰는 것: 좌우 부호가 미정이다(5장).
  - R4의 `TURN_KEEP_L`/`TURN_KEEP_R`(갈림길 유지): 차로를 바꿀 필요가 없는 경우가 많아 안내가 잔소리가 될 수 있다.
  - R5의 야간·비 조건: 엔진은 현재 시각을 쓰지 않으므로 별도 입력이 필요하다.
  - 모드(DIAGNOSE/CORRECT/TRAINING/QUIET)에서 R2~R6을 어떻게 다룰지: spec v0.4(tasks 단계 11)에서 함께 정한다.
  - `data/law_table.json`에 룰 연결 필드(`rule`)와 터널 등화 조항(LAW-16) 추가: 법규 Q&A와 리포트 연결 작업 때 한다.

---

## 7. 룰 R4: 나들목 진출입 전 깜빡이

**법 근거**: 도로교통법 제38조제1항, 시행령 별표 2 (법규표 LAW-04). 차로를 바꿀 때 일반도로는 30m, 고속도로는 100m 이상 앞선 지점부터 방향지시등을 켜야 한다.

**MVP 단순화**

- 내비 안내가 나들목 진입·진출(`RAMP_LEFT`, `RAMP_RIGHT`)일 때만 본다.
- 판정 지점은 고속도로 기준 100m 하나로 둔다. 엔진은 도로 종류를 모른다.
- 100m 지점에서 해당 방향 깜빡이가 켜져 있으면 KEPT. **꺼져 있어도 MISSED를 내지 않는다.** 차로 번호를 모르므로 이미 바깥 차로에 있어서 차로를 바꿀 필요가 없었는지 알 수 없기 때문이다.
- 회전 완료(진행 방향 변화)는 보지 않는다. 나들목은 진행 방향이 60°까지 바뀌지 않는 경우가 많다.

### 7.1 파라미터

| 이름 | 값 | 뜻 |
| --- | --- | --- |
| LANE_START_M | 300 | 나들목까지 이 거리 안이면 감시를 시작한다 |
| LANE_CHECK_M | 100 | 판정 지점 |
| LANE_GUIDE_MAX_M | 300 | 안내 거리의 최댓값 |
| GUIDE_LEAD_MS | 4000 | R1과 같은 값을 쓴다 |

안내 거리: laneGuideDistance(speedKph) = LANE_CHECK_M + speedKph ÷ 3.6 × GUIDE_LEAD_MS ÷ 1000 을 LANE_GUIDE_MAX_M 이하로 자른 값.

| 속도 | 안내 거리 |
| --- | --- |
| 60km/h | 약 167m |
| 80km/h | 약 189m |
| 100km/h | 약 211m |

### 7.2 상태

| 상태 | 뜻 | 기억하는 값 |
| --- | --- | --- |
| IDLE | 대기 | 없음 |
| APPROACHING | 나들목에 다가가는 중 | targetTurn(RAMP_LEFT/RAMP_RIGHT), guided |

### 7.3 전이표

R1과 같이 위에서부터 검사하고 처음 맞는 행 하나만 적용한다.

| # | 현재 | 조건 | 다음 | 출력 |
| --- | --- | --- | --- | --- |
| 0 | 모든 상태 | gear가 P 또는 R | IDLE | 없음 |
| 1 | IDLE | tbt.turn이 RAMP_LEFT 또는 RAMP_RIGHT이고 tbt.distanceM ≤ 300 | APPROACHING (targetTurn = tbt.turn, guided = false) | 없음 |
| 2 | APPROACHING | tbt가 null이거나 tbt.turn ≠ targetTurn | IDLE | 없음 (재탐색으로 보고 취소) |
| 3 | APPROACHING | tbt.distanceM ≤ 100 이고 turnSignal이 targetTurn과 같은 방향 | IDLE | EVENT (KEPT, guided 그대로, turn = LEFT 또는 RIGHT) |
| 4 | APPROACHING | tbt.distanceM ≤ 100 | IDLE | 없음 (MVP 단순화) |
| 5 | APPROACHING | 100 < tbt.distanceM ≤ laneGuideDistance(speedKph) 이고 turnSignal이 targetTurn과 다르고 guided == false | APPROACHING (guided = true) | GUIDE |

- "같은 방향": RAMP_LEFT면 turnSignal == LEFT, RAMP_RIGHT면 turnSignal == RIGHT. HAZARD는 같은 방향으로 치지 않는다.
- EVENT의 turn: RAMP_LEFT면 `LEFT`, RAMP_RIGHT면 `RIGHT`.
- GUIDE 문구: RAMP_LEFT면 `왼쪽 나들목이 곧이에요, 깜빡이 켜세요`, RAMP_RIGHT면 `오른쪽 나들목이 곧이에요, 깜빡이 켜세요`.
- GUIDE action: RAMP_LEFT면 `SIGNAL_LEFT`, RAMP_RIGHT면 `SIGNAL_RIGHT`.

---

## 8. 룰 R5: 터널 진입 전 전조등

**법 근거**: 도로교통법 제37조제1항제3호. 터널 안을 운행할 때는 전조등 등 등화를 켜야 한다. 범칙금은 아직 확인하지 못했다(법규표에 행이 없다).

**MVP 단순화**

- 내비 안내가 터널(`TUNNEL`)일 때만 본다. 야간·비 조건은 뺀다.
- 안내를 들은 뒤 전조등을 켜면 KEPT. **끝까지 안 켜도 MISSED를 내지 않는다.** 터널 안내가 사라지는 것이 터널 통과인지 경로 재탐색인지 구분할 수 없기 때문이다.
- 다가갈 때 이미 전조등이 ON 또는 AUTO이면 안내도 기록도 하지 않는다.
- headlight가 null이면 아무것도 하지 않는다.

### 8.1 파라미터

| 이름 | 값 | 뜻 |
| --- | --- | --- |
| TUNNEL_GUIDE_M | 200 | 터널까지 이 거리 안이면 안내한다 |

### 8.2 상태

| 상태 | 뜻 | 기억하는 값 |
| --- | --- | --- |
| IDLE | 대기 | 없음 |
| WATCHING | 안내를 했고 전조등이 켜지길 기다리는 중 | 없음 |
| DONE | 이번 터널은 끝남. 같은 터널로 다시 안내하지 않으려고 기다리는 중 | 없음 |

### 8.3 전이표

| # | 현재 | 조건 | 다음 | 출력 |
| --- | --- | --- | --- | --- |
| 0 | 모든 상태 | gear가 P 또는 R | IDLE | 없음 |
| 1 | IDLE | tbt.turn이 TUNNEL이고 tbt.distanceM ≤ 200 이고 headlight == OFF | WATCHING | GUIDE |
| 2 | IDLE | tbt.turn이 TUNNEL이고 tbt.distanceM ≤ 200 이고 headlight가 ON 또는 AUTO | DONE | 없음 (이미 켜져 있음) |
| 3 | WATCHING | headlight가 ON 또는 AUTO | DONE | EVENT (KEPT, guided = true, turn = null) |
| 4 | WATCHING | tbt가 null이거나 tbt.turn ≠ TUNNEL | IDLE | 없음 (MVP 단순화) |
| 5 | DONE | tbt가 null이거나 tbt.turn ≠ TUNNEL | IDLE | 없음 |

- IDLE에서 headlight가 null이면 #1, #2 둘 다 맞지 않아 IDLE에 머문다.
- GUIDE 문구: `터널이 곧 나와요, 전조등 켜세요`.
- GUIDE action: `HEADLIGHT_ON`.

---

## 9. 룰 R3: 적색 신호 우회전 일시정지

**법 근거**: 도로교통법 제5조, 시행규칙 별표 2 (법규표 LAW-12). 전방 차량 신호가 적색이면 정지선, 횡단보도, 교차로 직전에서 먼저 멈춘 뒤 우회전해야 한다. 승용차 범칙금 6만 원, 벌점 15점.

**MVP 단순화**

- 내비 안내가 우회전(`RIGHT`)이고 전방 신호(`light.phase`)가 `RED`일 때만 본다. 신호가 null이거나 `UNKNOWN`이면 판정하지 않는다.
- 정지선 위치는 모른다. 감시를 시작한 뒤 회전을 시작하기 전(진행 방향이 15° 바뀌기 전)에 한 번이라도 멈췄으면 지킨 것으로 본다.
- 회전 완료와 취소는 R1과 같은 방법(진행 방향 60° 변화, 주행 30초 타임아웃)을 쓴다.
- 신호는 실제 주행에서 서버(공공 신호 API)가 붙어야 들어온다. 그 전에는 시나리오 재생에서만 동작한다.

### 9.1 파라미터

| 이름 | 값 | 뜻 |
| --- | --- | --- |
| APPROACH_START_M | 100 | R1과 같다. 우회전이 이 거리 안이고 적색이면 감시를 시작한다 |
| CHECK_AT_M | 30 | R1과 같다. 이 거리 안이면 회전 단계로 넘어간다 |
| 안내 거리 | guideDistance(speedKph) | R1과 같다 (3.1) |
| STOP_KPH | 2.0 | 이 속도 이하인 프레임이 있으면 멈춘 것으로 본다 |
| TURN_START_DEG | 15.0 | 회전 단계에서 진행 방향이 이만큼 바뀌기 전까지만 멈춤을 인정한다 |
| TURN_DONE_DEG, TURN_TIMEOUT_MS | 60, 30000 | R1과 같다 |

### 9.2 상태

| 상태 | 뜻 | 기억하는 값 |
| --- | --- | --- |
| IDLE | 대기 | 없음 |
| APPROACHING | 적색 우회전 지점에 다가가는 중 | guided, stopped |
| TURNING | 회전이 끝나길 기다리는 중 | guided, stopped, startHeading, movingMs, lastT |

TURNING에서 주행 시간(movingMs) 갱신은 R1과 같다 (3.2).

### 9.3 전이표

| # | 현재 | 조건 | 다음 | 출력 |
| --- | --- | --- | --- | --- |
| 0 | 모든 상태 | gear가 P 또는 R | IDLE | 없음 |
| 1 | IDLE | tbt.turn이 RIGHT이고 tbt.distanceM ≤ 100 이고 light.phase == RED | APPROACHING (guided = false, stopped = false) | 없음 |
| 2 | APPROACHING | tbt가 null이거나 tbt.turn ≠ RIGHT | IDLE | 없음 (재탐색) |
| 3 | APPROACHING | light가 null이거나 phase ≠ RED 이고 stopped == false | IDLE | 없음 (멈출 의무가 사라짐) |
| 4 | APPROACHING | speedKph ≤ 2.0 | APPROACHING (stopped = true) | 없음 |
| 5 | APPROACHING | tbt.distanceM ≤ 30 | TURNING (startHeading = headingDeg, movingMs = 0, lastT = t) | 없음 |
| 6 | APPROACHING | tbt.distanceM ≤ guideDistance(speedKph) 이고 stopped == false 이고 guided == false | APPROACHING (guided = true) | GUIDE |
| 7 | TURNING | speedKph ≤ 2.0 이고 headingDiff(startHeading, headingDeg) < 15 | TURNING (stopped = true) | 없음 |
| 8 | TURNING | headingDiff(startHeading, headingDeg) ≥ 60 | IDLE | EVENT (stopped면 KEPT 아니면 MISSED, guided 그대로, turn = RIGHT) |
| 9 | TURNING | movingMs > 30000 | IDLE | 없음 (회전하지 않음) |

- TURNING에서는 tbt와 light를 보지 않는다. 회전 지점을 지나면 내비가 다음 안내로 바뀌고, 멈춘 뒤 신호가 바뀌는 것은 판정과 상관없기 때문이다.
- GUIDE 문구: `전방 적색이에요, 정지선에서 멈췄다 가세요`. action: `STOP`.

---

## 10. 룰 R6: 비 올 때 와이퍼

**근거**: 법규표에 해당 조항이 없다. 안전 안내로만 쓴다. EVENT는 남기지만 리포트의 준수율과 아낀 범칙금에서는 뺀다 (2.3).

**MVP 단순화**

- 비가 5초 이상 계속 오는데 와이퍼가 꺼져 있으면 한 번 안내하고, 켜면 KEPT를 기록한다. 끝까지 안 켜도 MISSED는 내지 않는다.
- rain이나 wiper가 null이면 아무것도 하지 않는다.

### 10.1 파라미터

| 이름 | 값 | 뜻 |
| --- | --- | --- |
| RAIN_HOLD_MS | 5000 | 비가 이만큼 계속 와야 안내한다 |

### 10.2 상태

| 상태 | 뜻 | 기억하는 값 |
| --- | --- | --- |
| IDLE | 대기 | 없음 |
| WAITING | 비가 오고 와이퍼가 꺼져 있어 시간을 재는 중 | rainSince |
| WATCHING | 안내했고 와이퍼가 켜지길 기다리는 중 | 없음 |
| DONE | 이번 비에는 끝남 | 없음 |

### 10.3 전이표

| # | 현재 | 조건 | 다음 | 출력 |
| --- | --- | --- | --- | --- |
| 0 | 모든 상태 | gear가 P 또는 R | IDLE | 없음 |
| 1 | IDLE | rain == true 이고 wiper == OFF 이고 speedKph > 0 | WAITING (rainSince = t) | 없음 |
| 2 | WAITING | rain ≠ true 이거나 wiper ≠ OFF | IDLE | 없음 |
| 3 | WAITING | t − rainSince ≥ 5000 | WATCHING | GUIDE |
| 4 | WATCHING | wiper가 ON 또는 AUTO | DONE | EVENT (KEPT, guided = true, turn = null) |
| 5 | WATCHING | rain ≠ true | IDLE | 없음 |
| 6 | DONE | rain ≠ true | IDLE | 없음 |

- GUIDE 문구: `비가 와요, 와이퍼 켜세요`. action: `WIPER_ON`.

---

## 11. 룰 R2: 깜빡이 없이 회전

**법 근거**: 도로교통법 제38조제1항 (법규표 LAW-03). 방향을 바꿀 때는 방향지시등으로 알려야 한다. 승용차 범칙금 3만 원, 과태료 4만 원.

**MVP 단순화**

- 내비 안내 없이도 동작한다. 최근 프레임의 진행 방향 변화로 회전을 찾는다. 핸들 각도는 좌우 부호가 미정이라 쓰지 않는다.
- 안내(GUIDE)는 없다. 회전이 끝나면 깜빡이를 켰는지로 KEPT·MISSED를 기록한다. KEPT도 기록해야 진단 리포트의 준수율을 계산할 수 있다.
- R1이 같은 회전을 판정하면 R2는 쉰다. 같은 회전을 두 번 세지 않기 위해서다.

### 11.1 파라미터

| 이름 | 값 | 뜻 |
| --- | --- | --- |
| R2_TURN_DEG | 60.0 | 진행 방향이 이만큼 바뀌면 회전으로 본다 |
| R2_TURN_WINDOW_MS | 10000 | 위 변화가 이 시간 안에 일어나야 한다. 천천히 굽는 길과 구분한다 |
| R2_TURN_MAX_KPH | 30.0 | 회전 구간의 모든 프레임이 이 속도 이하여야 한다 |
| R2_SIGNAL_LOOKBACK_MS | 5000 | 회전 시작 이만큼 전부터 회전 완료까지 같은 방향 깜빡이가 한 번이라도 켜졌으면 KEPT |
| R2_REVERSE_SKIP_MS | 30000 | 최근 이 시간 안에 기어 R이 있었으면 주차 중으로 보고 판정하지 않는다 |
| R2_OTHER_RULE_SKIP_MS | 10000 | R1·R4가 판정한 뒤 이 시간 동안은 판정하지 않는다 |

### 11.2 기억하는 값

R2는 상태 대신 최근 프레임 기록을 갖는다.

| 값 | 뜻 |
| --- | --- |
| history | 최근 R2_TURN_WINDOW_MS + R2_SIGNAL_LOOKBACK_MS(15초) 동안의 프레임 (t, headingDeg, speedKph, turnSignal) |
| lastReverseT | 마지막으로 기어 R이었던 프레임의 t |
| lastOtherRuleEventT | 마지막으로 R1·R4가 EVENT를 낸 프레임의 t |

### 11.3 판정 순서

한 프레임마다 위에서부터 검사하고 처음 맞는 행 하나만 적용한다.

| # | 조건 | 할 일 | 출력 |
| --- | --- | --- | --- |
| 0 | gear가 P 또는 R | history를 비운다. R이면 lastReverseT = t | 없음 |
| 1 | t − lastReverseT < 30000 | history를 비운다 | 없음 (주차 중) |
| 2 | 다른 룰이 이 회전을 본다: R1이나 R8이 IDLE이 아니거나, tbt.turn이 LEFT·RIGHT·ROUNDABOUT_ENTER·ROUNDABOUT_EXIT이고 100m 이내이거나, tbt.turn이 RAMP_LEFT·RAMP_RIGHT이고 300m 이내이거나, 이 프레임 또는 최근 10초 안에 R1·R4·R8 EVENT가 나왔다 | history를 비운다 | 없음 |
| 3 | 이 프레임을 history에 넣은 뒤, t − 10000 이후의 어떤 프레임 f에 대해 headingDiff(f, 지금) ≥ 60 이고 f부터 지금까지 모든 프레임이 speedKph ≤ 30 | 그런 f 중 가장 이른 것을 회전 시작으로 본다. history를 비운다 | EVENT (아래) |
| 4 | 그 밖 | history에 넣고 15초보다 오래된 프레임을 지운다 | 없음 |

- 회전 방향: 부호 있는 변화 ((지금 − f + 540) mod 360) − 180 이 양수면 RIGHT(시계 방향), 음수면 LEFT.
- EVENT: 회전 시작 5초 전부터 지금까지 history에 같은 방향 turnSignal이 한 번이라도 있으면 KEPT, 없으면 MISSED. guided = false, turn = 회전 방향.
- 판정 뒤 history를 비우므로 같은 회전을 두 번 세지 않는다. 다음 회전은 다시 60° 변화가 쌓여야 잡힌다.
- 유턴도 진행 방향이 60° 이상 바뀌므로 회전으로 센다. 법령도 유턴에 방향지시등을 요구한다.

---

## 12. 보조 지표: 급정거·급출발 (설계 미정)

룰이 아니라 정차 리포트에 보여주는 참고 지표다. 지킴·놓침, 범칙금, 음성 안내가 없다.

- **룰로 만들지 않는 이유**: 단독 범칙금이 없다(난폭운전 제46조의3은 여러 행위가 반복돼야 성립한다). 공모전 예시 "안전운전 점수 앱"과 겹친다. 속도가 1초에 한 번만 들어와 추정이 거칠다.
- **계산 방식 (초안)**: 연속한 두 프레임의 속도 차이를 시간 차이로 나눠 가속도를 추정한다. 감속이 기준보다 크면 급정거, 가속이 기준보다 크면 급출발로 한 번 센다. 한 번 센 뒤 짧은 시간 동안은 다시 세지 않는다.
- R3·R6·R2 구현 뒤에 한다. 아래가 정해지기 전에는 구현하지 않는다(5장).

---

## 13. 룰 R7: 앞유리 김서림

**근거**: 법규표에 해당 조항이 없다. 초보 운전자가 김이 서릴 때 어떤 버튼을 눌러야 하는지 몰라 시야를 잃는 문제를 돕는 안전 안내다. R6처럼 EVENT는 남기지만 리포트의 준수율과 아낀 범칙금에서는 뺀다 (2.3).

**MVP 단순화**

- 차량은 습기나 김서림을 직접 알려주지 않는다. 그래서 두 가지로 안내 시점을 정한다.
  1. 상황 추정: 비가 오고, 내기 순환이 켜져 있고, 성에 제거가 꺼진 채 주행이 FOG_HOLD_MS 이어지면 김이 서리기 쉬운 상황으로 본다.
  2. 음성 요청: 운전자가 "앞이 뿌옇다"고 말하면(`fogReported`) 바로 안내한다. 앱 STT가 붙어야 실제로 들어온다.
- 시연은 1번 상황 추정으로 한다.
- 성에 제거를 켜거나, 안내 때 켜져 있던 내기 순환을 끄면(외기) KEPT. 끝까지 안 해도 MISSED는 내지 않는다.
- R6(와이퍼) 안내가 진행 중이면 R7은 기다린다. 비가 올 때 두 문장을 한꺼번에 읽지 않기 위해서다.
- defroster가 null이면 아무것도 하지 않는다.

### 13.1 파라미터

| 이름 | 값 | 뜻 |
| --- | --- | --- |
| FOG_HOLD_MS | 10000 | 상황 추정 조건이 이만큼 이어지면 안내한다. 시연용 초안이다(5장) |

### 13.2 상태

| 상태 | 뜻 | 기억하는 값 |
| --- | --- | --- |
| IDLE | 대기 | 없음 |
| WAITING | 김이 서리기 쉬운 상황이 이어져 시간을 재는 중 | since |
| WATCHING | 안내했고 운전자가 따르길 기다리는 중 | recircAtGuide (안내 때 내기 순환이 켜져 있었는가) |
| DONE | 이번 상황은 끝남 | 없음 |

### 13.3 전이표

"김서림 상황"은 rain == true 이고 recirc == true 이고 defroster == false 이고 speedKph > 0 인 프레임이다.
"R6 진행 중"은 R6이 WAITING 또는 WATCHING인 상태다.

| # | 현재 | 조건 | 다음 | 출력 |
| --- | --- | --- | --- | --- |
| 0 | 모든 상태 | gear가 P 또는 R | IDLE | 없음 |
| 1 | IDLE | fogReported == true 이고 defroster == false | WATCHING (recircAtGuide = recirc == true) | GUIDE |
| 2 | IDLE | 김서림 상황이고 R6 진행 중이 아님 | WAITING (since = t) | 없음 |
| 3 | WAITING | 김서림 상황이 아니거나 R6 진행 중 | IDLE | 없음 |
| 4 | WAITING | t − since ≥ FOG_HOLD_MS | WATCHING (recircAtGuide = true) | GUIDE |
| 5 | WATCHING | defroster == true, 또는 recircAtGuide 이고 recirc == false | DONE | EVENT (KEPT, guided = true, turn = null) |
| 6 | DONE | rain ≠ true 이고 recirc ≠ true | IDLE | 없음 |

- WATCHING은 운전자가 따르거나 기어가 P·R이 될 때까지 유지한다. 같은 주행에서 안내를 반복하지 않기 위해서다.
- GUIDE 문구: `앞유리가 뿌예질 수 있어요, 성에 제거를 켜고 외기로 바꾸세요`. action: `DEFOG`.
- 앱의 "어떻게 하는지" 그림은 성에 제거(부채꼴)와 외기 순환 버튼 모양만 보여주고 위치는 말하지 않는다. 버튼 위치가 차종마다 다르기 때문이다.

---

## 14. 룰 R8: 회전교차로 진입·진출 깜빡이

**법 근거**: 도로교통법 제38조제1항(방향을 바꿀 때 신호), 시행령 별표 2 7호·8호 (법규표 LAW-17 진입, LAW-18 진출). 2022. 1. 11. 개정된 제38조제1항이 "회전교차로에 진입하거나 회전교차로에서 진출하는 경우" 신호를 명시한다. 들어갈 때는 진입 지점 30m 이상 앞에서 **왼쪽**(7호), 나갈 때는 진출하려는 때 **오른쪽**(8호). 처분은 제38조 위반이라 승용차 범칙금 3만 원(현장 적발), 과태료 4만 원(공익신고)으로 보며 별표 8 원문은 확인 필요다. 조문 확인 기록은 `docs/proposals/spec-new-rules.md` 0.1.

**판정 기준 (11/13 확정)**

- 들어갈 때와 나갈 때를 **따로** 판정한다. 회전교차로 하나에서 EVENT가 두 개(진입, 진출) 나온다.
- 기록 시점은 그 지점을 **통과했을 때**다. 회전교차로 안은 진행 방향이 계속 바뀌므로 R1처럼 진행 방향 변화(60°)로 끝을 볼 수 없다. 대신 내비의 다음 안내가 바뀌면(진입 → 진출 → 그다음 안내) 통과로 본다.

**MVP 단순화**

- 내비 안내가 회전교차로 진입(`ROUNDABOUT_ENTER`)·진출(`ROUNDABOUT_EXIT`)일 때만 본다. 내비 없이 도는 회전교차로는 보지 않는다.
- 진입은 R1과 같이 30m 지점에서 왼쪽 깜빡이를 본다. 진출은 회전교차로 안이 짧아서 출구 10m 앞(`RB_EXIT_CHECK_M`)에서 오른쪽 깜빡이를 본다.
- 첫 출구로 바로 나가는 경우에도 진입 왼쪽 깜빡이를 판정한다. 법문에 예외가 없다 (제안서 Q6-2를 이렇게 정함).
- 판정 지점을 한 번도 보지 못하고 통과하면(남은 거리가 판정 지점보다 먼 프레임에서 바로 다음 안내로 바뀜) 기록하지 않는다.

### 14.1 파라미터

| 이름 | 값 | 뜻 |
| --- | --- | --- |
| APPROACH_START_M | 100 | R1과 같다. 이 거리 안이면 감시를 시작한다 |
| CHECK_AT_M | 30 | R1과 같다. 진입 판정 지점 |
| RB_EXIT_CHECK_M | 10 | 진출 판정 지점. 시연용 초안이다(5장) |
| RB_PASS_M | 20 | 다음 안내로 바뀌기 직전 남은 거리가 이 값 이하면 그 지점을 통과한 것으로 본다. 더 멀면 재탐색으로 보고 취소한다 |
| guideDistance(speedKph) | R1과 같다 | 안내 거리 (50~100m) |

### 14.2 상태

| 상태 | 뜻 | 기억하는 값 |
| --- | --- | --- |
| IDLE | 대기 | 없음 |
| APPROACHING | 진입 또는 진출 지점에 다가가는 중 | target(ROUNDABOUT_ENTER/ROUNDABOUT_EXIT), guided, signalOk(판정 지점의 깜빡이, 보기 전에는 null), lastDistanceM |

### 14.3 전이표

위에서부터 검사하고 처음 맞는 행 하나만 적용한다. "판정 지점"은 target이 ROUNDABOUT_ENTER면 CHECK_AT_M, ROUNDABOUT_EXIT면 RB_EXIT_CHECK_M이다. "맞는 깜빡이"는 ENTER면 LEFT, EXIT면 RIGHT다 (HAZARD는 아니다).

| # | 현재 | 조건 | 다음 | 출력 |
| --- | --- | --- | --- | --- |
| 0 | 모든 상태 | gear가 P 또는 R | IDLE | 없음 |
| 1 | IDLE | tbt.turn이 ROUNDABOUT_ENTER 또는 ROUNDABOUT_EXIT이고 tbt.distanceM ≤ 100 | APPROACHING (target = tbt.turn, guided = false, signalOk = 판정 지점 안이면 맞는 깜빡이인지 아니면 null) | 없음 |
| 2 | APPROACHING | tbt가 null이거나 tbt.turn ≠ target 이고 lastDistanceM ≤ RB_PASS_M 이고 signalOk ≠ null | #1을 이번 tbt로 적용 (진입 다음 진출을 바로 감시) | EVENT (signalOk면 KEPT 아니면 MISSED, guided 그대로, turn = target) |
| 3 | APPROACHING | tbt가 null이거나 tbt.turn ≠ target | #1을 이번 tbt로 적용 | 없음 (재탐색, 또는 판정 지점을 못 봄) |
| 4 | APPROACHING | signalOk == null 이고 tbt.distanceM ≤ 판정 지점 | APPROACHING (signalOk = 맞는 깜빡이인지) | 없음 |
| 5 | APPROACHING | signalOk == null 이고 tbt.distanceM ≤ guideDistance(speedKph) 이고 맞는 깜빡이가 아니고 guided == false | APPROACHING (guided = true) | GUIDE |
| 6 | APPROACHING | 그 밖 | APPROACHING (lastDistanceM = tbt.distanceM) | 없음 |

- GUIDE 문구: ENTER면 `회전교차로에 들어가요, 왼쪽 깜빡이 켜세요`, EXIT면 `회전교차로에서 나가요, 오른쪽 깜빡이 켜세요`.
- GUIDE action: ENTER면 `SIGNAL_LEFT`, EXIT면 `SIGNAL_RIGHT`.
- #1·#4에서 판정 지점의 깜빡이를 정하면 바꾸지 않는다. 판정 지점을 지나 켜도 놓침이다 (R1과 같다).
- R2는 tbt가 회전교차로이거나 R8이 감시 중이거나 R8이 10초 안에 EVENT를 냈으면 판정하지 않는다 (11.3 #2). 같은 회전교차로를 R2가 "깜빡이 없이 회전"으로 다시 세지 않게 한다.

### 14.4 시나리오

공통: 회전교차로 진입 → 진출 → 직진. 진입 안내는 120·90·60·45·28·12m, 진출 안내는 40·25·15·8m(t=8000~11000), 그다음 직진 안내. 속도는 40에서 20까지 줄었다가 25. 진행 방향은 0°에서 회전교차로를 돌며 330·300·240·180·120·90°로 바뀐다(R2가 세지 않는지 확인).

| 이름 | 깜빡이 | 기대 출력 |
| --- | --- | --- |
| S30_회전교차로_안내후_켬 | 진입 안내 뒤 45m부터 LEFT, 진출 안내 뒤 15m부터 RIGHT | GUIDE R8(진입), EVENT R8 KEPT guided=true (진입), GUIDE R8(진출), EVENT R8 KEPT guided=true (진출) |
| S31_회전교차로_진출때_안켬 | 진입은 처음부터 LEFT, 진출 안내부터 끝까지 OFF | EVENT R8 KEPT guided=false (진입), GUIDE R8(진출), EVENT R8 MISSED guided=true (진출) |
| S32_회전교차로_깜빡이_안켬 | 끝까지 OFF | GUIDE R8, EVENT R8 MISSED guided=true, GUIDE R8, EVENT R8 MISSED guided=true. R2는 나오지 않는다 |
