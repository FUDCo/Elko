if (typeof ELKO === 'undefined') {
    ELKO = {};
}

ELKO.trace = {
    ALWAYS: 1, FATAL: 1, ERROR: 2, WARNING: 3, DEBUG: 4, VERBOSE: 5
};

ELKO.trace.traceLevel = ELKO.trace.WARNING;

ELKO.trace.print = function(s, level) {
    level = level || ELKO.trace.VERBOSE;
    if (ELKO.trace.traceLevel >= level) {
        alert(s);
    }
};

ELKO.trace.fatal =
    function(s) { ELKO.trace.print("FATAL: "  + s, ELKO.trace.FATAL); };
ELKO.trace.error =
    function(s) { ELKO.trace.print("ERROR: "  + s, ELKO.trace.ERROR); };
ELKO.trace.warning =
    function(s) { ELKO.trace.print("WARNING: "+ s, ELKO.trace.WARNING); };
ELKO.trace.debug =
    function(s) { ELKO.trace.print("DEBUG: "  + s, ELKO.trace.DEBUG); };
