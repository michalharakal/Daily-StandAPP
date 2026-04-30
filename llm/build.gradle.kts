import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    kotlin("plugin.serialization") version libs.versions.kotlin.get()
}

kotlin {

    jvmToolchain(25)
    jvm {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_25)
            freeCompilerArgs.addAll(
                listOf(
                    "-jvm-default=enable",
                    "-Xjdk-release=25",
                )
            )
        }
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
            jvmArgs = listOf("--add-modules", "jdk.incubator.vector", "--enable-preview")
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.io.core)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmMain {
            dependencies {
                // SKaiNET 0.21.0 (BOM-managed) + skainet-transformers 0.21.1 (per-artefact pinned).
                // Transformers 0.21.1 BOM is currently broken: it imports an
                // unpublished sk.ainet:skainet-bom:0.21.0 — see TR-08 in arc42 §11.
                implementation(project.dependencies.platform(libs.skainet.bom))

                // Core inference: DSL + CPU backend + GGUF I/O
                implementation(libs.skainet.lang.core)
                implementation(libs.skainet.backend.cpu)
                implementation(libs.skainet.io.gguf)

                // Agent loop + tool calling + Llama runtime
                implementation(libs.skainet.tx.runtime.kllama)
                implementation(libs.skainet.tx.agent)
                implementation(libs.skainet.tx.inference.llama)

                // JGit-backed tools need the data module
                implementation(project(":data"))
                implementation(libs.kotlinx.datetime)

                // Ktor HTTP client for REST API backend
                implementation(libs.ktor.client.cio)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
            }
        }
    }
}

tasks.withType<Test> {
    systemProperty("test.mode", "true")
}

// Embed the Llama 3.2 1B Instruct GGUF as a JAR resource. Pulled via
// `uv run scripts/download-model.py` — runs once, idempotent.
val embeddedModelFile = layout.projectDirectory.file(
    "src/jvmMain/resources/models/Llama-3.2-1B-Instruct-Q8_0.gguf"
).asFile

val prepareModel = tasks.register<Exec>("prepareModel") {
    description = "Download Llama-3.2-1B-Instruct-Q8_0.gguf into jvmMain resources via uv."
    workingDir = rootDir
    commandLine("uv", "run", "scripts/download-model.py")
    inputs.file(rootDir.resolve("scripts/download-model.py"))
    outputs.file(embeddedModelFile)
    onlyIf { !embeddedModelFile.exists() }
}

tasks.named("jvmProcessResources").configure {
    dependsOn(prepareModel)
}
