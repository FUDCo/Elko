package org.elkoserver.server.context.users;

import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.net.Connection;
import org.elkoserver.json.JSONObject;
import org.elkoserver.server.context.Contextor;
import org.elkoserver.server.context.User;
import org.elkoserver.util.ArgRunnable;
import java.util.concurrent.Callable;

/**
 * Factory that generates a non-persistent user object from connected mobile
 * device information.
 */
public class DeviceEphemeralUserFactory extends DevicePersistentUserFactory {

    /**
     * JSON-driven constructor.
     *
     * @param device  The name of the device (IOS, etc).
     */
    @JSONMethod({ "device" })
    public DeviceEphemeralUserFactory(String device) {
        super(device);
    }

  /**
   * Synthesize an ephemeral user object based on user description info
   * fetched from the Device.
   *
   * @param contextor  The contextor of the server in which the synthetic
   *    user will be present
   * @param connection  The connection over which the new user presented
   *    themself.
   * @param param  Arbitary JSON object parameterizing the construction.
   * @param handler  Handler to invoke with the resulting user object, or
   *    with null if the user object could not be produced.
   */
    public void provideUser(Contextor contextor, Connection connection,
                            JSONObject param, ArgRunnable handler)
    {
        DeviceCredentials creds =
            extractCredentials(contextor.appTrace(), param);
        User user = null;
        if (creds != null) {
            user = new User(creds.name, null, null, null, null);
            user.markAsEphemeral();
            user.objectIsComplete();
        }
        handler.run(user);
    }

}
