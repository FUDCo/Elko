#!/bin/bash
# Real Elko RTCP/HTTP networking, with JSSE peers and generated test credentials.
set -euo pipefail
if [[ $# -ne 1 ]]; then
    echo 'Usage: SELECTABLE_TLS_JAR=/path/to/selectable-tls.jar bash Build/test-tls.sh /path/to/commons-codec-1.9.jar' >&2
    exit 2
fi
root=$(cd "$(dirname "$0")/.." && pwd)
codec=$(cd "$(dirname "$1")" && pwd)/$(basename "$1")
tls_jar=${SELECTABLE_TLS_JAR:?Set SELECTABLE_TLS_JAR to the external SelectableTLS jar}
test -f "$codec"
test -f "$tls_jar"
working=$(mktemp -d "${TMPDIR:-/tmp}/elko-tls-test.XXXXXX")
trap 'rm -rf "$working"' EXIT
mkdir "$working/Classes"
if ! javac --release 17 -d "$working/Classes" -cp "$codec:$tls_jar" \
    -sourcepath "$root/ServerCore/java" \
    "$root/ServerCore/java/org/elkoserver/foundation/net/test/TlsTest.java" \
    > "$working/compiler.log" 2>&1; then
    cat "$working/compiler.log" >&2
    exit 1
fi
java -cp "$working/Classes:$codec:$tls_jar" \
    org.elkoserver.foundation.net.test.TlsTest "$working"
