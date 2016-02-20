package org.elkoserver.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.Callable;
import org.elkoserver.json.EncodeControl;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.json.JSONObject;
import org.elkoserver.json.SyntaxError;

import org.elkoserver.util.trace.Trace;

/**
 * Utility class to produce a slow service task that will make a simple HTTP
 * call out to a JSON webservice.
 */
public class JSONHTTPRequest {
    /** Suppress the Miranda constructor. */
    private JSONHTTPRequest() { }

    /**
     * Produce a task that will make an HTTP POST request to an external URL,
     * wait for the reponse, parse the response as JSON, and return the parsed
     * response as the product of the task.
     *
     * @param url  The URL to issue the HTTP request to.
     * @param request  A JSON literal containing the request body to be posted.
     *
     * @return a parsed JSON object representing the result that was returned
     *    by the webservice, or null if there was an error of some kind.
     */
    public static Callable<Object> make(final String url,
                                        final JSONLiteral request)
    {
        return new Callable<Object> () {
            public Object call() {
                OutputStreamWriter out = null;
                BufferedReader in = null;
                try {
                    URL urlObj = new URL(url);
                    HttpURLConnection conn =
                        (HttpURLConnection) urlObj.openConnection();
                    conn.setDoOutput(true);
                    conn.setRequestMethod("POST");

                    out = new OutputStreamWriter(conn.getOutputStream());
                    String toPost = request.sendableString();
                    if (Trace.comm.debug) {
                        Trace.comm.debugm("POSTing to: " + url);
                        Trace.comm.debugm("POST body: /" + toPost + "/");
                    }
                    out.write(toPost);
                    out.flush();

                    in = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) {
                        response.append(line);
                    }
                    in.close();
                    out.close();
                    String reply = response.toString();
                    if (Trace.comm.debug) {
                        Trace.comm.debugm("WS reply was: /" + reply + "/");
                    }
                    try {
                        return JSONObject.parse(reply);
                    } catch (SyntaxError e) {
                        return null;
                    }
                } catch (IOException ex) {
                    if (out != null) {
                        try {
                            out.close();
                        } catch (IOException e) { }
                    }
                    if (in != null) {
                        try {
                            in.close();
                        } catch (IOException e) { }
                    }
                }
                return null;
            }
        };
    }

    /**
     * Produce a task that will make an HTTP POST request to an external URL,
     * wait for the reponse, parse the response as JSON, and return the parsed
     * response as the product of the task.
     *
     * @param url  The URL to issue the HTTP request to.
     * @param request  A JSON object containing the request body to be posted.
     *
     * @return a parsed JSON object representing the result that was returned
     *    by the webservice, or null if there was an error of some kind.
     */
    public static Callable<Object> make(String url, JSONObject request) {
        return make(url, request.literal(EncodeControl.forClient));
    }
}

