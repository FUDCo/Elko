package org.elkoserver.server.presence.test;

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
import org.elkoserver.json.JSONArray;
import org.elkoserver.json.JSONObject;

/** Real TCP provider-loss regression. Run via Build/test-presence.sh. */
public class PresenceTest {
    private static class Peer implements AutoCloseable {
        final Socket socket;
        final BufferedReader input;
        final Writer output;
        Peer(Process server, int port, String role) throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            Socket connected;
            while (true) {
                connected = new Socket();
                try {
                    connected.connect(new InetSocketAddress("127.0.0.1", port), 1000);
                    break;
                } catch (IOException e) {
                    connected.close();
                    if (!server.isAlive() || System.nanoTime() >= deadline) throw e;
                    Thread.sleep(20);
                }
            }
            socket = connected;
            socket.setSoTimeout(3000);
            input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            output = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
            send("{\"to\":\"" + role + "\",\"op\":\"auth\",\"auth\":{\"type\":\"auth\",\"mode\":\"open\"}}");
        }
        void send(String message) throws IOException {
            output.write(message + "\r\n\r\n");
            output.flush();
        }
        JSONObject receive(String op) throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (System.nanoTime() < deadline) {
                String line = input.readLine();
                require(line != null, "Presence disconnected");
                if (line.trim().isEmpty()) continue;
                JSONObject row = JSONObject.parse(line);
                require(!"debug".equals(row.getProperty("op")), line);
                if (op.equals(row.getProperty("op"))) return row;
            }
            throw new AssertionError("Missing " + op);
        }
        void subscribe(String context) throws IOException {
            send("{\"to\":\"presence\",\"op\":\"subscribe\",\"context\":\"" + context + "\",\"domains\":[\"world\"],\"visible\":true}");
        }
        void enter(String user, String context) throws IOException {
            send("{\"to\":\"presence\",\"op\":\"user\",\"context\":\"" + context + "\",\"user\":\"" + user + "\",\"on\":true}");
        }
        void departure(String user, String context) throws Exception {
            JSONObject row;
            do { row = receive("utog"); }
            while (!user.equals(row.getProperty("user")) || !context.equals(row.getProperty("ctx")) || !Boolean.FALSE.equals(row.getProperty("on")));
        }
        public void close() throws IOException { socket.close(); }
    }
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static JSONObject dump(Peer admin) throws Exception {
        admin.send("{\"to\":\"admin\",\"op\":\"dump\",\"depth\":2}");
        return admin.receive("dump");
    }
    private static void counts(Peer admin, int users, int presences) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        do {
            JSONObject row = dump(admin);
            if (row.getInt("numusers") == users && row.getInt("numpresences") == presences) return;
            Thread.sleep(20);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Incorrect presence counts; expected " + users + "/" + presences);
    }
    private static void exercise(Process server, int port) throws Exception {
        try (Peer admin = new Peer(server, port, "admin");
             Peer observer = new Peer(server, port, "presence");
             Peer first = new Peer(server, port, "presence");
             Peer replacement = new Peer(server, port, "presence")) {
            observer.subscribe("observer-room");
            observer.enter("alice", "observer-room");
            observer.receive("gtou");
            first.subscribe("lost-room");
            first.enter("bob", "lost-room");
            first.enter("carol", "lost-room");
            first.subscribe("reassigned-room");
            first.enter("dave", "reassigned-room");
            counts(admin, 4, 4);
            replacement.subscribe("surviving-room");
            replacement.enter("bob", "surviving-room");
            replacement.subscribe("reassigned-room");
            replacement.send("{\"to\":\"presence\",\"op\":\"ping\",\"tag\":\"barrier\"}");
            replacement.receive("pong");
            counts(admin, 4, 5);
            first.close();
            observer.departure("bob", "lost-room");
            counts(admin, 3, 3); // Alice, Bob elsewhere, and reassigned Dave remain.
            JSONObject state = dump(admin);
            JSONArray users = state.getArray("users");
            for (int i = 0; i < users.size(); i++) {
                JSONObject user = users.getObject(i);
                require(!"carol".equals(user.getString("user")), "Disconnected-only user survived");
                JSONArray presences = user.getArray("pres");
                for (int j = 0; j < presences.size(); j++)
                    require(!"lost-room".equals(presences.get(j)), "Stale context survived");
            }
            replacement.close();
            counts(admin, 1, 1);
            try (Peer newcomer = new Peer(server, port, "presence")) {
                newcomer.subscribe("new-room");
                newcomer.enter("eve", "new-room");
                String snapshot = newcomer.receive("gtou").sendableString();
                require(!snapshot.contains("lost-room") && !snapshot.contains("surviving-room") && !snapshot.contains("reassigned-room"), "Stale presence in new subscriber snapshot");
            }
        }
    }
    public static void main(String[] args) throws Exception {
        Path working = Paths.get(args[0]).toAbsolutePath();
        Files.write(working.resolve("Data/graphs.json"), ("{\"ref\":\"graphs\",\"type\":\"graphtable\",\"graphs\":[{\"name\":\"world\",\"class\":\"org.elkoserver.server.presence.UniversalGraph\"}]}").getBytes(StandardCharsets.UTF_8));
        int port;
        try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) { port = listener.getLocalPort(); }
        Path log = working.resolve("server.log");
        Process server = new ProcessBuilder(new File(System.getProperty("java.home"), "bin/java").getPath(),
            "-cp", System.getProperty("java.class.path"), "org.elkoserver.foundation.boot.Boot",
            "tracelog_name=-", "trace_default=error", "conf.listen.protocol=tcp",
            "conf.listen.host=127.0.0.1:" + port, "conf.listen.bind=127.0.0.1:" + port,
            "conf.listen.allow=any", "conf.listen.auth.mode=open", "conf.comm.jsonstrictness=true",
            "conf.msgdiagnostics=true", "conf.presence.odb=" + working.resolve("Data"),
            "org.elkoserver.server.presence.PresenceServerBoot")
            .directory(working.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        boolean passed = false;
        try {
            exercise(server, port);
            passed = true;
            System.out.println("PASS: provider-loss departures, shared users, reassigned contexts and fresh snapshots");
        } finally {
            server.destroy();
            if (!server.waitFor(5, TimeUnit.SECONDS)) { server.destroyForcibly(); server.waitFor(); }
            if (!passed) System.err.write(Files.readAllBytes(log));
        }
    }
}
