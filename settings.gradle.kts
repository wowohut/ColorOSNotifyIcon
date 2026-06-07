pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
plugins {
    id("com.highcapable.gropify") version "1.0.1"
}
gropify {
    global {
        common {
            includeKeys(
                "GITHUB_CI_COMMIT_ID",
                "^project\\..*\$".toRegex()
            )
            permanentKeyValues("GITHUB_CI_COMMIT_ID" to "")
            locations(GropifyLocation.RootProject, GropifyLocation.SystemEnv)
        }
        android {
            includeKeys("GITHUB_CI_COMMIT_ID")
            // 手动指定类型，防止一些特殊 "COMMIT ID" 被生成为数值
            keyValuesRules("GITHUB_CI_COMMIT_ID" to ValueRule(String::class))
        }
    }
    rootProject { common { isEnabled = false } }
}
rootProject.name = "ColorOSNotifyIcon"
include(":app")
