#!/bin/bash
# Uses an already-running loopback mongod; the Java test owns a unique disposable DB.
set -euo pipefail
if [[ $# -ne 2 ]]; then
    echo "Usage: bash Build/test-mongo.sh /path/to/dependency-jars mongo-port" >&2
    echo "Jars: commons-codec 1.9; mongodb-driver-legacy, -sync, -core and bson 5.13.0" >&2
    exit 2
fi
root=$(cd "$(dirname "$0")/.." && pwd)
jars=$(cd "$1" && pwd)
working=$(mktemp -d "${TMPDIR:-/tmp}/elko-mongo-test.XXXXXX")
trap 'rm -rf "$working"' EXIT
mkdir "$working/Source" "$working/Classes"
cp -R "$root/ServerCore/java/." "$working/Source/"
version="$working/Source/org/elkoserver/foundation/server/BuildVersion.java"
sed 's/@BuildVersion@/MongoDB regression/g' "$version.tmpl" > "$version"
classpath="$jars/commons-codec-1.9.jar"
for artifact in mongodb-driver-legacy mongodb-driver-sync mongodb-driver-core bson; do
    test -f "$jars/$artifact-5.13.0.jar"
    classpath="$classpath:$jars/$artifact-5.13.0.jar"
done
if ! javac -source 8 -target 8 -d "$working/Classes" -cp "$classpath" \
    -sourcepath "$working/Source" \
    "$working/Source/org/elkoserver/objdb/store/mongostore/test/MongoObjectStoreTest.java" \
    "$working/Source/org/elkoserver/util/trace/logeater/LogEater.java" \
    > "$working/compiler.log" 2>&1; then
    cat "$working/compiler.log" >&2
    exit 1
fi
java -cp "$working/Classes:$classpath" \
    org.elkoserver.objdb.store.mongostore.test.MongoObjectStoreTest "$2"
