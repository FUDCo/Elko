package org.elkoserver.server.workshop.bank;

import java.security.SecureRandom;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;
import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.OptString;
import org.elkoserver.json.Encodable;
import org.elkoserver.json.EncodeControl;
import org.elkoserver.json.JSONArray;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.json.JSONObject;
import org.elkoserver.server.workshop.Workshop;
import org.elkoserver.util.ArgRunnable;
import org.elkoserver.util.trace.Trace;

/**
 * The Elko bank object.
 *
 * Each of the key abstractions managed by the bank is represented by its own
 * class: Account, Bank, Currency, Encumbrance, ExpirationDate and Key.
 *
 * However, due to way things get read and written, the persistent
 * representation consists of a smaller number of somewhat more complicated
 * objects.  In the persistent form there are two kinds of objects: the account
 * and the bank.
 *
 * ExpirationDate objects have a very simple representation and so are simply
 * represented as attributes of the things they are expiration dates for (keys
 * and encumbrances).
 *
 * Currency and Key objects are stored as part of the persistent form of the
 * Bank object.  The central motivating idea here is that these objects are (a)
 * few in number and (b) change infrequently.  Consequenly, we manage these
 * objects' persistent states as part of the persistent state of the Bank and
 * simply checkpoint the bank whenever a currency is added or a key is created
 * or cancelled.
 *
 * Encumbrances are stored as part of the persistent form of the Account
 * object.  Each account stores the collection of encumbrances on that account.
 * The central motivating idea here is that an encumbrance is innately
 * associated with the account that it encumbers, and further that, although
 * there may be a large number of encumbrances overall, there will typically be
 * a small number (typically zero) of encumbrances on any particular account.
 * Normally we will have little reason to be messing about with an encumbrance
 * except as part of messing about with its account, so reading and writing an
 * account object simply to modify an encumbrance doesn't generally introduce
 * additional overhead because we'd normally have to be reading or writing the
 * account anyway in such a case.
 */
class Bank implements Encodable {
    /** Currently defined currencies, by name. */
    private Map<String, Currency> myCurrencies;

    /** Access keys, by key ref. */
    private Map<String, Key> myKeys;

    /** A new root key that has been generated but not issued. */
    private Key myVirginRootKey;

    /** The reference string of this bank's root key. */
    private String myRootKeyRef;

    /** Random number source for generatng new refs. */
    static private SecureRandom theRandom = new SecureRandom();

    /** The workshop in which this bank is running. */
    private Workshop myWorkshop;

    /** Trace object, for logging. */
    private Trace myTrace;

    /** Reference string by which this bank is known. */
    private String myRef;

    /** MongoDB collection into which account data will be stored. */
    private String myAccountCollection;

    /**
     * JSON-driven constructor.
     *
     * @param ref  The ref of this bank object.
     * @param rootKeyRef  Optional ref of the key that is the root of this
     *    bank's authorization key hierarchy.  If omitted, a new root key will
     *    be generated and made available for issuance.
     * @param keys  Array of keys for access to this bank's contents.
     * @param currencies  Array of currencies this bank is managing.
     * @param accountCollection  Optional collection name for account storage.
     */
    @JSONMethod({ "ref", "rootkey", "keys", "currencies", "collection" })
    Bank(String ref, OptString rootKeyRef, Key keys[], Currency currencies[],
         OptString accountCollection)
    {
        myRef = ref;
        myCurrencies = new HashMap<String, Currency>();
        for (Currency curr : currencies) {
            myCurrencies.put(curr.name(), curr);
        }
        myKeys = new HashMap<String, Key>();
        for (Key key : keys) {
            myKeys.put(key.ref(), key);
        }
        myAccountCollection = accountCollection.value(null);

        myRootKeyRef = rootKeyRef.value(null);
        myVirginRootKey = null;
        boolean rootKeyGenerated = false;
        if (myRootKeyRef == null) {
            myRootKeyRef = generateRef("key");
            rootKeyGenerated = true;
        }
        Key rootKey = new Key(null, myRootKeyRef, "full", null,
                              new ExpirationDate(Long.MAX_VALUE), "root key");
        myKeys.put(myRootKeyRef, rootKey);
        if (rootKeyGenerated) {
            myVirginRootKey = rootKey;
        }
        for (Key key : keys) {
            if (key != rootKey) {
                Key parentKey = myKeys.get(key.parentRef());
                if (parentKey != null) {
                    key.setParent(parentKey);
                } else {
                    throw new Error("key " + key.ref() +
                                    " claims non-existent parent key " +
                                    key.parentRef());
                }
            }
        }
    }

    /**
     * Encode this bank for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     *    should be done.
     *
     * @return a JSON literal representing this bank.
     */
    public JSONLiteral encode(EncodeControl control) {
        if (control.toRepository()) {
            JSONLiteral result = new JSONLiteral("bank", control);
            result.addParameter("ref", myRef);
            result.addParameter("rootkey", myRootKeyRef);
            Key rootKey = myKeys.remove(myRootKeyRef);
            result.addParameter("keys", myKeys.values().toArray());
            myKeys.put(myRootKeyRef, rootKey);
            result.addParameter("currencies", myCurrencies.values().toArray());
            result.addParameterOpt("collection", myAccountCollection);
            result.finish();
            return result;
        } else {
            return null;
        }
    }

    /**
     * Make this bank live in a running workshop.
     *
     * @param workshop  The workshop this bank is running inside.
     */
    void activate(Workshop workshop) {
        myWorkshop = workshop;
        myTrace = myWorkshop.appTrace();
        if (myVirginRootKey != null) {
            checkpoint();
        }
    }

    /**
     * Write this bank's state to persistent storage.
     */
    private void checkpoint() {
        myWorkshop.putObject(myRef, this);
    }

    /**
     * Obtain an object that can be used for enumerating the valid currencies.
     *
     * @return an iterable over the current collection of currencies.
     */
    Iterable<Currency> currencies() {
        return myCurrencies.values();
    }

    /**
     * Delete a key.  This has the side effect of also deleting any keys for
     * which the given key is an ancestor.
     *
     * @param key  The key to be deleted.
     */
    void deleteKey(Key key) {
        for (Key otherKey : myKeys.values()) {
            if (otherKey.hasAncestor(key)) {
                myKeys.remove(otherKey.ref());
            }
        }
        myKeys.remove(key.ref());
        checkpoint();
    }

    /**
     * Inner logic of single-account atomic update, shared in common by the
     * withAccount() and withEncumberedAccount() methods.
     *
     * Note that at the stage at which this method is invoked it is not yet
     * known if the object that was read from the database is actually an
     * account object, but it should be.  Part of the job of this method is to
     * test for that.
     *
     * @param refRef  The ref by which the account or encumbrance was located.
     * @param allegedAccount  The object that was read from the database.
     * @param updater  Updater to effect the desired account modification.
     */
    private void doAccountUpdate(String refRef, Object allegedAccount,
                                 final AccountUpdater updater)
    {
        if (allegedAccount instanceof Account) {
            final Account account = (Account) allegedAccount;
            account.releaseExpiredEncumbrances();
            if (updater.modify(account)) {
                account.checkpoint(myWorkshop, myAccountCollection,
                                   new ArgRunnable() {
                    public void run (Object resultObj) {
                        String failure = (String) resultObj;
                        if (failure == null) {
                            /* Success */
                            updater.complete(null);
                        } else if (failure.charAt(0) == '@') {
                            /* Retryable error */
                            myTrace.debugm(account.ref() +
                                           " transaction retry: " + failure);
                            withAccount(account.ref(), updater);
                        } else {
                            /* Un-retryable error */
                            myTrace.errorm(account.ref() +
                                           " transaction aborted: " + failure);
                            updater.complete(failure);
                        }
                    }
                });
            }
        } else {
            myTrace.errorm("alleged account object obtained via "+ refRef +
                           " is not an account");
            updater.modify(null);
        }
    }


    /**
     * Inner logic of dual-account atomic update, shared in common by the
     * withTwoAccounts() and withEncumbranceAndAccount() methods.
     *
     * Note that at the stage at which this method is invoked it is not yet
     * known if the objects that were read from the database are actually
     * account objects, but they should be.  Part of the job of this method is
     * to test for that.  Further, because the order in which the database
     * retrieves objects in a multi-object query is undefined, even though one
     * object will correspond to the first ref parametr and the other will
     * correspond to the second, at the stage at which this is called, it is
     * not known which is which.  It is also the job of this method to ensure
     * that the two account objects are in the proper order before passing them
     * to the updater.
     *
     * @param refRef1  The ref by which the first account or encumbrance was
     *    located.
     * @param allegedAccount1  The first object that was read from the
     *    database.
     * @param refRef2  The ref by which the second account or encumbrance was
     *    located.
     * @param allegedAccount1  The second object that was read from the
     *    database.
     * @param updater  Updater to effect the desired account modifications.
     */
    private void doDualAccountUpdate(String refRef1, Object allegedAccount1,
                                     String refRef2, Object allegedAccount2,
                                     final DualAccountUpdater updater)
    {
        if (allegedAccount1 instanceof Account &&
                allegedAccount2 instanceof Account) {
            Account readAccount1 = (Account) allegedAccount1;
            Account readAccount2 = (Account) allegedAccount2;
            if (!refRef2.equals(readAccount2.ref())) {
                Account temp = readAccount1;
                readAccount1 = readAccount2;
                readAccount2 = temp;
            }
            final Account account1 = readAccount1;
            final Account account2 = readAccount2;
            account1.releaseExpiredEncumbrances();
            account2.releaseExpiredEncumbrances();
            if (updater.modify(account1, account2)) {
                account1.checkpoint(myWorkshop, myAccountCollection,
                                    new ArgRunnable() {
                    public void run(Object resultObj) {
                        String failure = (String) resultObj;
                        if (failure == null) {
                            /* Success writing account 1 */
                            account2.checkpoint(myWorkshop,
                                                myAccountCollection,
                                                new ArgRunnable() {
                                public void run(Object resultObj2) {
                                    String failure2 = (String) resultObj2;
                                    if (failure2 == null) {
                                        /* Success writing account 2 */
                                        updater.complete(null);
                                    } else if (failure2.charAt(0) == '@') {
                                        /* Not-really-retryable error */
                                        myTrace.errorm("Egregious failure: " + account2.ref() + " atomic update failure: " + failure2 + " AFTER phase 1 update success!");
                                    } else {
                                        /* Really-unretryable error */
                                        myTrace.errorm("Egregious failure: " + account2.ref() + " write failure: " + failure2 + " AFTER phase 1 update success!");
                                    }
                                }
                            });
                        } else if (failure.charAt(0) == '@') {
                            /* Retryable error */
                            myTrace.debugm(account1.ref() +
                                           " transaction retry: " + failure);
                            withTwoAccounts(account1.ref(), account2.ref(),
                                            updater);
                        } else {
                            /* Un-retryable error */
                            myTrace.errorm(account1.ref() +
                                           " transaction aborted: " + failure);
                            updater.complete(failure);
                        }
                    }
                });
            }
        } else {
            myTrace.errorm("at least one alleged account object obtained via "
                           + refRef1 + "+" + refRef2 + " is not an account");
            updater.modify(null, null);
        }
    }

    /**
     * Generate a new ref string for a new object.
     *
     * @param prefix  Ref prefix onto which an additional, unguessable token
     *    will be appended.
     *
     * @return a new reference string with the given prefix.
     */
    String generateRef(String prefix) {
        return prefix + '-' + Long.toHexString(Math.abs(theRandom.nextLong()));
    }

    /**
     * Lookup a currency by its name.
     *
     * @param currency  Name of the currency of interest.
     *
     * @return a Currency object describing the named currency, or null if
     *    there is no such currency.
     */
    Currency getCurrency(String currency) {
        return myCurrencies.get(currency);
    }

    /**
     * Lookup a key by its reference string.
     *
     * @param keyRef  The key desired.
     *
     * @return the Key with the given ref, or null if there is none.
     */
    Key getKey(String keyRef) {
        if (keyRef == null) {
            return null;
        } else {
            Key key = myKeys.get(keyRef);
            if (key.isExpired()) {
                deleteKey(key);
                key = null;
            }
            return key;
        }
    }

    /**
     * Obtain the bank's root key if nobody has yet gotten it.  This method
     * can only be usefully called once.
     *
     * @return this bank's root key if it hasn't previously been issued, else
     *    null.
     */
    Key issueRootKey() {
        Key result = myVirginRootKey;
        myVirginRootKey = null;
        return result;
    }

    /**
     * Create a new account.
     *
     * @param currency  The currency in which the account will be denominated.
     * @param owner  User ref of the new account's owner.
     * @param memo  Annotation on the account.
     *
     * @return a new, zero-balance, unencumbered account created according to
     *    the given parameters.
     */
    Account makeAccount(String currency, String owner, String memo) {
        if (getCurrency(currency) != null) {
            Account account =
                new Account(generateRef("acct"), 0, currency, owner, memo);
            account.checkpoint(myWorkshop, myAccountCollection, null);
            return account;
        } else {
            throw new Error("invalid currency " + currency);
        }
    }

    /**
     * Create a new currency.
     *
     * @param currency   Name of the new currency.
     * @param memo  Annotation on the currency.
     *
     * @return a new currency with the given name.
     */
    Currency makeCurrency(String currency, String memo) {
        Currency newCurrency = new Currency(currency, memo);
        myCurrencies.put(currency, newCurrency);
        checkpoint();
        return newCurrency;
    }

    /**
     * Create a new authorization key.
     *
     * @param parentKey  The key that is to be the parent of the new key.
     * @param auth  Authority of the new key.
     * @param currs  Optional currencies scoping the new key's authority.
     * @param expires   Optional expiration date on the key.
     * @param memo  Annotation on the key.
     *
     * @return a new key created according to the given parameters.
     */
    Key makeKey(Key parentKey, String auth, String[] currs,
                ExpirationDate expires, String memo)
    {
        Key key =
            new Key(parentKey, generateRef("key"), auth, currs, expires, memo);
        myKeys.put(key.ref(), key);
        checkpoint();
        return key;
    }

    /**
     * Produce a MongoDB query object to lookup an account from its ref.
     *
     * This method constructs a query object with the form:
     *
     * { ref: REF }
     *
     * @param ref  The ref of the account.
     *
     * @return a JSONObject suitable for querying MongoDB.
     */
    private JSONObject queryAccount(String ref) {
        JSONObject queryTemplate = new JSONObject();
        queryTemplate.addProperty("ref", ref);
        return queryTemplate;
    }

    /**
     * Produce a MongoDB query object to lookup an account from the ref of an
     * encumbrance on that account.
     *
     * This method constructs a query object with the form:
     *
     * { type: "bankacct", encs: { $elemMatch: { ref: ENCREF }}}
     *
     * @param encRef  The ref of the encumbrance.
     *
     * @return a JSONObject suitable for querying MongoDB.
     */
    private JSONObject queryEnc(String encRef) {
        JSONObject encMatchPattern = new JSONObject();
        encMatchPattern.addProperty("ref", encRef);

        JSONObject encMatch = new JSONObject();
        encMatch.addProperty("$elemMatch", encMatchPattern);

        JSONObject queryTemplate = new JSONObject();
        queryTemplate.addProperty("type", "bankacct");
        queryTemplate.addProperty("encs", encMatch);

        return queryTemplate;
    }

    /**
     * Produce a MongoDB query object to obtain two accounts, one designated by
     * the ref of one of its encumbrances and the other by the its ref
     * directly.
     *
     * @param encRef  The ref of an encumbrance on the first account desired.
     * @param accountRef  The ref of the second account desired.
     *
     * @return a JSONObject suitable for querying MongoDB.
     */
    private JSONObject queryEncAndAccount(String encRef, String accountRef) {
        return queryOr(queryEnc(encRef), queryAccount(accountRef));
    }

    /**
     * Produce a MongoDB query object to obtain the combined output of two
     * other queries.
     *
     * This method constructs a query object with the form:
     *
     * { $or: [ QUERY1, QUERY2 ] }
     *
     * @param query1  The first query.
     * @param query2  The second query.
     *
     * @return a JSONObject suitable for querying MongoDB.
     */
    private JSONObject queryOr(JSONObject query1, JSONObject query2) {
        JSONArray terms = new JSONArray();
        terms.add(query1);
        terms.add(query2);
        
        JSONObject queryTemplate = new JSONObject();
        queryTemplate.addProperty("$or", terms);
        return queryTemplate;
    }

    /**
     * Produce a MongoDB query object to obtain two accounts by their refs.
     *
     * @param ref1  The ref of the first account desired.
     * @param ref2  The ref of the second account desired.
     *
     * @return a JSONObject suitable for querying MongoDB.
     */
    private JSONObject queryTwoAccounts(String ref1, String ref2) {
        return queryOr(queryAccount(ref1), queryAccount(ref2));
    }

    /**
     * Lookup an account by its reference string and perform some operation on
     * it.
     *
     * @param accountRef  The ref of the account to be manipulated.
     * @param updater  Updater object that will effect the desired account
     *    manipulation.
     */
    void withAccount(final String accountRef, final AccountUpdater updater) {
        myWorkshop.getObject(accountRef, myAccountCollection,
                             new ArgRunnable() {
            public void run(Object obj) {
                if (obj == null) {
                    myTrace.errorm("account object " + accountRef +
                                   " not found");
                    updater.modify(null);
                } else {
                    doAccountUpdate(accountRef, obj, updater);
                }
            }
        });
    }

    /**
     * Lookup an account by the reference string of an encumbrance on the
     * account, and perform some operation on it.
     *
     * @param encRef  The ref of an encumbrance on the account to be
     *    manipulated.
     * @param updater  Updater object that will effect the desired account
     *    manipulation.
     */
    void withEncumberedAccount(final String encRef,
                               final AccountUpdater updater)
    {
        myWorkshop.queryObjects(queryEnc(encRef), myAccountCollection, 1,
                                new ArgRunnable() {
            public void run(Object queryResult) {
                Encumbrance enc = null;
                if (queryResult == null) {
                    myTrace.errorm("encumbrance object " + encRef +
                                   " not found");
                    updater.modify(null);
                } else if (queryResult instanceof Object[]) {
                    Object[] results = (Object[]) queryResult;
                    if (results.length == 1) {
                        doAccountUpdate(encRef, results[0], updater);
                    } else {
                        myTrace.errorm("wrong number of query results for " +
                                       encRef);
                        updater.modify(null);
                    }
                } else {
                    myTrace.errorm("query results for " + encRef +
                                   " are malformed");
                    updater.modify(null);
                }
            }
        });
    }

    /**
     * Lookup a pair of accounts, one by the reference string of an encumbrance
     * an the other directly, and perform some joint operation on them
     * atomically.
     *
     * @param ecRef  The ref of an encumbrance on the first account to be
     *    manipulated.
     * @param accountRef  The ref of the second account to be manipulated.
     * @param updater  Updater object that will effect the desired account
     *    manipulations.
     */
    void withEncumbranceAndAccount(final String encRef,
        final String accountRef, final DualAccountUpdater updater)
    {
        myWorkshop.queryObjects(queryEncAndAccount(encRef, accountRef),
                                myAccountCollection, 2, new ArgRunnable() {
            public void run(Object queryResult) {
                if (queryResult == null) {
                    myTrace.errorm("accounts via " + encRef + "+" +
                                   accountRef + " not found");
                    updater.modify(null, null);
                } else if (queryResult instanceof Object[]) {
                    Object[] results = (Object[]) queryResult;
                    if (results.length == 2) {
                        doDualAccountUpdate(encRef, results[0],
                                            accountRef, results[1], updater);
                    } else {
                        myTrace.errorm("wrong number of query results for " +
                                       encRef + "+" + accountRef);
                        updater.modify(null, null);
                    }
                } else {
                    myTrace.errorm("query results for "+ encRef + "+" +
                                   accountRef + " are malformed");
                    updater.modify(null, null);
                }
            }
        });
    }

    /**
     * Lookup a pair of accounts by their reference strings and perform some
     * joint operation on them atomically.
     *
     * @param account1Ref  The ref of the first account to be manipulated.
     * @param account2Ref  The ref of the second account to be manipulated.
     * @param updater  Updater object that will effect the desired account
     *    manipulations.
     */
    void withTwoAccounts(final String account1Ref, final String account2Ref,
                         final DualAccountUpdater updater)
    {
        myWorkshop.queryObjects(queryTwoAccounts(account1Ref, account2Ref),
                                myAccountCollection, 2, new ArgRunnable() {
            public void run(Object queryResult) {
                if (queryResult == null) {
                    myTrace.errorm("accounts " + account1Ref + "+" +
                                   account2Ref + " not found");
                    updater.modify(null, null);
                } else if (queryResult instanceof Object[]) {
                    Object[] results = (Object[]) queryResult;
                    if (results.length == 2) {
                        doDualAccountUpdate(account1Ref, results[0],
                                            account2Ref, results[1], updater);
                    } else {
                        myTrace.errorm("wrong number of query results for " +
                                       account1Ref + "+" + account2Ref);
                        updater.modify(null, null);
                    }
                } else {
                    myTrace.errorm("query results for "+ account1Ref + "+" +
                                   account2Ref + " are malformed");
                    updater.modify(null, null);
                }
            }
        });
    }
}
