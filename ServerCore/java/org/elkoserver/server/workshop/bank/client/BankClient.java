package org.elkoserver.server.workshop.bank.client;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.MessageHandlerException;
import org.elkoserver.foundation.json.OptBoolean;
import org.elkoserver.foundation.json.OptInteger;
import org.elkoserver.foundation.json.OptString;
import org.elkoserver.foundation.server.ServiceActor;
import org.elkoserver.foundation.server.ServiceLink;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.server.context.AdminObject;
import org.elkoserver.server.context.Contextor;
import org.elkoserver.server.workshop.bank.Currency;
import org.elkoserver.util.ArgRunnable;
import org.elkoserver.util.trace.Trace;

/**
 * Internal object that acts as a client for the external 'bank' service.
 */
public class BankClient extends AdminObject implements ArgRunnable {
    /** Connection to the workshop running the bank service. */
    private ServiceLink myServiceLink;

    /** Tag string indicating the current state of the service connection. */
    private String myStatus;

    /** Name of bank service this object acts as client for. */
    private String myServiceName;

    /** Collection of handlers for pending requests to the service. */
    private Map<String, BankReplyHandler> myResultHandlers;

    /** Trace object for logging. */
    static private Trace tr;

    /** Counter for generating transaction IDs */
    private int myXidCounter;

    /**
     * Constructor.
     */
    @JSONMethod({ "servicename" })
    public BankClient(String serviceName) {
        myServiceName = serviceName;
        myStatus = "startup";
        myXidCounter = 0;
        myResultHandlers = new HashMap<String, BankReplyHandler>();
    }

    /**
     * Make this object live inside the context server.  In this case we
     * initiate a connection to the external bank service.
     *
     * @param ref  Reference string identifying this object in the static
     *    object table.
     * @param contextor  The contextor for this server.
     */
    public void activate(String ref, Contextor contextor) {
        super.activate(ref, contextor);
        myStatus = "connecting";
        tr = contextor.appTrace();
        contextor.findServiceLink(myServiceName, this);
    }

    /**
     * Callback that is invoked when the service connection is established or
     * fails to be established.
     *
     * @param obj  The connection to the bank service, or null if connection
     *    setup failed.
     */
    public void run(Object obj) {
        if (obj != null) {
            myServiceLink = (ServiceLink) obj;
            myStatus = "connected";
        } else {
            myStatus = "failed";
        }
    }

    /**
     * Get the current status of the connection to the external service.
     *
     * @return a tag string describing the current connection state.
     */
    public String status() {
        return myStatus;
    }

    /**
     * Base class for request-specific handlers for replies from the bank.
     */
    abstract public static class BankReplyHandler {
        /**
         * Handle a failure result, internal version: log the error and then
         * call the application-specific handler.
         *
         * @param op  The message verb that failed
         * @param fail  Failure tag
         * @param desc  Error description
         */
        void innerFail(String op, String fail, String desc) {
            tr.errorm("bank " + op + " failure " + fail + ": " + desc);
            fail(fail, desc);
        }
        /**
         * Handle a failure result, application-specific version: do whatever
         * the application needs or wants to do in a failure case.  The base
         * implementation here does nothing, but application code can override.
         *
         * @param fail  Failure tag
         * @param desc  Error description
         */
        public void fail(String fail, String desc) {
        }
    }            

    /**
     * Internal class to hold onto a request under construction.
     */
    private class BankRequest {
        /** The message itself. */
        final JSONLiteral msg;

        /** Transaction ID, for matching requests with replies. */
        private String myXid;

        /**
         * Constructor.
         *
         * @param op  The message verb
         * @param key  Key ref for authorizing the request.
         * @param memo  Optional memo field annotating the request.
         */
        BankRequest(String op, String key, String memo) {
            myXid = "x" + myXidCounter++;
            msg = new JSONLiteral(myServiceName, op);
            msg.addParameterOpt("key", key);
            msg.addParameter("xid", myXid);
            msg.addParameterOpt("memo", memo);
        }

        /**
         * Finish the request under construction and send it.
         *
         * @param resultHandler  Application callback that will process the
         *    result from the bank service.
         */
        void send(BankReplyHandler resultHandler) {
            if (myServiceLink != null) {
                if (resultHandler != null) {
                    msg.addParameter("rep", ref());
                    myResultHandlers.put(myXid, resultHandler);
                }
                msg.finish();
                myServiceLink.send(msg);
            } else {
                resultHandler.fail("noconn", "no connection to bank service");
            }
        }
    }

    /**
     * Lookup the handler for a received reply.  If the reply indicated an
     * error result, the handler's fail() method will be invoked directly.  If
     * there was no registered handler, the error will be logged.
     *
     * @param xid  Transaction ID on the message, for matching requests with
     *    responses.
     * @param op  The message verb of the reply being handled, for logging.
     * @param optFail  The failure tag, present in the event of error.
     * @param optDesc  The error description, present in the event of error.
     *
     * @return the registered reply handler for the given transaction ID, if
     *    the reply parameters indicate a successful result, or null if there
     *    was a problem.
     */
    private BankReplyHandler handlerForReply(String op, String xid,     
        OptString optFail, OptString optDesc)
    {
        BankReplyHandler handler = myResultHandlers.get(xid);
        if (handler == null) {
            tr.errorm("no reply handler for bank xid " + xid);
        } 
        String fail = optFail.value(null);
        if (fail != null) {
            String desc = optDesc.value("");
            if (handler != null) {
                handler.innerFail(op, fail, desc);
            } else {
                tr.errorm("bank " + op + " failure " + fail + ": " + desc);
            }
            return null;
        } else {
            return handler;
        }
    }

    /**
     * Result handler class for requests that return an account ref (delete
     * account, freeze account, and unfreeze account).
     */
    abstract public static class AccountResultHandler
        extends BankReplyHandler
    {
        /**
         * Handle an account result.
         *
         * @param account  Ref of the account.
         */
        abstract public void result(String account);
    }

    /**
     * Result handler class for requests that return an array of account refs
     * (make accounts).
     */
    abstract public static class AccountsResultHandler
        extends BankReplyHandler
    {
        /**
         * Handle an account list result.
         *
         * @param accounts  Array of refs accounts.
         */
        abstract public void result(String[] accounts);
    }

    /**
     * Result handler class for requests that affect an account balance (mint,
     * unmint, and unmintEncmbrance).
     */
    abstract public static class BalanceResultHandler
        extends BankReplyHandler
    {
        /**
         * Handle a balance update result.
         *
         * @param acct  Ref of the account effected.
         * @param bal  New available balance in the account.
         */
        abstract public void result(String acct, int bal);
    }

    /**
     * Result handler class for the make currency request.
     */
    abstract public static class CurrencyResultHandler
        extends BankReplyHandler
    {
        /**
         * Handle a currency result.
         *
         * @param currency  The currency name.
         */
        abstract public void result(String currency);
    }

    /**
     * Result handler class for the encumber request.
     */
    abstract public static class EncumberResultHandler
        extends BankReplyHandler
    {
        /**
         * Handle an encumbrance result.
         *
         * @param enc  The ref of the created encumbrance.
         * @param  srcbal  Available balance in the encumbered account after
         *    encumbrance.
         */
        abstract public void result(String enc, int srcbal);
    }

    /**
     * Result handler class for request that return a key (cancel key,
     * duplicate key, issue root key, and make key).
     */
    abstract public static class KeyResultHandler extends BankReplyHandler {
        /**
         * Handle a key result.
         *
         * @param key  The ref of the key.
         */
        abstract public void result(String key);
    }

    /**
     * A struct describing an encumbrance, returned as part of the results from
     * query accounts.
     */
    public static class EncumbranceDesc {
        /** The ref of the encumbrance. */
        public final String enc;
        /** The amount of the encumbrance. */
        public final int amount;
        /** When the encumbrance expires. */
        public final String expires;
        /** Memo string associated with the encumbrance at creation time. */
        public final String memo;

        /**
         * JSON-driven constructor.
         */
        @JSONMethod({ "enc", "amount", "expires", "memo" })
        EncumbranceDesc(String enc, int amount, String expires, String memo) {
            this.enc = enc;
            this.amount = amount;
            this.expires = expires;
            this.memo = memo;
        }
    }

    /**
     * A struct describing an account, returned as part of the results from
     * query accounts.
     */
    public static class AccountDesc {
        /** The ref of the account. */
        public final String account;
        /** Currency the account is denominated in. */
        public final String currency;
        /** Available (unencumbered) balance in the account. */
        public final int avail;
        /**  Total (including encumbered) balance in the account. */
        public final int total;
        /** Flag that is true if the account is frozen, false if not. */
        public final boolean frozen;
        /** Memo string associated with account at creation time. */
        public final String memo;
        /** Ref of user who is the owner of the account. */
        public final String owner;
        /** Array of encumbrance information.  This will be null if the 'encs'
            parameter of the query was false. */
        public final EncumbranceDesc encs[];

        /**
         * JSON-driven construct.
         */
        @JSONMethod({ "account", "curr", "avail", "total", "frozen", "memo",
                       "owner", "?encs" })
        AccountDesc(String account, String currency, int avail, int total,
                    boolean frozen, String memo, String owner,
                    EncumbranceDesc encs[])
        {
            this.account = account;
            this.currency = currency;
            this.avail = avail;
            this.total = total;
            this.frozen = frozen;
            this.memo = memo;
            this.owner = owner;
            this.encs = encs;
        }
    }

    /**
     * Result handler class for the query account request.
     */
    abstract public static class QueryAccountsResultHandler
        extends BankReplyHandler
    {
        /**
         * Handle an accounts query result.
         *
         * @param accounts  Descriptors for the accounts queried.
         */
        abstract public void result(AccountDesc[] accounts);
    }

    /**
     * Result handler class for the query currencies request.
     */
    abstract public static class QueryCurrenciesResultHandler
        extends BankReplyHandler
    {
        /**
         * Handle an query currencies result.
         *
         * @param currencies  Array of currency descriptors.
         */
        abstract public void result(Currency[] currencies);
    }

    /**
     * Result handler class for the query encumbrance request.
     */
    abstract public static class QueryEncumbranceResultHandler
        extends BankReplyHandler
    {
        /**
         * Handle an encumbrance query result.
         *
         * @param enc  Ref of the encumbrace.
         * @param currency  Currency the encumbrance is denominated in.
         * @param account  Ref of the encumbered account
         * @param amount  Amount that is encumbered
         * @param expires  Expiration time of the encumbrance
         * @param memo  Memo string associated with encumbrance when created.
         */
        abstract public void result(String enc, String currency,
                                    String account, int amount, String expires,
                                    String memo);
    }

    /**
     * Result handler class for the release request.
     */
    abstract public static class ReleaseResultHandler
        extends BankReplyHandler
    {
        /**
         * Handle an encumbrance release result.
         *
         * @param src  Ref of the account that was un-encumbered.
         * @param srcbal  Available balance in the account after the release
         *    was processed.
         * @param active  Flag that is true if the encumbrance was still active
         *    when it was released, fales if it had expired.
         */
        abstract public void result(String src, int srcbal, boolean active);
    }

    /**
     * Result handler class for transfer requests (transfer and
     * transferEncumbrance).
     */
    abstract public static class TransferResultHandler
        extends BankReplyHandler
    {
        /**
         * Handle a transfer result.
         *
         * @param src  Source account ref.
         * @param srcbal  Available balance in src account after transfer.
         * @param dst  Destination account ref.
         * @param dstbal  Available balance in dst account after transfer.
         */
        abstract public void result(String src, int srcbal, String dst,
                                    int dstbal);
    }

    /**
     * Cancel an authorization key.
     *
     * @param key  Authorizing key.
     * @param memo  Transaction annotation.
     * @param cancel  Ref of key to be cancelled.
     * @param resultHandler  Callback to be invoked on the result.
     */
    public void cancelKey(String key, String memo, String cancel,
                          KeyResultHandler resultHandler)
    {
        BankRequest req = new BankRequest("cancelkey", key, memo);
        req.msg.addParameter("cancel", cancel);
        req.send(resultHandler);
    }

    /**
     * Delete an account.
     *
     * @param key  Authorizing key.
     * @param memo  Transaction annotation.
     * @param account  Ref of account to be deleted.
     * @param resultHandler  Callback to be invoked on the result.
     */
    public void deleteAccount(String key, String memo, String account,
                              AccountResultHandler resultHandler)
    {
        BankRequest req = new BankRequest("deleteaccount", key, memo);
        req.msg.addParameter("account", account);
        req.send(resultHandler);
    }

    /**
     * Duplicate an authorization key.
     *
     * @param key  Authorizing key.
     * @param memo  Transaction annotation.
     * @param expires  Expiration time for the new key.
     * @param resultHandler  Callback to be invoked on the result.
     */
    public void dupKey(String key, String memo, String expires,
                       KeyResultHandler resultHandler)
    {
        BankRequest req = new BankRequest("dupkey", key, memo);
        req.msg.addParameterOpt("expires", expires);
        req.send(resultHandler);
    }

    /**
     * Encumber an account, i.e., provisionally reserve funds for a fuure
     * transaction.
     *
     * @param key  Authorizing key.
     * @param memo  Transaction annotation.
     * @param src  Ref of account to be encumbered.
     * @param amount  Quantity of funds to encumber.
     * @param expiration  Expiration time for encumbrance.
     * @param resultHandler  Callback to be invoked on the result.
     */
    public void encumber(String key, String memo, String src, int amount,
                         long expiration, EncumberResultHandler resultHandler)
    {
        BankRequest req = new BankRequest("encumber", key, memo);
        req.msg.addParameter("src", src);
        req.msg.addParameter("amount", amount);
        req.msg.addParameter("expires", "+" + expiration);
        req.send(resultHandler);
    }        

    /**
     * Freeze an account, rendering it temporarily unable to participate in
     * transactions.
     *
     * @param key  Authorizing key.
     * @param memo  Transaction annotation.
     * @param account  Ref of account to be frozen.
     * @param resultHandler  Callback to be invoked on the result.
     */
    public void freezeAccount(String key, String memo, String account,
                              AccountResultHandler resultHandler)
    {
        BankRequest req = new BankRequest("freezeaccount", key, memo);
        req.msg.addParameter("account", account);
        req.send(resultHandler);
    }

    /**
     * Obtain the bank's root key, if nobody yet has it.
     *
     * @param resultHandler  Callback to be invoked on the result.
     */
    public void issueRootKey(KeyResultHandler resultHandler)
    {
        BankRequest req = new BankRequest("issuerootkey", null, null);
        req.send(resultHandler);
    }

    /**
     * Create a set of new accounts.
     *
     * @param key  Authorizing key.
     * @param memo  Transaction annotation.
     * @param currencies  Currencies in which the new accounts will be
     *    denominated.
     * @param owner  Ref of the account owner.
     * @param resultHandler  Callback to be invoked on the result.
     */
    public void makeAccounts(String key, String memo, String[] currencies,
                             String owner, AccountsResultHandler resultHandler)
    {
        BankRequest req = new BankRequest("makeaccounts", key, memo);
        req.msg.addParameter("currs", currencies);
        req.msg.addParameter("owner", owner);
        req.send(resultHandler);
    }

    /**
     * Create a new currency.
     *
     * @param key  Authorizing key.
     * @param memo  Transaction annotation.
     * @param currency  Name for the new currency.
     * @param resultHandler  Callback to be invoked on the result.
     */
    public void makeCurrency(String key, String memo, String currency,
                             CurrencyResultHandler resultHandler)
    {
        BankRequest req = new BankRequest("makecurrency", key, memo);
        req.msg.addParameter("currency", currency);
        req.send(resultHandler);
    }

    /**
     * Create a new authorization key.
     *
     * @param key  Authorizing key.
     * @param memo  Transaction annotation.
     * @param type  Type of authorization new key should grant.
     * @param currency  Optioal name of currency scoping new key's authority,
     *    or null.
     * @param expires  Expiration time for the new key.
     * @param resultHandler  Callback to be invoked on the result.
     */
    public void makeKey(String key, String memo, String type, String currency,
                        String expires, KeyResultHandler resultHandler)
    {
        BankRequest req = new BankRequest("makekey", key, memo);
        req.msg.addParameter("type", type);
        req.msg.addParameterOpt("currency", currency);
        req.msg.addParameterOpt("expires", expires);
        req.send(resultHandler);
    }

    /**
     * Create money into an accont.
     *
     * @param key  Authorizing key.
     * @param memo  Transaction annotation.
     * @param dst  Ref of account to receive the funds.
     * @param amount  Quantity of money to create.
     * @param resultHandler  Callback to be invoked on the result.
     */
    public void mint(String key, String memo, String dst, int amount,
                     BalanceResultHandler resultHandler)
    {
        BankRequest req = new BankRequest("mint", key, memo);
        req.msg.addParameter("dst", dst);
        req.msg.addParameter("amount", amount);
        req.send(resultHandler);
    }        

    /**
     * Get information about an account.
     *
     * @param key  Authorizing key.
     * @param memo  Transaction annotation.
     * @param accounts  Refs of the accounts being queried.
     * @param encs  Flag that is true if the results should also include
     *    information about extant encumbrances on the account.
     * @param resultHandler  Callback to be invoked on the result.
     */
    public void queryAccounts(String key, String memo, String[] accounts,
        boolean encs, QueryAccountsResultHandler resultHandler)
    {
        BankRequest req = new BankRequest("queryaccounts", key, memo);
        if (encs) {
            req.msg.addParameter("encs", true);
        }
        req.msg.addParameter("accounts", accounts);
        req.send(resultHandler);
    }

    /**
     * Get information about extant currencies.
     *
     * @param key  Authorizing key.
     * @param memo  Transaction annotation.
     * @param resultHandler  Callback to be invoked on the result.
     */
    public void queryCurrencies(String key, String memo,
                                QueryCurrenciesResultHandler resultHandler)
    {
        BankRequest req = new BankRequest("querycurrencies", key, memo);
        req.send(resultHandler);
    }

    /**
     * Get information about an encumbrance.
     *
     * @param key  Authorizing key.
     * @param memo  Transaction annotation.
     * @param enc  Ref of the enumbrance being queried.
     * @param resultHandler  Callback to be invoked on the result.
     */
    public void queryEncumbrance(String key, String memo, String enc,
                                 QueryEncumbranceResultHandler resultHandler)
    {
        BankRequest req = new BankRequest("unmintenc", key, memo);
        req.msg.addParameter("enc", enc);
        req.send(resultHandler);
    }

    /**
     * Release a previous encumbrance on an account.
     *
     * @param key  Authorizing key.
     * @param memo  Transaction annotation.
     * @param enc  Ref of the encumbrance to be released
     * @param resultHandler  Callback to be invoked on the result.
     */
    public void releaseEncumbrance(String key, String memo, String enc,
                                   ReleaseResultHandler resultHandler)
    {
        BankRequest req = new BankRequest("releaseenc", key, memo);
        req.msg.addParameter("enc", enc);
        req.send(resultHandler);
    }        

    /**
     * Transfer money from one account to another.
     *
     * @param key  Authorizing key.
     * @param memo  Transaction annotation.
     * @param src  Ref of source account.
     * @param dst  Ref of destination account.
     * @param amount  Quantity of funds to transfer.
     * @param resultHandler  Callback to be invoked on the result.
     */
    public void transfer(String key, String memo, String src, String dst,
                         int amount, TransferResultHandler resultHandler)
    {
        BankRequest req = new BankRequest("xfer", key, memo);
        req.msg.addParameter("src", src);
        req.msg.addParameter("dst", dst);
        req.msg.addParameter("amount", amount);
        req.send(resultHandler);
    }

    /**
     * Transfer encumbered funds into another account.
     *
     * @param key  Authorizing key.
     * @param memo  Transaction annotation.
     * @param enc  Encumbrance that is the source of funds.
     * @param dst  Ref of destination account.
     * @param resultHandler  Callback to be invoked on the result.
     */
    public void transferEncumbrance(String key, String memo, String enc,
        String dst, TransferResultHandler resultHandler)
    {
        BankRequest req = new BankRequest("xferenc", key, memo);
        req.msg.addParameter("enc", enc);
        req.msg.addParameter("dst", dst);
        req.send(resultHandler);
    }

    /**
     * Unfreeze an account, rendering it once again able to participate in
     * transactions.
     *
     * @param key  Authorizing key.
     * @param memo  Transaction annotation.
     * @param account  Ref of account to be frozen.
     * @param resultHandler  Callback to be invoked on the result.
     */
    public void unfreezeAccount(String key, String memo, String account,
                                AccountResultHandler resultHandler)
    {
        BankRequest req = new BankRequest("unfreezeaccount", key, memo);
        req.msg.addParameter("account", account);
        req.send(resultHandler);
    }

    /**
     * Destroy money from an account.
     *
     * @param key  Authorizing key.
     * @param memo  Transaction annotation
     * @param src  Ref of account to lose the funds.
     * @param amount  Quantity of money to destroy.
     * @param resultHandler  Callback to be invoked on the result.
     */
    public void unmint(String key, String memo, String src, int amount,
                       BalanceResultHandler resultHandler)
    {
        BankRequest req = new BankRequest("unmint", key, memo);
        req.msg.addParameter("src", src);
        req.msg.addParameter("amount", amount);
        req.send(resultHandler);
    }        

    /**
     * Destroy encumbered funds.
     *
     * @param key  Authorizing key.
     * @param memo  Transaction annotation
     * @param enc  Ref of encumbrance that reserves the funds to be destroyed.
     * @param resultHandler  Callback to be invoked on the result.
     */
    public void unmintEncumbrance(String key, String memo, String enc,
                                  BalanceResultHandler resultHandler)
    {
        BankRequest req = new BankRequest("unmintenc", key, memo);
        req.msg.addParameter("enc", enc);
        req.send(resultHandler);
    }

    /**
     * JSON message handler for the response to a cancel key request.
     */
    @JSONMethod({ "xid", "fail", "desc", "cancel" })
    public void cancelkey(ServiceActor from, String xid, OptString fail,
                          OptString desc, OptString optCancel)
    {
        KeyResultHandler handler =
            (KeyResultHandler) handlerForReply("cancelkey", xid, fail, desc);
        if (handler != null) {
            String cancel = optCancel.value(null);
            if (cancel == null) {
                handler.innerFail("cancelkey", "badreply",
                                  "required reply parameter cancel missing");
            } else {
                handler.result(cancel);
            }
        }
    }

    /**
     * JSON message handler for the response to a delete account request.
     */
    @JSONMethod({ "xid", "fail", "desc", "account" })
    public void deleteaccount(ServiceActor from, String xid, OptString fail,
                            OptString desc, OptString optAccount)
    {
        AccountResultHandler handler = (AccountResultHandler)
            handlerForReply("deleteaccount", xid, fail, desc);
        if (handler != null) {
            String account = optAccount.value(null);
            if (account == null) {
                handler.innerFail("deleteaccount", "badreply",
                                  "required reply parameter account missing");
            } else {
                handler.result(account);
            }
        }
    }

    /**
     * JSON message handler for the response to a duplicate key request.
     */
    @JSONMethod({ "xid", "fail", "desc", "newkey" })
    public void dupkey(ServiceActor from, String xid, OptString fail,
                       OptString desc, OptString optNewkey)
    {
        KeyResultHandler handler =
            (KeyResultHandler) handlerForReply("dupkey", xid, fail, desc);
        if (handler != null) {
            String newkey = optNewkey.value(null);
            if (newkey == null) {
                handler.innerFail("dupkey", "badreply",
                                  "required reply parameter newkey missing");
            } else {
                handler.result(newkey);
            }
        }
    }

    /**
     * JSON message handler for the response to an encumber request.
     */
    @JSONMethod({ "xid", "fail", "desc", "enc", "srcbal" })
    public void encumber(ServiceActor from, String xid, OptString fail,
                     OptString desc, OptString optEnc, OptInteger optSrcbal)
    {
        EncumberResultHandler handler = (EncumberResultHandler)
            handlerForReply("encumber", xid, fail, desc);
        if (handler != null) {
            String enc = optEnc.value(null);
            int srcbal = optSrcbal.value(-1);
            if (enc == null) {
                handler.innerFail("encumber", "badreply",
                                  "required reply parameter enc missing");
            } else if (srcbal < 0) {
                handler.innerFail("encumber", "badreply",
                                  "required reply parameter srcbal missing");
            } else {
                handler.result(enc, srcbal);
            }
        }
    }

    /**
     * JSON message handler for the response to a freeze account request.
     */
    @JSONMethod({ "xid", "fail", "desc", "account" })
    public void freezeaccount(ServiceActor from, String xid, OptString fail,
                              OptString desc, OptString optAccount)
    {
        AccountResultHandler handler = (AccountResultHandler)
            handlerForReply("freezeaccount", xid, fail, desc);
        if (handler != null) {
            String account = optAccount.value(null);
            if (account == null) {
                handler.innerFail("freezeaccount", "badreply",
                                  "required reply parameter account missing");
            } else {
                handler.result(account);
            }
        }
    }

    /**
     * JSON message handler for the response to an issue root key request.
     */
    @JSONMethod({ "xid", "fail", "desc", "rootkey" })
    public void issuerootkey(ServiceActor from, String xid, OptString fail,
                          OptString desc, OptString optRootkey)
    {
        KeyResultHandler handler = (KeyResultHandler)
            handlerForReply("issuerootkey", xid, fail, desc);
        if (handler != null) {
            String rootkey = optRootkey.value(null);
            if (rootkey == null) {
                handler.innerFail("issuerootkey", "badreply",
                                  "required reply parameter rootkey missing");
            } else {
                handler.result(rootkey);
            }
        }
    }

    /**
     * JSON message handler for the response to a make account request.
     */
    @JSONMethod({ "xid", "fail", "desc", "?accounts" })
    public void makeaccounts(ServiceActor from, String xid, OptString fail,
                             OptString desc, String[] accounts)
    {
        AccountsResultHandler handler = (AccountsResultHandler)
            handlerForReply("makeaccounts", xid, fail, desc);
        if (handler != null) {
            if (accounts == null) {
                handler.innerFail("makeaccounts", "badreply",
                                  "required reply parameter accounts missing");
            } else {
                handler.result(accounts);
            }
        }
    }

    /**
     * JSON message handler for the response to a make currency request.
     */
    @JSONMethod({ "xid", "fail", "desc", "currency" })
    public void makecurrency(ServiceActor from, String xid, OptString fail,
                                OptString desc, OptString optCurrency)
    {
        CurrencyResultHandler handler = (CurrencyResultHandler)
            handlerForReply("makecurrency", xid, fail, desc);
        if (handler != null) {
            String currency = optCurrency.value(null);
            if (currency == null) {
                handler.innerFail("makecurrency", "badreply",
                                  "required reply parameter currency missing");
            } else {
                handler.result(currency);
            }
        }
    }

    /**
     * JSON message handler for the response to a make key request.
     */
    @JSONMethod({ "xid", "fail", "desc", "newkey" })
    public void makekey(ServiceActor from, String xid, OptString fail,
                                OptString desc, OptString optNewkey)
    {
        KeyResultHandler handler = (KeyResultHandler)
            handlerForReply("makekey", xid, fail, desc);
        if (handler != null) {
            String newkey = optNewkey.value(null);
            if (newkey == null) {
                handler.innerFail("makekey", "badreply",
                                  "required reply parameter newkey missing");
            } else {
                handler.result(newkey);
            }
        }
    }

    /**
     * JSON message handler for the response to a mint request.
     */
    @JSONMethod({ "xid", "fail", "desc", "dst", "dstbal" })
    public void mint(ServiceActor from, String xid, OptString fail,
                     OptString desc, OptString optDst, OptInteger optDstbal)
    {
        BalanceResultHandler handler =
            (BalanceResultHandler) handlerForReply("mint", xid, fail, desc);
        if (handler != null) {
            String dst = optDst.value(null);
            int dstbal = optDstbal.value(-1);
            if (dst == null) {
                handler.innerFail("mint", "badreply",
                                  "required reply parameter dst missing");
            } else if (dstbal < 0) {
                handler.innerFail("mint", "badreply",
                                  "required reply parameter dstbal missing");
            } else {
                handler.result(dst, dstbal);
            }
        }
    }

    /**
     * JSON message handler for the response to a query account request.
     */
    @JSONMethod({ "xid", "fail", "desc", "?accounts" })
    public void queryaccounts(ServiceActor from, String xid, OptString fail,
                              OptString desc, AccountDesc[] accounts)
    {
        QueryAccountsResultHandler handler = (QueryAccountsResultHandler)
            handlerForReply("queryaccounts", xid, fail, desc);
        if (handler != null) {
            if (accounts == null) {
                handler.innerFail("queryaccounts", "badreply",
                                  "required reply parameter accounts missing");
            } else {
                handler.result(accounts);
            }
        }
    }

    /**
     * JSON message handler for the response to a query currencies request.
     */
    @JSONMethod({ "xid", "fail", "desc", "?currencies" })
    public void querycurrencies(ServiceActor from, String xid, OptString fail,
                                OptString desc, Currency[] currencies)
    {
        QueryCurrenciesResultHandler handler = (QueryCurrenciesResultHandler)
            handlerForReply("querycurrencies", xid, fail, desc);
        if (handler != null) {
            handler.result(currencies);
        }
    }

    /**
     * JSON message handler for the response to a query encumbrance request.
     */
    @JSONMethod({ "xid", "fail", "desc", "enc", "curr", "account", "amount",
                  "expires", "memo" })
    public void queryenc(ServiceActor from, String xid, OptString fail,
                         OptString desc, OptString optEnc, OptString optCurr,
                         OptString optAccount, OptInteger optAmount,
                         OptString optExpires, OptString optMemo)
    {
        QueryEncumbranceResultHandler handler = (QueryEncumbranceResultHandler)
            handlerForReply("queryenc", xid, fail, desc);
        if (handler != null) {
            String enc = optEnc.value(null);
            String curr = optCurr.value(null);
            String account = optAccount.value(null);
            int amount = optAmount.value(-1);
            String expires = optExpires.value(null);
            String memo = optMemo.value(null);
            if (enc == null) {
                handler.innerFail("queryenc", "badreply",
                                  "required reply parameter enc missing");
            } else if (curr == null) {
                handler.innerFail("queryenc", "badreply",
                                  "required reply parameter curr missing");
            } else if (account == null) {
                handler.innerFail("queryenc", "badreply",
                                  "required reply parameter account missing");
            } else if (expires == null) {
                handler.innerFail("queryenc", "badreply",
                                  "required reply parameter expires missing");
            } else if (amount < 0) {
                handler.innerFail("queryenc", "badreply",
                                  "required reply parameter amount missing");
            } else {
                handler.result(enc, curr, account, amount, expires, memo);
            }
        }
    }

    /**
     * JSON message handler for the response to a release encumbrance request.
     */
    @JSONMethod({ "xid", "fail", "desc", "src", "srcbal", "active" })
    public void releaseenc(ServiceActor from, String xid, OptString fail,
                           OptString desc, OptString optSrc,
                           OptInteger optSrcbal, OptBoolean optActive)
    {
        ReleaseResultHandler handler = (ReleaseResultHandler)
            handlerForReply("releaseenc", xid, fail, desc);
        if (handler != null) {
            String src = optSrc.value(null);
            int srcbal = optSrcbal.value(-1);
            boolean active = optActive.value(true);
            if (src == null) {
                handler.innerFail("releaseenc", "badreply",
                                  "required reply parameter src missing");
            } else if (srcbal < 0) {
                handler.innerFail("releaseenc", "badreply",
                                  "required reply parameter srcbal missing");
            } else {
                handler.result(src, srcbal, active);
            }
        }
    }

    /**
     * JSON message handler for the response to an unfreeze account request.
     */
    @JSONMethod({ "xid", "fail", "desc", "account" })
    public void unfreezeaccount(ServiceActor from, String xid, OptString fail,
                                OptString desc, OptString optAccount)
    {
        AccountResultHandler handler = (AccountResultHandler)
            handlerForReply("unfreezeaccount", xid, fail, desc);
        if (handler != null) {
            String account = optAccount.value(null);
            if (account == null) {
                handler.innerFail("unfreezeaccount", "badreply",
                                  "required reply parameter account missing");
            } else {
                handler.result(account);
            }
        }
    }

    /**
     * JSON message handler for the response to an unmint request.
     */
    @JSONMethod({ "xid", "fail", "desc", "src", "srcbal" })
    public void unmint(ServiceActor from, String xid, OptString fail,
                       OptString desc, OptString optSrc, OptInteger optSrcbal)
    {
        BalanceResultHandler handler =
            (BalanceResultHandler) handlerForReply("unmint", xid, fail, desc);
        if (handler != null) {
            String src = optSrc.value(null);
            int srcbal = optSrcbal.value(-1);
            if (src == null) {
                handler.innerFail("unmint", "badreply",
                                  "required reply parameter src missing");
            } else if (srcbal < 0) {
                handler.innerFail("unmint", "badreply",
                                  "required reply parameter srcbal missing");
            } else {
                handler.result(src, srcbal);
            }
        }
    }

    /**
     * JSON message handler for the response to an unmint encumbrance request.
     */
    @JSONMethod({ "xid", "fail", "desc", "src", "srcbal" })
    public void unmintenc(ServiceActor from, String xid, OptString fail,
                          OptString desc, OptString optSrc,
                          OptInteger optSrcbal)
    {
        BalanceResultHandler handler = (BalanceResultHandler)
            handlerForReply("unmintenc", xid, fail, desc);
        if (handler != null) {
            String src = optSrc.value(null);
            int srcbal = optSrcbal.value(-1);
            if (src == null) {
                handler.innerFail("unmintenc", "badreply",
                                  "required reply parameter src missing");
            } else if (srcbal < 0) {
                handler.innerFail("unmintenc", "badreply",
                                  "required reply parameter srcbal missing");
            } else {
                handler.result(src, srcbal);
            }
        }
    }

    /**
     * JSON message handler for the response to a transfer request.
     */
    @JSONMethod({ "xid", "fail", "desc", "src", "srcbal", "dst", "dstbal" })
    public void xfer(ServiceActor from, String xid, OptString fail,
                     OptString desc, OptString optSrc, OptInteger optSrcbal,
                     OptString optDst, OptInteger optDstbal)
    {
        TransferResultHandler handler =
            (TransferResultHandler) handlerForReply("xfer", xid, fail, desc);
        if (handler != null) {
            String src = optSrc.value(null);
            int srcbal = optSrcbal.value(-1);
            String dst = optDst.value(null);
            int dstbal = optDstbal.value(-1);
            if (src == null) {
                handler.innerFail("xfer", "badreply",
                                  "required reply parameter src missing");
            } else if (srcbal < 0) {
                handler.innerFail("xfer", "badreply",
                                  "required reply parameter srcbal missing");
            } else if (dst == null) {
                handler.innerFail("xfer", "badreply",
                                  "required reply parameter dst missing");
            } else if (dstbal < 0) {
                handler.innerFail("xfer", "badreply",
                                  "required reply parameter dstbal missing");
            } else {
                handler.result(src, srcbal, dst, dstbal);
            }
        }
    }

    /**
     * JSON message handler for the response to a transfer encumbrance request.
     */
    @JSONMethod({ "xid", "fail", "desc", "src", "srcbal", "dst", "dstbal" })
    public void xferenc(ServiceActor from, String xid, OptString fail,
                     OptString desc, OptString optSrc, OptInteger optSrcbal,
                     OptString optDst, OptInteger optDstbal)
    {
        TransferResultHandler handler = (TransferResultHandler)
            handlerForReply("xferenc", xid, fail, desc);
        if (handler != null) {
            String src = optSrc.value(null);
            int srcbal = optSrcbal.value(-1);
            String dst = optDst.value(null);
            int dstbal = optDstbal.value(-1);
            if (src == null) {
                handler.innerFail("xferenc", "badreply",
                                  "required reply parameter src missing");
            } else if (srcbal < 0) {
                handler.innerFail("xferenc", "badreply",
                                  "required reply parameter srcbal missing");
            } else if (dst == null) {
                handler.innerFail("xferenc", "badreply",
                                  "required reply parameter dst missing");
            } else if (dstbal < 0) {
                handler.innerFail("xferenc", "badreply",
                                  "required reply parameter dstbal missing");
            } else {
                handler.result(src, srcbal, dst, dstbal);
            }
        }
    }
}
