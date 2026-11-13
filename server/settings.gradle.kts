rootProject.name = "server"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include(":engine")
project(":engine").projectDir = file("../engine")