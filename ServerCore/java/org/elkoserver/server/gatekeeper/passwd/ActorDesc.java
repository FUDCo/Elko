package org.elkoserver.server.gatekeeper.passwd;

import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.OptBoolean;
import org.elkoserver.foundation.json.OptString;
import org.elkoserver.json.EncodeControl;
import org.elkoserver.json.Encodable;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.util.trace.Trace;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * Database object describing an actor.
 */
class ActorDesc implements Encodable {
    /** The mandatory, invariant, unique, machine readable identifier. */
    private String myID;

    /** The optional, unique, internal use identifier. */
    private String myInternalID;

    /** The optional, variable, non-unique, human readable identifier. */
    private String myName;

    /** Password for login, or null if not password protected. */
    private String myPassword;

    /** Salt for this actor's password. */
    private byte[] mySalt;

    /** Flag controlling permission for actor to modify their own password. */
    private boolean myCanSetPass;

    /** Random number generator, for generating password salt. */
    static private SecureRandom theRandom = new SecureRandom();

    /** Object to SHA hash passwords. */
    private static MessageDigest theSHA;
    static {
        try {
            theSHA = MessageDigest.getInstance("SHA");
        } catch (NoSuchAlgorithmException e) {
            /* According to Sun's documentation, this exception can't actually
               happen, since the JVM is required to support the SHA algorithm.
               However, the compiler requires the catch.  And it *could* happen
               if either the documentation or the JVM implementation are wrong.
               Like that ever happens. */
            Trace.comm.fatalError("This JVM lacks SHA support");
        }
    }

    /**
     * Normal constructor.
     *
     * @param id  The unique ID.
     * @param internalID  The internal ID, or null for none.
     * @param name  The human readable label.
     * @param password  Login password (plaintext), or null for none.
     * @param canSetPass  Permission to change password.
     */
    ActorDesc(String id, String internalID, String name, String password,
              boolean canSetPass)
    {
        myID = id;
        myInternalID = internalID;
        myName = name;
        setPassword(password);
        myCanSetPass = canSetPass;
    }

    /**
     * JSON-driven constructor.
     *
     * @param id  The unique ID.
     * @param internalID  The internal ID.
     * @param name  The human readable label.
     * @param password  Login password (hashed).
     * @param canSetPass  Permission to change password.
     */
    @JSONMethod({ "id", "iid", "name", "password", "cansetpass" })
    ActorDesc(String id, OptString optInternalID, OptString optName,
              OptString optPassword, OptBoolean optCanSetPass)
    {
        myID = id;
        myInternalID = optInternalID.value(null);
        myName = optName.value(null);
        myPassword = optPassword.value(null);
        if (myPassword == null) {
            mySalt = null;
        } else {
            mySalt = new byte[4];
            for (int i = 0; i < 4; ++i) {
                String frag = myPassword.substring(i*2, i*2+2);
                mySalt[i] = (byte) (Integer.parseInt(frag, 16) & 0xFF);
            }
        }
        myCanSetPass = optCanSetPass.value(true);
    }

    /**
     * Test if this actor can set their own password.
     * 
     * @return true if this actor can set their own password.
     */
    boolean canSetPass() {
        return myCanSetPass;
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
        JSONLiteral result = new JSONLiteral("actor", control);
        result.addParameter("id", myID);
        result.addParameterOpt("iid", myInternalID);
        result.addParameterOpt("name", myName);
        result.addParameterOpt("password", myPassword);
        if (!myCanSetPass) {
            result.addParameter("cansetpass", myCanSetPass);
        }
        result.finish();
        return result;
    }

    /**
     * Compute a string that represents the SHA hash of a password string.
     *
     * @param salt  Random salt to impede dictionary attacks.
     * @param password  The password to hash.
     */
    private String hashPassword(byte[] salt, String password) {
        if (password == null) {
            password = "";
        }
        theSHA.update(salt);
        theSHA.update(password.getBytes());
        byte hash[] = theSHA.digest();
        char encoded[] = new char[hash.length*2 + 8];
        for (int i = 0; i < 4; ++i) {
            encoded[i*2 + 0] =
                Integer.toHexString((salt[i] & 0xF0) >> 4).charAt(0);
            encoded[i*2 + 1] =
                Integer.toHexString((salt[i] & 0x0F)     ).charAt(0);
        }
        for (int i = 0; i < hash.length; ++i) {
            encoded[i*2 + 8] =
                Integer.toHexString((hash[i] & 0xF0) >> 4).charAt(0);
            encoded[i*2 + 9] =
                Integer.toHexString((hash[i] & 0x0F)     ).charAt(0);
        }
        return new String(encoded);
    }

    /**
     * Get this actor's unique ID.
     *
     * @return this actor's unique ID.
     */
    String id() {
        return myID;
    }

    /**
     * Get this actor's internal ID.
     *
     * @return this actor's internal ID.
     */
    String internalID() {
        if (myInternalID != null) {
            return myInternalID;
        } else {
            return myID;
        }
    }

    /**
     * Get this actor's human-readable label.
     *
     * @return this actor's human-readable label.
     */
    String name() {
        return myName;
    }

    /**
     * Set this actor's permission to change their password.
     *
     * @param canSetPass  true if actor can change their password, false if not
     */
    void setCanSetPass(boolean canSetPass) {
        myCanSetPass = canSetPass;
    }

    /**
     * Set this actor's internal identifier.
     *
     * @param internalID  New internal ID.
     */
    void setInternalID(String internalID) {
        if ("".equals(internalID)) {
            myInternalID = null;
        } else {
            myInternalID = internalID;
        }
    }

    /**
     * Set this actor's name.
     *
     * @param name  New name.
     */
    void setName(String name) {
        if ("".equals(name)) {
            myName = null;
        } else {
            myName = name;
        }
    }

    /**
     * Set this actor's password.
     *
     * @param password  New password (null for no password).
     */
    void setPassword(String password) {
        if (password == null) {
            mySalt = null;
            myPassword = null;
        } else {
            mySalt = new byte[4];
            theRandom.nextBytes(mySalt);
            myPassword = hashPassword(mySalt, password);
        }
    }

    /**
     * Test if a string matches this actor's password.
     *
     * @param password  String to test.
     *
     * @return true if 'password' matches this actor's password.
     */
    boolean testPassword(String password) {
        if (myPassword == null) {
            return true;
        } else {
            if (password == null) {
                password = "";
            }
            return myPassword.equals(hashPassword(mySalt, password));
        }
    }
}
