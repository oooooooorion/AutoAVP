pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
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

rootProject.name = "AutoAVP"
include(":app")

// Redirect build directory to a local path to avoid Google Drive sync locks
gradle.beforeProject {
    val isWindows = System.getProperty("os.name").lowercase().contains("win")
    val localBuildPath = if (isWindows) "C:/temp/AutoAVP-build" else "/tmp/AutoAVP-build"
    layout.buildDirectory.set(file("$localBuildPath/${project.name}"))
}
