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
                // SKaiNET 0.22.0 (BOM-managed) + skainet-transformers 0.21.1 (per-artefact pinned).
                // Transformers 0.21.1 BOM is currently broken: it imports an
                // unpublished sk.ainet:skainet-bom:0.21.0 — see TR-08 in arc42 §11.
                // Core 0.22.0 is wire-compatible with transformers 0.21.1's
                // 0.21.0 transitive coordinates; gradle's resolution picks the
                // higher version.
                implementation(project.dependencies.platform(libs.skainet.bom))

                // Core inference: DSL + Panama Vector CPU backend + GGUF I/O
                implementation(libs.skainet.lang.core)
                implementation(libs.skainet.backend.cpu)
                implementation(libs.skainet.io.gguf)

                // 0.22.0 native FFM CPU kernels (FP32 SGEMM + Q4_K matmul).
                // Auto-registers via META-INF/services/sk.ainet.backend.api.kernel.KernelProvider
                // and outranks the Panama Vector provider when its bundled
                // platform shared library loads (Linux x64 in our case).
                implementation(libs.skainet.backend.native.cpu)

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

// Resolve `uv` on PATH so we can skip the download cleanly on environments
// (CI, sandboxed builds) that don't have it. Setting STANDAPP_SKIP_MODEL_DOWNLOAD=1
// also disables the task — useful when iterating without needing the embedded GGUF.
val uvOnPath: String? = System.getenv("PATH")
    ?.split(File.pathSeparatorChar)
    ?.map { File(it, "uv") }
    ?.firstOrNull { it.canExecute() }
    ?.absolutePath
val skipModelDownload: Boolean =
    System.getenv("STANDAPP_SKIP_MODEL_DOWNLOAD") == "1" || uvOnPath == null

val prepareModel = tasks.register<Exec>("prepareModel") {
    description = "Download Llama-3.2-1B-Instruct-Q8_0.gguf into jvmMain resources via uv."
    workingDir = rootDir
    commandLine("uv", "run", "scripts/download-model.py")
    inputs.file(rootDir.resolve("scripts/download-model.py"))
    outputs.file(embeddedModelFile)
    onlyIf {
        when {
            embeddedModelFile.exists() -> false
            skipModelDownload -> {
                logger.lifecycle(
                    "[prepareModel] skipping model download — " +
                        if (uvOnPath == null) "`uv` not on PATH"
                        else "STANDAPP_SKIP_MODEL_DOWNLOAD=1"
                )
                false
            }
            else -> true
        }
    }
}

tasks.named("jvmProcessResources").configure {
    dependsOn(prepareModel)
}
