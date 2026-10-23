package org.elkoserver.server.gatekeeper.test;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import org.elkoserver.json.JSONObject;

/** Actual Gatekeeper and controlled TCP Director peer; no external services. */
public class GatekeeperTest {
    private static class Peer implements AutoCloseable {
        final Socket socket;
        final BufferedReader input;
        final Writer output;
        Peer(Socket connection) throws Exception {
            socket = connection;
            socket.setSoTimeout(5000);
            input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            output = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
        }
        void send(String message) throws IOException {
            output.write(message + "\r\n\r\n");
            output.flush();
        }
        JSONObject receive(String op) throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (System.nanoTime() < deadline) {
                String line = input.readLine();
                require(line != null, "Unexpected peer disconnect");
                if (line.trim().isEmpty()) continue;
                JSONObject row = JSONObject.parse(line);
                require(!"debug".equals(row.getProperty("op")), line);
                if (op.equals(row.getProperty("op"))) return row;
            }
            throw new AssertionError("Missing " + op);
        }
        public void close() throws IOException { socket.close(); }
    }
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static Peer connect(Process server, int port) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (true) {
            Socket socket = new Socket();
            try {
                socket.connect(new InetSocketAddress("127.0.0.1", port), 1000);
                return new Peer(socket);
            } catch (IOException e) {
                socket.close();
                if (!server.isAlive() || System.nanoTime() >= deadline) throw e;
                Thread.sleep(20);
            }
        }
    }
    private static void reserve(Peer client) throws IOException {
        client.send("{\"to\":\"gatekeeper\",\"op\":\"reserve\",\"protocol\":\"tcp\",\"context\":\"entry\"}");
    }
    private static void exercise(Process server, int port, ServerSocket listener) throws Exception {
        try (Peer client = connect(server, port); Peer first = new Peer(listener.accept())) {
            first.receive("auth");
            reserve(client);
            first.receive("reserve"); // Leave this request outstanding across loss.
            first.close();
            require("director connection lost".equals(client.receive("reserve").getProperty("deny")),
                    "Pending reservation did not fail on disconnect");
            for (int iteration = 0; iteration < 2; iteration++) {
                try (Peer replacement = new Peer(listener.accept())) {
                    replacement.receive("auth");
                    reserve(client);
                    JSONObject request = replacement.receive("reserve");
                    require("room".equals(request.getProperty("context")), "Place mapping changed");
                    replacement.send("{\"to\":\"director\",\"op\":\"reserve\",\"context\":\"room\",\"hostport\":\"127.0.0.1:12345\",\"reservation\":\"restored\"}");
                    JSONObject reply = client.receive("reserve");
                    require("restored".equals(reply.getProperty("auth")), "Reservation not restored after reconnect");
                }
            }
        }
    }
    public static void main(String[] args) throws Exception {
        Path working = Paths.get(args[0]).toAbsolutePath();
        Files.write(working.resolve("Data/p-entry.json"),
            "{\"type\":\"place\",\"name\":\"entry\",\"context\":\"room\"}".getBytes(StandardCharsets.UTF_8));
        int port;
        try (ServerSocket allocation = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) { port = allocation.getLocalPort(); }
        Path log = working.resolve("server.log");
        try (ServerSocket listener = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))) {
            listener.setSoTimeout(5000);
            Process server = new ProcessBuilder(new File(System.getProperty("java.home"), "bin/java").getPath(),
                "-cp", System.getProperty("java.class.path"), "org.elkoserver.foundation.boot.Boot",
                "tracelog_name=-", "trace_default=error", "conf.listen.protocol=tcp",
                "conf.listen.host=127.0.0.1:" + port, "conf.listen.bind=127.0.0.1:" + port,
                "conf.listen.allow=any", "conf.listen.auth.mode=open", "conf.comm.jsonstrictness=true",
                "conf.msgdiagnostics=true", "conf.gatekeeper.odb=" + working.resolve("Data"),
                "conf.gatekeeper.anonymous=true", "conf.gatekeeper.director.retry=1",
                "conf.gatekeeper.director.host=127.0.0.1:" + listener.getLocalPort(),
                "conf.gatekeeper.director.auth.mode=open", "org.elkoserver.server.gatekeeper.GatekeeperBoot")
                .directory(working.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
            boolean passed = false;
            try {
                exercise(server, port, listener);
                passed = true;
                System.out.println("PASS: pending reservation failure and repeated Director reconnect with fresh reservations");
            } finally {
                server.destroy();
                if (!server.waitFor(5, TimeUnit.SECONDS)) { server.destroyForcibly(); server.waitFor(); }
                if (!passed) System.err.write(Files.readAllBytes(log));
            }
        }
    }
}
