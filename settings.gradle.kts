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
        maven { url = uri("https://jitpack.io") }
        ivy {
            name = "SherpaOnnxOfficialReleases"
            url = uri("https://github.com/k2-fsa/sherpa-onnx/releases/download")
            patternLayout {
                artifact("v[revision]/sherpa-onnx-[revision].[ext]")
            }
            metadataSources { artifact() }
            content { includeModule("com.k2fsa", "sherpa-onnx") }
        }
    }
}

rootProject.name = "AIAgentsApp"
include(":app")
include(":benchmark")
include(":voice")
