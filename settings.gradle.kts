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
        mavenCentral()
    }
}

include(":data", ":domain", ":llm", ":mcp-server", ":benchmark")
include("StandAPP-cli")
include(":standapp-ai-engine")

// Cloud API - OpenAI-compatible REST API modules
include(":cloud-api:model", ":cloud-api:server", ":cloud-api:client", ":cloud-api:agent")

// Composite build: source-level dependency on SKaiNET-transformers (which itself
// composite-includes SKaiNET). Substitutes the sk.ainet.transformers:* coordinates
// used in libs.versions.toml with the local project, so the daily app exercises
// in-flight transformers + skainet changes without any mavenLocal staging.
//
// Substitutions are EXPLICIT: the upstream artifactIds
// (skainet-transformers-*) come from per-module POM_ARTIFACT_ID properties,
// which Gradle's automatic composite substitution does not reliably map to
// project paths — without these rules the coordinates silently resolve from
// Maven Central and local changes never reach this build.
val localSkaiNetTransformers = file("../SKaiNET-transformers")
if (localSkaiNetTransformers.isDirectory) {
    includeBuild(localSkaiNetTransformers) {
        dependencySubstitution {
            substitute(module("sk.ainet.transformers:skainet-transformers-runtime-kllama"))
                .using(project(":llm-runtime:kllama"))
            substitute(module("sk.ainet.transformers:skainet-transformers-agent"))
                .using(project(":llm-agent"))
            substitute(module("sk.ainet.transformers:skainet-transformers-inference-llama"))
                .using(project(":llm-inference:llama"))
            substitute(module("sk.ainet.transformers:skainet-transformers-core"))
                .using(project(":llm-core"))
        }
    }
}


val javaVersion = System.getProperty("java.version")?.substringBefore('.')?.toIntOrNull() ?: 0
check(javaVersion >= 21) {
    """
    Daily-StandApp requires JDK 21+ but it is currently using JDK $javaVersion.
    Java Home: [${System.getProperty("java.home")}]
    JDK 25 is used via Gradle toolchain for compilation.
    """.trimIndent()
}
