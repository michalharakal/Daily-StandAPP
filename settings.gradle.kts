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

// SKaiNET composite build -- compile from source, no publishing needed
includeBuild("../SKaiNET") {
    dependencySubstitution {
        substitute(module("sk.ainet.core:skainet-kllama"))
            .using(project(":skainet-apps:skainet-kllama"))
        substitute(module("sk.ainet.core:skainet-kllama-agent"))
            .using(project(":skainet-apps:skainet-kllama-agent"))
    }
}

check(JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_21)) {
    """
    Daily-StandApp requires JDK 21+ but it is currently using JDK ${JavaVersion.current()}.
    Java Home: [${System.getProperty("java.home")}]
    https://developer.android.com/build/jdks#jdk-config-in-studio
    """.trimIndent()
}
