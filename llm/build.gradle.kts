import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig
import java.util.Locale

plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

val osName: String = System.getProperty("os.name").lowercase()
val osArch: String = System.getProperty("os.arch").lowercase()

val detectedOs = when {
    osName.contains("mac") -> "osx"
    osName.contains("linux") -> "linux"
    osName.contains("windows") -> "windows"
    else -> throw GradleException("Unsupported OS: $osName")
}

val detectedArch = when (osArch) {
    "x86_64", "amd64" -> "x86_64"
    "aarch64", "arm64" -> "aarch_64"
    else -> throw GradleException("Unsupported Arch: $osArch")
}

kotlin {

    jvmToolchain(21)  // Use JDK 17 toolchain for compilation (if using Java 17 features)
    jvm {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
            freeCompilerArgs.addAll(
                listOf(
                    "-Xjvm-default=all",
                    "-Xjdk-release=21",
                )
            )
        }
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
            jvmArgs = listOf("--add-modules", "jdk.incubator.vector")
        }
    }

    macosX64()
    macosArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.io.core)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)

        }
        jvmMain.dependencies {
            implementation("com.github.tjake:jlama-core:0.8.4") {
                exclude("org.slf4j", "slf4j-log4j12")
            }
            implementation("com.github.tjake:jlama-native:0.8.4:$detectedOs-$detectedArch")
        }
    }
}

tasks.withType<Test> {
    systemProperty("test.mode", "true")
}
