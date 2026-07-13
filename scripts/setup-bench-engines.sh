#!/usr/bin/env bash
# Install benchmark-only LLM engines into the local Maven repo (~/.m2/).
#
# Runs once per developer machine. Idempotent — skips engines whose
# artifacts are already present in ~/.m2/repository.
#
# Engines installed:
#   - Deliverance (https://github.com/edwardcapriolo/deliverance)
#   - qxotic      (https://github.com/qxoticai/qxotic)
#
# After running this, the benchmark module can be built with:
#   ./gradlew :benchmark:jvmRun -Pdeliverance.enabled=true -Pqxotic.enabled=true
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
EXTERNAL_DIR="${REPO_ROOT}/external"
M2="${HOME}/.m2/repository"

mkdir -p "${EXTERNAL_DIR}"

# install_engine NAME REPO_URL CHECK_ARTIFACT_PATH [MVN_EXTRA_ARGS...]
#
# CHECK_ARTIFACT_PATH is relative to ~/.m2/repository — if present, skip.
# MVN_EXTRA_ARGS are passed to `mvn install` after the default flags.
install_engine() {
    local name="$1"
    local repo_url="$2"
    local check_artifact="$3"
    shift 3
    local extra_args=("$@")

    if [[ -f "${M2}/${check_artifact}" ]]; then
        echo "✓ ${name} already in ~/.m2 — skipping"
        return 0
    fi

    if [[ ! -d "${EXTERNAL_DIR}/${name}" ]]; then
        echo "→ cloning ${name} into ${EXTERNAL_DIR}/${name}"
        git clone --depth=20 "${repo_url}" "${EXTERNAL_DIR}/${name}"
    else
        echo "→ ${name} clone exists, updating to latest upstream"
        (cd "${EXTERNAL_DIR}/${name}" && \
            git fetch --depth=20 origin --quiet && \
            git reset --hard "origin/$(git rev-parse --abbrev-ref origin/HEAD | cut -d/ -f2)" --quiet)
    fi

    echo "→ mvn install ${name} (skipping test execution; this can take a few minutes)"
    # -DskipTests skips standard surefire execution but still compiles tests
    # and builds *-tests.jar artifacts that sibling modules need.
    # qxotic's toknroll-core uses a custom skip property
    # (${toknroll.skipDependentTests}) that ignores -DskipTests, so we set
    # both. -fae ("fail at end") tolerates lingering per-module test failures.
    (cd "${EXTERNAL_DIR}/${name}" && \
        mvn install \
            -DskipTests=true \
            -Dtoknroll.skipDependentTests=true \
            -fae \
            "${extra_args[@]}" \
            -q)
    if [[ ! -f "${M2}/${check_artifact}" ]]; then
        echo "✗ ${name}: expected artifact ${check_artifact} missing after install."
        echo "  The upstream version has probably changed — align the version in"
        echo "  this script AND in gradle/libs.versions.toml with the clone's pom:"
        echo "    grep -m1 '<version>' ${EXTERNAL_DIR}/${name}/pom.xml"
        return 1
    fi
    echo "✓ ${name} installed"
}

# Deliverance builds with maven.compiler.release=25 — refuse early with a
# clear message instead of failing halfway through the reactor.
JAVA_MAJOR="$(java -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -1)"
if (( JAVA_MAJOR < 25 )); then
    echo "✗ JDK 25+ required to build Deliverance (java on PATH is ${JAVA_MAJOR})."
    echo "  Point JAVA_HOME/PATH at a JDK 25 before running this script."
    exit 1
fi

# Deliverance — pure-Java JVM inference. Apache 2.0.
# DELIVERANCE_VERSION must match `deliverance` in gradle/libs.versions.toml;
# the post-install artifact check above trips loudly when upstream bumps it.
# Only core+safetensors (and their -am dependencies math/tensor) are needed —
# skips the unrelated web/sketches/plugin modules of the reactor.
DELIVERANCE_VERSION="0.0.11-SNAPSHOT"
install_engine \
    "deliverance" \
    "https://github.com/edwardcapriolo/deliverance.git" \
    "io/teknek/deliverance/core/${DELIVERANCE_VERSION}/core-${DELIVERANCE_VERSION}.jar" \
    -pl core,safetensors -am

# qxotic — JVM-native LLM inference toolkit. Apache 2.0.
# Different shape from Deliverance: qxotic's "library" surface (jota, gguf,
# toknroll-*) is low-level tensor algebra. The runnable Llama 3.2 Q8_0 driver
# lives in the `examples` module as a CLI with main() — Llama32CliQ8_0.java.
# Our QxoticLLMService wraps that CLI as a subprocess (one process per
# generate() call). We capture the runtime classpath here so the wrapper does
# not need its own Maven invocation per call.
# -Dnative.skip.build=true skips the cmake/Metal/CUDA native backend builds
# (the metal one needs the Xcode Metal toolchain); the benchmark only uses the
# CPU Q8_0 path, which is pure JVM. -Dnative.skip.tests=true is needed too:
# the native-backend modules gate surefire on their own property, which
# ignores -DskipTests, and their tests require the just-skipped native libs.
install_engine \
    "qxotic" \
    "https://github.com/qxoticai/qxotic.git" \
    "com/qxotic/examples/0.1.0/examples-0.1.0.jar" \
    -pl examples -am -Dnative.skip.build=true -Dnative.skip.tests=true

# Capture the qxotic examples-module runtime classpath so the subprocess
# wrapper can `java -cp $(cat)` without re-resolving every call. The dep
# plugin lists transitive deps of `examples`; we append the examples jar
# itself manually (the plugin doesn't include the project's own output).
QXOTIC_CP_FILE="${EXTERNAL_DIR}/qxotic-classpath.txt"
QXOTIC_EXAMPLES_JAR="${M2}/com/qxotic/examples/0.1.0/examples-0.1.0.jar"
if [[ -d "${EXTERNAL_DIR}/qxotic" && -f "${QXOTIC_EXAMPLES_JAR}" ]]; then
    if [[ ! -f "${QXOTIC_CP_FILE}" ]]; then
        echo "→ generating qxotic classpath file at ${QXOTIC_CP_FILE}"
        (cd "${EXTERNAL_DIR}/qxotic" && \
            mvn -pl examples -am -q \
                -DincludeScope=runtime \
                -Dmdep.outputFile="${QXOTIC_CP_FILE}" \
                dependency:build-classpath)
        # Append the examples jar (dep plugin omits the project's own output).
        printf ':%s' "${QXOTIC_EXAMPLES_JAR}" >> "${QXOTIC_CP_FILE}"
        echo "✓ qxotic classpath captured ($(wc -c < "${QXOTIC_CP_FILE}") bytes)"
    else
        echo "✓ qxotic classpath file already present"
    fi
fi

echo
echo "All benchmark engines ready. Next:"
echo
echo "  # Deliverance (HuggingFace owner/name; auto-downloads the model)"
echo "  ./gradlew :benchmark:jvmRun -Pdeliverance.enabled=true \\"
echo "      -e BENCH_BACKENDS=DELIVERANCE \\"
echo "      -e BENCH_DELIVERANCE_MODEL=TinyLlama/TinyLlama-1.1B-Chat-v1.0"
echo
echo "  # qxotic (path to a local Llama-3.2 Q8_0 GGUF; reuses the one shipped in :llm)"
echo "  ./gradlew :benchmark:jvmRun -Pqxotic.enabled=true \\"
echo "      -e BENCH_BACKENDS=QXOTIC \\"
echo "      -e BENCH_QXOTIC_MODEL=\$HOME/.cache/standapp/models/Llama-3.2-1B-Instruct-Q8_0.gguf"
