package org.elkoserver.server.workshop.bank;

import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.OptString;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.json.Encodable;
import org.elkoserver.json.EncodeControl;
import java.util.Arrays;

/**
 * Object representing an access key: an authorization to perform some set of
 * actions.
 */
class Key implements Encodable {
    /** Key's reference string, its unique identifier. */
    private String myRef;

    /** What family of actions this key authorizes. */
    private String myAuth;

    /** Currencies upon which this key authorizes action, or null if the key is
        not scoped by currency. */
    private String[] myCurrencies;

    /** The key that created this key. */
    private Key myParent;

    /** Time after which this key will no longer work. */
    private ExpirationDate myExpires;

    /** Arbitrary annotation associated with the key at creation time. */
    private String myMemo;

    /** Ref of parent key.  Note that this field is valid only during the
        extended key decode/construction process, and must be null in a
        well-formed key object. */
    private String myParentRef;

    /**
     * Direct constructor.
     *
     * @param parent  The key's parent key.
     * @param ref  Reference string for the key.
     * @param auth  Auth code denoting the key's authority.
     * @param currencies  Optional currencies scoping the key's authority.
     * @param expires  The key's expiration date.
     * @param memo  Annotation on key.
     */
    Key(Key parent, String ref, String auth, String[] currencies,
        ExpirationDate expires, String memo)
    {
        myParentRef = null;
        myParent = parent;
        myRef = ref;
        myAuth = auth;
        myExpires = expires;
        myMemo = memo;
        if (currencies != null) {
            Arrays.sort(currencies);
        }
        myCurrencies = currencies;
    }

    /**
     * JSON-driven constructor.
     *
     * @param parentRef  The ref of the key's parent key.
     * @param ref  Reference string for the key.
     * @param auth  Auth code denoting the key's authority.
     * @param currencies  Optional currencies scoping the key's authority.
     * @param expires  The key's expiration date.
     * @param memo  Annotation on key.
     */
    @JSONMethod({ "parent", "ref", "auth", "?currs", "expires", "memo" })
    Key(String parentRef, String ref, String auth, String[] currencies,
        ExpirationDate expires, OptString memo)
    {
        this(null, ref, auth, currencies, expires, memo.value(null));
        myParentRef = parentRef;
    }

    /**
     * Encode this key for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     *    should be done.
     *
     * @return a JSON literal representing this key.
     */
    public JSONLiteral encode(EncodeControl control) {
        if (control.toRepository()) {
            JSONLiteral result = new JSONLiteral(control);
            result.addParameter("ref", myRef);
            result.addParameter("parent", myParent.ref());
            result.addParameter("currs", myCurrencies);
            result.addParameter("auth", myAuth);
            result.addParameter("expires", myExpires);
            result.addParameterOpt("memo", myMemo);
            result.finish();
            return result;
        } else {
            return null;
        }
    }

    /**
     * Test if this key authorizes operations on a particular currency.
     *
     * @param currency  The currency of interest.
     *
     * @return true if this key authorizes operations on the given currency,
     *    false if not.
     */
    boolean allowsCurrency(String currency) {
        if (myParentRef != null) {
            throw new Error("attempt to exercise authority of incomplete key");
        }
        return myCurrencies == null ||
            Arrays.binarySearch(myCurrencies, currency) >= 0;
    }

    /**
     * Test if this key authorizes a particular kind of operation.
     *
     * @param authNeed  The kind of authority that is desired
     *
     * @return true if this key grants the kind of authority sought, false
     *    if not.
     */
    boolean allowsOperation(String authNeed) {
        if (myParentRef != null) {
            throw new Error("attempt to exercise authority of incomplete key");
        }
        if (myAuth.equals("full") || myAuth.equals("curr")) {
            return true;
        } else {
            return myAuth.equals(authNeed);
        }
    }

    /**
     * Obtain the kind of authority this key grants.
     *
     * @return this key's auth value.
     */
    String auth() {
        return myAuth;
    }

    /**
     * Obtain the currencies that scope this key's authority.
     *
     * @return this key's scoping currencies, or null if its authority is not
     *    currency scoped.
     */
    String[] currencies() {
        return myCurrencies;
    }

    /**
     * Obtain the date after which this key no longer works.
     *
     * @return this key's expiration date.
     */
    ExpirationDate expires() {
        return myExpires;
    }

    /**
     * Test if a given key is somewhere in this key's creation ancestry.
     *
     * @param key  The potential parent key of interest.
     *
     * @return true iff the given key was this key's creator, or its creator's
     *    creator, or its creator's creator's creator, etc.
     */
    boolean hasAncestor(Key key) {
        if (myParentRef != null) {
            throw new Error("attempt to test parent of incomplete key");
        }
        if (myParent == key) {
            return true;
        } else if (myParent == null) {
            return false;
        } else {
            return myParent.hasAncestor(key);
        }
    }

    /**
     * Obtain the ref of this key's parent, as part of construction.
     *
     * @return the parent key ref.
     */
    String parentRef() {
        if (myParent != null) {
            throw new Error("attempt to get parent ref of complete key");
        }
        return myParentRef;
    }

    /**
     * Test if this key is expired.
     *
     * @return true if this key is expired, false if not.
     */
    boolean isExpired() {
        return myExpires.isExpired();
    }

    /**
     * Obtain this key's memo string, an arbitrary annotation associated with
     * the key when it was created.
     *
     * @return this key's memo.
     */
    String memo() {
        return myMemo;
    }

    /**
     * Obtain this key's unique identifier string.
     *
     * @return this key's ref.
     */
    String ref() {
        return myRef;
    }

    /**
     * Assign this key's parent key.  This operation may only be done once
     * per key.
     *
     * @param parent  The key that should be regarded as this key's parent.
     */
    void setParent(Key parent) {
        if (myParentRef == null || myParent != null) {
            throw new Error("setParent on a key that already has a parent");
        }
        myParent = parent;
        myParentRef = null;
    }
}
