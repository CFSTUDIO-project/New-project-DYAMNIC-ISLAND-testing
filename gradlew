#!/usr/bin/env sh
# NOVA PILL Gradle bootstrap. Uses an installed Gradle when present; otherwise
# downloads the pinned distribution. This bootstrap avoids committing a wrapper
# JAR that could not be retrieved in the offline generation environment.
set -eu
VERSION="8.13"
BASE="${GRADLE_USER_HOME:-$HOME/.gradle}/wrapper/dists/gradle-${VERSION}-bin/nova-pill"
GRADLE="$BASE/gradle-${VERSION}/bin/gradle"
if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi
if [ ! -x "$GRADLE" ]; then
  mkdir -p "$BASE"
  ARCHIVE="$BASE/gradle-${VERSION}-bin.zip"
  if command -v curl >/dev/null 2>&1; then
    curl -fL --retry 3 --connect-timeout 20 "https://services.gradle.org/distributions/gradle-${VERSION}-bin.zip" -o "$ARCHIVE"
  elif command -v wget >/dev/null 2>&1; then
    wget -O "$ARCHIVE" "https://services.gradle.org/distributions/gradle-${VERSION}-bin.zip"
  else
    echo "Gradle 8.13 is missing and neither curl nor wget is installed." >&2
    exit 1
  fi
  if command -v unzip >/dev/null 2>&1; then
    unzip -q -o "$ARCHIVE" -d "$BASE"
  else
    echo "unzip is required to extract Gradle 8.13." >&2
    exit 1
  fi
  rm -f "$ARCHIVE"
fi
exec "$GRADLE" "$@"
