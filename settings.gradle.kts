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
        maven { url = uri("https://jitpack.io") } // DantSu ESC/POS + iMin printer libraries
    }
}

rootProject.name = "universal-printer-search"
include(":universal-printer-search")
include(":adapter-star")
include(":universal-printer")
include(":example")
