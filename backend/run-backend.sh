#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

command -v java >/dev/null 2>&1 || { echo "ERROR: Install JDK 21 first."; exit 1; }
if ! java_output=$(java -version 2>&1); then
  echo "ERROR: Java could not run."; printf '%s\n' "$java_output"; exit 1
fi
if ! printf '%s\n' "$java_output" | grep -Eq 'version "21([.]|\")|openjdk 21([.]|[[:space:]])'; then
  echo "ERROR: JDK 21 is required. Detected:"; printf '%s\n' "$java_output"; exit 1
fi

target_dir="$PWD/target"
[[ -d "$target_dir" ]] || { echo "ERROR: No target directory. Build once with: mvn clean package"; exit 1; }
jar=""
for candidate in "$target_dir"/*.jar; do
  [[ -f "$candidate" ]] || continue
  case "$(basename "$candidate")" in *-sources.jar|*-javadoc.jar|*.original.jar|*.plain.jar) continue ;; esac
  if [[ -z "$jar" || "$candidate" -nt "$jar" ]]; then jar="$candidate"; fi
done
[[ -n "$jar" ]] || { echo "ERROR: No packaged JAR found. Build once with: mvn clean package"; exit 1; }

if [[ -z "${APP_API_TOKEN:-}" || ${#APP_API_TOKEN} -lt 32 ]]; then
  command -v openssl >/dev/null 2>&1 || { echo "ERROR: OpenSSL is needed to generate APP_API_TOKEN."; exit 1; }
  APP_API_TOKEN="$(openssl rand -hex 32)"
  export APP_API_TOKEN
  echo "Generated API token for this run. Enter it in the web/iPhone/Android client:"
  printf '%s\n' "$APP_API_TOKEN"
fi
export APP_BIND_ADDRESS="${APP_BIND_ADDRESS:-127.0.0.1}"
echo "Starting packaged application: $(basename "$jar")"
echo "Live trading is disabled. Backend bind address: $APP_BIND_ADDRESS"
exec java -jar "$jar"
