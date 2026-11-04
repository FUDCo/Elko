package org.elkoserver.foundation.net.test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.KeyStore;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;
import org.elkoserver.foundation.actor.JSONHTTPFramer;
import org.elkoserver.foundation.boot.BootProperties;
import org.elkoserver.foundation.net.Connection;
import org.elkoserver.foundation.net.MessageHandler;
import org.elkoserver.foundation.net.MessageHandlerFactory;
import org.elkoserver.foundation.net.NetworkManager;
import org.elkoserver.foundation.run.Runner;
import org.elkoserver.json.EncodeControl;
import org.elkoserver.json.JSONObject;
import org.elkoserver.util.trace.Trace;

/** Exercises actual Elko framing over plaintext and authenticated TLS. */
public final class TlsTest {
    private TlsTest()
    {
    }

    /** Generate a temporary server identity and a client that trusts it. */
      private static SSLContext
    credentials(Path directory, String password) throws Exception
    {
        Path store = directory.resolve("identity.p12");
        Process keytool = new ProcessBuilder(
            Path.of(System.getProperty("java.home"), "bin/keytool").toString(),
            "-genkeypair", "-alias", "test", "-keyalg", "EC",
            "-groupname", "secp256r1", "-dname", "CN=localhost",
            "-ext", "SAN=dns:localhost,ip:127.0.0.1", "-validity", "2",
            "-storetype", "PKCS12", "-keystore", store.toString(),
            "-storepass", password, "-noprompt")
            .redirectErrorStream(true)
            .redirectOutput(directory.resolve("keytool.log").toFile()).start();
        if (!keytool.waitFor(30, TimeUnit.SECONDS)) {
            keytool.destroyForcibly();
            throw new AssertionError("keytool timeout");
        }
        require(keytool.exitValue() == 0,
            "keytool failure: " +
            Files.readString(directory.resolve("keytool.log")));
        KeyStore keys = KeyStore.getInstance("PKCS12");
        try (InputStream input = Files.newInputStream(store)) {
            keys.load(input, password.toCharArray());
        }
        TrustManagerFactory trust = TrustManagerFactory.getInstance(
            TrustManagerFactory.getDefaultAlgorithm());
        trust.init(keys);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, trust.getTrustManagers(), null);
        return context;
    }

    /** Exchange application messages through an HTTP messaging session. */
      private static void
    http(int port, SSLContext context, boolean secure) throws Exception
    {
        HttpClient client = HttpClient.newBuilder().sslContext(context)
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5)).build();
        String base = (secure ? "https" : "http") + "://localhost:" + port +
            "/messages/";
        String session = request(client, base + "connect", null)
            .getString("sessionid");
        JSONObject ack = request(client, base + "xmit/" + session + "/1",
            "{\"to\":\"test\",\"op\":\"echo\",\"n\":17}");
        require(ack.getProperty("error") == null, "HTTP xmit acknowledged");
        JSONObject selected =
            request(client, base + "select/" + session + "/1", null);
        require(selected.getArray("msgs").getObject(0).getInt("n") == 17,
                "HTTP message echo");
        request(client, base + "disconnect/" + session, null);
    }

    /** Run the regression, or act as its child server. */
      public static void
    main(String[] args) throws Exception
    {
        Path directory = Path.of(args[0]).toAbsolutePath();
        if (args.length == 3 && args[1].equals("serve")) {
            serve(directory, args[2]);
            return;
        }
        String password = UUID.randomUUID().toString();
        SSLContext context = credentials(directory, password);
        Path log = directory.resolve("server.log");
        Process child = new ProcessBuilder(
            Path.of(System.getProperty("java.home"), "bin/java").toString(),
            "-cp", System.getProperty("java.class.path"),
            TlsTest.class.getName(), directory.toString(), "serve", password)
            .directory(directory.toFile()).redirectErrorStream(true)
            .redirectOutput(log.toFile()).start();
        boolean passed = false;
        try {
            Path portsFile = directory.resolve("ports");
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (!Files.exists(portsFile)) {
                require(child.isAlive() && System.nanoTime() < deadline,
                        "Elko listener startup timed out");
                Thread.sleep(20);
            }
            String[] ports = Files.readString(portsFile).trim().split(" ");
            int tcp = Integer.parseInt(ports[0]);
            int tls = Integer.parseInt(ports[1]);
            rtcp(tcp, null);
            rtcp(tls, context);
            boolean rejected = false;
            try (Socket invalid = socket(tls, context, "wrong.invalid")) {
                require(invalid.isConnected(), "connected peer");
            } catch (SSLException expected) {
                rejected = true;
            }
            require(rejected,
                    "hostname verification must reject the wrong identity");
            http(Integer.parseInt(ports[2]), context, false);
            http(Integer.parseInt(ports[3]), context, true);
            resume(tcp, null);
            resume(tls, context);
            /* New connections after failure and closure. */
            rtcp(tls, context);
            rtcp(tcp, null);
            passed = true;
            System.out.println("PASS: mixed plaintext/TLS RTCP, " +
                "large ordered messages, final-message drain, " +
                "identity rejection, " +
                "HTTP/HTTPS messaging, reconnect and session resumption");
        } finally {
            child.destroy();
            if (!child.waitFor(5, TimeUnit.SECONDS)) {
                child.destroyForcibly();
                child.waitFor();
            }
            if (!passed) {
                System.err.write(Files.readAllBytes(log));
            }
        }
    }

    /** Read an application reply, skipping RTCP framing and acknowledgments. */
      private static JSONObject
    reply(BufferedReader input) throws Exception
    {
        for (;;) {
            String line = input.readLine();
            require(line != null, "RTCP reply lost");
            if (line.startsWith("{")) {
                return JSONObject.parse(line);
            }
            require(line.isBlank() || line.startsWith("ack ") ||
                    line.matches("[0-9]+ [0-9]+"),
                    "unexpected RTCP frame: " + line);
        }
    }

    /** Send one HTTP messaging operation and decode its response. */
      private static JSONObject
    request(HttpClient client, String url, String body) throws Exception
    {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(5));
        if (body != null) {
            builder.header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        }
        HttpResponse<String> response =
            client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        require(response.statusCode() == 200,
            "HTTP status " + response.statusCode() + ": " + response.body());
        return JSONObject.parse(response.body());
    }

    /** Fail the regression when an expected result is absent. */
      private static void
    require(boolean value, String detail)
    {
        if (!value) {
            throw new AssertionError(detail);
        }
    }

    /** Resume an RTCP session over a replacement transport connection. */
      private static void
    resume(int port, SSLContext context) throws Exception
    {
        String session;
        try (Socket initial = socket(port, context, "localhost")) {
            Writer output = new OutputStreamWriter(initial.getOutputStream(),
                                                   StandardCharsets.UTF_8);
            BufferedReader input = new BufferedReader(new InputStreamReader(
                initial.getInputStream(), StandardCharsets.UTF_8));
            output.write("start\n");
            output.flush();
            String started = input.readLine();
            require(started.startsWith("start "), "resumable session start");
            session = started.substring(6).trim();
            output.write("1 0\n{\"to\":\"test\",\"op\":\"echo\",\"n\":11}\n\n");
            output.flush();
            require(reply(input).getInt("n") == 11,
                    "message before connection loss");
        }
        try (Socket resumed = socket(port, context, "localhost")) {
            Writer output = new OutputStreamWriter(resumed.getOutputStream(),
                                                   StandardCharsets.UTF_8);
            BufferedReader input = new BufferedReader(new InputStreamReader(
                resumed.getInputStream(), StandardCharsets.UTF_8));
            output.write("resume " + session + " 1\n");
            output.flush();
            require(input.readLine().equals("resume " + session + " 1"),
                    "RTCP session resumed with previous sequence");
            output.write("2 1\n{\"to\":\"test\",\"op\":\"echo\",\"n\":22}\n\n");
            output.flush();
            require(reply(input).getInt("n") == 22,
                    "message after connection resumption");
            output.write("end\n");
            output.flush();
        }
    }

    /** Check ordered large messages and the final message before TLS close. */
      private static void
    rtcp(int port, SSLContext context) throws Exception
    {
        try (Socket socket = socket(port, context, "localhost")) {
            Writer output = new OutputStreamWriter(socket.getOutputStream(),
                                                   StandardCharsets.UTF_8);
            BufferedReader input = new BufferedReader(new InputStreamReader(
                socket.getInputStream(), StandardCharsets.UTF_8));
            output.write("start\n");
            output.flush();
            require(input.readLine().startsWith("start "),
                    "RTCP session start");
            String payload = "x".repeat(60000);
            /* Multiple payloads larger than Elko's 2 KiB read buffer. */
            for (int i = 0; i < 3; i++) {
                output.write((i + 1) + " 0\n{\"to\":\"test\",\"op\":\"echo\"," +
                    "\"n\":" + i + ",\"payload\":\"" + payload + "\"}\n\n");
            }
            output.flush();
            for (int i = 0; i < 3; i++) {
                JSONObject echoed = reply(input);
                require(echoed.getInt("n") == i &&
                        echoed.getString("payload").equals(payload),
                        "ordered RTCP payload");
            }
            if (context != null) {
                output.write("4 3\n{\"to\":\"test\",\"op\":\"finish\"," +
                             "\"payload\":\"" + payload + "\"}\n\n");
                output.flush();
                require(reply(input).getString("payload").equals(payload),
                        "final TLS reply survives queued close");
                String line;
                while ((line = input.readLine()) != null) {
                    require(line.isBlank() || line.equals("end") ||
                            line.startsWith("ack "), "unexpected final frame");
                }
            }
        }
    }

    /** Serve plaintext and TLS RTCP/HTTP through the same NetworkManager. */
      private static void
    serve(Path directory, String password) throws Exception
    {
        Runner runner = new Runner("TLS regression server");
        runner.now(() -> {
            BootProperties props = new BootProperties();
            props.setProperty("conf.ssl.enable", "true");
            props.setProperty("conf.ssl.keystoretype", "PKCS12");
            props.setProperty("conf.ssl.keyfile",
                              directory.resolve("identity.p12").toString());
            props.setProperty("conf.ssl.keypassword", password);
            props.setProperty("conf.comm.rtcpbacklog", "1048576");
            NetworkManager network = new NetworkManager(change -> {}, props,
                                                        time -> {}, runner);
            MessageHandlerFactory factory = connection -> new MessageHandler() {
                  public void
                connectionDied(Connection conn, Throwable reason)
                {
                }

                  public void
                processMessage(Connection conn, Object message)
                {
                    JSONObject request = (JSONObject) message;
                    conn.sendMsg(request.literal(EncodeControl.forClient));
                    if ("finish".equals(request.getProperty("op"))) {
                        conn.close();
                    }
                }
            };
            int tcp = network.listenRTCP("127.0.0.1:0", factory,
                                         Trace.comm, false).getPort();
            int tls = network.listenRTCP("127.0.0.1:0", factory,
                                         Trace.comm, true).getPort();
            int http = network.listenHTTP("127.0.0.1:0", factory, "messages",
                new JSONHTTPFramer(Trace.comm), false, Trace.comm).getPort();
            int https = network.listenHTTP("127.0.0.1:0", factory, "messages",
                new JSONHTTPFramer(Trace.comm), true, Trace.comm).getPort();
            /* Publish readiness after the complete port list is visible. */
            Files.writeString(directory.resolve("ports.tmp"),
                              tcp + " " + tls + " " + http + " " + https);
            Files.move(directory.resolve("ports.tmp"),
                       directory.resolve("ports"),
                       StandardCopyOption.ATOMIC_MOVE);
            return null;
        });
    }

    /** Open a plain connection or a TLS connection with identity checking. */
      private static Socket
    socket(int port, SSLContext context, String peer) throws Exception
    {
        Socket transport = new Socket();
        transport.connect(new InetSocketAddress("127.0.0.1", port), 5000);
        transport.setSoTimeout(5000);
        if (context == null) {
            return transport;
        }
        SSLSocket socket = (SSLSocket) context.getSocketFactory()
            .createSocket(transport, peer, port, true);
        SSLParameters params = socket.getSSLParameters();
        params.setEndpointIdentificationAlgorithm("HTTPS");
        socket.setSSLParameters(params);
        try {
            socket.startHandshake();
            return socket;
        } catch (Exception failure) {
            socket.close();
            throw failure;
        }
    }
}
