#!/bin/bash
# Requires a JDK and the existing Commons Codec dependency; no MongoDB.
# Optional second argument selects another checkout for before/after testing.
set -euo pipefail
if [[ $# -lt 1 || $# -gt 2 ]]; then
    echo "Usage: bash Build/test-repository.sh /path/to/commons-codec-1.9.jar [source-checkout]" >&2
    exit 2
fi
test_root=$(cd "$(dirname "$0")/.." && pwd)
source_root=${2:-$test_root}
codec=$(cd "$(dirname "$1")" && pwd)/$(basename "$1")
test -f "$codec"
working=$(mktemp -d "${TMPDIR:-/tmp}/elko-repository-test.XXXXXX")
trap 'rm -rf "$working"' EXIT
mkdir "$working/Source" "$working/Classes" "$working/Data"
cp -R "$source_root/ServerCore/java/." "$working/Source/"
version="$working/Source/org/elkoserver/foundation/server/BuildVersion.java"
sed 's/@BuildVersion@/Repository initialization regression/g' "$version.tmpl" > "$version"
if ! javac -d "$working/Classes" -cp "$codec" -sourcepath "$working/Source" \
    "$working/Source/org/elkoserver/foundation/boot/Boot.java" \
    "$working/Source/org/elkoserver/server/repository/RepositoryBoot.java" \
    "$working/Source/org/elkoserver/objdb/store/filestore/FileObjectStore.java" \
    "$test_root/ServerCore/java/org/elkoserver/server/repository/test/RepositoryTest.java" \
    > "$working/compiler.log" 2>&1; then
    cat "$working/compiler.log" >&2
    exit 1
fi
java -cp "$working/Classes:$codec" \
    org.elkoserver.server.repository.test.RepositoryTest "$working"
