package kkam.app

// 경로 데모의 목적지 검색. 에뮬레이터 내비는 검색·경로 계산을 못 하고, 네이버 Directions API는 Client Secret이 필요해서
// 미리 계산한 경로(assets JSON)를 목적지로 둔다. 검색창에 입력하면 이 목록에서 맞는 것을 보여준다.
// Android에 의존하지 않아서 JVM 테스트로 확인한다 (RouteDestinationsTest).

/** 미리 계산한 경로가 있는 목적지 하나. */
data class DemoDestination(
    val name: String,
    /** 목록 두 번째 줄. 예: "경기 성남시 분당구" */
    val area: String,
    /** 경로 데모 파일 (assets). 출발지는 파일의 start. */
    val asset: String,
    /** 이름 말고도 찾히는 말. 띄어쓰기는 무시한다. */
    val keywords: List<String> = emptyList(),
)

/** 지금은 PT 시연 경로 하나. 목적지를 늘리면 assets에 경로 파일을 더하고 여기에 한 줄 더한다. */
val DEMO_DESTINATIONS = listOf(
    // Pleos를 만드는 현대차그룹 계열사의 판교 사무실 (주소 출처와 좌표는 경로 파일의 destinationNote).
    // 시연에서 "현대자동차 판교"라고 입력해도 찾히게 찾는 말에 넣는다.
    DemoDestination(
        name = "포티투닷 (42dot)",
        area = "경기 성남시 수정구 창업로40번길 20 (판교 제2테크노밸리)",
        asset = RouteDemo.ASSET,
        keywords = listOf("42dot", "포티투닷", "현대자동차 판교", "현대차 판교", "현대자동차", "현대차", "판교", "pleos", "플레오스"),
    ),
)

/** 입력한 말이 이름이나 찾는 말의 일부면 보여준다. 빈 입력이면 아무것도 보여주지 않는다. */
fun searchDestinations(query: String, destinations: List<DemoDestination> = DEMO_DESTINATIONS): List<DemoDestination> {
    val q = query.normalized()
    if (q.isEmpty()) return emptyList()
    return destinations.filter { d -> (listOf(d.name) + d.keywords).any { it.normalized().contains(q) || q.contains(it.normalized()) } }
}

private fun String.normalized(): String = lowercase().filterNot { it.isWhitespace() }
