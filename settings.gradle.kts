rootProject.name = "Daily-StandAPP"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
    }
}

include(":data", ":domain", ":llm", ":mcp-server", ":benchmark")
include("StandAPP-cli")
include(":standapp-ai-engine")

// Cloud API - OpenAI-compatible REST API modules
include(":cloud-api:model", ":cloud-api:server", ":cloud-api:client", ":cloud-api:agent")


check(JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_24)) {
    """
    Daily-StandApp requires JDK 25+ but it is currently using JDK ${JavaVersion.current()}.
    Java Home: [${System.getProperty("java.home")}]
    """.trimIndent()
}
