package org.elkoserver.server.repository.test;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import org.elkoserver.json.EncodeControl;
import org.elkoserver.json.JSONArray;
import org.elkoserver.json.JSONObject;

/** Standalone TCP/file-store regression. Run via Build/test-repository.sh. */
public class RepositoryTest {
    private BufferedReader myInput;
    private Writer myOutput;
    private int mySequence;

    private RepositoryTest(Socket socket) throws IOException {
        myInput = new BufferedReader(new InputStreamReader(
            socket.getInputStream(), StandardCharsets.UTF_8));
        myOutput = new OutputStreamWriter(socket.getOutputStream(),
                                         StandardCharsets.UTF_8);
    }

    private void send(JSONObject message) throws IOException {
        myOutput.write(message.literal(EncodeControl.forRepository)
                       .sendableString() + "\r\n\r\n");
        myOutput.flush();
    }

    private JSONObject request(String op, JSONObject item) throws Exception {
        String tag = Integer.toString(++mySequence);
        JSONObject message = new JSONObject("rep", op);
        message.addProperty("tag", tag);
        JSONArray what = new JSONArray();
        what.add(item);
        message.addProperty("what", what);
        send(message);
        while (true) {
            String line = myInput.readLine();
            require(line != null, "Repository disconnected");
            if (line.trim().isEmpty()) {
                continue;
            }
            JSONObject reply = JSONObject.parse(line);
            require(!"debug".equals(reply.getProperty("op")), line);
            if (tag.equals(reply.getProperty("tag"))) {
                require(op.equals(reply.getProperty("op")), line);
                JSONArray results = reply.getArray("results");
                require(results.size() == 1, line);
                return results.getObject(0);
            }
        }
    }

    private static void require(boolean condition, String detail) {
        if (!condition) {
            throw new AssertionError(detail);
        }
    }

    private static void checkObject(JSONObject object, int value)
        throws Exception
    {
        require("probe".equals(object.getString("ref")) &&
                object.getInt("value") == value, "Incorrect stored object");
    }

    private void exercise(Path data) throws Exception {
        send(JSONObject.parse("{\"to\":\"rep\",\"op\":\"auth\",\"auth\":" +
                              "{\"type\":\"auth\",\"mode\":\"open\"}}"));
        JSONObject ref = JSONObject.parse("{\"type\":\"reqi\",\"ref\":\"probe\"}");
        checkObject(JSONObject.parse(request("get", ref).getString("obj")), 41);
        JSONObject put = new JSONObject("puti");
        put.addProperty("ref", "probe");
        put.addProperty("obj", "{\"ref\":\"probe\",\"value\":42}");
        require(request("put", put).getProperty("failure") == null, "Put failed");
        checkObject(JSONObject.parse(request("get", ref).getString("obj")), 42);
        checkObject(JSONObject.parse(new String(Files.readAllBytes(data),
                                               StandardCharsets.UTF_8)), 42);
        require(request("remove", ref).getProperty("failure") == null,
                "Remove failed");
        require("not found".equals(request("get", ref).getProperty("failure")),
                "Missing object did not return not found");
        require(!Files.exists(data), "Remove did not delete the file");
    }

    private static Socket connect(Process server, int port) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (true) {
            Socket socket = new Socket();
            try {
                socket.connect(new InetSocketAddress("127.0.0.1", port), 1000);
                socket.setSoTimeout(5000);
                return socket;
            } catch (IOException e) {
                socket.close();
                if (!server.isAlive() || System.nanoTime() >= deadline) {
                    throw new IOException("Repository did not start", e);
                }
                Thread.sleep(20);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Path working = Paths.get(args[0]).toAbsolutePath();
        Path data = working.resolve("Data/probe.json");
        Files.write(data, "{\"ref\":\"probe\",\"value\":41}"
                    .getBytes(StandardCharsets.UTF_8));
        int port;
        try (ServerSocket listener = new ServerSocket(
                 0, 1, InetAddress.getByName("127.0.0.1"))) {
            port = listener.getLocalPort();
        }
        Path log = working.resolve("server.log");
        Process server = new ProcessBuilder(
            new File(System.getProperty("java.home"), "bin/java").getPath(),
            "-cp", System.getProperty("java.class.path"),
            "org.elkoserver.foundation.boot.Boot", "tracelog_name=-",
            "trace_default=error", "conf.listen.protocol=tcp",
            "conf.listen.host=127.0.0.1:" + port,
            "conf.listen.bind=127.0.0.1:" + port,
            "conf.listen.allow=any", "conf.listen.auth.mode=open",
            "conf.comm.jsonstrictness=true", "conf.msgdiagnostics=true",
            "conf.rep.odb=" + working.resolve("Data"),
            "org.elkoserver.server.repository.RepositoryBoot")
            .directory(working.toFile()).redirectErrorStream(true)
            .redirectOutput(log.toFile()).start();
        boolean passed = false;
        try {
            try (Socket socket = connect(server, port)) {
                new RepositoryTest(socket).exercise(data);
            }
            passed = true;
            System.out.println("PASS: Repository startup, get, persisted put, " +
                               "remove and missing-object result");
        } finally {
            server.destroy();
            if (!server.waitFor(5, TimeUnit.SECONDS)) {
                server.destroyForcibly();
                server.waitFor();
            }
            if (!passed) {
                System.err.write(Files.readAllBytes(log));
            }
        }
    }
}
