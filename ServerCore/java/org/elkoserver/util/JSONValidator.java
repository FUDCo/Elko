package org.elkoserver.util;

import org.elkoserver.json.JSONArray;
import org.elkoserver.json.JSONDecodingException;
import org.elkoserver.json.JSONObject;
import org.elkoserver.json.SyntaxError;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.LinkedList;

/**
 * Standalone command-line application to read and parse JSON object
 * descriptors and indicate any problems found.
 */
public class JSONValidator {
    /**
     * Convenience function for writing an error message to stderr.
     *
     * @param line  The line to write.
     */
    private static void e(String line) {
        System.err.println(line);
    }

    /**
     * Convenience function for writing a diagnostics message to stdout.
     *
     * @param line  The line to write.
     */
    private static void p(String line) {
        System.out.println(line);
    }

    /**
     * Print command line usage information and then exit.
     */
    private static void usage() {
        e("usage: java JSONValidator [opts...]");
        e("  [-f[ile]] FILE      Use FILE as an input source");
        e("  -                   Use stdin as an input source");
        e("  -h[elp] or -?       Output this usage information");
        System.exit(0);
    }

    /**
     * Validate the contents of a file as a JSON object.
     *
     * @param source  Pathname of the file, or "-" to read stdin.
     */
    private static String validate(String source) {
        File file = new File(source);
        BufferedReader in;
        try {
            if (source.equals("-")) {
                in = new BufferedReader(new InputStreamReader(System.in));
            } else {
                in = new BufferedReader(new FileReader(file));
            }
        } catch (FileNotFoundException e) {
            e("bad " + source + " not found");
            return null;
        }

        StringBuilder inbuf = new StringBuilder();
        String line = null;
        do {
            try {
                line = in.readLine();
            } catch (IOException e) {
                e("problem reading file: " + e);
                return null;
            }
            if (line != null) {
                inbuf.append(' ');
                inbuf.append(line);
            }
        } while (line != null);
        try {
            JSONObject obj = JSONObject.parse(inbuf.toString());
            return obj.getString("ref");
        } catch (SyntaxError e) {
            e("bad " + source + " syntax error: " + e.getMessage());
            return null;
        } catch (JSONDecodingException e) {
            e("bad " + source + " object has no ref string");
            return null;
        }
    }

    /**
     * Program main: parse command line flags, then scan each input source and
     * do the appropriate things with it.
     */
    public static void main(String args[]) {
        LinkedList<String> sources = new LinkedList<String>();
        try {
            for (int i = 0; i < args.length; ) {
                String arg = args[i++];
                if (arg.startsWith("-")) {
                    if (arg.equals("-file") ||
                               arg.equals("-f")) {
                        sources.add(args[i++]);
                    } else if (arg.equals("-")) {
                        sources.add("-");
                    } else if (arg.equals("-help") ||
                               arg.equals("-h") ||
                               arg.equals("-?")) {
                        usage();
                    } else {
                        e("ignoring unknown command line flag: " + arg);
                        usage();
                    }
                 } else {
                    sources.add(arg);
               }
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            usage();
        }
        boolean allOK = true;
        for (String src : sources) {
            String ref = validate(src);
            if (ref != null) {
                p("ok " + src + " (" + ref + ")");
            } else {
                allOK = false;
            }
            if (!allOK) {
                System.exit(1);
            }
        }
    }
}
