rootProject.name = "Daily-StandAPP"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Auto-provisions the JDK 25 toolchain the modules request when the
    // launching JDK (CI images, developer default `java`) is 21.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        // Opt-in: `-PskainetMavenLocal=true` resolves the SKaiNET artifacts from ~/.m2 first,
        // for a locally published engine or transformers build (publishToMavenLocal) that
        // Maven Central does not carry yet. CI never sets it.
        if (providers.gradleProperty("skainetMavenLocal").orNull == "true") {
            mavenLocal { content { includeGroupByRegex("sk\\.ainet(\\..*)?") } }
        }
        mavenCentral()
    }
}

include(":data", ":domain", ":llm", ":mcp-server", ":benchmark")
include("StandAPP-cli")
include(":standapp-ai-engine")

// Cloud API - OpenAI-compatible REST API modules
include(":cloud-api:model", ":cloud-api:server", ":cloud-api:client", ":cloud-api:agent")

// Opt-in composite build against a local SKaiNET-transformers checkout:
//
//     ./gradlew ... -PuseLocalTransformers=true
//
// By default the published sk.ainet.transformers:* 0.61.0 artifacts from Maven
// Central are used, so a plain checkout and CI resolve the same graph.
//
// Substitutions are EXPLICIT: the upstream artifactIds (skainet-transformers-*)
// come from per-module POM_ARTIFACT_ID properties, which Gradle's automatic
// composite substitution does not reliably map to project paths — without
// these rules the coordinates silently resolve from Maven Central.
// `-PlocalTransformersPath=../SKaiNET-transformers-laya` points the composite at another
// checkout or worktree (the Laya triage stage needs the `feature/laya-adapter` branch until
// transformers ships the laya and decisions modules on Maven Central).
val localSkaiNetTransformers = file(providers.gradleProperty("localTransformersPath").orNull ?: "../SKaiNET-transformers")
if (providers.gradleProperty("useLocalTransformers").orNull == "true") {
    check(localSkaiNetTransformers.isDirectory) {
        "useLocalTransformers=true but ${localSkaiNetTransformers.absolutePath} is not a directory"
    }
    includeBuild(localSkaiNetTransformers) {
        dependencySubstitution {
            // The BOM too, so the composite needs no published transformers version at all.
            substitute(module("sk.ainet.transformers:skainet-transformers-bom"))
                .using(project(":llm-bom"))
            substitute(module("sk.ainet.transformers:skainet-transformers-core"))
                .using(project(":llm-core"))
            substitute(module("sk.ainet.transformers:skainet-transformers-agent"))
                .using(project(":llm-agent"))
            substitute(module("sk.ainet.transformers:skainet-transformers-inference-llama"))
                .using(project(":llm-inference:llama"))
            substitute(module("sk.ainet.transformers:skainet-transformers-inference-qwen"))
                .using(project(":llm-inference:qwen"))
            substitute(module("sk.ainet.transformers:skainet-transformers-inference-laya"))
                .using(project(":llm-inference:laya"))
            substitute(module("sk.ainet.transformers:skainet-transformers-decisions-api"))
                .using(project(":decisions:decisions-api"))
            substitute(module("sk.ainet.transformers:skainet-transformers-decisions-runtime"))
                .using(project(":decisions:decisions-runtime"))
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
