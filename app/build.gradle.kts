import java.util.Properties

plugins {
    id("com.android.application")
}

// 네이버 지도 Client ID는 local.properties(커밋 안 함)의 NAVER_MAP_CLIENT_ID에서 읽는다.
// 값이 없어도 빌드는 되고, 그때는 경로 데모에서 지도만 안 뜬다.
val naverMapClientId: String = Properties().run {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
    getProperty("NAVER_MAP_CLIENT_ID").orEmpty()
}

android {
    namespace = "kkam.app"
    compileSdk = 36
    useLibrary("android.car")

    defaultConfig {
        applicationId = "kkam.app"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"
        buildConfigField("String", "NAVER_MAP_CLIENT_ID", "\"$naverMapClientId\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // 시나리오는 엔진 테스트와 같은 파일을 쓴다. 복사하지 않는다.
    sourceSets.getByName("main").assets.directories.add(rootProject.file("scenarios").path)
    // 법규 룰 테이블(law_table.json)도 엔진·서버와 같은 파일을 쓴다.
    sourceSets.getByName("main").assets.directories.add(rootProject.file("data").path)
}

dependencies {
    implementation(project(":engine"))
    implementation("ai.pleos.playground:TextToSpeech:3.3.1")
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("ai.pleos.playground:NaviHelper:3.0.5")
    implementation("ai.pleos.playground:FusedLocation:1.1.3")
    // 경로 데모의 앱 안 지도 (네이버 지도). 키는 local.properties의 NAVER_MAP_CLIENT_ID.
    implementation("com.naver.maps:map-sdk:3.24.1")

    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
