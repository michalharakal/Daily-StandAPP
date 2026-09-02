import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.zip.ZipFile

plugins {
    kotlin("jvm")
    application
    alias(libs.plugins.shadow)
}

group = "de.jug_da.standapp.cli"
version = "1.0-SNAPSHOT"

// Both models keep their GGUF weights memory-mapped and packed (Q8_0 / Q4_K_M),
// so the heap holds mostly the KV caches (capped at a 4096-token window) and
// FP32 activations. 12 GB is generous headroom; models are loaded one at a
// time unless --keep-models is passed.
val standappJvmArgs = listOf(
    "--enable-preview",
    "--add-modules=jdk.incubator.vector",
    "--enable-native-access=ALL-UNNAMED",
    // Commit messages and summaries are UTF-8 regardless of the shell locale.
    "-Dfile.encoding=UTF-8",
    "-Dstdout.encoding=UTF-8",
    "-Dstderr.encoding=UTF-8",
)

application {
    mainClass.set("de.jug_da.app.cli.MainKt")
    applicationDefaultJvmArgs = standappJvmArgs + listOf("-Xms1g", "-Xmx12g")
}

// Self-contained fat JAR (tens of MB — models are downloaded at first run, not bundled).
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

    // Workaround (same as upstream skainet-cli): shadow's mergeServiceFiles()
    // can drop one of two co-located META-INF/services entries when both
    // skainet-backend-cpu and skainet-backend-native-cpu are on the classpath,
    // leaving the fat JAR without the native kernel provider / view kernel
    // pack. Re-merge both service files from the runtime classpath.
    val runtimeJars = project.configurations.named("runtimeClasspath")
        .map { it.files.filter { f -> f.name.endsWith(".jar") } }
    doLast {
        val jar = archiveFile.get().asFile
        val servicePaths = listOf(
            "META-INF/services/sk.ainet.backend.api.kernel.KernelProvider",
            "META-INF/services/sk.ainet.backend.api.kernel.ViewKernelPack",
        )
        for (servicePath in servicePaths) {
            val entries = linkedSetOf<String>()
            for (cpJar in runtimeJars.get()) {
                ZipFile(cpJar).use { zf ->
                    val zipEntry = zf.getEntry(servicePath) ?: return@use
                    zf.getInputStream(zipEntry).bufferedReader().useLines { lines ->
                        lines.map { it.trim() }
                            .filter { it.isNotEmpty() && !it.startsWith("#") }
                            .forEach { entries.add(it) }
                    }
                }
            }
            if (entries.isEmpty()) continue
            val tmpFile = temporaryDir.resolve(servicePath.substringAfterLast('/') + ".txt")
            tmpFile.writeText(entries.joinToString("\n", postfix = "\n"))
            ant.withGroovyBuilder {
                "zip"("destfile" to jar.absolutePath, "update" to true) {
                    "zipfileset"("file" to tmpFile.absolutePath, "fullpath" to servicePath)
                }
            }
        }
    }
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))

    implementation(project(":llm"))
    implementation(project(":data"))
    implementation(project(":standapp-ai-engine"))

    // DataSourceException for the model-download error path.
    implementation(project.dependencies.platform(libs.skainet.bom))
    implementation(libs.skainet.data.source)

    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.slf4j.simple)
}

tasks.test {
    useJUnitPlatform()
    jvmArgs(standappJvmArgs)
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
    jvmArgs(standappJvmArgs)
    minHeapSize = "1g"
    maxHeapSize = "12g"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

kotlin {
    jvmToolchain(25)
}
