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
            jvmArgs = listOf(
                "--add-modules", "jdk.incubator.vector",
                "--enable-preview",
                "--enable-native-access=ALL-UNNAMED",
            )
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
                // SKaiNET core + transformers, both BOM-managed (0.59.0 / 0.60.0).
                implementation(project.dependencies.platform(libs.skainet.bom))
                // api, not implementation: :llm exposes decisions-api on its api configuration, so every consumer

                // (CLI, benchmark, …) needs the transformers BOM's version constraints on its own classpath.

                api(project.dependencies.platform(libs.skainet.tx.bom))

                // Engine: DSL + execution contexts, kernel SPI, GGUF I/O, hf:// downloads.
                implementation(libs.skainet.lang.core)
                implementation(libs.skainet.backend.api)
                implementation(libs.skainet.backend.cpu)
                implementation(libs.skainet.backend.native.cpu)
                implementation(libs.skainet.io.core)
                implementation(libs.skainet.io.gguf)
                implementation(libs.skainet.data.source)

                // Transformers: decoder loader/runtime, agent loop + chat templates,
                // and the two model families this app drives (Qwen3 tool stage,
                // Llama 3.2 summariser).
                implementation(libs.skainet.tx.core)
                implementation(libs.skainet.tx.agent)
                implementation(libs.skainet.tx.inference.qwen)
                implementation(libs.skainet.tx.inference.llama)

                // Typed decisions: the shared request/outcome contract, the runtime that applies
                // the acceptance policy, and Laya (mmBERT encoder + decision heads) as the adapter.
                api(libs.skainet.tx.decisions.api)   // the decide { } block is a decisions-dsl scope, visible to callers
                implementation(libs.skainet.tx.decisions.runtime)
                implementation(libs.skainet.tx.inference.laya)

                // JGit-backed git tool + engine prompt/parse types.
                implementation(project(":data"))
                implementation(project(":standapp-ai-engine"))
                implementation(libs.kotlinx.datetime)
                implementation(libs.kotlinx.serialization.json)

                // Ktor HTTP client: REST API backend + long-timeout model downloads.
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.cio)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
            }
        }
        jvmTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

tasks.withType<Test> {
    systemProperty("test.mode", "true")
}
