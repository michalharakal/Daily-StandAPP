import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    `maven-publish`
    signing
}

group = "dev.standapp"
version = "0.1.0"

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
    jvmToolchain(21)

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    sourceSets {
        jvmMain.dependencies {
            api(project(":standapp-ai-engine"))

            implementation(libs.kotlinx.coroutines.core)
            implementation("com.github.tjake:jlama-core:0.8.4") {
                exclude("org.slf4j", "slf4j-log4j12")
            }
            implementation("com.github.tjake:jlama-native:0.8.4:$detectedOs-$detectedArch")
        }
    }
}

publishing {
    publications.withType<MavenPublication> {
        pom {
            name.set("StandAPP AI Engine — JLama Backend")
            description.set("JLama backend for standapp-ai-engine (JVM only)")
            url.set("https://github.com/niclas/Daily-StandAPP")
            licenses {
                license {
                    name.set("Apache-2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0")
                }
            }
            scm {
                url.set("https://github.com/niclas/Daily-StandAPP")
            }
        }
    }

    repositories {
        maven {
            name = "sonatype"
            val releasesUrl = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            val snapshotsUrl = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
            url = if (version.toString().endsWith("SNAPSHOT")) snapshotsUrl else releasesUrl
            credentials {
                username = findProperty("ossrhUsername") as String? ?: System.getenv("OSSRH_USERNAME")
                password = findProperty("ossrhPassword") as String? ?: System.getenv("OSSRH_PASSWORD")
            }
        }
    }
}

signing {
    val signingKey = findProperty("signing.key") as String? ?: System.getenv("GPG_SIGNING_KEY")
    val signingPassword = findProperty("signing.password") as String? ?: System.getenv("GPG_SIGNING_PASSWORD")
    if (signingKey != null && signingPassword != null) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications)
    }
}
