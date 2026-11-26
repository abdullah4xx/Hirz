pluginManagement {
    repositories {
        val localM2 = System.getenv("HIRZ_LOCAL_M2")
        if (localM2 != null) {
            maven { url = uri(localM2) }
        }
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
        val localM2 = System.getenv("HIRZ_LOCAL_M2")
        if (localM2 != null) {
            maven { url = uri(localM2) }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "Hirz"
include(":app")
