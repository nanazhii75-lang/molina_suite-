pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "molina-suite"

include(":app")
include(":core:core-common")
include(":core:core-storage")
include(":features:feature-terminal")
include(":features:feature-code")
include(":features:feature-mpv")
include(":engines:termux:terminal-emulator")
include(":engines:termux:terminal-view")
include(":engines:termux:termux-shared")
include(":engines:termux:app")
include(":engines:sora-core")
include(":engines:sora-textmate")
project(":engines:sora-core").projectDir = file("engines/sora-modules/editor")
project(":engines:sora-textmate").projectDir = file("engines/sora-modules/language-textmate")
include(":engines:mpv")
project(":engines:mpv").projectDir = file("engines/mpv-android/app")
