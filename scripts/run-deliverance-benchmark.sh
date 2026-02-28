#!/usr/bin/env bash
set -euo pipefail

DELIVERANCE_DIR="${DELIVERANCE_DIR:-$(cd "$(dirname "$0")/../../deliverance" && pwd)}"
STANDAPP_DIR="${STANDAPP_DIR:-$(cd "$(dirname "$0")/.." && pwd)}"
JAVA_HI="${JAVA_HI:-25}"          # jenv name for Deliverance JDK
JAVA_LO="${JAVA_LO:-21}"          # jenv name for Daily-StandAPP JDK
PORT="${PORT:-8080}"
MODEL="${MODEL:-TinyLlama-1.1B-Chat-v1.0-Jlama-Q4}"
RUNS="${RUNS:-3}"

eval "$(jenv init -)"

# ---- 1. Build Deliverance ----
echo ">>> Building Deliverance (Java $JAVA_HI)"
cd "$DELIVERANCE_DIR"
jenv local "$JAVA_HI"
JAVA_HOME=$(jenv prefix) mvn install -DskipTests=true -Dmaven.test.skip=true -Dgpg.skip=true -pl \!vibrant-maven-plugin -q

# ---- 2. Start Deliverance server ----
echo ">>> Starting Deliverance on :$PORT"
JAVA_HOME=$(jenv prefix) \
java -XX:-UseCompactObjectHeaders \
     --add-opens java.base/java.nio=ALL-UNNAMED \
     --add-modules jdk.incubator.vector \
     -Ddeliverance.tensor.operations.type=jvector \
     -Dserver.port="$PORT" -Xmx3G \
     -jar web/target/web-0.0.4-SNAPSHOT.jar &
SRV_PID=$!
trap "kill $SRV_PID 2>/dev/null; wait $SRV_PID 2>/dev/null" EXIT

echo "Waiting for server..."
for i in $(seq 1 90); do
  curl -sf "http://localhost:$PORT/chat/completions" \
    -X POST -H "Content-Type: application/json" \
    -d "{\"model\":\"$MODEL\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}" \
    >/dev/null 2>&1 && break
  [ "$i" -eq 90 ] && { echo "Server failed to start"; exit 1; }
  sleep 2
done
echo "Deliverance ready (PID $SRV_PID)"

# ---- 3. Run benchmark ----
echo ">>> Running benchmark (Java $JAVA_LO)"
cd "$STANDAPP_DIR"
jenv local "$JAVA_LO"

BENCH_DELIVERANCE_URL="http://localhost:$PORT" \
BENCH_DELIVERANCE_MODEL="$MODEL" \
BENCH_RUNS="$RUNS" \
BENCH_BACKENDS="DELIVERANCE" \
./gradlew :benchmark:jvmRun

echo ">>> Done. Results in benchmark-results/"
