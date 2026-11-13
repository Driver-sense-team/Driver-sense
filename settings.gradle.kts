pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://nexus-playground.pleos.ai/repository/maven-releases/") }
        // 네이버 지도 SDK (경로 데모 지도, app/만 쓴다)
        maven {
            url = uri("https://repository.map.naver.com/archive/maven")
            content { includeGroup("com.naver.maps") }
        }
    }
}

rootProject.name = "kkam-engine"
include(":engine")
include(":app")
