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
include(":engines:termux:terminal-emulator")
include(":engines:termux:terminal-view")
include(":engines:termux:termux-shared")
include(":engines:termux:app")
