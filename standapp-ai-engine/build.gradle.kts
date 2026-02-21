import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
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
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }

        jvmMain.dependencies {
            implementation(libs.ktor.client.cio)
        }

        jvmTest.dependencies {
            implementation(libs.kotlin.test.junit)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
        }
    }
}

publishing {
    publications.withType<MavenPublication> {
        pom {
            name.set("StandAPP AI Engine")
            description.set("KMP library for generating developer standup summaries from commit data using local or cloud LLMs")
            url.set("https://github.com/niclas/Daily-StandAPP")
            licenses {
                license {
                    name.set("Apache-2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0")
                }
            }
            developers {
                developer {
                    id.set("niclas")
                    name.set("Niclas Hedhman")
                }
            }
            scm {
                url.set("https://github.com/niclas/Daily-StandAPP")
                connection.set("scm:git:git://github.com/niclas/Daily-StandAPP.git")
                developerConnection.set("scm:git:ssh://github.com/niclas/Daily-StandAPP.git")
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
