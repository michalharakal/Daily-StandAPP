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

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            implementation(project(":llm"))
            implementation(project(":data"))
            implementation(project(":domain"))
        }
        
        jvmMain.dependencies {
            implementation("io.ktor:ktor-server-core-jvm:3.0.1")
            implementation("io.ktor:ktor-server-netty-jvm:3.0.1")
            implementation("io.ktor:ktor-server-websockets-jvm:3.0.1")
            implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:3.0.1")
            implementation("io.ktor:ktor-server-content-negotiation-jvm:3.0.1")
            implementation("io.ktor:ktor-server-cors-jvm:3.0.1")
        }
        
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        
        jvmTest.dependencies {
            implementation("org.junit.jupiter:junit-jupiter-api:5.12.1")
            implementation("io.ktor:ktor-server-test-host-jvm:3.0.1")
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
    archiveBaseName.set("mcp-server-fat")
}