# spec 추가 제안: 새 코치 상황 6가지

2026-11-11 · 제안 단계 (AI 담당 확인 전) · 작성: 앱 담당 요청으로 정리

> **진행 상황 (2026-11-11, origin/main 병합 후)**
>
> - main에 AI 담당의 spec v0.3·v0.3.1이 먼저 들어갔다. 이 문서의 1장(R3)과 2장(R2)은 **spec v0.3 9장·11장으로 대체**되었다. AI 담당이 따로 구현했고, 이 브랜치도 그 구현을 따른다. 아래 1·2장은 비교용 기록으로만 남긴다. 차이는 "부록 A".
> - 룰 번호: R6 = 비 올 때 와이퍼, R7 = 앞유리 김서림으로 정해졌다. 회전교차로는 **R8**로 바꿨다.
> - 시나리오 번호: main이 S13~S24를 쓴다. 이 문서의 시나리오는 유턴 S25·S26, 연속 회전 S27~S29, 회전교차로 S30~S32로 다시 매겼다.
> - 구현됨(이 브랜치): 유턴(R1 확장), 연속 회전(R1 #1a·#2a). 구현 안 됨: R8(앱 `when` 분기가 같이 바뀌어야 해서 보류, 6.8).

이 문서는 `docs/spec.md`에 넣을 내용을 **제안**한다. 코드는 아직 바꾸지 않았다. AI 담당이 확인하고, 아래 "미정 질문"에 답이 정해지면 spec.md에 옮기고 구현한다.

| # | 상황 | 제안 룰 | 판정 수준 |
| --- | --- | --- | --- |
| 1 | 적색 신호 우회전 일시정지 | R3 (spec 6장 계획) | 안내 + KEPT/MISSED |
| 2 | 깜빡이 없이 회전 (TBT 없는 골목 포함) | R2 (spec 6장 계획) | 기록만 (안내 없음) + KEPT/MISSED |
| 3 | 유턴 깜빡이 | **R1 확장** 추천 | 안내 + KEPT/MISSED |
| 4 | 연속 회전 | R1 보완 (새 룰 아님) | R1과 같음 |
| 5 | 고속도로 차로 변경 깜빡이 | **판정하지 않음** 추천 | Q&A·주간 코칭에서만 다룸 |
| 6 | 회전교차로 진입·진출 깜빡이 | R8 (신규) | 진입 KEPT/MISSED, 진출 안내 + KEPT만 |

---

## 0. 확인한 근거

### 0.1 법령 (2026-11-11, 국가법령정보센터 원문)

| 조항 | 내용 (요약이 아니라 원문 기준) |
| --- | --- |
| 도로교통법 제2조제13호의2 | "회전교차로": 원형 교통섬을 중심으로 반시계방향으로 통행하도록 한 원형의 도로 |
| 제2조제28호 | "서행": 즉시 정지시킬 수 있는 정도의 느린 속도로 진행하는 것 |
| 제2조제30호 | "일시정지": 바퀴를 일시적으로 **완전히** 정지시키는 것 |
| 제25조의2 (2022. 1. 11. 신설) | ① 회전교차로에서는 반시계방향으로 통행 ② 진입하려는 경우 서행하거나 일시정지, 이미 진행하는 차에 진로 양보 ③ 회전교차로 통행을 위해 신호하는 앞차의 진행을 뒤차가 방해하면 안 됨 |
| 제38조제1항 (2022. 1. 11. 개정) | 좌회전·우회전·횡단·**유턴**·서행·정지·후진, 같은 방향으로 진행하면서 진로를 바꾸려는 경우와 **회전교차로에 진입하거나 회전교차로에서 진출하는 경우**에는 그 행위가 끝날 때까지 신호해야 한다 |
| 시행령 별표 2 (2023. 10. 17. 개정) 1호 | 좌회전·횡단·**유턴**·왼쪽 진로 변경: 그 지점(좌회전은 교차로 가장자리) 30m(고속도로 100m) 이상 앞에서 **왼쪽** 방향지시기 |
| 별표 2 2호 | 우회전·오른쪽 진로 변경: 30m(고속도로 100m) 이상 앞에서 **오른쪽** 방향지시기 |
| 별표 2 7호 | **회전교차로에 진입하려는 때**: 그 지점 30m 이상 앞에서 **왼쪽** 방향지시기 |
| 별표 2 8호 | **회전교차로에서 진출하려는 때**: **그 행위를 하려는 때** **오른쪽** 방향지시기 |

- 출처: law.go.kr 도로교통법 제2조·제25조의2·제38조 조문 화면, 시행령 별표 2 PDF.
- 별표 8(범칙금)의 회전교차로 행은 원문을 읽지 못했다. 제38조 위반이므로 LAW-01과 같은 금액(승용차 범칙금 3만 원)으로 보이지만 **확인 필요**로 둔다.

### 0.2 NaviHelper SDK (navi-data 3.0.4, `TurnType`)

앱 Gradle 캐시의 SDK 클래스에서 직접 확인했다.

- `TURN_ENTER_ROUNDABOUT_02`, `_03`, `_04`, `_06`, `_08`, `_09`, `_10`, `_12`: 회전교차로 진입. 숫자는 **나갈 출구의 시계 방향**으로 보인다(02·03시 = 오른쪽 첫 출구, 12시 = 직진, 09·10시 = 왼쪽, 06시 = 되돌아감). 공개 문서가 없어 **추정**이다.
- `TURN_EXIT_ROUNDABOUT`: 회전교차로 진출.
- 그 밖: `TURN_UNKNOWN`, `TURN_KEEP_L/R`, `TURN_DEVIDE_MERGE`, `TURN_TOLL`, 고가·지하차도 진입/회피 등.
- 지금 `app/src/main/java/kkam/app/SdkMapping.kt`는 회전교차로 값을 모두 `OTHER`로 바꾼다. 그래서 **지금 엔진은 회전교차로를 알 수 없다.** 엔진의 `TurnType`에 값을 추가하고 앱 매핑을 바꿔야 한다(6장).
- `TBTInfo`는 `type`, `distance`, `description`만 있다. **차로 번호, 도로 종류는 없다.**
- `NaviHelperEventListener`에 `onCurrentRoadSpeedLimit(Int)`(현재 도로 제한속도)와 `onCautionPointInfo`(주의 지점. `LANE_CHANGE_CAMERA_ALERT`, `CUT_IN_VIOLATION_ENFORCEMENT` 포함)가 있다. 5장 차로 변경의 대안에서 쓴다.
- 에뮬레이터 내비는 위치가 움직이지 않아(spec 5장) 회전교차로 TBT가 실제로 어떤 순서로 오는지(진입 뒤 `TURN_EXIT_ROUNDABOUT`가 따로 오는지) **확인할 수 없다.**

### 0.3 기존 엔진에서 다시 쓰는 것

- `headingDiff(a, b)`, `guideDistance(speedKph)`, `APPROACH_START_M`, `CHECK_AT_M`, `TURN_DONE_DEG`, `TURN_TIMEOUT_MS` (R1Rule.kt)
- 전이표 규칙: 위에서부터 검사, 처음 맞는 행 하나만 적용, 맞는 행이 없으면 상태 유지.
- 시나리오 공통값(spec 4.1)을 그대로 쓴다. 아래 표에서 적지 않은 필드는 공통값이다.

### 0.4 모델 변경 요약 (전부 합친 것)

| 대상 | 바뀌는 것 | 필요한 룰 | 다른 담당 영향 |
| --- | --- | --- | --- |
| `RuleId` | `R8` 추가 | 회전교차로 | 앱 리포트, 서버 저장 |
| `TurnType` | `ROUNDABOUT_IN`, `ROUNDABOUT_OUT` 추가 | 회전교차로 | 앱 `SdkMapping.kt` 수정 (앱 담당) |
| `Tbt` | `exitClock: Int? = null` 추가 (선택, 질문 Q6-3) | 회전교차로 | 앱 매핑 |
| `GuideAction` | `STOP` 추가 | R3 | 앱이 "따름"을 `speedKph == 0`으로 확인 |
| `Event.turn` | `UTURN` 값이 들어올 수 있음 | 유턴(R1 확장) | 앱·서버가 UTURN을 처리 |
| `SignalFrame.light` | 형식은 그대로. **실제로 채워야 함** | R3 | 서버 담당(신호 API) → 앱 |

새 입력 필드는 없다. 기존 필드 기본값 규칙(`= null`)을 지키면 S1~S12 시나리오는 그대로 읽힌다.

---

## 1. R3: 적색 신호 우회전 일시정지

**목적**: 전방 차량 신호가 적색일 때 우회전하면서 정지선 앞에서 멈추지 않는 습관을 고친다. 처분이 R1의 두 배(범칙금 6만 원, 벌점 15점)라 "아낀 범칙금" 효과가 가장 크다.

**법 근거**: LAW-12 (도로교통법 제5조, 시행규칙 별표 2). "일시정지"는 바퀴를 완전히 멈추는 것(제2조제30호).

**MVP 단순화**

- 내비 안내가 우회전(`RIGHT`)이고 `light`가 있을 때만 본다. `light`가 null이거나 `UNKNOWN`이면 아무것도 하지 않는다.
- 판정 지점 30m 안에서 신호가 적색이면 "멈춰야 하는 우회전"으로 본다. 그 뒤 회전이 끝나기 전에 `speedKph == 0`인 프레임이 있으면 KEPT, 없으면 MISSED.
- 회전을 시작하기 전에 녹색으로 바뀌면 이번 우회전은 판정하지 않는다(적색 우회전이 아니게 됨).
- 우회전 삼색등, 보행자 유무(LAW-13)는 입력이 없어 보지 않는다.
- 정지 위치는 보지 않는다. 30m 안에서 한 번이라도 멈추면 KEPT다(질문 Q1-2).

### 1.1 입력 신호

| 필드 | 지금 있나 | 쓰는 곳 |
| --- | --- | --- |
| tbt (RIGHT, distanceM) | 있음 | 감시 시작, 30m 지점 |
| light.phase | 있음 (형식만. 앱은 지금 null을 보냄) | 적색 여부 |
| speedKph | 있음 | 정지 여부, 주행 시간 |
| headingDeg | 있음 | 회전 시작·완료 |
| brake | 있음 | 쓰지 않음 (속도 0이면 충분) |

`light`가 "다음 TBT 교차로의 차량 신호"인지는 서버 담당과 확인해야 한다(Q1-1).

### 1.2 파라미터

| 이름 | 값 | 뜻 |
| --- | --- | --- |
| APPROACH_START_M | 100 | R1과 같음. 감시 시작 거리 |
| STOP_ZONE_M | 30 | 이 거리 안에서 적색이면 멈춰야 하는 우회전으로 본다. R1의 CHECK_AT_M과 같은 값 |
| STOP_SPEED_KPH | 0.0 | 이 속도 이하면 멈춘 것으로 본다. 법 정의(완전히 정지)를 따른다 (Q1-3) |
| TURN_START_DEG | 15 | 진행 방향이 이만큼 바뀌면 회전을 시작한 것으로 본다. 녹색 전환 판단에 쓴다 |
| TURN_DONE_DEG | 60 | R1과 같음 |
| TURN_TIMEOUT_MS | 30000 | R1과 같음 |

안내 거리는 R1의 `guideDistance(speedKph)`를 그대로 쓴다.

### 1.3 상태

| 상태 | 뜻 | 기억하는 값 |
| --- | --- | --- |
| IDLE | 대기 | 없음 |
| APPROACHING | 우회전 지점에 다가가는 중 | guided |
| CHECKING | 적색을 확인했고, 멈추는지 보며 회전이 끝나길 기다리는 중 | guided, stopped, startHeading, movingMs, lastT |
| DONE | 이번 우회전은 판정하지 않기로 함. TBT가 바뀌길 기다리는 중 | 없음 |

CHECKING에서는 R1의 TURNING처럼 전이표 전에 movingMs, lastT를 먼저 갱신한다.

### 1.4 전이표

| # | 현재 | 조건 | 다음 | 출력 |
| --- | --- | --- | --- | --- |
| 0 | 모든 상태 | gear가 P 또는 R | IDLE | 없음 |
| 1 | IDLE | tbt.turn == RIGHT 이고 tbt.distanceM ≤ 100 | APPROACHING (guided = false) | 없음 |
| 2 | APPROACHING | tbt가 null이거나 tbt.turn ≠ RIGHT | IDLE | 없음 (재탐색 또는 이미 회전함) |
| 3 | APPROACHING | tbt.distanceM ≤ 30 이고 light.phase == RED | CHECKING (stopped = speedKph ≤ 0, startHeading = headingDeg, movingMs = 0, lastT = t) | 없음 |
| 4 | APPROACHING | 30 < tbt.distanceM ≤ guideDistance(speedKph) 이고 light.phase == RED 이고 guided == false | APPROACHING (guided = true) | GUIDE |
| 5 | CHECKING | headingDiff(startHeading, headingDeg) ≥ 60 | IDLE | EVENT (stopped면 KEPT 아니면 MISSED, guided 그대로, turn = RIGHT) |
| 6 | CHECKING | light.phase == GREEN 이고 headingDiff(startHeading, headingDeg) < 15 | DONE | 없음 (회전 전에 녹색으로 바뀜) |
| 7 | CHECKING | stopped == false 이고 speedKph ≤ 0 | CHECKING (stopped = true) | 없음 |
| 8 | CHECKING | movingMs > 30000 | IDLE | 없음 (회전하지 않음) |
| 9 | DONE | tbt가 null이거나 tbt.turn ≠ RIGHT | IDLE | 없음 |

- CHECKING에서는 tbt를 보지 않는다(R1 TURNING과 같은 이유). light가 null·UNKNOWN으로 바뀌어도 이미 정한 판정을 이어간다.
- 30m 안에서 녹색이었다가 적색이 되면 #3이 그때 적용된다. 30m 안에서 끝까지 녹색이면 판정 없이 #2로 끝난다.
- GUIDE 문구: `전방 적색이에요, 정지선에서 멈췄다 가세요` (spec 6장). action: `STOP`.
- 같은 우회전에서 R1 GUIDE와 R3 GUIDE가 같은 프레임에 나올 수 있다(Q1-5).

### 1.5 시나리오

모든 프레임 turnSignal = RIGHT로 두어 R1은 안내 없이 KEPT만 내게 한다. light의 remainSec는 null.

**S13_적색우회전_멈춤**

| t | tbt | speedKph | headingDeg | light |
| --- | --- | --- | --- | --- |
| 0 | RIGHT 120 | 40 | 90 | RED |
| 2000 | RIGHT 90 | 38 | 90 | RED |
| 4000 | RIGHT 60 | 32 | 90 | RED |
| 7000 | RIGHT 28 | 20 | 90 | RED |
| 9000 | RIGHT 12 | 0 | 90 | RED |
| 13000 | RIGHT 12 | 0 | 90 | RED |
| 15000 | RIGHT 5 | 8 | 110 | RED |
| 17000 | 다음 안내 | 15 | 160 | RED |

기대: GUIDE t=4000 R3 → EVENT t=17000 R1 KEPT guided=false → EVENT t=17000 R3 KEPT guided=true

**S14_적색우회전_서행만**: S13에서 t=9000을 speedKph 8로 바꾸고, t=13000을 빼고, t=11000 `RIGHT 5, 10km/h, 110°`, t=13000 `다음 안내, 15km/h, 160°`.
기대: GUIDE t=4000 R3 → EVENT t=13000 R1 KEPT guided=false → EVENT t=13000 R3 MISSED guided=true

**S15_적색우회전_신호정보없음**: S14와 같고 light는 모두 null.
기대: EVENT t=13000 R1 KEPT guided=false (R3 출력 없음)

**S16_적색우회전_접근중_녹색**: S14와 같고 t=9000의 light만 GREEN (그 뒤 프레임도 GREEN).
기대: GUIDE t=4000 R3 → EVENT t=13000 R1 KEPT guided=false (R3 EVENT 없음, #6 → DONE)

### 1.6 오판 위험

| 위험 | 결과 | 줄이는 방법 |
| --- | --- | --- |
| light가 다른 교차로(또는 직진 신호가 아닌 신호)를 가리킴 | 엉뚱한 GUIDE·MISSED | 서버가 TBT 교차로의 차량 신호를 주는지 확인 (Q1-1) |
| 신호 정보 지연 (공공 API 갱신 주기) | 녹색인데 적색으로 보고 판정 | remainSec가 작으면(예: 2초 이하) 판정하지 않는 행 추가 검토 (Q1-4) |
| 앞차 때문에 멀리서 멈췄다가 정지선에서는 멈추지 않음 | 실제 위반인데 KEPT | MVP에서는 받아들인다. 정지 위치를 보려면 TBT 기준점 확인이 먼저 (Q1-2) |
| 차량 속도 센서가 정지 때 0.0이 아니라 0.x를 줌 | 멈췄는데 MISSED | 에뮬레이터·SDK 최솟값 확인 후 STOP_SPEED_KPH 조정 (Q1-3) |
| 우회전 전용 삼색등 적색 (아예 우회전 금지) | 멈추고 가도 KEPT | 입력 없음. 범위 밖으로 둔다 |

---

## 2. R2: 깜빡이 없이 회전

**목적**: 내비 안내가 없는 회전(골목, 주차장 출구, 경로 밖 회전)에서도 깜빡이를 켰는지 기록한다. R1은 TBT가 있어야만 동작하므로 R2가 빈 곳을 채운다.

**법 근거**: LAW-03 (도로교통법 제38조제1항). 30m 기준은 LAW-01·별표 2.

**MVP 단순화** (spec 6장 메모를 따른다)

- tbt를 쓰지 않는다. 진행 방향 변화로 회전을 잡는다.
- 저속(≤ 30km/h)에서 진행 방향이 빠르게 바뀌기 시작하면 "회전 시작", 시작 방향에서 60° 이상 바뀌면 "회전 완료".
- 회전 시작 전 5초 안에 그 방향 깜빡이가 한 프레임이라도 켜져 있으면 KEPT, 아니면 MISSED. (30m를 거리로 잴 수 없어 시간으로 대신한다. 20km/h에서 5초 ≈ 28m)
- 안내(GUIDE)는 하지 않는다. 회전을 미리 알 수 없기 때문이다(proposal 5.2 "없음(기록만)").
- 다른 룰이 이 회전을 맡고 있으면(R1, R8) R2는 쉰다. 같은 회전을 두 번 기록하지 않기 위해서다.
- steeringDeg는 쓰지 않는다. 좌우 부호가 미정이고 null일 수 있다(spec 5장). 필요하면 보조 조건으로 나중에 넣는다(Q2-4).

### 2.1 입력 신호

| 필드 | 지금 있나 | 쓰는 곳 |
| --- | --- | --- |
| headingDeg, t | 있음 | 회전 속도, 회전 완료, 방향 |
| speedKph | 있음 | 저속 조건, 주행 시간 |
| turnSignal | 있음 | 깜빡이 기억 |
| gear | 있음 | 주차·후진 제외 |
| 다른 룰이 회전을 맡고 있는지 | **엔진 내부 값** (새 입력 아님) | R1·R8와 겹침 방지 |

### 2.2 R1과 겹치는 회전 처리 (tasks 단계 12 요구)

| 안 | 방법 | 장점 | 단점 |
| --- | --- | --- | --- |
| A (추천) | Engine이 R1·R8가 IDLE이 아닌지(이 프레임 처리 전 또는 후)를 R2에 넘긴다. 맡고 있으면 R2는 쉬고, 끝난 뒤 주행 10초 동안 더 쉰다 | 한 회전에 한 기록. 전이표 한 행으로 설명 가능 | R1이 다가가다 다른 골목으로 꺾으면 그 회전은 둘 다 기록하지 않는다 |
| B | R2는 항상 돌고, Engine이 같은 프레임 근처에 R1 EVENT가 있으면 R2 EVENT를 버린다 | R2 상태 머신이 단순 | "근처"의 기준이 또 하나의 매직 넘버. Engine에 판정 논리가 섞임 |
| C | tbt가 null일 때(내비 안 쓸 때)만 R2를 돌린다 | 가장 단순 | 내비 안내 중 경로 밖 골목 회전을 놓친다 (이번 요청 범위와 맞지 않음) |

"끝난 뒤 10초 더 쉬기"가 필요한 이유: R1은 60°에서 EVENT를 내고 IDLE이 되는데, 실제 회전은 90°(유턴은 180°)까지 이어진다. 남은 회전을 R2가 새 회전으로 잡으면 중복 기록이 된다.

### 2.3 파라미터

| 이름 | 값 | 뜻 |
| --- | --- | --- |
| R2_MIN_SPEED_KPH | 5 | 이보다 느리면 회전 시작으로 보지 않는다. 정지 중 GPS 진행 방향이 튀는 것을 거른다 |
| R2_MAX_SPEED_KPH | 30 | 이보다 빠르면 회전 시작으로 보지 않는다. 굽은 도로를 거른다 (spec 6장) |
| R2_TURN_RATE_DPS | 8 | 직전 프레임 대비 진행 방향이 초당 이만큼 이상 바뀌면 회전 시작. 완만한 곡선을 거른다 |
| TURN_DONE_DEG | 60 | R1과 같음 |
| R2_TURN_WINDOW_MS | 10000 | 회전 시작 후 이 시간 안에 60°가 안 되면 곡선으로 보고 취소 |
| SIGNAL_LOOKBACK_MS | 5000 | 회전 시작 전 이 시간 안에 깜빡이가 켜졌으면 KEPT (spec 6장) |
| R2_AFTER_PARK_MS | 60000 | 주차(P)·후진(R) 뒤 주행 시간이 이만큼 쌓이기 전에는 판정하지 않는다. 주차장 안 회전을 거른다 |
| R2_HANDOFF_MS | 10000 | 다른 룰이 회전을 맡았다 끝난 뒤 이만큼 주행할 때까지 쉰다 |

- 회전 속도: turnRate = headingDiff(prevHeading, headingDeg) ÷ ((t − prevT) ÷ 1000). 프레임 간격이 달라도 같은 기준이 되도록 각도가 아니라 초당 각도로 본다.
- 방향: signedDiff(a, b) = ((b − a + 540) mod 360) − 180. 양수면 RIGHT(시계 방향), 음수면 LEFT.

### 2.4 상태

| 상태 | 뜻 | 기억하는 값 |
| --- | --- | --- |
| CRUISING | 회전을 기다리며 달리는 중 (처음 상태) | 없음 |
| TURNING | 회전이 시작됐고 60°가 되길 기다리는 중 | startHeading, startT, direction(LEFT/RIGHT), signalOk |
| COOLDOWN | 판정하지 않고 쉬는 중 (주차 직후, 다른 룰이 맡음) | remainMs |

상태와 상관없이 프레임마다 전이표 **전에** 갱신하는 값 (R1의 movingMs와 같은 방식)

- turnRate를 계산한 뒤 prevHeading = headingDeg, prevT = t. 첫 프레임은 turnRate = 0.
- turnSignal이 LEFT면 lastLeftT = t, RIGHT면 lastRightT = t.
- COOLDOWN이면 speedKph > 0일 때 remainMs에서 (t − 직전 t)를 뺀다.

### 2.5 전이표

| # | 현재 | 조건 | 다음 | 출력 |
| --- | --- | --- | --- | --- |
| 0 | 모든 상태 | gear가 P 또는 R | COOLDOWN (remainMs = 60000) | 없음 |
| 1 | 모든 상태 | otherRuleBusy | COOLDOWN (remainMs = max(지금 remainMs, 10000)) | 없음 (R1·R8가 맡음) |
| 2 | COOLDOWN | remainMs ≤ 0 | CRUISING | 없음 |
| 3 | CRUISING | 5 ≤ speedKph ≤ 30 이고 turnRate ≥ 8 | TURNING (startHeading = prevHeading, startT = t, direction = signedDiff 부호, signalOk = 그 방향 lastT ≥ t − 5000) | 없음 |
| 4 | TURNING | headingDiff(startHeading, headingDeg) ≥ 60 | CRUISING | EVENT (signalOk면 KEPT 아니면 MISSED, guided = false, turn = direction) |
| 5 | TURNING | t − startT > 10000 | CRUISING | 없음 (곡선으로 보고 취소) |

- otherRuleBusy: Engine이 계산한다. R1 또는 R8가 이 프레임을 처리하기 **전이나 후에** IDLE이 아니면 true. "전"을 넣는 이유는 R1이 EVENT를 내고 IDLE이 된 그 프레임에도 R2가 쉬어야 하기 때문이다.
- #3의 startHeading은 직전 프레임 값이다. 회전이 이미 시작된 프레임에서 잡히므로 그 전 방향이 기준이다.
- Engine의 호출 순서: R1 → R2 → R3 → R4 → R5 → R8. R2는 R8보다 먼저 불리므로 R8의 "후" 상태 대신 R8의 직전 프레임 상태를 쓴다(구현 때 확인).

### 2.6 시나리오

tbt는 모두 null(내비 없음).

**S17_골목우회전_깜빡이켬**

| t | speedKph | turnSignal | headingDeg |
| --- | --- | --- | --- |
| 0 | 30 | OFF | 0 |
| 2000 | 25 | RIGHT | 0 |
| 4000 | 20 | RIGHT | 0 |
| 6000 | 15 | RIGHT | 30 |
| 8000 | 15 | RIGHT | 80 |
| 10000 | 25 | OFF | 90 |

t=6000: 2초에 30° → 15°/s ≥ 8 → TURNING(start 0, RIGHT, 직전 5초 안 RIGHT 있음). t=8000: 80° → EVENT.
기대: EVENT t=8000 R2 KEPT guided=false

**S18_골목좌회전_깜빡이없음**: headingDeg 90, 90, 90, 60, 10, 0. turnSignal 모두 OFF.
기대: EVENT t=8000 R2 MISSED guided=false (turn = LEFT)

**S19_완만한_곡선도로**: speedKph 25, turnSignal OFF, t = 0~22000을 2000 간격, headingDeg 0에서 프레임마다 +8 (2초에 8° = 4°/s, 총 88°).
기대: 출력 없음 (turnRate가 한 번도 8을 넘지 않음)

**S20_내비안내중_좌회전_R1만**: S1 프레임 그대로.
기대: EVENT t=11000 R1 KEPT guided=false (R2 출력 없음)

**S21_주차장_출발후_회전**

| t | gear | speedKph | turnSignal | headingDeg |
| --- | --- | --- | --- | --- |
| 0 | P | 0 | OFF | 0 |
| 2000 | D | 8 | OFF | 0 |
| 4000 | D | 10 | OFF | 0 |
| 6000 | D | 10 | OFF | 30 |
| 8000 | D | 10 | OFF | 90 |
| 10000 | D | 15 | OFF | 90 |

기대: 출력 없음 (COOLDOWN 중)

### 2.7 오판 위험

| 위험 | 결과 | 줄이는 방법 |
| --- | --- | --- |
| 저속 급커브(산길 헤어핀) | 회전으로 보고 MISSED | turnRate·10초 창으로 일부 거름. 남는 위험은 받아들이고 리포트에 "골목 회전"으로 따로 보여줄지 검토 (Q2-2) |
| 정지·저속에서 GPS 진행 방향 튐 | 가짜 회전 | R2_MIN_SPEED_KPH. 실제 headingDeg 품질은 에뮬레이터에서 측정 불가 (spec 5장) |
| 주차장·드라이브스루 안 회전 | MISSED 남발 | 주차 후 60초 주행 동안 쉼. 주차 없이 들어간 주차장은 못 거름 (Q2-3) |
| 차로 변경, 진로 변경 | 각도가 작아 잡히지 않음 | 의도한 동작 (5장) |
| 유턴 (TBT 없음) | 왼쪽 회전으로 기록 | 의도한 동작. 유턴 깜빡이도 왼쪽이 맞다 |
| 루프형 나들목 램프 | 회전으로 기록 | 램프 속도가 30km/h를 넘는 경우가 많아 대부분 거름. R4가 끝난 직후라면 10초 쉬기로 거름 |
| R1이 다가가다 다른 골목으로 꺾음 | 아무도 기록 안 함 | 안 A의 한계. 받아들인다 |
| 프레임 간격이 매우 김 (예: 5초) | 회전 시작을 놓침 | 앱 프레임 주기 확인 필요 (spec 5장 TBT 갱신 주기와 같은 문제) |

---

## 3. 유턴 깜빡이

**목적**: 내비가 유턴을 안내할 때 왼쪽 깜빡이를 미리 켜게 한다.

**법 근거**: LAW-03 (제38조제1항에 유턴 명시), 시행령 별표 2 1호: 유턴은 **왼쪽** 방향지시기, 그 지점 30m 이상 앞.

**입력**: 새 입력 없음. 앱은 이미 `TURN_UTURN` → `UTURN`으로 보낸다. 지금 R1은 `LEFT`, `RIGHT`만 봐서 유턴을 무시한다.

### 3.1 R1 확장 vs 새 룰

| | A. R1 확장 (추천) | B. 새 룰 (예: R7) |
| --- | --- | --- |
| 바꾸는 코드 | R1Rule의 함수 4개(isTurn, isSameDirection, guideAction, guideText)에 UTURN 한 줄씩 | 상태 머신 하나를 거의 그대로 복사 |
| 상태·전이표 | 그대로. 조건의 "LEFT 또는 RIGHT"를 "LEFT, RIGHT, UTURN"으로 | 새 표 (R1과 같은 내용) |
| 법 근거 | 같은 제38조·별표 2 1호 (판정 지점도 30m로 같다) | 같음 |
| 리포트·습관 단위 | R1 하나에 섞임. EVENT.turn = UTURN으로 구분 가능 | 유턴만 따로 준수율·졸업 |
| 앱·서버 영향 | Event.turn에 UTURN 값 처리 | RuleId 추가, 리포트 항목 추가 |
| 회전 완료 | 60°에서 완료 처리. 유턴은 180°라 반드시 넘는다 | 같음 |

A를 추천한다. 판정 방식이 R1과 완전히 같고, 코드 중복이 없으며, PT에서 "R1은 회전·유턴 전 깜빡이"로 설명하면 된다. 유턴만 따로 습관 관리가 필요해지면 그때 EVENT.turn으로 나눠 집계하면 된다(Q3-1).

### 3.2 바뀌는 것 (A안)

- 전이표 #1: `tbt.turn이 LEFT, RIGHT, UTURN 중 하나이고 tbt.distanceM ≤ 100`
- "같은 방향": UTURN이면 turnSignal == LEFT
- GUIDE 문구: UTURN이면 `유턴 곧이에요, 왼쪽 깜빡이 켜세요`. action: `SIGNAL_LEFT`
- EVENT.turn: `UTURN`
- 파라미터, 상태, 나머지 행은 그대로.

### 3.3 시나리오

**S25_유턴_안내후_켬**: S2와 같고 tbt.turn = UTURN, headingDeg는 90, 90, 90, 90, 90, 90, 80, 30, 270.
기대: GUIDE t=4000 R1 → EVENT t=11000 R1 KEPT guided=true

**S26_유턴_반대쪽_깜빡이**: S25와 같고 t=6000부터 turnSignal = RIGHT.
기대: GUIDE t=4000 R1 → EVENT t=11000 R1 MISSED guided=true

### 3.4 오판 위험

- 유턴 구역에서 신호를 오래 기다림: R1의 주행 시간 타임아웃이 대기 시간을 세지 않으므로 문제없다(S7과 같음).
- 유턴 지점의 TBT 거리 기준점: 별표 2는 유턴을 "그 행위를 하려는 지점" 기준으로 쓴다(좌회전의 "교차로 가장자리"와 다름). 기준점은 spec 5장 미확인 항목과 같다.
- 내비가 유턴 대신 "좌회전 두 번"처럼 안내하는 경우: 4장 연속 회전으로 처리된다.

---

## 4. 연속 회전 (R1 보완)

**목적**: 좌회전 직후 우회전처럼 TBT가 연달아 올 때 R1이 꼬이지 않는지 확인하고, 꼬이는 곳을 고친다. 새 룰이 아니다.

**법 근거**: R1과 같음 (LAW-01, LAW-03).

**입력**: 새 입력 없음.

### 4.1 지금 R1으로 따라가 본 결과 (코드 읽고 추론, 실행 전)

| 경우 | 지금 동작 | 문제 |
| --- | --- | --- |
| 첫 회전 끝나고 다음 TBT가 30m보다 멀다 | #5로 EVENT → IDLE, 다음 프레임 #1 → APPROACHING. 정상 | 없음 |
| 첫 회전이 끝나는 프레임에 다음 TBT가 이미 30m 안 | IDLE → (다음 프레임) APPROACHING → (그 다음) #3. 프레임이 하나 부족하면 TBT가 바뀌어 #2로 취소 | **두 번째 회전 기록 없음** |
| 같은 방향 연속(좌·좌)이고 첫 회전의 30m 프레임을 놓침 | TBT가 같은 LEFT라 #2가 안 맞음. APPROACHING이 그대로 이어지고 guided가 첫 회전 값으로 남음 | **두 번째 회전 안내가 안 나감**, guided가 틀림 |
| 첫 회전이 60°까지 안 바뀜(비스듬한 교차로)인데 바로 다음 회전 | TURNING이 tbt를 안 보므로 주행 30초 타임아웃까지 묶임 | **두 번째 회전 놓침**, 첫 회전도 기록 없음 |

### 4.2 보완 제안

| 이름 | 바꾸는 것 | 해결하는 경우 |
| --- | --- | --- |
| F1 (추천) | IDLE에 #1 앞에 새 행: `tbt.turn이 LEFT/RIGHT(/UTURN)이고 tbt.distanceM ≤ 30` → TURNING (guided = false, signalOk = 그 프레임 깜빡이, startHeading = headingDeg …) | 두 번째 TBT가 처음부터 30m 안. 경로 안내가 회전 직전에 시작된 경우도 같이 해결 |
| F2 (추천) | APPROACHING에 `lastDistanceM` 추가. #2 다음에 새 행: `tbt.distanceM > lastDistanceM + TBT_JUMP_M` → APPROACHING (targetTurn = tbt.turn, guided = false) 출력 없음. TBT_JUMP_M = 20 | 같은 방향 연속에서 TBT 거리가 다시 커짐 = 새 회전 |
| F3 (보류) | TURNING에서 다음 TBT가 30m 안에 오면 첫 회전을 끝난 것으로 볼지 | 비스듬한 교차로. spec 5장 "회전 완료를 TBT 변화로 볼지"와 같은 질문이라 함께 정한다 (Q4-2) |

F1을 넣은 전이표 IDLE 부분:

| # | 현재 | 조건 | 다음 | 출력 |
| --- | --- | --- | --- | --- |
| 1a (신규) | IDLE | tbt.turn이 회전(LEFT/RIGHT/UTURN)이고 tbt.distanceM ≤ 30 | TURNING (targetTurn = tbt.turn, guided = false, signalOk = 같은 방향, startHeading = headingDeg, movingMs = 0, lastT = t) | 없음 |
| 1 | IDLE | tbt.turn이 회전이고 tbt.distanceM ≤ 100 | APPROACHING | 없음 |

F2를 넣은 APPROACHING 부분:

| # | 현재 | 조건 | 다음 | 출력 |
| --- | --- | --- | --- | --- |
| 2 | APPROACHING | tbt가 null이거나 tbt.turn ≠ targetTurn | IDLE | 없음 |
| 2a (신규) | APPROACHING | tbt.distanceM > lastDistanceM + 20 | APPROACHING (guided = false, lastDistanceM = tbt.distanceM) | 없음 (앞 회전은 놓친 것으로 보고 기록하지 않음) |
| 3, 4 | (그대로, 단 상태를 유지하는 모든 경우 lastDistanceM = tbt.distanceM) | | | |

### 4.3 시나리오

**S27_연속회전_좌후우_여유** (지금 코드로도 통과해야 함)

| t | tbt | speedKph | turnSignal | headingDeg |
| --- | --- | --- | --- | --- |
| 0 | LEFT 120 | 40 | LEFT | 90 |
| 2000 | LEFT 90 | 38 | LEFT | 90 |
| 4000 | LEFT 60 | 32 | LEFT | 90 |
| 7000 | LEFT 28 | 22 | LEFT | 90 |
| 9000 | LEFT 10 | 15 | LEFT | 80 |
| 11000 | RIGHT 90 | 15 | LEFT | 30 |
| 13000 | RIGHT 70 | 25 | OFF | 0 |
| 15000 | RIGHT 50 | 25 | OFF | 0 |
| 16000 | RIGHT 40 | 22 | RIGHT | 0 |
| 17000 | RIGHT 28 | 20 | RIGHT | 0 |
| 19000 | RIGHT 10 | 15 | RIGHT | 20 |
| 21000 | 다음 안내 | 15 | RIGHT | 70 |

기대: EVENT t=11000 R1 KEPT guided=false → GUIDE t=15000 R1 → EVENT t=21000 R1 KEPT guided=true

**S28_연속회전_좌후우_근접** (F1 필요)

t=0~9000은 S24와 같고, 그 뒤:

| t | tbt | speedKph | turnSignal | headingDeg |
| --- | --- | --- | --- | --- |
| 11000 | RIGHT 25 | 15 | RIGHT | 30 |
| 13000 | RIGHT 8 | 12 | RIGHT | 0 |
| 15000 | 다음 안내 | 15 | RIGHT | 70 |
| 17000 | 다음 안내 (480m) | 25 | OFF | 90 |

- 지금 코드: EVENT t=11000 R1 KEPT만 (두 번째 회전은 t=15000에 #2로 취소)
- F1 적용 후 기대: EVENT t=11000 R1 KEPT guided=false → EVENT t=15000 R1 KEPT guided=false

**S29_같은방향_연속_30m놓침** (F2 필요)

| t | tbt | speedKph | turnSignal | headingDeg |
| --- | --- | --- | --- | --- |
| 0 | LEFT 120 | 40 | OFF | 90 |
| 2000 | LEFT 90 | 38 | OFF | 90 |
| 4000 | LEFT 60 | 32 | OFF | 90 |
| 6000 | LEFT 35 | 26 | OFF | 90 |
| 8000 | LEFT 90 | 25 | OFF | 30 |
| 10000 | LEFT 60 | 30 | OFF | 30 |
| 12000 | LEFT 28 | 22 | OFF | 30 |
| 14000 | 다음 안내 | 15 | OFF | 330 |

- 지금 코드: GUIDE t=4000 → EVENT t=14000 R1 MISSED guided=true (두 번째 회전 안내 없음)
- F2 적용 후 기대: GUIDE t=4000 → GUIDE t=10000 → EVENT t=14000 R1 MISSED guided=true

**(번호 없음)_비스듬한_좌회전후_우회전** (한계 기록용. Q4-2 답에 따라 기대값을 정한다)

첫 좌회전이 90°→45°(45°만 바뀜), 이어서 RIGHT TBT 60m → 우회전. 지금 코드는 두 회전 모두 기록 없음.

### 4.4 오판 위험

- F1: 다가오는 과정을 못 본 회전도 판정하므로 안내 기회 없이 MISSED가 나올 수 있다. 법 기준(30m 전 점등)에는 맞는다.
- F2: TBT 거리가 GPS 오차로 20m 넘게 튀면 guided가 초기화되어 안내가 한 번 더 나갈 수 있다. 결과(KEPT/MISSED)는 바뀌지 않는다.
- 두 회전 사이 거리가 30m보다 짧으면 법적으로도 두 번째 회전의 30m 점등이 불가능하다. 이 경우 첫 회전 깜빡이를 반대로 바꾸는 시점이 애매하다(Q4-3).

---

## 5. 고속도로 차로 변경 깜빡이

**목적**: 고속도로에서 차로를 바꿀 때 100m 전부터 깜빡이를 켜는 습관.

**법 근거**: LAW-02, LAW-04 (제38조제1항 "같은 방향으로 진행하면서 진로를 바꾸려고 하는 경우", 별표 2 1·2호 고속도로 100m).

### 5.1 판정할 수 있는가

| 필요한 정보 | 있나 | 비고 |
| --- | --- | --- |
| 차로를 바꿨다는 사실 | **없음** | 차로 변경은 진행 방향이 2~5°만 바뀐다. GPS 진행 방향 오차보다 작다. 핸들 각도도 몇 도 수준이고 부호가 미정이다 |
| 지금 몇 차로인지 | **없음** | TBTInfo에 차로 정보가 없다. ADAS(차선 인식)는 SDK에 없다 (proposal F12 로드맵) |
| 고속도로인지 | 간접적으로만 | `onCurrentRoadSpeedLimit`이 90·100·110이면 고속도로·자동차전용도로일 가능성. 엔진 입력에는 없음 |
| 차로를 바꿔야 하는 곳 | 일부 | `TURN_KEEP_L/R`(갈림길), `TURN_DEVIDE_MERGE`, 나들목(R4가 이미 담당) |

### 5.2 제안: 판정하지 않는다

- EVENT(KEPT/MISSED)를 내지 않는다. 차로 변경을 감지할 입력이 없어 MISSED는 근거가 없고, KEPT만 내도 "깜빡이를 켰다 = 차로를 바꿨다"가 아니어서 기록 의미가 없다.
- 이 법규는 **법규 Q&A(LAW-02·04)와 주간 코칭 문장**에서 다룬다. 이미 법규표에 있다.
- R4(나들목)가 고속도로 차로 변경의 가장 흔한 경우를 안내 중심으로 이미 다룬다.

### 5.3 대안 (채택하면 spec에 추가)

| 안 | 내용 | 필요한 것 | 판단 |
| --- | --- | --- | --- |
| B | 차로 변경 단속 카메라 주의 지점(`LANE_CHANGE_CAMERA_ALERT`, `CUT_IN_VIOLATION_ENFORCEMENT`) 300m 앞에서 안내만 ("차로 변경 단속 구간이에요, 바꿀 땐 깜빡이 미리 켜세요") | SignalFrame에 `caution` 입력 추가, 앱이 `onCautionPointInfo` 수집 | "단속되기 전에 미리 알려준다"는 서비스 문구와 잘 맞음. 다만 단속 카메라 회피 앱처럼 보일 수 있어 문구 검토 필요 (Q5-1) |
| C | R4를 `TURN_KEEP_L/R`까지 넓혀 안내만 | 앱 매핑 (KEEP → 새 TurnType) | spec 6장에서 "잔소리"로 이미 뺀 안. 다시 넣지 않는 것을 추천 |
| D | 핸들·진행 방향으로 차로 변경 감지 | 실주행 데이터 (proposal AI-6) | 공모전 기간에 데이터가 없어 불가 |

`onCurrentRoadSpeedLimit`을 입력으로 받으면 R1·R4의 고속도로 100m 기준을 도로 종류에 따라 고를 수 있다. 이 안은 이번 범위 밖이며 질문으로만 남긴다(Q5-2).

---

## 6. R8: 회전교차로 진입·진출 깜빡이 (신규)

**목적**: 회전교차로에 들어갈 때 왼쪽, 나갈 때 오른쪽 깜빡이를 켜는 습관. 2022년 법 개정으로 생긴 의무라 모르는 운전자가 많다.

**법 근거** (0.1 원문 확인)

| 동작 | 깜빡이 | 시기 | 의무/권장 | 근거 |
| --- | --- | --- | --- | --- |
| 진입 | 왼쪽 | 진입 지점 30m 이상 앞 | **의무** | 제38조제1항, 시행령 별표 2 7호 |
| 진출 | 오른쪽 | 진출하려는 때 (거리 기준 없음) | **의무** | 제38조제1항, 시행령 별표 2 8호 |
| 진입 때 서행·일시정지, 회전 중인 차에 양보 | (깜빡이 아님) | | 의무 | 제25조의2제2항 |

- 법령 원문에는 "권장"으로 구분된 깜빡이가 없다. 진입·진출 모두 의무다.
- 다만 **첫 출구(02·03시)로 바로 나가는 경우**에도 진입 때 왼쪽을 켜야 하는지는 법문상 예외가 없지만, 실제로는 진입 직후 바로 오른쪽으로 바꿔야 해서 현실적인 판정 기준이 애매하다(Q6-2).
- 처분 금액: 제38조 위반이라 LAW-01과 같은 승용차 범칙금 3만 원, 과태료 4만 원으로 보인다. 별표 8 원문 **확인 필요**.

**TBT로 알 수 있는가**: 있다 (0.2). `TURN_ENTER_ROUNDABOUT_*`(진입, 출구 시계 방향 포함)와 `TURN_EXIT_ROUNDABOUT`(진출). 지금은 앱이 `OTHER`로 바꿔서 엔진에 오지 않는다.

**MVP 단순화**

- 진입: R1과 같은 방식. 30m 지점에서 왼쪽 깜빡이면 KEPT, 아니면 MISSED. 판정 지점까지 안내.
- 진출: 진출 TBT 10m 안에서 오른쪽 깜빡이면 KEPT. **꺼져 있어도 MISSED를 내지 않는다.** 진출 TBT가 실제로 오는지, 거리 기준점이 어디인지 에뮬레이터에서 확인할 수 없기 때문이다(R4와 같은 이유).
- 진출 TBT가 오지 않으면 진출은 아무것도 하지 않는다. 주행 60초 뒤 IDLE로 돌아간다.
- 회전 중 진행 방향은 보지 않는다(회전교차로는 방향이 계속 바뀐다). 그 사이 R2는 쉰다(2.2 안 A).

### 6.1 입력 신호

| 필드 | 지금 있나 | 추가할 것 |
| --- | --- | --- |
| tbt.turn | 있음 | `ROUNDABOUT_IN`, `ROUNDABOUT_OUT` 값 추가 |
| tbt.exitClock | 없음 | `Int? = null`. ENTER_ROUNDABOUT의 숫자(2, 3, 4, 6, 8, 9, 10, 12). Q6-2에 쓰일 때만 추가 (Q6-3) |
| turnSignal, speedKph | 있음 | |

앱 변환표 추가 (앱 담당에게 요청할 내용, 이 문서에서는 고치지 않음)

| SDK 값 | 엔진 값 |
| --- | --- |
| `TURN_ENTER_ROUNDABOUT_02` ~ `_12` | `ROUNDABOUT_IN` (exitClock = 숫자) |
| `TURN_EXIT_ROUNDABOUT` | `ROUNDABOUT_OUT` |

### 6.2 파라미터

| 이름 | 값 | 뜻 |
| --- | --- | --- |
| APPROACH_START_M | 100 | R1과 같음 |
| CHECK_AT_M | 30 | 진입 판정 지점. 별표 2 7호의 30m |
| RB_EXIT_GUIDE_M | 50 | 진출 TBT가 이 거리 안이면 진출 안내 |
| RB_EXIT_CHECK_M | 10 | 진출 판정 지점. 법은 "진출하려는 때"라 거리가 없어 진출 직전으로 둔다 |
| RB_TIMEOUT_MS | 60000 | 진입 후 주행 시간이 이만큼 쌓여도 진출 TBT가 없으면 끝 |

진입 안내 거리는 R1의 `guideDistance(speedKph)`.

### 6.3 상태

| 상태 | 뜻 | 기억하는 값 |
| --- | --- | --- |
| IDLE | 대기 | 없음 |
| ENTERING | 회전교차로 진입 지점에 다가가는 중 | guided |
| CIRCLING | 진입 판정을 마치고 회전교차로 안을 도는 중 | exitGuided, movingMs, lastT |

CIRCLING에서는 전이표 전에 movingMs, lastT를 먼저 갱신한다(R1 TURNING과 같다).

### 6.4 전이표

| # | 현재 | 조건 | 다음 | 출력 |
| --- | --- | --- | --- | --- |
| 0 | 모든 상태 | gear가 P 또는 R | IDLE | 없음 |
| 1 | IDLE | tbt.turn == ROUNDABOUT_IN 이고 tbt.distanceM ≤ 100 | ENTERING (guided = false) | 없음 |
| 2 | ENTERING | tbt가 null이거나 tbt.turn ≠ ROUNDABOUT_IN | IDLE | 없음 (재탐색) |
| 3 | ENTERING | tbt.distanceM ≤ 30 이고 turnSignal == LEFT | CIRCLING (exitGuided = false, movingMs = 0, lastT = t) | EVENT (KEPT, guided 그대로, turn = LEFT) |
| 4 | ENTERING | tbt.distanceM ≤ 30 | CIRCLING (같음) | EVENT (MISSED, guided 그대로, turn = LEFT) |
| 5 | ENTERING | 30 < tbt.distanceM ≤ guideDistance(speedKph) 이고 turnSignal ≠ LEFT 이고 guided == false | ENTERING (guided = true) | GUIDE (진입) |
| 6 | CIRCLING | tbt.turn == ROUNDABOUT_OUT 이고 tbt.distanceM ≤ 10 이고 turnSignal == RIGHT | IDLE | EVENT (KEPT, guided = exitGuided, turn = RIGHT) |
| 7 | CIRCLING | tbt.turn == ROUNDABOUT_OUT 이고 tbt.distanceM ≤ 10 | IDLE | 없음 (MVP 단순화) |
| 8 | CIRCLING | tbt.turn == ROUNDABOUT_OUT 이고 tbt.distanceM ≤ 50 이고 turnSignal ≠ RIGHT 이고 exitGuided == false | CIRCLING (exitGuided = true) | GUIDE (진출) |
| 9 | CIRCLING | movingMs > 60000 | IDLE | 없음 |

- 진입은 R1과 달리 회전 완료를 기다리지 않고 30m에서 바로 EVENT를 낸다(R4와 같은 방식). 회전교차로는 진입 후 진행 방향 변화가 출구마다 달라 완료 기준을 잡기 어렵다.
- GUIDE 문구: 진입 `회전교차로예요, 들어갈 때 왼쪽 깜빡이 켜세요` (action `SIGNAL_LEFT`), 진출 `곧 나가요, 오른쪽 깜빡이 켜세요` (action `SIGNAL_RIGHT`).
- Q6-2에서 "첫 출구는 진입 판정 생략"으로 정하면 #3·#4·#5 앞에 `exitClock이 2 또는 3이면 → CIRCLING, 출력 없음` 행을 넣는다.

### 6.5 시나리오

**S30_회전교차로_진입진출_켬**

| t | tbt | speedKph | turnSignal | headingDeg |
| --- | --- | --- | --- | --- |
| 0 | ROUNDABOUT_IN 120 | 35 | OFF | 0 |
| 2000 | ROUNDABOUT_IN 90 | 32 | OFF | 0 |
| 4000 | ROUNDABOUT_IN 60 | 28 | OFF | 0 |
| 6000 | ROUNDABOUT_IN 40 | 22 | LEFT | 0 |
| 7000 | ROUNDABOUT_IN 25 | 18 | LEFT | 0 |
| 9000 | ROUNDABOUT_OUT 40 | 20 | LEFT | 300 |
| 11000 | ROUNDABOUT_OUT 20 | 20 | RIGHT | 240 |
| 12000 | ROUNDABOUT_OUT 8 | 20 | RIGHT | 200 |
| 14000 | 다음 안내 | 30 | OFF | 180 |

t=4000: 28km/h 안내 거리 약 61m → 진입 GUIDE. t=9000: 진출 50m 안, 오른쪽 아님 → 진출 GUIDE.
기대: GUIDE t=4000 R8 → EVENT t=7000 R8 KEPT guided=true → GUIDE t=9000 R8 → EVENT t=12000 R8 KEPT guided=true

**S31_회전교차로_진입_안켬**: S30과 같고 t=6000·7000의 turnSignal = OFF, t=9000부터 RIGHT.
기대: GUIDE t=4000 R8 → EVENT t=7000 R8 MISSED guided=true → EVENT t=12000 R8 KEPT guided=false

**S32_회전교차로_진출TBT없음**: S30의 t=0~7000(turnSignal은 처음부터 LEFT) 뒤로 tbt가 `STRAIGHT 500`에서 줄어드는 프레임만 70초 이어짐.
기대: EVENT t=7000 R8 KEPT guided=false (진출 출력 없음, 주행 60초 뒤 IDLE)

R2는 위 세 시나리오 모두에서 R8가 맡고 있으므로 출력이 없어야 한다.

### 6.6 오판 위험

| 위험 | 결과 | 줄이는 방법 |
| --- | --- | --- |
| ENTER_ROUNDABOUT의 숫자 뜻이 추정과 다름 | Q6-2 처리가 틀림 | exitClock은 Q6-2를 채택할 때만 쓰고, 실제 내비 화면과 대조 (확인 필요) |
| 진출 TBT가 안 옴 | 진출 안내·기록 없음 | MISSED를 안 내므로 잘못된 기록은 없음. S30으로 고정 |
| 진입 TBT 거리 기준점이 양보선이 아니라 원 중심 | 진입 판정이 늦음 (안쪽에서 판정) | spec 5장 기준점 미확인과 같은 문제 |
| 소형 회전교차로를 내비가 일반 교차로(LEFT/RIGHT)로 안내 | R1이 판정 | 받아들인다 |
| 첫 출구 진출 운전자에게 왼쪽 깜빡이 MISSED | 운전자가 납득 못함 | Q6-2 |
| 진출 때 오른쪽을 켜려고 진입 깜빡이를 일찍 끔 | 영향 없음 (진입은 30m에서 이미 판정) | |

### 6.7 `data/law_table.json` 새 항목 초안

LAW-16은 spec 6장에서 터널 등화 조항용으로 예약되어 있어 LAW-17부터 쓴다.

```json
[
  {
    "id": "LAW-17",
    "topic": "회전교차로 진입 방향지시등",
    "situation": "회전교차로에 들어갈 때",
    "article": "도로교통법 제38조제1항, 시행령 별표 2",
    "summary": "회전교차로에 들어가기 30m 전부터 왼쪽 방향지시등을 켜야 해요. 2022년부터 법에 명시됐어요.",
    "penalty": "승용차 범칙금 3만 원 (현장 적발), 과태료 4만 원 (공익신고)",
    "sourceUrl": "https://www.law.go.kr/법령/도로교통법시행령",
    "disputed": false,
    "note": "별표 2 7호. 처분 금액은 제38조 위반 기준이며 별표 8 회전교차로 행 원문 확인 필요. 첫 출구로 바로 나갈 때의 판정 기준은 spec 미정."
  },
  {
    "id": "LAW-18",
    "topic": "회전교차로 진출 방향지시등",
    "situation": "회전교차로에서 나갈 때",
    "article": "도로교통법 제38조제1항, 시행령 별표 2",
    "summary": "회전교차로에서 나갈 때는 오른쪽 방향지시등을 켜야 해요. 나가려는 때 켜면 돼요.",
    "penalty": "승용차 범칙금 3만 원 (현장 적발), 과태료 4만 원 (공익신고)",
    "sourceUrl": "https://www.law.go.kr/법령/도로교통법시행령",
    "disputed": false,
    "note": "별표 2 8호. 시기는 거리 기준 없이 '그 행위를 하려는 때'. 처분 금액은 별표 8 원문 확인 필요."
  },
  {
    "id": "LAW-19",
    "topic": "회전교차로 통행 방법",
    "situation": "회전교차로에 들어가거나 안에서 돌 때 누가 먼저인지 궁금할 때",
    "article": "도로교통법 제25조의2",
    "summary": "회전교차로는 반시계 방향으로 돌아요. 들어갈 때는 서행하거나 멈추고, 이미 돌고 있는 차에 양보해야 해요. 깜빡이를 켠 앞차를 방해하면 안 돼요.",
    "penalty": "확인 필요",
    "sourceUrl": "https://www.law.go.kr/법령/도로교통법",
    "disputed": false,
    "note": "2022. 1. 11. 신설. 위반 처분 금액은 별표 8에서 확인 필요. 앱은 양보 여부를 판정하지 않음."
  }
]
```

기존 항목 수정 제안

- LAW-03 `summary`에 "유턴할 때는 왼쪽 방향지시등을 30m 전부터 켜야 해요." 추가 (별표 2 1호).
- LAW-04 `note`는 그대로("앱은 차로 변경을 판정하지 않음"). 5장 제안과 맞다.

---

## 7. 구현 순서 추천

| 순서 | 작업 | 이유 | 다른 담당 필요 |
| --- | --- | --- | --- |
| 1 | 유턴 (R1 확장) + S25·S26 | 가장 작다. 입력이 이미 들어온다 | 앱·서버: Event.turn에 UTURN 처리 (확인만) |
| 2 | 연속 회전 F1·F2 + S27~S29 | R1이 핵심 룰이라 다른 룰보다 먼저 단단히 한다. R2가 R1 상태를 보므로 R2 전에 고정 | 없음 |
| 3 | R2 + S17~S21 | tasks 단계 12 일정(11/17~19). 새 입력 없음 | 없음 |
| 4 | R3 + S13~S16 | tasks 단계 13 일정(11/20~23). 엔진은 바로 만들 수 있지만 실제 동작은 light 연동에 달림 | 서버 담당(신호 API), 앱 담당(light 채우기) |
| 5 | R8 회전교차로 + S30~S32, 법규표 LAW-17~19 | 모델 변경(TurnType, RuleId)과 앱 매핑이 같이 가야 함 | 앱 담당(SdkMapping), 서버 담당(R8 저장), PM(proposal 5.2 표) |
| - | 고속도로 차로 변경 | 판정하지 않음. Q&A·코칭 문장만 | AI 담당(코칭 프롬프트) |

룰 하나가 끝날 때마다 `./gradlew test` 전체 통과 확인 후 커밋한다.

---

## 8. spec 미정 질문 (AI 담당 확인 필요)

**공통**

- Q0-1. (정리됨) 회전교차로 룰 번호는 R8로 정했다. R6 = 와이퍼, R7 = 김서림(spec v0.3·v0.3.1). proposal.md 5.2 표(R4 = 회전교차로, R5 = 어린이보호구역)를 spec 번호에 맞게 고칠지는 PM·AI 담당이 정한다.
- Q0-2. (정리됨) 시나리오 번호는 main의 S13~S24 뒤로 이어서 S25~S32를 쓴다. 1장(R3)의 S13~S16 이름은 제안 당시 기록이며 main 시나리오와 다르다.

**R3 (1장)**

- Q1-1. `light`는 "다음 TBT 교차로의 차량 신호(직진 신호)"인가? 서버가 어느 교차로 신호를 주는지 서버 담당과 정해야 한다.
- Q1-2. 정지 위치를 볼 것인가? 지금 제안은 30m 안 어디서든 한 번 멈추면 KEPT.
- Q1-3. "멈춤" = speedKph == 0 (법 정의)로 둘 것인가, 센서 오차를 감안해 예: ≤ 1km/h로 둘 것인가?
- Q1-4. 황색(YELLOW)은 적색처럼 볼 것인가? 지금 제안은 적색만. remainSec가 아주 작을 때(곧 녹색) 판정을 건너뛸 것인가?
- Q1-5. 같은 프레임에 R1 GUIDE("우회전 곧이에요, 깜빡이 켜세요")와 R3 GUIDE가 겹치면 둘 다 낼 것인가, 하나로 합칠 것인가? (TTS가 연달아 두 번 나감)
- Q1-6. 멈췄다가 녹색으로 바뀐 뒤 우회전한 경우 판정 없음(DONE)으로 두는 것이 맞는가? KEPT로 칠 수도 있다.

**R2 (2장)**

- Q2-1. R1과 겹침 처리 안 A(다른 룰이 맡으면 쉼)로 정해도 되는가?
- Q2-2. 파라미터 초기값(8°/s, 5초, 10초 창, 주차 후 60초)을 이대로 시작하고 실측 후 조정하는가?
- Q2-3. 엔진이 처음 켜졌을 때 주차 직후로 볼 것인가(COOLDOWN에서 시작)? 지금 제안은 CRUISING에서 시작.
- Q2-4. steeringDeg를 보조 조건(예: |steeringDeg| ≥ 90)으로 쓸 것인가? 부호는 필요 없지만 null일 때의 동작을 정해야 한다.
- Q2-5. 반대 방향 깜빡이를 켜고 회전하면 MISSED로 보는가? (지금 제안: 같은 방향만 KEPT. R1과 같음)

**유턴 (3장)**

- Q3-1. R1 확장(A안)으로 정해도 되는가? 리포트·졸업에서 유턴을 따로 볼 필요가 있는가?

**연속 회전 (4장)**

- Q4-1. F1(30m 안에서 처음 본 회전도 판정), F2(TBT 거리 점프 = 새 회전)를 넣을 것인가?
- Q4-2. 회전 완료를 TBT 변화로도 볼 것인가? (spec 5장 기존 질문. 비스듬한 교차로 시나리오 기대값이 이 답에 달렸다)
- Q4-3. 두 회전 사이가 30m보다 짧아 두 번째 회전의 30m 점등이 불가능한 경우를 판정에서 뺄 것인가?

**차로 변경 (5장)**

- Q5-1. "판정하지 않음"으로 정해도 되는가? 대안 B(단속 카메라 주의 지점 안내)를 넣을 것인가?
- Q5-2. `onCurrentRoadSpeedLimit`을 입력으로 받아 R1·R4의 30m/100m 기준을 고르게 할 것인가? (이번 범위 밖)

**회전교차로 (6장)**

- Q6-1. 진입은 KEPT/MISSED, 진출은 KEPT만으로 정해도 되는가?
- Q6-2. 첫 출구(02·03시)로 바로 나가는 경우 진입 왼쪽 깜빡이를 판정할 것인가? 법문에는 예외가 없다.
- Q6-3. `Tbt.exitClock`을 추가할 것인가? Q6-2에서 생략하기로 할 때만 필요하다.
- Q6-4. 진출 판정 지점 10m, 진출 안내 50m를 이대로 둘 것인가? 진출 TBT가 실제로 오는지 확인 전이다.
- Q6-5. 진입 때 서행·양보(제25조의2제2항)는 판정하지 않고 Q&A(LAW-19)로만 다루는가?

---

## 부록 A. R2: 이 제안과 AI 담당 구현(spec v0.3 11장)의 차이

이 브랜치는 AI 담당 구현을 따른다. 이 제안의 R2 코드는 병합 때 버렸다.

| 항목 | 이 제안 (2장) | AI 담당 spec v0.3 11장 (채택) |
| --- | --- | --- |
| 구조 | 상태 머신 (CRUISING, TURNING, COOLDOWN) | 상태 없이 최근 15초 프레임 기록(history) |
| 회전 시작 | 직전 프레임 대비 초당 8° 이상 | 10초 안 어떤 프레임과 비교해 60° 이상이면 그 프레임이 시작 |
| 속도 조건 | 회전 시작 프레임이 5~30km/h | 회전 구간 모든 프레임이 30km/h 이하 (최소 속도 없음) |
| 깜빡이 | 회전 시작 전 5초 안 | 회전 시작 5초 전부터 회전 완료까지 |
| 주차 제외 | P·R 뒤 주행 60초 | R 뒤 30초 (시각 기준), P는 기록만 비움 |
| R1과 겹침 | R1이 처리 전·후 IDLE 아니면 쉼 + 끝난 뒤 주행 10초 | R1 IDLE 아님, LEFT·RIGHT 100m 안, RAMP 300m 안, R1·R4 EVENT 뒤 10초 |
| 정지 중 GPS 튐 | 5km/h 미만은 회전 시작 아님 | 따로 거르지 않음 (AI 담당 확인 필요) |

## 부록 B. R8을 구현하려면 앱에서 같이 바꿔야 하는 곳

엔진에 `RuleId.R8`, `TurnType.ROUNDABOUT_IN`·`ROUNDABOUT_OUT`을 넣으면 앱의 빠짐없는 `when`이 컴파일되지 않는다(2026-11-11 main 기준). 이 브랜치에서는 `app/`을 고치지 않으므로 R8 구현을 보류했다. 앱 담당에게 제안하는 변경:

| 파일 | 위치 | 추가할 분기 (제안) |
| --- | --- | --- |
| `GuideKind.kt` | `RuleId.guideKind()` | `RuleId.R8 -> GuideKind.TURN_SIGNAL` |
| `CoachState.kt` | `TurnType.label()` | `ROUNDABOUT_IN -> "회전교차로 진입"`, `ROUNDABOUT_OUT -> "회전교차로 진출"` |
| `CoachState.kt` | EVENT 제목 `when (rule)` | `RuleId.R8 -> "회전교차로 깜빡이"` |
| `DriveReport.kt` | `RuleId.fineWon()` | `RuleId.R8 ->` 30000 (R1과 같음, 별표 8 확인 필요) |
| `DriveReport.kt` | `RuleId.lawId()` | `RuleId.R8 -> "LAW-17"` (진입. 진출은 LAW-18) |
| `SdkMapping.kt` | `toEngineTurnType()` | `TURN_ENTER_ROUNDABOUT_* -> ROUNDABOUT_IN`, `TURN_EXIT_ROUNDABOUT -> ROUNDABOUT_OUT` |
| `CoachState.kt` | `SignalFrame.target()` | 회전교차로 카드가 필요하면 `ROUNDABOUT_IN`(왼쪽, 30m), `ROUNDABOUT_OUT`(오른쪽, 10m) 대상 추가. 없으면 지금처럼 `else -> null` |

