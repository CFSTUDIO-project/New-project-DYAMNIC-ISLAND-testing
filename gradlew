#!/usr/bin/env sh

set -eu

VERSION="8.13"

BASE="${GRADLE_USER_HOME:-$HOME/.gradle}/wrapper/dists/gradle-${VERSION}-bin/nova-pill"
GRADLE="$BASE/gradle-${VERSION}/bin/gradle"
ARCHIVE="$BASE/gradle-${VERSION}-bin.zip"

# Always use the pinned Gradle version.
# Never use the system-installed Gradle.

if [ ! -x "$GRADLE" ]; then
    echo "Downloading Gradle ${VERSION}..."

    mkdir -p "$BASE"

    if command -v curl >/dev/null 2>&1; then
        curl -fL --retry 3 \
            --connect-timeout 20 \
            "https://services.gradle.org/distributions/gradle-${VERSION}-bin.zip" \
            -o "$ARCHIVE"
    elif command -v wget >/dev/null 2>&1; then
        wget -O "$ARCHIVE" \
            "https://services.gradle.org/distributions/gradle-${VERSION}-bin.zip"
    else
        echo "ERROR: curl or wget is required."
        exit 1
    fi

    if ! command -v unzip >/dev/null 2>&1; then
        echo "ERROR: unzip is required."
        exit 1
    fi

    unzip -q -o "$ARCHIVE" -d "$BASE"
    rm -f "$ARCHIVE"
fi

echo "Using pinned Gradle ${VERSION}"

exec "$GRADLE" "$@"
