pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral(); maven("https://jitpack.io") }
}
rootProject.name = "Android Agent"
include(":app", ":core", ":engine-codex", ":runtime", ":workspace", ":adb", ":device-tools", ":overlay", ":voice")
