import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    kotlin("plugin.serialization") version "2.1.20"
}

kotlin {
    jvmToolchain(21)

    jvm {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
        // Enable preview features for JDK 21
        compilations.all {
            kotlinOptions.freeCompilerArgs += "-Xadd-modules=jdk.incubator.vector"
        }
        
    }

    macosArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.mcp.kotlin.server)
            implementation(libs.ktor.server.cio)
            implementation(libs.ktor.server.sse)

            implementation(project(":llm"))
            implementation(project(":data"))
            implementation(project(":domain"))
        }
        
        jvmMain.dependencies {
        }
        
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        
        jvmTest.dependencies {
        }
    }
}

// Additional JAR configuration for fat JAR creation
tasks.named<Jar>("jvmJar") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = "de.jug_da.standapp.mcp.MCPServerKt"
    }
    from({
        configurations.getByName("jvmRuntimeClasspath").map { file ->
            if (file.isDirectory) file else zipTree(file)
        }
    })
    archiveBaseName.set("mcp-server")
    // Exclude signature files to avoid security exceptions
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}