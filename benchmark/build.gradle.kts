import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    kotlin("plugin.serialization") version libs.versions.kotlin.get()
}

// ---------------------------------------------------------------------------
// Optional alternative-engine integration. Enabled via Gradle properties:
//
//   ./gradlew :benchmark:jvmRun -Pdeliverance.enabled=true
//   ./gradlew :benchmark:jvmRun -Pqxotic.enabled=true
//
// When a property is set:
//   - the corresponding source set under src/jvmMain/<engine>/ is included
//   - the engine's coordinates are added as implementation deps
//   - mavenLocal() is added to repositories (alternatives don't publish to
//     Maven Central; see scripts/setup-bench-engines.sh)
//
// When unset (CI default), nothing changes and the alternative engines are
// not on the classpath. BenchmarkEngineRegistry's reflection lookup returns
// null and the relevant BENCH_*_MODEL env vars print a helpful warning.
// ---------------------------------------------------------------------------
val deliveranceEnabled: Boolean = providers.gradleProperty("deliverance.enabled")
    .map { it.toBoolean() }.orElse(false).get()
val qxoticEnabled: Boolean = providers.gradleProperty("qxotic.enabled")
    .map { it.toBoolean() }.orElse(false).get()

if (deliveranceEnabled || qxoticEnabled) {
    // Project-level repositories shadow the settings-level dependency
    // resolution config — re-declare both so the Kotlin compiler plugin
    // (resolved from Central) and the alternative engines (mavenLocal) both
    // resolve.
    repositories {
        mavenCentral()
        mavenLocal()
    }
}

kotlin {
    jvmToolchain(25)

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_25)
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.io.core)

            implementation(project(":llm"))
            implementation(project(":data"))
            implementation(project(":standapp-ai-engine"))
        }

        jvmMain {
            if (deliveranceEnabled) {
                kotlin.srcDir("src/jvmMain/deliverance")
            }
            if (qxoticEnabled) {
                kotlin.srcDir("src/jvmMain/qxotic")
            }
            dependencies {
                implementation(libs.kotlinx.serialization.json)
                if (deliveranceEnabled) {
                    implementation(libs.deliverance.core)
                    implementation(libs.deliverance.safetensors)
                }
            }
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }

        jvmTest.dependencies {
            implementation(libs.kotlin.test.junit)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

tasks.named<Jar>("jvmJar") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = "de.jug_da.standapp.benchmark.MainKt"
    }
    from({
        configurations.getByName("jvmRuntimeClasspath").map { file ->
            if (file.isDirectory) file else zipTree(file)
        }
    })
    archiveBaseName.set("benchmark")
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}
