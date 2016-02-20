package org.elkoserver.foundation.server.metadata;

import org.elkoserver.foundation.boot.BootProperties;
import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.OptString;
import org.elkoserver.json.Encodable;
import org.elkoserver.json.EncodeControl;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.util.trace.Trace;

/**
 * Descriptor containing information required or presented to authorize a
 * connection.
 */
public class AuthDesc implements Encodable {
    /** The authorization mode. */
    private String myMode;

    /** The authorization code, or null if not relevant. */
    private String myCode;

    /** The authorization ID, or null if not relevant. */
    private String myID;

    /** Singleton open authorization descriptor. This may be used in all
        circumstances where open mode authorization is required or
        presented. */
    public static final AuthDesc theOpenAuth =
        new AuthDesc("open", (String) null, (String) null);

    /**
     * Direct constructor.
     *
     * @param mode  Authorization mode.
     * @param code  Authorization code, or null if not relevant.
     * @param id  Authorization ID, or null if not relevant.
     */
    public AuthDesc(String mode, String code, String id) {
        myMode = mode;
        myCode = code;
        myID = id;
    }

    /**
     * JSON-driven constructor.
     *
     * @param mode  Authorization mode.
     * @param code  Optional authorization code.
     * @param id  Optional authorization ID.
     */
    @JSONMethod({ "mode", "code", "id" })
    public AuthDesc(String mode, OptString code, OptString id) {
        this(mode, code.value(null), id.value(null));
    }

    /**
     * Get the authorization code.
     *
     * @return the authorization code (or null if there is none).
     */
    public String code() {
        return myCode;
    }

    /**
     * Produce an AuthDesc object from information contained in the server
     * configuration properties.
     *
     * <p>The authorization mode is extracted from propRoot+".auth.mode".
     * Currently, there are three possible authorization mode values that are
     * recognized: "open", "password", and "reservation".
     *
     * <p>Open mode is unrestricted access.  No additional descriptive
     * information is required for open mode.
     *
     * <p>Password mode requires a secret code string for access.  This code
     * string is extracted from propRoot+".auth.code".  Additionally, an
     * identifier may also be required, which will be extracted from
     * propRoot+".auth.id" if that property is present.
     *
     * <p>Reservation mode requires a reservation string for access.  The
     * reservation string is communicated via a separate pathway, but it
     * optionally may be associated with an identifier extracted from
     * propRoot+".auth.id".
     *
     * @param props  The properties themselves.
     * @param propRoot  Prefix string for all the properties describing the
     *    authorization information of interest.
     * @param appTrace  Trace object for error logging.
     *
     * @return an AuthDesc object constructed according to the properties
     *    rooted at 'propRoot' as described above, or null if no such valid
     *    authorization information could be found.
     */
    public static AuthDesc fromProperties(BootProperties props,
                                          String propRoot, Trace appTrace)
    {
        propRoot += ".auth";
        String mode = props.getProperty(propRoot + ".mode", "open");
        if (mode.equals("open")) {
            return theOpenAuth;
        } else {
            String code = props.getProperty(propRoot + ".code", null);

            if (mode.equals("password")) {
                if (code == null) {
                    appTrace.errorm("missing value for " + propRoot + ".code");
                    return null;
                }
            } else if (!mode.equals("reservation")) {
                appTrace.errorm("unknown value for " + propRoot +
                                ".auth.mode: " + mode);
                return null;
                
            }

            String id = props.getProperty(propRoot + ".id", null);
            return new AuthDesc(mode, code, id);
        }
    }

    /**
     * Get the authorization ID.
     *
     * @return the authorization ID (or null if there is none).
     */
    public String id() {
        return myID;
    }

    /**
     * Get the authorization mode.
     *
     * @return the authorization mode.
     */
    public String mode() {
        return myMode;
    }

    /**
     * Check an authorization.  This authorization descriptor is treated as a
     * set of requirements.  The authorization descriptor given in the 'auth'
     * parameter is treated as a presented set of authorization credentials.
     * The credentials are compared to the requirements to see if they satisfy
     * them.
     *
     * @param auth  Authorization credentials being presented.
     *
     * @return true if 'auth' correctly authorizes connection under
     *    the authorization configuration described by this object.
     */
    public boolean verify(AuthDesc auth) {
        if (auth == null) {
            return myMode.equals("open");
        } else if (myMode.equals(auth.mode())) {
            if (myMode.equals("open")) {
                return true;
            } else if (myMode.equals("password")) {
                return myCode.equals(auth.code());
            } else {
                return false;
            }
        } else {
            return false;
        }
    }
    
    /**
     * Encode this object for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     *    should be done.
     *
     * @return a JSON literal representing this object.
     */
    public JSONLiteral encode(EncodeControl control) {
        if (control.toClient() && myMode.equals("open")) {
            return null;
        } else {
            JSONLiteral result = new JSONLiteral("auth", control);
            result.addParameter("mode", myMode);
            result.addParameterOpt("id", myID);
            result.addParameterOpt("code", myCode);
            result.finish();
            return result;
        }
    }
}

