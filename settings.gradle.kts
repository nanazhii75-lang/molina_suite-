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
        // Hanya untuk NewPipeExtractor dan nanojson (dependensinya); grup lain tidak diambil dari sini.
        maven(url = "https://jitpack.io") {
            content {
                includeGroup("com.github.teamnewpipe")
                includeGroup("com.github.TeamNewPipe")
            }
        }
    }
}

rootProject.name = "molina-suite"

include(":app")
include(":core:core-common")
include(":core:core-storage")
include(":features:feature-terminal")
include(":features:feature-code")
include(":features:feature-mpv")
include(":features:feature-settings")
include(":features:feature-x11")
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
include(":engines:termux-x11-stub")
project(":engines:termux-x11-stub").projectDir = file("engines/termux-x11/stub")
include(":engines:termux-x11-lorie")
project(":engines:termux-x11-lorie").projectDir = file("engines/termux-x11/lorie")
