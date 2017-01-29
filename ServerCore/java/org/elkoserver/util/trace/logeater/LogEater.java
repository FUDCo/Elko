package org.elkoserver.util.trace.logeater;

import org.elkoserver.json.JSONArray;
import org.elkoserver.json.JSONObject;
import org.elkoserver.json.SyntaxError;
import org.elkoserver.util.trace.Trace;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.Mongo;
import com.mongodb.MongoException;
import com.mongodb.WriteResult;

/**
 * Standalone command-line application to read and parse one or more
 * server log files and selectively dump the contents into a MongoDB
 * collection for analysis and/or archiving.
 *
 * Each instance of LogEater consumes a single log file.
 */
public class LogEater {
    /** Reader for parsing the log file. */
    private BufferedReader myIn;

    /** Description of the log file. */
    private SourceInfo mySource;

    /** Sequence number to track entry order despite duplicated timestamps. */
    private int mySeq;

    /** MongoDB object ID of the source descriptor record for this log. */
    private Object mySourceID;

    /* These statics capture global parameterization from the command line. */

    /** If true, output debug info to help determine if it's working right. */
    private static boolean amTesting = false;

    /** If true, process communications message entries. */
    private static boolean amEatingComm = false;

    /** If true, process metrics entries. */
    private static boolean amEatingMetrics = true;

    /** If true, process server debug entries. */
    private static boolean amEatingDebug = false;

    /** If true, record processed entries in the database. */
    private static boolean amWriting = true;

    /** The MongoDB collection into which things will be recorded. */
    private static DBCollection theCollection;


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
     * Simple struct class to hold the description of a log file.
     */
    private static class SourceInfo {
        /** Where did this come from? */
        public String path;
        /** What sort of log is this (e.g., "context", "workshop", etc.) */
        public String type;
        /** Distinguishing label (e.g., "Foon prod", "Fred's test") */
        public String label;
    }

    /**
     * Print command line usage information and then exit.
     */
    private static void usage() {
        e("usage: java LogEater [opts...]");
        e("  [-f[ile]] FILE      Use FILE as an input source");
        e("  -                   Use stdin as an input source");
        e("  -l[abel] LABEL      Tag the next input source with LABEL");
        e("  -t[type] TYPE       Record the next input source as type TYPE");
        e("");
        e("  -c[omm]             Eat message traffic logs");
        e("  -noc[omm]           Don't eat message traffic logs {default}");
        e("  -m[etrics]          Eat metrics data logs {default}");
        e("  -nom[etrics]        Don't eat metrics data logs");
        e("  -d[ebug]            Eat server debug logs");
        e("  -nod[ebug]          Don't eat server debug logs {default}");
        e("  -all                Equivalent to -c -m -d");
        e("");
        e("  -dbhost HOST:PORT   MongoDB is at HOST:PORT {localhost:27017}");
        e("  -db DBNAME          Use MongoDB database DBNAME {elko}");
        e("  -dbcoll COLL        Use MongoDB collection COLL {metrics}");
        e("  -nodb               Don't write to the database");
        e("");
        e("  -test               Spew diagnostic output to stdout");
        e("  -h[elp] or -?       Output this usage information");
        System.exit(0);
    }

    /**
     * Program main: parse command line flags, then scan each input source and
     * do the appropriate things with it.
     */
    public static void main(String args[]) {
        LinkedList<SourceInfo> sources = new LinkedList<SourceInfo>();
        SourceInfo source = new SourceInfo();
        String dbHost = "localhost:27017";
        String dbName = "elko";
        String dbCollName = "metrics";

        try {
            for (int i = 0; i < args.length; ) {
                String arg = args[i++];
                if (arg.startsWith("-")) {
                    if (arg.equals("-test")) {
                        amTesting = true;
                    } else if (arg.equals("-comm") ||
                               arg.equals("-c")) {
                        amEatingComm = true;
                    } else if (arg.equals("-nocomm") ||
                               arg.equals("-noc")) {
                        amEatingComm = false;
                    } else if (arg.equals("-metrics") ||
                               arg.equals("-m")) {
                        amEatingMetrics = true;
                    } else if (arg.equals("-nometrics") ||
                               arg.equals("-nom")) {
                        amEatingMetrics = false;
                    } else if (arg.equals("-debug") ||
                               arg.equals("-d")) {
                        amEatingDebug = true;
                    } else if (arg.equals("-nodebug") ||
                               arg.equals("-nod")) {
                        amEatingDebug = false;
                    } else if (arg.equals("-all")) {
                        amEatingComm = true;
                        amEatingMetrics = true;
                        amEatingDebug = true;
                    } else if (arg.equals("-file") ||
                               arg.equals("-f")) {
                        if (source.path != null) {
                            sources.add(source);
                            source = new SourceInfo();
                        }
                        source.path = args[i++];
                    } else if (arg.equals("-")) {
                        if (source.path != null) {
                            sources.add(source);
                            source = new SourceInfo();
                        }
                        source.path = arg;
                    } else if (arg.equals("-label") ||
                               arg.equals("-l")) {
                        source.label = args[i++];
                    } else if (arg.equals("-type") ||
                               arg.equals("-t")) {
                        source.type = args[i++];
                    } else if (arg.equals("-dbhost")) {
                        dbHost = args[i++];
                    } else if (arg.equals("-db")) {
                        dbName = args[i++];
                    } else if (arg.equals("-dbcoll")) {
                        dbCollName = args[i++];
                    } else if (arg.equals("-nodb")) {
                        amWriting = false;
                    } else if (arg.equals("-help") ||
                               arg.equals("-h") ||
                               arg.equals("-?")) {
                        usage();
                    } else {
                        e("ignoring unknown command line flag: " + arg);
                        usage();
                    }
                } else {
                    if (source.path != null) {
                        sources.add(source);
                        source = new SourceInfo();
                    }
                    source.path = arg;
                }
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            usage();
        }
        if (source.path != null) {
            sources.add(source);
        }
        if (amWriting) {
            int colon = dbHost.indexOf(':');
            int port;
            String host;
            if (colon < 0) {
                port = 27017;
                host = dbHost;
            } else {
                port = Integer.parseInt(dbHost.substring(colon + 1)) ;
                host = dbHost.substring(0, colon);
            }
            Mongo mongo;
            try {
                mongo = new Mongo(host, port);
            } catch (UnknownHostException e) {
                e("mongodb server " + dbHost + ": unknown host");
                System.exit(1);
                return; // to make the compiler shut up.
            }
            DB db = mongo.getDB(dbName);
            theCollection = db.getCollection(dbCollName);
        }
        for (SourceInfo src : sources) {
            LogEater eater = new LogEater(src);
            if (eater.ok()) {
                eater.eatFile();
            }
        }
    }

    /**
     * Construct a log eater for an input source.
     *
     * @param source  Description of the source.
     */
    private LogEater(SourceInfo source) {
        mySeq = 1;
        File file = new File(source.path);
        if (source.label == null) {
            source.label = file.getName();
        }
        if (source.type != null) {
            source.type = "-";
        }
        try {
            if (source.path.equals("-")) {
                myIn = new BufferedReader(new InputStreamReader(System.in));
            } else {
                myIn = new BufferedReader(new FileReader(file));
            }
            mySource = source;
        } catch (FileNotFoundException e) {
            e("File '" + source.path + "' not found");
            myIn = null;
        }
    }

    /**
     * Test if this instance initialized properly.
     *
     * @return true if we're good to go, false if this instance should not be
     *    used.
     */
    private boolean ok() {
        return myIn != null;
    }

    /**
     * Write a record to the database describing the input source.
     */
    private void recordSource() {
        DBObject out = new BasicDBObject();
        out.put("tag", "src");
        out.put("path", mySource.path);
        out.put("name", mySource.label);
        out.put("srctype", mySource.type);
        theCollection.insert(out);
        mySourceID = out.get("_id");
    }        

    /**
     * Scan the entire input source and process each line appropriately.
     */
    private void eatFile() {
        if (amWriting) {
            recordSource();
        }
        String line = null;
        do {
            try {
                line = myIn.readLine();
            } catch (IOException e) {
                e("problem reading file: " + e);
                break;
            }
            if (line != null) {
                if (!eatLine(line)) {
                    break;
                }
            }
        } while (line != null);
    }

    /** Regexp to interpret the canonical information in each log line
     *
     * Each line looks like:
     *
     *      - YYYY/MM/DD hh:mm:ss.fff TAG subsystem : otherstuff
     *   or
     *      - YYYY/MM/DD hh:mm:ss.fff TAG subsystem (sourceloc) otherstuff
     *  
     * The otherstuff part varies according to the TAG and is processed by
     * separately.
     */
    final private static Pattern COARSE_PATTERN =
        Pattern.compile("- (\\d{4})/(\\d{2})/(\\d{2}) (\\d{2}):(\\d{2}):(\\d{2})\\.(\\d{3}) ([-a-zA-Z0-9]+) ([-a-zA-Z0-9]+) (:|\\(([^)]*)\\)) (.*)");

    /**
     * Scan a single log line and process it appropriately.
     *
     * @param line  The line to be scanned
     *
     * @return true if successful, false if there was a problem.
     */
    private boolean eatLine(String line) {
        if (line.charAt(0) == '-') {
            Matcher matcher = COARSE_PATTERN.matcher(line);
            if (matcher.matches()) {
                int year = Integer.parseInt(matcher.group(1));
                int month = Integer.parseInt(matcher.group(2));
                int day = Integer.parseInt(matcher.group(3));
                int hour = Integer.parseInt(matcher.group(4));
                int minute = Integer.parseInt(matcher.group(5));
                int second = Integer.parseInt(matcher.group(6));
                int milli = Integer.parseInt(matcher.group(7));

                Calendar cal = Calendar.getInstance();
                cal.set(year, month - 1, day, hour, minute, second);
                cal.set(Calendar.MILLISECOND, milli);
                long timestamp = cal.getTimeInMillis();
                
                String tag = matcher.group(8);
                String subsystem = matcher.group(9);
                String locationField = matcher.group(10);
                String location = matcher.group(11);
                String message = matcher.group(12);
                eatParsedLine(timestamp, tag, subsystem, location, message);
                return true;
            } else {
                e("malformed line /" + line + "/");
                return false;
            }
        } else {
            return true;
        }
    }

    /** Table mapping from message tags to logging levels. */
    private static Map<String,Integer> tags = new HashMap<String, Integer>();
    static {
        tags.put("NTC", Trace.NOTICE);
        tags.put("MET", Trace.METRICS);
        tags.put("MSG", Trace.MESSAGE);
        tags.put("ERR", Trace.ERROR);
        tags.put("WRN", Trace.WARNING);
        tags.put("WLD", Trace.WORLD);
        tags.put("USE", Trace.USAGE);
        tags.put("EVN", Trace.EVENT);
        tags.put("DBG", Trace.DEBUG);
        tags.put("VRB", Trace.VERBOSE);
    };

    /** Regexp to parse the message field of a metrics entry. */
    final private static Pattern METRICS_PATTERN =
        Pattern.compile("([^ ]+) (\\d+) (.*)");

    /** Regexp to parse the message field of a comm entry. */
    final private static Pattern COMM_PATTERN =
        Pattern.compile("([^ ]+) (<-|->) (.*)");

    /**
     * Process a parsed log line.
     *
     * @param timestamp  The message timestamp.
     * @param tagStr  The message tag.
     * @param subsystem  The logging subsystem.
     * @param location  The source code location info, or null.
     * @param message  The tag-specific message field.
     *
     * @return true if successful, false if there was a problem.
     */
    private boolean eatParsedLine(long timestamp, String tagStr,
                                  String subsystem, String location,
                                  String message)
    {
        Integer tag = tags.get(tagStr);
        if (tag == null) {
            e("unknown log tag '" + tagStr + "'");
            return false;
        }
        switch (tag) {
            case Trace.METRICS:
                if (amEatingMetrics) {
                    Matcher metricsMatcher = METRICS_PATTERN.matcher(message);
                    if (metricsMatcher.matches()) {
                        String path = metricsMatcher.group(1);
                        String idStr = metricsMatcher.group(2);
                        int id = Integer.parseInt(idStr);
                        String value = metricsMatcher.group(3);
                        return processMetrics(timestamp, subsystem, path, id,
                                              value);
                    } else {
                        e("malformed metrics message /" + message + "/");
                        return false;
                    }
                } else {
                    return true;
                }
            case Trace.NOTICE:
                if (amEatingDebug) {
                    return processInfo(timestamp, subsystem, message);
                } else {
                    return true;
                }
            case Trace.MESSAGE:
                if (amEatingComm) {
                    Matcher commMatcher = COMM_PATTERN.matcher(message);
                    if (commMatcher.matches()) {
                        String connection = commMatcher.group(1);
                        String direction = commMatcher.group(2);
                        boolean inbound = direction.equals("->");
                        String msg = commMatcher.group(3);
                        return processComm(timestamp, subsystem, connection,
                                           inbound, msg);
                    } else {
                        e("malformed comm message /" + message + "/");
                        return false;
                    }
                } else {
                    return true;
                }
            case Trace.ERROR:
            case Trace.WARNING:
            case Trace.WORLD:
            case Trace.USAGE:
            case Trace.EVENT:
            case Trace.DEBUG:
            case Trace.VERBOSE:
                if (amEatingDebug) {
                    return processDebug(timestamp, subsystem, tag, location,
                                        message);
                } else {
                    return true;
                }
        }
        return false;
    }

    /**
     * Create a new MongoDB object and initialize it with the stuff that is
     * common to all log records.
     *
     * @param timestamp  The message timestamp.
     * @param tag  The message tag.
     * @param subsystem  The logging subsystem.
     *
     * @return the newly create DBObject.
     */
    private DBObject baseDBObject(long timestamp, String tag, String subsystem)
    {
        DBObject out = new BasicDBObject();
        out.put("src", mySourceID);
        out.put("seq", mySeq++);
        out.put("ts", timestamp);
        out.put("tag", tag);
        out.put("sys", subsystem);
        return out;
    }

    /**
     * Translate a JSON property value to its corresponding DBObject value.
     *
     * @param value  The value to be thus translated.
     *
     * @return an object equivalent to 'value' but suitable for storing into
     *    a DBObject property.
     */
    private Object valueToDBValue(Object value) {
        if (value instanceof JSONObject) {
            value = jsonObjectToDBObject((JSONObject) value);
        } else if (value instanceof JSONArray) {
            value = jsonArrayToDBArray((JSONArray) value);
        } else if (value instanceof Long) {
            long intValue = ((Long) value).longValue();
            if (Integer.MIN_VALUE <= intValue &&
                intValue <= Integer.MAX_VALUE) {
                value = new Integer((int) intValue);
            }
        }
        return value;
    }

    /**
     * Translate a JSON array into a corresponding DBObject array.
     *
     * @param arr  The array to be tranlsated.
     *
     * @return an ArrayList equivalent to 'arr' that can be used in a DBObject.
     */
    private ArrayList<Object> jsonArrayToDBArray(JSONArray arr) {
        ArrayList<Object> result = new ArrayList<Object>(arr.size());
        for (Object elem : arr) {
            result.add(valueToDBValue(elem));
        }
        return result;
    }

    /**
     * Translate a JSON object into a DBObject.
     *
     * @param obj  The JSON object to be translated.
     *
     * @return a DBObject equivalent to 'obj' that can be stored as a MongoDB
     *    record.
     */
    private DBObject jsonObjectToDBObject(JSONObject obj) {
        DBObject result = new BasicDBObject();
        for (Map.Entry<String, Object> prop : obj.properties()) {
            result.put(prop.getKey(), valueToDBValue(prop.getValue()));
        }
        return result;
    }

    /**
     * Process a metrics (MET) entry.
     *
     * @param timestamp  The message timestamp.
     * @param subsystem  The logging subsystem.
     * @param path  The metric identifier.
     * @param id  The session id.
     * @param value  The value that was logged.
     *
     * @return true if successful, false if there was a problem.
     */
    private boolean processMetrics(long timestamp, String subsystem,
                                   String path, int id, String value)
    {
        if (amTesting) {
            String timestr =
                String.format("%1$tY/%1$tm/%1$td %1$tT.%1$tL", timestamp);
            p("- " + timestr + " MET " + subsystem + " : " + path + " " + id +
              " " + value);
        }
        if (amWriting) {
            DBObject out = baseDBObject(timestamp, "MET", subsystem);
            out.put("path", path);
            out.put("id", id);
            value = "{hack:" + value + "}";
            JSONObject json;
            try {
                json = JSONObject.parse(value);
            } catch (SyntaxError e) {
                e("invalid JSON value /" + value + "/");
                return false;
            }
            DBObject dbValue = jsonObjectToDBObject(json);
            out.put("val", dbValue.get("hack"));
            theCollection.insert(out);
        }
        return true;
    }

    /**
     * Process an info (NTC) entry.
     *
     * @param timestamp  The message timestamp.
     * @param subsystem  The logging subsystem.
     * @param message  The free-form message text.
     *
     * @return true if successful, false if there was a problem.
     */
    private boolean processInfo(long timestamp, String subsystem,
                                String message)
    {
        if (amTesting) {
            String timestr =
                String.format("%1$tY/%1$tm/%1$td %1$tT.%1$tL", timestamp);
            p("- " + timestr + " NTC " + subsystem + " : " + message);
        }
        if (amWriting) {
            DBObject out = baseDBObject(timestamp, "NTC", subsystem);
            out.put("msg", message);
            theCollection.insert(out);
        }
        return true;
    }
    
    /**
     * Process a comm (MSG) entry.
     *
     * @param timestamp  The message timestamp.
     * @param subsystem  The logging subsystem.
     * @param connection  Identifier of the connection over which the message
     *    was sent or received.
     * @param inbound  True if the message was received, false if it was sent.
     * @param msg  The JSON message itself.
     *
     * @return true if successful, false if there was a problem.
     */
    private boolean processComm(long timestamp, String subsystem,
                                String connection, boolean inbound, String msg)
    {
        if (amTesting) {
            String timestr =
                String.format("%1$tY/%1$tm/%1$td %1$tT.%1$tL", timestamp);
            String direction = inbound ? " -> " : " <- ";
            p("- " + timestr + " MSG " + subsystem + " : " + connection +
              direction + msg);
        }
        if (amWriting) {
            DBObject out = baseDBObject(timestamp, "MSG", subsystem);
            out.put("conn", connection);
            out.put("in", inbound);
            out.put("msg", msg);
            theCollection.insert(out);
        }
        return true;
    }

    /**
     * Process a debug entry.
     *
     * @param timestamp  The message timestamp.
     * @param subsystem  The logging subsystem.
     * @param level  The logging level the message was recorded at.
     * @param location  The source text location, or null.
     * @param message  The free-form message text.
     *
     * @return true if successful, false if there was a problem.
     */
    private boolean processDebug(long timestamp, String subsystem, int level,
                                 String location, String message)
    {
        String tag;
        switch (level) {
            case Trace.ERROR:   tag = "ERR"; break;
            case Trace.WARNING: tag = "WRN"; break;
            case Trace.WORLD:   tag = "WLD"; break;
            case Trace.USAGE:   tag = "USE"; break;
            case Trace.EVENT:   tag = "EVN"; break;
            case Trace.DEBUG:   tag = "DBG"; break;
            case Trace.VERBOSE: tag = "VRB"; break;
            default:            tag = "???"; break;
        }
        if (amTesting) {
            String timestr =
                String.format("%1$tY/%1$tm/%1$td %1$tT.%1$tL", timestamp);
            String locationStr;
            if (location == null) {
                locationStr = ":";
            } else {
                locationStr = "(" + location + ")";
            }
            p("- " + timestr + " " + tag + " " + subsystem + " " +
              locationStr + " " + message);
        }
        if (amWriting) {
            DBObject out = baseDBObject(timestamp, tag, subsystem);
            if (location != null) {
                out.put("loc", location);
            }
            out.put("msg", message);
            theCollection.insert(out);
        }
        return true;
    }
}
