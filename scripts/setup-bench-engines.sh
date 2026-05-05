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

# install_engine NAME REPO_URL CHECK_ARTIFACT_PATH
#
# CHECK_ARTIFACT_PATH is relative to ~/.m2/repository — if present, skip.
install_engine() {
    local name="$1"
    local repo_url="$2"
    local check_artifact="$3"

    if [[ -f "${M2}/${check_artifact}" ]]; then
        echo "✓ ${name} already in ~/.m2 — skipping"
        return 0
    fi

    if [[ ! -d "${EXTERNAL_DIR}/${name}" ]]; then
        echo "→ cloning ${name} into ${EXTERNAL_DIR}/${name}"
        git clone --depth=20 "${repo_url}" "${EXTERNAL_DIR}/${name}"
    else
        echo "→ ${name} clone exists, fetching latest"
        (cd "${EXTERNAL_DIR}/${name}" && git fetch --all --quiet)
    fi

    echo "→ mvn install ${name} (skipping tests; this can take a few minutes)"
    (cd "${EXTERNAL_DIR}/${name}" && mvn install -DskipTests -q)
    echo "✓ ${name} installed"
}

# Deliverance — pure-Java JVM inference. Apache 2.0.
install_engine \
    "deliverance" \
    "https://github.com/edwardcapriolo/deliverance.git" \
    "io/teknek/deliverance/core/0.0.4-SNAPSHOT/core-0.0.4-SNAPSHOT.jar"

# qxotic — alternative JVM inference. (Coordinates filled in once the
# upstream POM publishes; placeholder until then.)
# install_engine \
#     "qxotic" \
#     "https://github.com/qxoticai/qxotic.git" \
#     "ai/qxotic/qxotic-core/0.1.0-SNAPSHOT/qxotic-core-0.1.0-SNAPSHOT.jar"

echo
echo "All benchmark engines ready. Next:"
echo "  ./gradlew :benchmark:jvmRun -Pdeliverance.enabled=true \\"
echo "      -e BENCH_BACKENDS=DELIVERANCE \\"
echo "      -e BENCH_DELIVERANCE_MODEL=TinyLlama/TinyLlama-1.1B-Chat-v1.0"
