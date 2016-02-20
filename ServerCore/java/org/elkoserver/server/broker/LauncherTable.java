package org.elkoserver.server.broker;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.OptBoolean;
import org.elkoserver.json.Encodable;
import org.elkoserver.json.EncodeControl;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.json.JSONLiteralArray;
import org.elkoserver.objdb.ObjDB;
import org.elkoserver.util.trace.Trace;

/**
 * Holder of knowledge as to how to start external processes, normally for the
 * purpose of launching servers of various kinds.
 */
class LauncherTable implements Encodable {
    /** Map from launcher names to launcher descriptors. */
    private Map<String, Launcher> myLaunchers;

    /** Ref of this table instance, nominally "launchertable" */
    private String myRef;

    /** Dirty flag for checkpointing changes to launcher settings. */
    private boolean amDirty;

    /** Trace object for error messages and logging. */
    private static Trace tr;

    /* Possible startup modes.  Initial mode starts up a clean server from
       scratch based on configured initialization parameters.  Restart restarts
       a previously stopped server.  Recover starts up a previously crashed
       server. */
    static final int START_INITIAL = 1;
    static final int START_RECOVER = 2;
    static final int START_RESTART = 3;

    /**
     * JSON-drive constructor.
     *
     * @param ref  Ref of this table.  Usually there is only one and it is
     *    called "launchtable".
     * @param launchers  Array of launcher configurations.
     */
    @JSONMethod({ "ref", "launchers" })
    LauncherTable(String ref, Launcher[] launchers) {
        myRef = ref;
        myLaunchers = new HashMap<String, Launcher>();
        for (Launcher launcher : launchers) {
            myLaunchers.put(launcher.componentName(), launcher);
        }
        amDirty = true;
    }

    /**
     * Encode this table.
     *
     * @param control  Encode control determining what flavor of encoding
     *    should be done.
     *
     * @return a JSON literal representing this table.
     */
    public JSONLiteral encode(EncodeControl control) {
        JSONLiteral result = new JSONLiteral("launchertable", control);
        result.addParameter("ref", myRef);
        result.addParameter("launchers", myLaunchers.values());
        result.finish();
        return result;
    }

    /**
     * Encode an array of the launcher descriptions for benefit of the
     * admin console.
     *
     * @return a JSON literal representing the launchers.
     */
    JSONLiteralArray encodeAsArray() {
        JSONLiteralArray result = new JSONLiteralArray();
        for (Launcher launcher : myLaunchers.values()) {
            result.addElement(launcher);
        }
        result.finish();
        return result;
    }

    /**
     * Assign the trace object.  Note that this is static.
     *
     * @param appTrace  The trace object to use.
     */
    static void setTrace(Trace appTrace) {
        tr = appTrace;
    }

    /**
     * Save this launcher table to the repository if it has changed.
     *
     * @param odb  The object database to save into.
     */
    void checkpoint(ObjDB odb) {
        if (amDirty && odb != null) {
            odb.putObject(myRef, this, null, false, null);
            amDirty = false;
        }
    }

    /**
     * Do whatever launches should be done as part of cluster startup.
     *
     * @param startMode  What sort of startup this is.
     */
    void doStartupLaunches(int startMode) {
        for (Launcher launcher : myLaunchers.values()) {
            if ((startMode == START_INITIAL && launcher.isInitialLauncher()) ||
                    launcher.isRunSettingOn()) {
                launcher.launch();
                if (!launcher.isRunSettingOn()) {
                    launcher.setRunSettingOn(true);
                    amDirty = true;
                }
            }
        }
    }

    /**
     * Execute one of the launchers.
     *
     * @param component  Name of the component to be launched.
     *
     * @return null on success, an error string on failure.
     */
    String launch(String component) {
        Launcher launcher = myLaunchers.get(component);
        if (launcher != null) {
            if (!launcher.isRunSettingOn()) {
                launcher.setRunSettingOn(true);
                amDirty = true;
            }
            return launcher.launch();
        } else {
            return "fail " + component + " not found";
        }
    }

    /**
     * Set the "initial launcher" flag for a component.  This flag controls
     * whether the component should be started when the startup mode is
     * Initial.
     *
     * This method is has no effect if the named component doesn't exist.
     *
     * @param component  The name of the component.
     * @param flag  The setting for the flag: true=>launch this component
     *    when starting in Initial mode; false=> don't.
     */
    void setInitialLauncher(String component, boolean flag) {
        Launcher launcher = myLaunchers.get(component);
        if (launcher != null) {
            launcher.setInitialLauncher(flag);
            amDirty = true;
        }
    }
    
    /**
     * Set the "run setting" flag for a component.  This flag controls
     * whether the component should be started when the startup mode is
     * Restart or Recover.
     *
     * This method is has no effect if the named component doesn't exist.
     *
     * @param component  The name of the component.
     * @param flag  The setting for the flag: true=>launch this component
     *    when starting in Restart or Recover mode; false=> don't.
     */
    void setRunSettingOn(String component, boolean flag) {
        Launcher launcher = myLaunchers.get(component);
        if (launcher != null) {
            launcher.setRunSettingOn(flag);
            amDirty = true;
        }
    }

    /**
     * Launcher for a single cluster component.
     */
    static class Launcher implements Encodable {
        /** The name of the component this launcher launches for. */
        private String myComponentName;

        /** Command script to start the external process. */
        private String myLaunchScript;

        /** Flag that is true if this launcher should be executed when starting
            in Initial mode. */
        private boolean amInitialLauncher;

        /** Flag that is true if this launcher should be executed when starting
            in Restart or Recover mode. */
        private boolean amRunSettingOn;
        
        /**
         * JSON-drive constructor.
         *
         * @param name  Component name.
         * @param script  Launch script.
         * @param optInitial  Optional "initial" flag; defaults to false.
         * @param optRunSetting  Optional "run setting" flag; defaults to true.
         */
        @JSONMethod({ "name", "script", "initial", "on" })
        Launcher(String name, String script, OptBoolean optInitial,
                 OptBoolean optRunSetting)
        {
            myComponentName = name;
            myLaunchScript = script;
            amInitialLauncher = optInitial.value(false);
            amRunSettingOn = optRunSetting.value(true);
        }
        
        /**
         * Encode this launcher.
         *
         * @param control  Encode control determining what flavor of encoding
         *    should be done.
         *
         * @return a JSON literal representing this launcher.
         */
        public JSONLiteral encode(EncodeControl control) {
            JSONLiteral result = new JSONLiteral("launcher", control);
            result.addParameter("name", myComponentName);
            result.addParameter("script", myLaunchScript);
            result.addParameter("on", amRunSettingOn);
            if (amInitialLauncher) {
                result.addParameter("initial", true);
            }
            result.finish();
            return result;
        }
        
        /**
         * Obtain the name of the component this launcher launches.
         *
         * @return the component name.
         */
        String componentName() {
            return myComponentName;
        }
        
        /**
         * Test if this is an initial launcher.
         *
         * @return the value of the initial launcher flag.
         */
        boolean isInitialLauncher() {
            return amInitialLauncher;
        }

        /**
         * Test if the component's run setting is On.
         *
         * @return the value of the run setting flag.
         */
        boolean isRunSettingOn() {
            return amRunSettingOn;
        }
        
        /**
         * Execute this launcher by starting a new process that begins by
         * executing the configured script.
         *
         * @return null on success, an error string on failure.
         */
        String launch() {
            try {
                StringTokenizer parser = new StringTokenizer(myLaunchScript);
                List<String> exploded = new LinkedList<String>();
                while (parser.hasMoreTokens()) {
                    exploded.add(parser.nextToken());
                }
                Process process = new ProcessBuilder(exploded).start();
                tr.eventm("start process '" + myComponentName + "'");
                amRunSettingOn = true;
                return null;
            } catch (IOException e) {
                tr.eventm("process launch '" + myComponentName + "' failed: " +
                          e);
                return "fail " + myComponentName + " " + e;
            }
        }

        /**
         * Set the "initial launcher" flag.
         *
         * @param flag  The new setting for the flag.
         */
        void setInitialLauncher(boolean flag) {
            amInitialLauncher = flag;
        }

        /**
         * Set the "run setting" flag.
         *
         * @param flag  The new setting for the flag.
         */
        void setRunSettingOn(boolean flag) {
            amRunSettingOn = flag;
        }
    }
}
