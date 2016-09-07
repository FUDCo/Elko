Configuring Elko servers for SSL support

1. Make sure the SSL library .jar is on the classpath.  There are two versions
   of this:

        ScalableSSL.jar -- you obtain this under license from Melbourne
            Software.  See:
                http://www.telekinesis.com.au/wipv3_6/page2/show.jsp?id=230173&db=Entries
            This library provides full support for SSL in the Elko server.

        fakessl.jar -- this is built in the Elko distribution.  This is a
            development and testing stub library. It provides no actual SSL
            support but allows the SSL-enabled server to load and run without
            unresolved class load errors as long as you don't actually
            configure any SSL listeners (if you do configure an SSL listener,
            you'll just get an error message in the server log).

   You can include both of these .jars on your classpath, but if you do, be
   sure that ScalableSSL.jar is earlier in the classpath than fakessl.jar.  If
   you do this, you can, for example, use a single script to set up your
   environment variables for both development and production even though only
   the production environment actually has a ScalableSSL.jar file installed.

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

4. When running with ScalableSSL, you will need to have your license key for
   the library, which you get from Melbourne Software.  This lives in a file
   called "serial.txt" in the server's run directory.  You may also wish to
   have a ScalableSSL_Logging.properties properties file (in the same
   directory) to configure the library's own internal logging -- mostly to shut
   it up.  A couple of examples of the latter property file (one to be
   maximally verbose and the other to be silent) are included in the Elko
   distribution.

