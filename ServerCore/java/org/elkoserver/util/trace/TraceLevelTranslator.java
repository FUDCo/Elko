package org.elkoserver.util.trace;

/**
 * Translate numerical trace levels into strings and vice versa.
 */
class TraceLevelTranslator {

    /** 
     * Convert numberic trace levels into three-character synonyms.
     *
     * @param level  The level to be converted
     *
     * @return a three-character tag string for 'level'
     */
    static String terse(int level) {
        switch (level) {
            case Trace.NOTICE:  return "NTC";
            case Trace.METRICS: return "MET";
            case Trace.MESSAGE: return "MSG";
            case Trace.ERROR:   return "ERR";
            case Trace.WARNING: return "WRN";
            case Trace.WORLD:   return "WLD";
            case Trace.USAGE:   return "USE";
            case Trace.EVENT:   return "EVN";
            case Trace.DEBUG:   return "DBG";
            case Trace.VERBOSE: return "VRB";
            default:
                assert false: "Bad level";
                return "???";
        }
    }

    /** 
     * Convert a string into one of the numeric trace levels.
     *
     * @param level  The string to be converted
     *
     * @return the level number named by 'level'
     *
     * @throws IllegalArgumentException if the string is not recognized.
     */
    static int toInt(String level) throws IllegalArgumentException {
        if (level.equalsIgnoreCase("error")) {
            return Trace.ERROR;
        } else if (level.equalsIgnoreCase("warning")) {
            return Trace.WARNING;
        } else if (level.equalsIgnoreCase("world")) {
            return Trace.WORLD;
        } else if (level.equalsIgnoreCase("usage")) {
            return Trace.USAGE;
        } else if (level.equalsIgnoreCase("event")) {
            return Trace.EVENT;
        } else if (level.equalsIgnoreCase("debug")) {
            return Trace.DEBUG;
        } else if (level.equalsIgnoreCase("verbose")) {
            return Trace.VERBOSE;
        } else {
            String problem = "Incorrect tracing threshold '" + level + "'";
            Trace.trace.errori(problem);
            throw new IllegalArgumentException(problem);
        }
    }
}
