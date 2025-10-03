import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Locale

plugins {
    kotlin("jvm")
    application
}

group = "sk.ai.net.jlama"
version = "1.0-SNAPSHOT"

application {
    mainClass.set("sk.ai.net.jlama.MainKt")
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))

    val jlamaVersion = "0.8.4"
    implementation("com.github.tjake:jlama-core:$jlamaVersion")
    implementation("org.slf4j:slf4j-simple:2.0.12")

    val osName = System.getProperty("os.name").lowercase(Locale.getDefault())
    val osArch = System.getProperty("os.arch").lowercase(Locale.getDefault())
    println((osName))
    println((osArch))
    val classifier = when {
        osName.contains("linux") && osArch.contains("amd64") -> "linux-x86_64"
        osName.contains("mac") && osArch.contains("x86_64") -> "macos-x86_64"
        osName.contains("mac") && (osArch.contains("aarch64") || osArch.contains("arm64")) -> "osx-aarch_64"
        osName.contains("windows") && osArch.contains("amd64") -> "windows-x86_64"
        else -> throw GradleException("Unsupported OS or architecture: $osName-$osArch")
    }

    implementation("com.github.tjake:jlama-native:$jlamaVersion:$classifier")
//    implementation("org.eclipse.jgit:org.eclipse.jgit:7.1.0.202411261347-r")

}

tasks.test {
    useJUnitPlatform()
    jvmArgs("--enable-preview", "--add-modules=jdk.incubator.vector")
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("--enable-preview", "--add-modules", "jdk.incubator.vector"))
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        freeCompilerArgs.addAll(listOf("-Xjvm-default=all"))
    }
}

tasks.withType<JavaExec>().configureEach {
    jvmArgs = listOf("--enable-preview", "--add-modules=jdk.incubator.vector")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

kotlin {
    jvmToolchain(21)
}