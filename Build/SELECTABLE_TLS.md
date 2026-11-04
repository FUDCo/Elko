# SelectableTLS networking dependency

Elko uses the external MIT-licensed `com.fudco:selectable-tls:0.1.0`
release, available from [Maven Central](https://repo.maven.apache.org/maven2/com/fudco/selectable-tls/0.1.0/)
and [GitHub](https://github.com/FUDCo/SelectableTLS/releases/tag/v0.1.0).
Its runtime JAR SHA-256 is
`a1a4f9e8f3cc17c373142a37c7b0b26a3726ac81bb67ee568303ba892b29d80f`.

## Make/jdep build

Use Java 17 or newer, GNU make, and jdep revision
`7b940380b090159b854d69a2a8ba6a8ae9226c84` (or compatible later revision).
Put both `jdep` and its companion `touchp` on PATH. From the checkout root,
download and verify the runtime dependency:

```sh
mkdir -p Distrib/External
curl -fL https://repo.maven.apache.org/maven2/com/fudco/selectable-tls/0.1.0/selectable-tls-0.1.0.jar \
    -o Distrib/External/selectable-tls.jar
printf '%s  %s\n' \
    a1a4f9e8f3cc17c373142a37c7b0b26a3726ac81bb67ee568303ba892b29d80f \
    Distrib/External/selectable-tls.jar | shasum -a 256 -c -
```

The fixed local filename is used by the make and launcher classpaths; retain
the version and checksum when distributing it with an installation.

The external dependency directory must also contain:

- `commons-codec.jar`: Commons Codec 1.9.
- `mongodb-driver-legacy-5.13.0.jar`.
- `mongodb-driver-sync-5.13.0.jar`.
- `mongodb-driver-core-5.13.0.jar`.
- `bson-5.13.0.jar`.

The MongoDB artifacts are the same dependency set already declared by pom.xml;
the old single `mongo.jar` classpath is insufficient for that driver.

```sh
make -C ServerCore/build
mkdir -p Distrib/Internal
cp ServerCore/built/bin/elkoserver.jar Distrib/Internal/
```

`Run/common/setenvvars` now uses this dependency set and the supported
`java -server` invocation. The historical `-Xconcurrentio` flag is rejected by
current JDKs. Site-specific `JAVA_RUN` overrides remain available.

No ScalableSSL runtime, serial key or fake scalablessl package is needed. Remove
their former classpath entries. TLS uses standard JSSE key and trust configuration;
see `ServerCore/doc/ssldoc.t` for listener and certificate properties. Keep private
keys outside the source/dependency distribution. Plaintext and TLS listeners
share the same provider and normal message-dispatch architecture.

## Optional Maven build

Run `mvn clean package` from the checkout root. Maven resolves the published
SelectableTLS dependency and the other declared libraries from Maven Central.
No local snapshot or manual Maven installation is required.

## Regression checks

```sh
export SELECTABLE_TLS_JAR="$PWD/Distrib/External/selectable-tls.jar"
bash Build/test-tls.sh "$PWD/Distrib/External/commons-codec.jar"
bash Build/test-repository.sh "$PWD/Distrib/External/commons-codec.jar"
bash Build/test-presence.sh "$PWD/Distrib/External/commons-codec.jar"
bash Build/test-gatekeeper.sh "$PWD/Distrib/External/commons-codec.jar"
```

The TLS fixture checks plaintext/TLS RTCP, large ordered messages, final-message
drain, wrong-host rejection, HTTP/HTTPS, reconnect and session resumption using
real listeners and generated test credentials. The service tests cover storage,
provider loss/reassignment and reservation/reconnect behavior. They do not
establish an application-capacity limit or qualify an entirely encrypted farm.
