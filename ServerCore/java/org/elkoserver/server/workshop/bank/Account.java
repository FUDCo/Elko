package org.elkoserver.server.workshop.bank;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;
import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.OptBoolean;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.json.Encodable;
import org.elkoserver.json.EncodeControl;
import org.elkoserver.server.workshop.Workshop;
import org.elkoserver.util.ArgRunnable;

/**
 * Object representing an account: a store of money in some currency belonging
 * to some user.
 */
class Account implements Encodable {
    /** Account's reference string, its unique identifier. */
    private String myRef;

    /** This instance's version number, for ensuring atomic updates. */
    private int myVersion;

    /** Name of currency this account is denominated in. */
    private String myCurrency;

    /** User ref of the account's owner. */
    private String myOwner;

    /** Arbitrary annotation associated with the account at creation time. */
    private String myMemo;

    /** Flag that account is blocked from participating in transactions. */
    private boolean amFrozen;

    /** Total amount of money (both available & encumbered) in the account. */
    private int myTotalBalance;

    /** Amount of unencumbered funds in account. */
    private int myAvailBalance;

    /** Collection of encumbrances, sorted by expiration date. */
    private TreeSet<Encumbrance> myEncumbrancesByExpiration;

    /** Collection of encumbrances, indexed by ref. */
    private Map<String, Encumbrance> myEncumbrancesByRef;

    /** Flag indicating that this account has been deleted. */
    private boolean amDeleted;

    /**
     * Direct constructor.
     *
     * @param ref  Reference string for account.
     * @param version  The current version number of the account.
     * @param currency  Currency in which account will be denominated.
     * @param owner  Ref of account owner.
     * @param memo  Annotation on account.
     */
    Account(String ref, int version, String currency, String owner,
            String memo)
    {
        myRef = ref;
        myVersion = version;
        myCurrency = currency;
        myOwner = owner;
        myMemo = memo;
        amFrozen = false;
        myAvailBalance = 0;
        myTotalBalance = 0;
        myEncumbrancesByExpiration = new TreeSet<Encumbrance>();
        myEncumbrancesByRef = new HashMap<String, Encumbrance>();
        amDeleted = false;
    }

    /**
     * JSON-driven constructor.
     *
     * @param ref  Reference string for account.
     * @param version  The current version number of the account.
     * @param currency  Currency in which account will be denominated.
     * @param owner  Ref of account owner.
     * @param memo  Annotation on account.
     * @param balance  Total amount of funds in account (including encumbered)
     * @param frozen  Flag indicating whether or not account is frozen.
     * @param encumbrances  Encumbrances on the account.
     * @param deleted  Flag indicating whether or not account is deleted.
     */
    @JSONMethod({ "ref", "version", "curr", "owner", "memo", "bal", "frozen",
                "encs", "deleted" })
    Account(String ref, int version, String currency, String owner,
            String memo, int totalBalance, boolean frozen,
            Encumbrance[] encumbrances, OptBoolean deleted)
    {
        this(ref, version, currency, owner, memo);
        myTotalBalance = totalBalance;
        myAvailBalance = totalBalance;
        amFrozen = frozen;
        for (Encumbrance enc : encumbrances) {
            if (!enc.isExpired()) {
                enc.setAccount(this);
                myEncumbrancesByExpiration.add(enc);
                myEncumbrancesByRef.put(enc.ref(), enc);
                myAvailBalance -= enc.amount();
            }
        }
        amDeleted = deleted.value(false);
    }

    /**
     * Encode this account for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     *    should be done.
     *
     * @return a JSON literal representing this account.
     */
    public JSONLiteral encode(EncodeControl control) {
        if (control.toRepository()) {
            JSONLiteral result = new JSONLiteral("bankacct", control);
            result.addParameter("ref", myRef);
            result.addParameter("version", myVersion);
            result.addParameter("curr", myCurrency);
            result.addParameter("owner", myOwner);
            result.addParameter("memo", myMemo);
            result.addParameter("bal", myTotalBalance);
            result.addParameter("frozen", amFrozen);
            result.addParameter("encs", myEncumbrancesByExpiration.toArray());
            if (amDeleted) {
                result.addParameter("deleted", true);
            }
            result.finish();
            return result;
        } else {
            return null;
        }
    }

    /**
     * Obtain the available account balance.  This is the amount of
     * unencumbered funds currently in the account.
     *
     * @return the quantity of available funds in the account.
     */
    int availBalance() {
        return myAvailBalance;
    }

    /**
     * Save the state of this account to persistent storage.
     *
     * @param workshop  Workshop object this account is being managed within,
     *    for access to the persistent store.
     * @param collection  MongoDB collection into which to save the account.
     * @param resultHandler  Handler that will be invoked with status of
     *    write, after completion.
     */
    void checkpoint(Workshop workshop, String collection,
                    ArgRunnable resultHandler)
    {
        if (myVersion == 0) {
            myVersion = 1;
            workshop.putObject(myRef, this, collection, resultHandler);
        } else {
            workshop.updateObject(myRef, myVersion++, this, collection,
                                  resultHandler);
        }
    }

    /**
     * Obtain the currency in which this account is denominated.
     *
     * @return the name of this account's currency.
     */
    String currency() {
        return myCurrency;
    }

    /**
     * Mark this account as deleted.
     */
    void delete() {
        if (myTotalBalance > 0) {
            throw new Error("attempt to delete non-empty account");
        }
        amDeleted = true;
    }

    /**
     * Add to this account's balance.
     *
     * @param amount  The amount of funds to deposit.
     */
    void deposit(int amount) {
        if (amount < 0) {
            throw new Error("deposit negative amount");
        }
        myTotalBalance += amount;
        myAvailBalance += amount;
    }

    /**
     * Encumber some of this account's available funds.
     *
     * @param enc  The encumbrance to add to this account.
     */
    void encumber(Encumbrance enc) {
        if (myAvailBalance < enc.amount()) {
            throw new Error("insufficient funds");
        }
        myEncumbrancesByExpiration.add(enc);
        myEncumbrancesByRef.put(enc.ref(), enc);
        myAvailBalance -= enc.amount();
    }

    /**
     * Obtain an object that can be used for enumerating the account's
     * encumbrances.
     *
     * @return an interable over the current collection of encumbrances.
     */
    Iterable<Encumbrance> encumbrances() {
        return myEncumbrancesByExpiration;
    }

    /**
     * Obtain one of this account's encumbrances.
     *
     * @param encRef  The ref of the encumbrance sought.
     *
     * @return the requested encumbrance, or null if the given ref does not
     *    designate one of this account's encumbrances.
     */
    Encumbrance getEncumbrance(String encRef) {
        return myEncumbrancesByRef.get(encRef);
    }

    /**
     * Test if this account is deleted.
     *
     * @return true if this account is deleted, false if not.
     */
    boolean isDeleted() {
        return amDeleted;
    }

    /**
     * Test if this account is frozen.
     *
     * @return true if this account is frozen, false if not.
     */
    boolean isFrozen() {
        return amFrozen;
    }

    /**
     * Obtain this account's memo string, an arbitrary annotation associated
     * with the account when it was created.
     *
     * @return this account's memo.
     */
    String memo() {
        return myMemo;
    }

    /**
     * Obtain the ref of the account's owner.
     *
     * @return this account's owner ref.
     */
    String owner() {
        return myOwner;
    }

    /**
     * Redeem an encumbrance on this account, subtracting the encumbered
     * amount from the total balance.
     *
     * @param enc  The encumbrance being redeemed.
     *
     * @return the amount of money withdrawn by the redemption.
     */
    int redeemEncumbrance(Encumbrance enc) {
        myEncumbrancesByExpiration.remove(enc);
        myEncumbrancesByRef.remove(enc.ref());
        myTotalBalance -= enc.amount();
        return enc.amount();
    }

    /**
     * Obtain this account's unique identifier string.
     *
     * @return this account's ref.
     */
    String ref() {
        return myRef;
    }

    /**
     * Release an encumbrance from this account, adding the encumbered amount
     * back to the available balance.
     *
     * @param enc  The encumbrance to release.
     */
    void releaseEncumbrance(Encumbrance enc) {
        if (!myEncumbrancesByExpiration.remove(enc)) {
            throw new Error("attempt to remove encumbrance that wasn't there");
        }
        myEncumbrancesByRef.remove(enc.ref());
        myAvailBalance += enc.amount();
    }

    /**
     * Release any expired encumbrances on this account.
     */
    void releaseExpiredEncumbrances() {
        while (!myEncumbrancesByExpiration.isEmpty()) {
            Encumbrance first = myEncumbrancesByExpiration.first();
            if (first.isExpired()) {
                first.release();
            } else {
                break;
            }
        }
    }

    /**
     * Freeze or unfreeze this account.
     *
     * @param frozen  Flag indicated the desired freeze state.
     */
    void setFrozen(boolean frozen) {
        amFrozen = frozen;
    }

    /**
     * Obtain the total amount of money in this account, both available and
     * encumbered.
     *
     * @return this account's total balance.
     */
    int totalBalance() {
        return myTotalBalance;
    }

    /**
     * Subtract from this account's balance.
     *
     * @param amount  The amount of funds to withdraw.
     */
    void withdraw(int amount) {
        if (amount < 0) {
            throw new Error("withdraw negative amount");
        } else if (myAvailBalance < amount) {
            throw new Error("insufficient funds");
        }
        myTotalBalance -= amount;
        myAvailBalance -= amount;
    }
}
