package org.elkoserver.server.context.test;

import org.elkoserver.foundation.json.Cryptor;
import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.ObjectDecoder;
import org.elkoserver.foundation.net.Connection;
import org.elkoserver.json.JSONDecodingException;
import org.elkoserver.json.JSONObject;
import org.elkoserver.json.SyntaxError;
import org.elkoserver.server.context.Contextor;
import org.elkoserver.server.context.EphemeralUserFactory;
import org.elkoserver.server.context.InternalActor;
import org.elkoserver.server.context.User;
import org.elkoserver.util.trace.Trace;
import java.io.IOException;
import java.util.Iterator;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Test class for the user factory interface.
 *
 * This factory expects to be parameterized with a JSON object of the form:
 *
 * { blob: STRING }
 *
 * where the 'blob' STRING is a JSON object literal encrypted with a key that
 * this factory is configured with at construction time.  This object literal
 * in turn consists of:
 *
 * { nonce: STRING,
 *   expire: INT,
 *   context: CONTEXT_REF,
 *   user: USER_OBJ }
 *
 * where:
 *    'nonce' is a random string token generated from a secure random source
 *    'expire' is the time (seconds since epoch) that the nonce expires
 *    'context' is the optional ref of the context into which the user will go
 *    'user' is a JSON encoded User object, as would be returned by the ODB
 *
 * Upon receiving one of these, if the current time is before the expiration
 * time and the nonce is not a previously received nonce, this factory will
 * construct and return the User object described by the 'user' property.  In
 * all other cases it will return null.
 *
 * Note that this factory will only keep track of unexpired nonces, and further
 * that we expect the expiration times on these to be relatively short (e.g.,
 * 30 seconds -- long enough to flow through the system in normal operation but
 * not long enough to be considered persistent by any standard and not long
 * enough for a large number of them to accumulate in this factory's history of
 * nonces seen).
 */
class TestUserFactory implements EphemeralUserFactory {
    /** Cryptor incorporating the key used to decrypt blobs. */
    private Cryptor myCryptor;

    /** Collection of unexpired (or recently expired) nonces previously seen */
    private SortedSet<Nonce> myNonces;

    /** Timestamp (seconds) we last removed expired nonces from myNonces */
    private long myLastPurgeTime;

    /** Time threshold (seconds) for triggering expired nonce purge */
    private static final int PURGE_TIME_THRESHOLD = 60;

    /** Collection size threshold for triggering expired nonce purge */
    private static final int PURGE_SIZE_THRESHOLD = 1000;

    /**
     * Nonce.  Our nonces consist of an unguessable random string and an
     * expiration time.
     */
    private class Nonce implements Comparable<Nonce> {
        final int expiration;
        final String nonceID;
        Nonce(int expiration, String nonceID) {
            this.expiration = expiration;
            this.nonceID = nonceID;
        }
        public int compareTo(Nonce other) {
            int primary =  expiration - other.expiration;
            if (primary == 0) {
                return nonceID.compareTo(other.nonceID);
            } else {
                return primary;
            } 
        }
    }

    /**
     * JSON-driven constructor.
     */
    @JSONMethod({ "key" })
    public TestUserFactory(String key) {
        myNonces = new TreeSet<Nonce>();
        myLastPurgeTime = System.currentTimeMillis() / 1000;
        try {
            myCryptor = new Cryptor(key);
        } catch (IOException e) {
            Trace.startup.errorm("invalid Cryptor key '" + key +
                                 "' for TestUserFactory");
            myCryptor = null;
        }
    }  

    /**
     * Synthesize a user object.
     *
     * @param contextor  The contextor of the server in which the synthetic
     *    user will be present
     * @param connection  The connection over which the new user presented
     *    themself.
     * @param param  Arbitary JSON object parameterizing the construction.
     * @param contextRef  Ref of context the new synthesized user will be
     *    placed into
     * @param contextTemplate  Ref of the context template for the context
     *
     * @return a synthesized User object constructed according the
     *    parameterized descriptor, or null if no such User object could be
     *    produced for any reason.
     */
    public User provideUser(Contextor contextor, Connection connection,
                            JSONObject param, String contextRef,
                            String contextTemplate)
    {
        if (myCryptor != null) {
            try {
                String blob = param.getString("blob");
                JSONObject params = myCryptor.decryptJSONObject(blob);
                JSONObject userDesc = params.getObject("user");
                int expire = params.getInt("expire");
                long now = System.currentTimeMillis() / 1000;
                if (expire > now) {
                    String nonceID = params.getString("nonce");
                    Nonce nonce = new Nonce(expire, nonceID);
                    if (!myNonces.contains(nonce)) {
                        purgeExpiredNonces(now);
                        myNonces.add(nonce);
                        String reqContextRef =
                            params.optString("context", null);
                        if (reqContextRef != null &&
                                !reqContextRef.equals(contextRef)) {
                            contextor.appTrace().errorm(
                                "context ref mismatch");
                            return null;
                        }
                        String reqContextTemplate =
                            params.optString("ctmpl", null);
                        if (reqContextTemplate != null &&
                                !reqContextTemplate.equals(contextTemplate)) {
                            contextor.appTrace().errorm(
                                "context template ref mismatch");
                            return null;
                        }
                        Object result =
                            ObjectDecoder.decode(User.class, userDesc,
                                                 contextor.odb());
                        return (User) result;
                    } else {
                        contextor.appTrace().errorm("reused nonce");
                    }
                } else {
                    contextor.appTrace().errorm("expired nonce");
                }
            } catch (IOException e) {
                contextor.appTrace().errorm("malformed cryptoblob");
            } catch (SyntaxError e) {
                contextor.appTrace().errorm("bad JSON string in cryptoblob");
            } catch (JSONDecodingException e) {
                contextor.appTrace().errorm("missing or improperly typed property in cryptoblob");
            }
        }        
        return null;
    }

    /**
     * Scan through the collection of seen nonces and throw away the expired
     * ones, if it's been long enough since we last did that or if the
     * collection has grown too big.
     *
     * @param now  Time (seconds since epoch) that we are doing this
     */
    private void purgeExpiredNonces(long now) {
        if (now - myLastPurgeTime > PURGE_TIME_THRESHOLD ||
                myNonces.size() > PURGE_SIZE_THRESHOLD) {
            myLastPurgeTime = now;
            Iterator<Nonce> iter = myNonces.iterator();
            while (iter.hasNext()) {
                Nonce nonce = iter.next();
                if (nonce.expiration < now) {
                    iter.remove();
                } else {
                    break;
                }
            }
        }
    }
}
