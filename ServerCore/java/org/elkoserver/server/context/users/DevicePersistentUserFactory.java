package org.elkoserver.server.context.users;

import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.net.Connection;
import org.elkoserver.json.JSONDecodingException;
import org.elkoserver.json.JSONObject;
import org.elkoserver.server.context.Contextor;
import org.elkoserver.server.context.Mod;
import org.elkoserver.server.context.User;
import org.elkoserver.server.context.UserFactory;
import org.elkoserver.util.ArgRunnable;
import org.elkoserver.util.trace.Trace;
import java.util.concurrent.Callable;

/**
 * Factory used to generate a user from a device.
 */
public class DevicePersistentUserFactory implements UserFactory {
    
    /**
     * The name of the device (IOS, etc)
     */
    private String myDevice;

    @JSONMethod({ "device" })
    public DevicePersistentUserFactory(String device) {
        myDevice = device;
    }  
    
    public String getDevice() {
        return myDevice;
    }

    /**
     * Produce a user object.
     *
     * @param contextor The contextor of the server in which the requested
     *    user will be present
     * @param connection  The connection over which the new user presented
     *    themself.
     * @param param   Arbitary JSON object parameterizing the construction.
     *    this is analogous to the user record read from the ODB, but may be
     *    anything that makes sense for the particular factory implementation.
     *    Of course, the sender of this parameter must be coordinated with the
     *    factory implementation.
     * @param handler   Handler to be called with the result.  The result will
     *    be the user object that was produced, or null if none could be.
     */
    public void provideUser(final Contextor contextor, Connection connection,
                            JSONObject param, final ArgRunnable handler) {
        final DeviceCredentials creds =
            extractCredentials(contextor.appTrace(), param);
        if (creds != null) {
            contextor.server().enqueueSlowTask(new Callable<Object>() {
                public Object call() {
                    contextor.queryObjects(deviceQuery(creds.uuid), null, 0,
                      new DeviceQueryResultHandler(contextor, creds, handler));
                    return null;
                }
            }, null);
        } else {
            handler.run(null);
        }        
    }

    private class DeviceQueryResultHandler implements ArgRunnable {
        private Contextor myContextor;
        private DeviceCredentials myCreds;
        private ArgRunnable myHandler;

        DeviceQueryResultHandler(Contextor contextor, DeviceCredentials creds,
                                 ArgRunnable handler)
        {
            myContextor = contextor;
            myCreds = creds;
            myHandler = handler;
        }

        public void run(Object queryResult) {
            User user;
            Object result[] = (Object []) queryResult;
            if (result != null && result.length > 0) {
                if (result.length > 1) {
                    myContextor.appTrace().warningm("uuid query loaded " +
                        result.length + " users, choosing first");
                }
                user = (User) result[0];
            } else {
                String name = myCreds.name;
                if (name == null) {
                    name = "AnonUser";
                }
                String uuid = myCreds.uuid;
                myContextor.appTrace().eventi("synthesizing user record for " +
                                              uuid);
                DeviceUserMod mod = new DeviceUserMod(uuid);
                user = new User(name, new Mod[] { mod }, null,
                                myContextor.uniqueID("u"), null);
                user.markAsChanged();
            }
            myHandler.run(user);
        }
    }

    private JSONObject deviceQuery(String uuid) {
        // { type: "user",
        //   mods: { $elemMatch: { type: "deviceuser", uuid: UUID }}}

        JSONObject modMatchPattern = new JSONObject();
        modMatchPattern.addProperty("type", "deviceuser");
        modMatchPattern.addProperty("uuid", uuid);

        JSONObject modMatch = new JSONObject();
        modMatch.addProperty("$elemMatch", modMatchPattern);

        JSONObject queryTemplate = new JSONObject();
        queryTemplate.addProperty("type", "user");
        queryTemplate.addProperty("mods", modMatch);

        return queryTemplate;
    }
    
    /**
     * Extract the user login credentials from a user factory parameter object.
     *
     * @param appTrace  Trace object for error logging
     * @param param  User factory parameters
     *
     * @return a credentials object as described by the parameter object given,
     *    or null if parameters were missing or invalide somehow.
     */
    protected DeviceCredentials extractCredentials(Trace appTrace,
                                                   JSONObject param)
    {
        try {
            String uuid = param.getString("uuid");
            String name = param.optString("name", null);
            if (name == null) {
                name = param.optString("nickname", null);
            }
            return new DeviceCredentials(uuid, name);
        } catch (JSONDecodingException e) {
            appTrace.errorm("bad parameter: " + e);
        }
        return null;
    }

    /**
     * Struct object holding login info for a device user.
     */
    static class DeviceCredentials {
        /** The device ID */
        final public String uuid;
        /** Name of the user */
        final public String name;

        DeviceCredentials(String uuid, String name) {
            this.uuid = uuid;
            this.name = name;
        }
    }

}
