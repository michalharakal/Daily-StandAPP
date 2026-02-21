import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    `maven-publish`
    signing
}

group = "dev.standapp"
version = "0.1.0"

kotlin {
    jvmToolchain(21)

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
            freeCompilerArgs.add("-Xadd-modules=jdk.incubator.vector")
        }
    }

    sourceSets {
        jvmMain.dependencies {
            api(project(":standapp-ai-engine"))

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.skainet.kllama)
            implementation(libs.skainet.llm)
            implementation(libs.skainet.kllama.agents)
            implementation(libs.skainet.lang.core)
            implementation(libs.skainet.io.core)
            implementation(libs.skainet.io.gguf)
            implementation(libs.skainet.backend.cpu)
        }
    }
}

publishing {
    publications.withType<MavenPublication> {
        pom {
            name.set("StandAPP AI Engine — SKaiNET Backend")
            description.set("SKaiNET KLlama backend for standapp-ai-engine (JVM only)")
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
