import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    jvmToolchain(21)

    jvm {
        // Target JDK 17 for JVM compilation
        compilations.all {
            kotlinOptions.jvmTarget = "21"
            // Enable the incubating Vector API module for the compiler
            kotlinOptions.freeCompilerArgs += "-Xadd-modules=jdk.incubator.vector"
        }
        // (Optional) Use JDK 17 toolchain for compilation
        // java.toolchain.languageVersion.set(JavaLanguageVersion.of(17))
    }
        listOf(
            iosArm64(),
            iosSimulatorArm64()
        ).forEach { iosTarget ->
            iosTarget.binaries.framework {
                baseName = "SinusApproximatorKit"
                isStatic = true
            }
        }


        @OptIn(ExperimentalWasmDsl::class)
        wasmJs {
            browser {
                val rootDirPath = project.rootDir.path
                val projectDirPath = project.projectDir.path
                commonWebpackConfig {
                    devServer = (devServer ?: KotlinWebpackConfig.DevServer()).apply {
                        static = (static ?: mutableListOf()).apply {
                            // Serve sources to debug inside browser
                            add(rootDirPath)
                            add(projectDirPath)
                        }
                    }
                }
            }
        }

        sourceSets {
            commonMain.dependencies {
                implementation(libs.kotlinx.io.core)
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")
            }
            jvmMain.dependencies {
                implementation("org.eclipse.jgit:org.eclipse.jgit:7.1.0.202411261347-r")
                implementation("org.slf4j:slf4j-simple:2.0.16")
            }
        }
    }

    android {
        namespace = "sk.ai.net.client.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_11
            targetCompatibility = JavaVersion.VERSION_11
        }
        defaultConfig {
            minSdk = libs.versions.android.minSdk.get().toInt()
        }
    }

