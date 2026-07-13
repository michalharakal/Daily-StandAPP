import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    application
    alias(libs.plugins.shadow)
}

group = "de.jug_da.standapp.cli"
version = "1.0-SNAPSHOT"

application {
    mainClass.set("de.jug_da.app.cli.MainKt")
    applicationDefaultJvmArgs = listOf(
        "--enable-preview",
        "--add-modules=jdk.incubator.vector",
        "--enable-native-access=ALL-UNNAMED",
        // Empirical: shadow JAR run from /tmp OOMed at -Xmx12g (HeapKvCache.<init>);
        // 24 GB lets it allocate Llama 3.2 1B's 128K KV cache plus FP32 weights plus
        // GC headroom. Lower if you constrain context length.
        "-Xms2g",
        "-Xmx24g",
    )
}

// Self-contained fat JAR: bundles the embedded Llama 3.2 1B Q8_0 GGUF resource
// (~1.3 GB) plus all skainet kernel-provider service files. mergeServiceFiles is
// essential because skainet 0.21.0's CPU backend discovers kernels via
// META-INF/services/sk.ainet.backend.api.kernel.KernelProvider.
tasks.shadowJar {
    archiveBaseName.set("standapp")
    archiveClassifier.set("all")
    mergeServiceFiles()
    manifest {
        attributes(
            "Main-Class" to "de.jug_da.app.cli.MainKt",
            "Enable-Native-Access" to "ALL-UNNAMED",
        )
    }
    // Sign-related entries break when shadow concatenates JARs; strip them.
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))

    implementation(project(":llm"))
    implementation(project(":data"))
    implementation(project(":standapp-ai-engine"))

    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.slf4j.simple)
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
    jvmArgs(
        "--enable-preview",
        "--add-modules=jdk.incubator.vector",
        "--enable-native-access=ALL-UNNAMED",
    )
    minHeapSize = "2g"
    maxHeapSize = "16g"
}

// The application plugin's run task is created late; force-override its JVM
// settings here so the KV-cache allocation does not OOM. -Xmx24g matches the
// minimum the shadow JAR needs (see comment in `application { ... }`).
tasks.named<JavaExec>("run").configure {
    jvmArgs = listOf(
        "--enable-preview",
        "--add-modules=jdk.incubator.vector",
        "--enable-native-access=ALL-UNNAMED",
    )
    minHeapSize = "2g"
    maxHeapSize = "24g"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

kotlin {
    jvmToolchain(25)
}
