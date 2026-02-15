rootProject.name = "Daily-StandAPP"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        mavenLocal()
    }
}

include(":composeApp", ":shared", ":data", ":domain", ":llm", ":mcp-server", ":benchmark")
include("StandAPP-cli")

// SKaiNET 0.13.0 -- composite build for agent module resolution
includeBuild("../SKaiNET") {
    dependencySubstitution {
        substitute(module("sk.ainet.core:skainet-apps-kllama"))
            .using(project(":skainet-apps:skainet-kllama"))
        substitute(module("sk.ainet.core:skainet-apps-kllama-agent"))
            .using(project(":skainet-apps:skainet-kllama-agent"))
        substitute(module("sk.ainet.core:skainet-lang-core"))
            .using(project(":skainet-lang:skainet-lang-core"))
        substitute(module("sk.ainet.core:skainet-backend-cpu"))
            .using(project(":skainet-backends:skainet-backend-cpu"))
        substitute(module("sk.ainet.core:skainet-io-core"))
            .using(project(":skainet-io:skainet-io-core"))
        substitute(module("sk.ainet.core:skainet-io-gguf"))
            .using(project(":skainet-io:skainet-io-gguf"))
        substitute(module("sk.ainet.core:skainet-llm"))
            .using(project(":skainet-apps:skainet-llm"))
    }
}

check(JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_21)) {
    """
    Daily-StandApp requires JDK 21+ but it is currently using JDK ${JavaVersion.current()}.
    Java Home: [${System.getProperty("java.home")}]
    https://developer.android.com/build/jdks#jdk-config-in-studio
    """.trimIndent()
}
