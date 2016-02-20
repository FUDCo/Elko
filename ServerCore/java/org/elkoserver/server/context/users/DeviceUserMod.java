package org.elkoserver.server.context.users;

import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.json.EncodeControl;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.server.context.Mod;
import org.elkoserver.server.context.UserMod;

/**
 * This Mod holds device specific identity information for a user.
 */
public class DeviceUserMod extends Mod implements UserMod {
    /** Device specific UID, a device dependent user ID */
    private String myDeviceUUID;

    /**
     * JSON-driven constructor.
     *
     * @param uuid  The Device specific user ID
     */
    @JSONMethod({ "uuid" })
    public DeviceUserMod(String uuid) {
        myDeviceUUID = uuid;
    }

    /**
     * Get the Device UID
     */
    public String uuid() {
        return myDeviceUUID;
    }

    /**
     * Encode this mod for transmission or persistence.  This mod is
     * persisted but never transmitted to a client.
     *
     * @param control  Encode control determining what flavor of encoding
     *    should be done.
     *
     * @return a JSONLiteral representing the encoded state of this mod.
     */
    public JSONLiteral encode(EncodeControl control) {
        if (control.toRepository()) {
            JSONLiteral obj = new JSONLiteral("deviceuser", control);
            obj.addParameter("uuid", myDeviceUUID);
            obj.finish();
            return obj;
        } else {
            return null;
        }
    }
}
