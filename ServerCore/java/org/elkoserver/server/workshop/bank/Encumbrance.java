package org.elkoserver.server.workshop.bank;

import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.OptString;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.json.Encodable;
import org.elkoserver.json.EncodeControl;

/**
 * Object representing an encumbrance, a tentative reservation of the funds in
 * some account.
 */
class Encumbrance implements Comparable, Encodable {
    /** This encumbrance's unique identifier string. */
    private String myRef;

    /** The account this encumbrance encumbers. */
    private Account myAccount;

    /** The quantity of funds encumbered. */
    private int myAmount;

    /** Time when this encumbrance expires. */
    private ExpirationDate myExpires;

    /** Arbitrary annotation associated with the encumbrance at creation
        time. */
    private String myMemo;

    /**
     * Direct constructor.
     *
     * @param ref  Reference string for the encumbrance
     * @param account  The account being encumbered
     * @param amount  The amount being encumbered
     * @param expires  When the encumbrance should vanish if not released or
     *    redeemed.
     * @param memo  Annotation on encumbrance.
     */
    Encumbrance(String ref, Account account, int amount,
                ExpirationDate expires, String memo)
    {
        myRef = ref;
        myAccount = account;
        myAmount = amount;
        myExpires = expires;
        myMemo = memo;
    }

    /**
     * JSON-driven constructor.
     *
     * @param ref  Reference string for the encumbrance
     * @param amount  The amount being encumbered
     * @param expires  When the encumbrance should vanish if not released or
     *    redeemed.
     * @param memo  Optional annotation on encumbrance.
     */
    @JSONMethod({ "ref", "amount", "expires", "memo" })
    Encumbrance(String ref, int amount, ExpirationDate expires, OptString memo)
    {
        this(ref, null, amount, expires, memo.value(null));
    }

    /**
     * Encode this encumbrance for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     *    should be done.
     *
     * @return a JSON literal representing this encumbrance.
     */
    public JSONLiteral encode(EncodeControl control) {
        if (control.toRepository()) {
            JSONLiteral result = new JSONLiteral(control);
            result.addParameter("ref", myRef);
            result.addParameter("amount", myAmount);
            result.addParameter("expires", myExpires);
            result.addParameterOpt("memo", myMemo);
            result.finish();
            return result;
        } else {
            return null;
        }
    }

    /**
     * Obtain the account this encumbrance encumbers.
     *
     * @return this encumbrance's account.
     */
    Account account() {
        return myAccount;
    }

    /**
     * Obtain the quantity of funds this encumbrance encumbers.
     *
     * @return this encumbrance's amount.
     */
    int amount() {
        return myAmount;
    }

    /**
     * Compare this encumbrance to another according to the dictates of the
     * standard Java Comparable interface.  Encumbrances are compared by
     * comparing their expiration dates.  Two encumbrances with the same
     * expiration date are compared by the values of their hash codes, just so
     * that sorts will be stable.
     *
     * @param other  The other encumbrance to compare to.
     *
     * @return a value less than, equal to, or greater than zero according to
     *    whether this encumbrance's expiration date is before, at, or after
     *    other's.
     */
    public int compareTo(Object other) {
        Encumbrance otherEnc = (Encumbrance) other;
        int result = myExpires.compareTo(otherEnc.myExpires);
        if (result == 0) {
            result = hashCode() - other.hashCode();
        }
        return result;
    }

    /**
     * Obtain the date after which this enumbrance no longer encumbers.
     *
     * @return this encumbrance's expiration date.
     */
    ExpirationDate expires() {
        return myExpires;
    }

    /**
     * Test if this encumbrance is expired.
     *
     * @return true if this encumbrance is expired, false if not.
     */
    boolean isExpired() {
        return myExpires.isExpired();
    }

    /**
     * Obtain this encumbrance's memo string, an arbitrary annotation
     * associated with the encumbrance when it was created.
     *
     * @return this encumbrance's memo.
     */
    String memo() {
        return myMemo;
    }

    /**
     * Redeem this encumbrance, subtracting the encumbered amount from its
     * account's total balance.
     *
     * @return the amount of money withdrawn by the redemption.
     */
    int redeem() {
        return myAccount.redeemEncumbrance(this);
    }

    /**
     * Obtain this encumbrance's unique identifier string.
     *
     * @return this encumbrance's ref.
     */
    String ref() {
        return myRef;
    }

    /**
     * Release this encumbrance, adding the encumbered amount back to its
     * account's available balance.
     */
    void release() {
        myAccount.releaseEncumbrance(this);
    }

    /**
     * Assign the account associated with this encumbrance.  This operation
     * may only be done once per encumbrance object.
     *
     * @param account   The account that this encumbrance encumbers.
     */
    void setAccount(Account account) {
        if (myAccount != null) {
            throw new Error(
                "attempt to set account on encumbrance that already has one");
        }
        myAccount = account;
    }
}
