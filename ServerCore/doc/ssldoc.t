Configuring Elko servers for SSL support

1. Use Java 17 or later and put the external SelectableTLS library on the
   classpath for both plaintext-only and TLS-enabled servers. The published
   coordinates are com.fudco:selectable-tls:0.1.0. This is a
   separately built dependency, not a package compiled inside Elko.

   For the legacy Makefile/run scripts, place the library at
   Distrib/External/selectable-tls.jar. Remove obsolete ScalableSSL.jar and
   fakessl.jar classpath entries. No proprietary library or fake replacement
   is used by this build. The dependency is available from Maven Central and
   the FUDCo/SelectableTLS GitHub release v0.1.0.
   See Build/SELECTABLE_TLS.md for installation, hashes and make/jdep commands.

2. Configure the server startup script (or Java properties file) to enable and
   configure SSL support generally.  The relevant properties are:

   conf.ssl.enable -- This is a boolean. Set it to true to turn on SSL support.

   conf.ssl.keyfile -- Path to the SSL keystore file (this is the file that
       contains your server's SSL keys and certificates).

   conf.ssl.keypassword -- Password to open the keystore file.

   conf.ssl.keystoretype string -- Type of the SSL keystore.  This defaults to
       "JKS" if unspecified, which is usually what you want, but can be set to
       any keystore type supported by the java.security.KeyStore class.

   conf.ssl.keymanageralgorithm -- Key manager algorithm. This defaults to
      "SunX509" if unspecified, which is also usually what you want, but can be
      set to any value supported by the javax.net.ssl.KeyManagerFactory class.

   Obviously, you will need to actually have the appropriate SSL credentials in
   hand and know all their relevant particulars to set these properties
   appropriately.

   For example, your server startup script might contain the following bits:

      conf.ssl.enable=true \
      conf.ssl.keyfile=ourcerts.jks \
      conf.ssl.keypassword=foobar \


3. Configure the server startup script (or Java properties file) to define any
   HTTPS listener ports you want to use.  Configuring an HTTPS listener is
   essentially identical to configuring an ordinary HTTP listener, except that
   you add:

      conf.listen<N>.secure=true

   where <N> is the listener number.  For example, say you previously had
   defined an HTTP listener on port 80 with the following fragment in your
   server startup file:

      conf.listen1.host=server.example.com:80 \
      conf.listen1.bind=server.example.com:80 \
      conf.listen1.protocol=http \
      conf.listen1.domain=example.com \
      conf.listen1.root=ourgame \

   You might put an additional SSL listener on port 443 by adding:

      conf.listen2.host=server.example.com:443 \
      conf.listen2.bind=server.example.com:443 \
      conf.listen2.protocol=http \
      conf.listen2.domain=example.com \
      conf.listen2.root=ourgame \
      conf.listen2.secure=true \

   Note that the form of all the configuration settings is the same in both
   cases (in particular, note that the protocol is still specfified as "http",
   NOT "https" -- HTTPS is just HTTP over SSL), except for the addition of the
   "secure" setting in the SSL case.

4. SelectableTLS uses the JDK's JSSE implementation and enables TLS 1.2 and
   TLS 1.3 by default. It requires no license key or serial.txt file. TLS
   negotiation proceeds independently of application read/write interests.
   Normal TLS connection close drains accepted output and sends close_notify;
   handshake and output shutdown each have a default 30-second deadline.

   Run the Java integration test with:

      SELECTABLE_TLS_JAR=/path/to/selectable-tls.jar \
          bash Build/test-tls.sh /path/to/commons-codec-1.9.jar

   This generates temporary credentials and checks authenticated TLS alongside
   plaintext RTCP and HTTP messaging, final output delivery and reconnects.
   Existing standalone regression scripts also require SELECTABLE_TLS_JAR.

