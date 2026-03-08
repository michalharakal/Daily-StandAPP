import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {

    jvmToolchain(25)

    jvm {
        compilations.all {
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.io.core)
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }

        jvmMain.dependencies {
            implementation(libs.eclipse.jgit)
            implementation(libs.slf4j.simple)
        }
        jvmTest.dependencies {
            implementation("org.junit.jupiter:junit-jupiter-api:5.12.1")
        }
    }
}
