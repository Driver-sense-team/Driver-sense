<div align="center">

# Driver Sense 판정 엔진

</div>

차량 신호를 한 프레임씩 받아 안내 요청(GUIDE)과 판정 이벤트(EVENT)를 돌려주는 순수 Kotlin 라이브러리입니다. Android에 의존하지 않아서 에뮬레이터 없이 테스트할 수 있습니다.

**현재 범위 (spec v0.3.2)**: R1(회전·유턴 전 깜빡이), R2(깜빡이 없이 회전, 기록만), R3(적색 신호 우회전 일시정지), R4(나들목 진출입 전 깜빡이), R5(터널 진입 전 전조등), R6(비 올 때 와이퍼), R7(앞유리 김서림)이 동작합니다. 회전교차로(R8)는 제안 단계입니다([docs/proposals/spec-new-rules.md](../docs/proposals/spec-new-rules.md)). 엔진 입력에 모드는 아직 없고, 안내 켬/끔은 앱이 GUIDE 출력을 거르는 방식으로 처리합니다. 입출력 형식의 기준은 [docs/spec.md](../docs/spec.md)입니다.

## 앱에서 쓰기

앱 모듈의 의존성에 추가합니다. 프로젝트 설정은 [app/README.md](../app/README.md)를 참고합니다.

```kotlin
dependencies {
    implementation(project(":engine"))
}
```

필요 환경: JDK 17 (`jvmToolchain(17)`). `kotlinx-serialization-json`은 엔진을 통해 함께 들어옵니다.

## 사용법

프레임을 넣고, 나온 출력을 `when`으로 나누면 됩니다.

```kotlin
import kkam.engine.Engine
import kkam.engine.model.Event
import kkam.engine.model.Gear
import kkam.engine.model.Guide
import kkam.engine.model.SignalFrame
import kkam.engine.model.Tbt
import kkam.engine.model.TurnSignal
import kkam.engine.model.TurnType

val engine = Engine()

// 차량 신호가 들어올 때마다 호출합니다.
val frame = SignalFrame(
    t = System.currentTimeMillis(),
    speedKph = 28.0,
    gear = Gear.D,
    steeringDeg = null,        // SDK에서 안 나오면 null
    turnSignal = TurnSignal.OFF,
    brake = false,
    lat = 37.5501,
    lon = 127.0732,
    headingDeg = 90.0,
    tbt = Tbt(TurnType.LEFT, 48), // 경로 안내 중이 아니면 null
    light = null,              // 신호 정보가 없으면 null
)

for (output in engine.onFrame(frame)) {
    when (output) {
        is Guide -> speak(output.text)    // TTS + 코치 카드
        is Event -> saveAndShow(output)   // 리포트 + 서버 저장
    }
}
```

### 주의할 점

- 엔진 객체 하나를 주행 내내 유지합니다. 프레임마다 새로 만들면 상태가 사라져 아무것도 판정하지 못합니다.
- `onFrame`은 항상 같은 스레드에서 호출합니다.
- 한 프레임의 출력은 0개, 1개 또는 여러 개일 수 있습니다.
- 프레임은 1초에 1번 이상 넣습니다. 시나리오의 프레임 간격과 비슷한 값이며, 실제 주기는 에뮬레이터 확인 후 확정합니다.
- 숫자 필드의 값이 잠시 없으면 임의로 0을 넣지 않습니다. `headingDeg`는 마지막 유효값을 유지합니다.
- `Event.id`는 엔진 객체마다 `evt-1`부터 다시 시작합니다. 서버 저장 키로 쓸 수 없습니다.

## 시나리오로 데모 재생하기

시나리오는 녹화된 주행 신호입니다. 앱의 PT 데모(F9)는 실제 신호 대신 이 파일을 재생합니다.

앱의 `ScenarioPlayer.kt`가 루트 `scenarios/`를 assets로 읽어 프레임을 시간 순서대로 넣습니다. 흐름은 아래와 같습니다.

```kotlin
import kkam.engine.Engine
import kkam.engine.model.Scenario
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json

suspend fun replay(context: Context, fileName: String) {
    val text = context.assets.open(fileName).bufferedReader().use { it.readText() }
    val scenario = Json.decodeFromString(Scenario.serializer(), text)

    val engine = Engine()
    var previousT = 0L
    for (frame in scenario.frames) {
        delay(frame.t - previousT) // 녹화된 간격만큼 기다립니다.
        previousT = frame.t

        showVehicleSignals(frame)  // 데모 화면 왼쪽: 차량 신호 값
        handleOutputs(engine.onFrame(frame)) // 위의 when 처리와 같습니다.
    }
}
```

`delay`는 앱의 `kotlinx-coroutines-android` 의존성을 사용합니다. 엔진에는 포함되어 있지 않습니다. `expect` 필드는 테스트용이라 재생에는 쓰지 않습니다.

| 시나리오 | 내용 | 나와야 하는 출력 |
| --- | --- | --- |
| S1 | 좌회전, 처음부터 깜빡이 켬 | KEPT |
| S2 | 좌회전, 안내 듣고 켬 | GUIDE, KEPT |
| S3 | 좌회전, 끝까지 안 켬 | GUIDE, MISSED |
| S4 | 우회전, 처음부터 켬 | KEPT |
| S5 | 좌회전, 반대쪽 깜빡이 | GUIDE, MISSED |
| S6 | 접근 중 재탐색 | 없음 |
| S7 | 판정 후 신호 대기 | GUIDE, MISSED |
| S8 | 판정 후 주차 | 없음 |
| S9 | 재탐색 후 우회전 | KEPT |
| S10 | 판정 후 회전 없이 주행 | 없음 |
| S11 | 터널, 안내 듣고 전조등 켬 (R5) | GUIDE, KEPT |
| S12 | 오른쪽 나들목, 안내 듣고 깜빡이 켬 (R4) | GUIDE, KEPT |
| S13 | 적색 우회전, 안내 듣고 정지선에서 멈춤 (R3, R1) | GUIDE, KEPT, KEPT |
| S14 | 적색 우회전, 서행만 하고 멈추지 않음 (R3) | GUIDE, KEPT, MISSED |
| S15 | 적색 우회전, 다가가는 중 녹색으로 바뀜 (R3) | KEPT (R1만) |
| S16 | 우회전, 신호 정보 없음 (R3) | KEPT (R1만) |
| S17 | 비 오는데 와이퍼 꺼짐, 안내 듣고 켬 (R6) | GUIDE, KEPT |
| S18 | 내비 없이 좌회전, 깜빡이 안 켬 (R2) | MISSED |
| S19 | 내비 없이 좌회전, 깜빡이 켬 (R2) | KEPT |
| S20 | 완만한 커브 (R2 오탐 방지) | 없음 |
| S21 | 후진 뒤 회전, 주차장으로 봄 (R2) | 없음 |
| S22 | 비 오는데 내기 순환, 김서림 안내 후 성에 제거 켬 (R7) | GUIDE, KEPT |
| S23 | 음성 "앞이 뿌옇다", 안내 후 성에 제거 켬 (R7) | GUIDE, KEPT |
| S24 | 비 오는데 외기 순환 (R7) | 없음 |
| S25 | 유턴, 안내 듣고 왼쪽 깜빡이 켬 (R1) | GUIDE, KEPT |
| S26 | 유턴, 반대쪽(오른쪽) 깜빡이 (R1) | GUIDE, MISSED |
| S27 | 좌회전 뒤 여유 있게 우회전 (R1 연속 회전) | KEPT, GUIDE, KEPT |
| S28 | 좌회전 뒤 바로 우회전 (R1 연속 회전, 30m 안에서 처음 본 회전) | KEPT, KEPT |
| S29 | 같은 방향 연속 회전, 두 번째는 30m에서 놓침 (R1) | GUIDE, GUIDE, MISSED |

## 개발

```bash
./gradlew test
```

시나리오 테스트만 실행합니다.

```bash
./gradlew test --tests "*ScenarioTest*"
```
