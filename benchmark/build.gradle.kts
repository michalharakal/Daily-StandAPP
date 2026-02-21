import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    kotlin("plugin.serialization") version libs.versions.kotlin.get()
}

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
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.io.core)

            implementation(project(":llm"))
            implementation(project(":data"))
            implementation(project(":standapp-ai-engine"))
        }

        jvmMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }

        jvmTest.dependencies {
            implementation(libs.kotlin.test.junit)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
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
