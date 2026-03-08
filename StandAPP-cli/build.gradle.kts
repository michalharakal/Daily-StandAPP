import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    application
}

group = "de.jug_da.standapp.cli"
version = "1.0-SNAPSHOT"

application {
    mainClass.set("de.jug_da.app.cli.MainKt")
}

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    testImplementation(kotlin("test"))

    implementation(project(":llm"))
    implementation(project(":data"))
    implementation(project(":standapp-ai-engine"))

    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.coroutines.core)
    implementation("org.slf4j:slf4j-simple:2.0.17")
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
        jvmTarget.set(JvmTarget.JVM_25)
        freeCompilerArgs.addAll(listOf("-jvm-default=enable"))
    }
}

tasks.withType<JavaExec>().configureEach {
    jvmArgs = listOf("--enable-preview", "--add-modules=jdk.incubator.vector")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

kotlin {
    jvmToolchain(25)
}
