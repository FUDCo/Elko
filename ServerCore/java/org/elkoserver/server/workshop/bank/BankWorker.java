package org.elkoserver.server.workshop.bank;

import java.text.ParseException;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.json.JSONLiteralArray;
import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.MessageHandlerException;
import org.elkoserver.foundation.json.OptBoolean;
import org.elkoserver.foundation.json.OptString;
import org.elkoserver.server.workshop.WorkerObject;
import org.elkoserver.server.workshop.WorkshopActor;
import org.elkoserver.util.ArgRunnable;

/**
 * Workshop worker object for the bank service.
 */
public class BankWorker extends WorkerObject {
    /** The bank this worker is the interface to. */
    private Bank myBank;

    /** Reference string for the bank, which is known prior to the bank being
        loaded. */
    private String myBankRef;

    /**
     * Common state for a request to the banking service.
     */
    private class RequestEnv {
        /** The actor from whom the request was received. */
        public final WorkshopActor from;

        /** The request verb, aka the "op" parameter. */
        public final String verb;

        /** Key authorizing the request. */
        public final Key key;

        /** Client-side transaction ID.  This will be echoed in the reply. */
        public final String xid;

        /** Object to address any reply to, or null if no reply is desired. */
        public final String rep;

        /** Optional text notation for logging.  Null if elided. */
        public final String memo;

        /**
         * Constructor.
         *
         * @param from  Actor who sent the request
         * @param verb  Operation verb
         * @param key  Key authorizing operation
         * @param xid  Client-side transaction ID, or null
         * @param rep  Reference to reply object, or null
         * @param memo  Arbitrary annotation
         */
        private RequestEnv(WorkshopActor from, String verb, Key key,
                           String xid, String rep, String memo)
        {
            this.from = from;
            this.verb = verb;
            this.key = key;
            this.xid = xid;
            this.rep = rep;
            this.memo = memo;
        }

        /**
         * Test if a write status represents a failure.  Send a failure reply
         * if it is.
         *
         * @param failure  Failure string from the account write.  A value of
         *    null indicates that the write was successful.
         * @param tag  Tag string indicating the role of the account, for
         *    logging and error message purposes.  Typically this will be
         *    "dst" or "src".
         *
         * @return true if the account write failed, false if not.
         */
        boolean accountWriteFailure(String failure, String tag) {
            if (failure != null) {
                fail(tag + "unwritable",
                     tag + " account write failed: " + failure);
                return true;
            } else {
                return false;
            }
        }

        /**
         * Test whether a given monetary quantity is valid.  At the moment,
         * this checks to be sure that the amount is not negative.  Sends a
         * failure reply if the amount given is invalid.
         *
         * @param amount  The monetary amount of interest.
         *
         * @return true if the amount was invalid, false if not.
         */
        boolean amountValidationFailure(int amount) {
            if (amount <= 0) {
                fail("badamount", "invalid 'amount' parameter");
                return true;
            } else {
                return false;
            }
        }

        /**
         * Begin constructing a message replying to this request.  It is
         * addressed to the designatedd reply recipient, has the same verb as
         * the request, and has the 'xid' parameter added if appropriate.  It
         * is up to the caller to fill in the operation-specific reply
         * parameters, finish the reply literal, and send it off.
         *
         * @return an open JSON literal as described.
         */
        JSONLiteral beginReply() {
            JSONLiteral msg = new JSONLiteral(rep, verb);
            if (xid != null) {
                msg.addParameter("xid", xid);
            }
            return msg;
        }

        /**
         * Test whether the request key authorizes operations on a given
         * currency.  Sends a failure reply if it does not.
         *
         * @param currency  The currency the requestor wishes to operate on.
         *
         * @return true if authorization failed, false if not.
         */
        boolean currencyAuthorityFailure(String currency) {
            if (!key.allowsCurrency(currency)) {
                fail("autherr", "bad authorization key");
                return true;
            } else {
                return false;
            }
        }

        /**
         * Test whether the request key authorizes operations on a collection
         * of currencies.  Sends a failure reply if it does not.
         *
         * @param currencies  The currencies the requestor wishes to operate
         *    on.
         *
         * @return true if authorization failed, false if not.
         */
        boolean currencyAuthorityFailure(String[] currencies) {
            if (currencies == null) {
                return false;
            }
            for (String currency : currencies) {
                if (currencyAuthorityFailure(currency)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Test whether a given array of currencies is valid or not.  Sends a
         * failure reply if the array or any of the currencies in it are
         * invalid.
         *
         * @param currencies  The currency names of the currencies of interest.
         *
         * @return true if the currencies array was invalid, false if not.
         */
        boolean currencyValidationFailure(String[] currencies) {
            if (currencies == null || currencies.length == 0) {
                fail("badcurr", "invalid currency list");
                return true;
            }
            for (String currency : currencies) {
                if (myBank.getCurrency(currency) == null) {
                    fail("badcurr", "invalid currency " + currency);
                    return true;
                }
            }
            return false;
        }

        /**
         * Reply to this request with a failure message, if the request
         * indicated that a reply was desired.
         *
         * @param fail  Failure code string
         * @param desc  Error message for logging and debugging.
         */
        void fail(String fail, String desc) {
            if (rep != null) {
                JSONLiteral reply = beginReply();
                reply.addParameter("fail", fail);
                reply.addParameter("desc", desc);
                reply.finish();
                from.send(reply);
            }
        }

        /**
         * Test whether an account is frozen.  Sends a failure reply if it is.
         *
         * @param account  The account of interest.
         * @param tag  Tag string indicating the role of the account, for
         *    logging and error message purposes.  Typically this will be
         *    "dst" or "src".
         *
         * @return true if the account is frozen, false if not.
         */
        boolean frozenAccountFailure(Account account, String tag) {
            if (account.isFrozen()) {
                fail(tag + "frozen", tag + " account is frozen");
                return true;
            } else {
                return false;
            }
        }

        /**
         * Test whether an account object is valid (in this case, not null).
         * Sends a failure reply if the account was not valid.
         *
         * @param account  The account of interest.
         * @param tag  Tag string indicating the role of the account, for
         *    logging and error message purposes.  Typically this will be
         *    "dst" or "src".
         *
         * @return true if the account was invalid, false if not.
         */
        boolean invalidAccountFailure(Account account, String tag) {
            if (account == null) {
                fail("bad" + tag, "invalid " + tag + " account id");
                return true;
            } else {
                return false;
            }
        }

        /**
         * Test whether an encumbrance object is valid (in this case, not
         * null).  Sends a failure reply if the encumbrance was not valid.
         *
         * @param enc  The encumbrance of interest.
         *
         * @return true if the encumbrance was invalid, false if not.
         */
        boolean invalidEncumbranceFailure(Encumbrance enc) {
            if (enc == null) {
                fail("badenc", "invalid encumbrance id " + enc);
                return true;
            } else {
                return false;
            }
        }

        /**
         * Parse and return an expiration date from its string representation.
         * Sends a failure reply if the expiration date string given was
         * invalid.
         *
         * @param expiresStr  A string allegedly containing an expiration date.
         * @param limitToKey  Control flag: if true, an expiration date that is
         *    later than the authorizing key will be considered invalid.
         *
         * @return the expiration date described by the given string, if valid,
         *    or null if not.
         */
        ExpirationDate getValidExpiration(String expiresStr,
                                          boolean limitToKey)
        {
            ExpirationDate expires;
            if (expiresStr == null && limitToKey) {
                expires = key.expires();
            } else {
                try {
                    expires = new ExpirationDate(expiresStr);
                } catch (ParseException e) {
                    fail("badexpiry", "invalid 'expires' parameter: " + e);
                    return null;
                }
            }
            if (expires == null) {
                fail("badexpiry", "invalid 'expires' parameter");
                return null;
            } else if (limitToKey && key.expires().compareTo(expires) < 0) {
                fail("badexpiry", "expiration time exceeds authority");
                return null;
            } else if (expires.isExpired()) {
                fail("badexpiry", "expiration time in the past");
                return null;
            } else {
                return expires;
            }
        }

        /**
         * Test whether the request key authorizes the operation requested.
         * Sends a failure reply if it does not.
         *
         * @param operation  Operation family of operation desired.
         *
         * @return true if authorization failed, false if not.
         */
        boolean operationAuthorityFailure(String operation) {
            if (!key.allowsOperation(operation)) {
                fail("autherr", "bad authorization key");
                return true;
            } else {
                return false;
            }
        }
    }

    /**
     * JSON-driven constructor.
     *
     * @param serviceName  The name by which this worker object will be
     *    addressed.  If omitted, it defaults to "bank".
     * @param bankRef  Reference string for the persistent bank object that
     *    this worker object provides the interface to.
     */
    @JSONMethod({ "service", "bank" })
    public BankWorker(OptString serviceName, String bankRef) {
        super(serviceName.value("bank"));
        myBankRef = bankRef;
    }

    /**
     * Activate the bank service.
     */
    public void activate() {
        workshop().getObject(myBankRef, new ArgRunnable() {
            public void run(Object obj) {
                if (obj instanceof Bank) {
                    myBank = (Bank) obj;
                    myBank.activate(workshop());
                } else {
                    workshop().appTrace().errorm("alleged bank object " +
                                                 myBankRef + " is not a bank");
                }
            }
        });
    }
    
    /**
     * Generate a new RequestEnv object by extracting and checking the
     * various common parameters from the request message.  Sends a failure
     * reply if the authorization key given was invalid or if any normally-
     * optional-but-in-this-case-required parameters are missing.
     *
     * @param from  Actor who sent the request.
     * @param verb  Operation verb.
     * @param keyRef  Reference string for the authorization key, or null if
     *    no key is required.
     * @param optXid  Optional client-side transaction ID.
     * @param optRep  Optional reference for addressing the reply.
     * @param repRequired  Flag that is true if the normal optional reply
     *    address is actually required in this case.
     * @param optMemo   Optional annotation for logging the operation
     * @param memeRequired  Flag that is true if the normally optional memo
     *    parameter is actually required in this case.
     *
     * @return a new RequestEnv object constructed by processing the
     *    given parameters, or null if these parameters were somehow
     *    invalid.
     */
    RequestEnv init(WorkshopActor from, String verb, String keyRef,
            OptString optXid, OptString optRep, boolean repRequired,
            OptString optMemo, boolean memoRequired)
        throws MessageHandlerException
    {
        from.ensureAuthorizedClient();
        
        String xid = optXid.value(null);
        String rep = optRep.value(null);
        String memo = optMemo.value(null);
        if (rep == null && repRequired) {
            return null;
        }
        Key key = null;
        if (myBank != null) {
            key = myBank.getKey(keyRef);
        }
        RequestEnv env = new RequestEnv(from, verb, key, xid, rep, memo);
        if (myBank == null) {
            env.fail("unready", "bank object not yet loaded");
            return null;
        }
        if (key == null && keyRef != null) {
            env.fail("autherr", "bad authorization key");
            return null;
        } else if (memo == null && memoRequired) {
            env.fail("nomemo", "request lacked required 'memo' parameter");
            return null;
        } else {
            return env;
        }
    }

    /**
     * Message handler for the 'issuerootkey' request: obtain the bank's root
     * key for the first time.
     *
     * @param from  The sender of the request.
     * @param xid  Optional client-side response tag.
     * @param rep  Optioanl reference to client object to reply to.
     * @param memo  Transaction annotation, for logging.
     */
    @JSONMethod({ "xid", "rep", "memo" })
    public void issuerootkey(WorkshopActor from, OptString xid, OptString rep,
                             OptString memo)
        throws MessageHandlerException
    {
        RequestEnv env =
            init(from, "issuerootkey", null, xid, rep, true, memo, false);
        if (env == null) {
            return;
        }
        Key rootKey = myBank.issueRootKey();
        if (rootKey == null) {
            env.fail("onceonly",
                     "the root key for this bank has already been issued");
        } else {
            JSONLiteral reply = env.beginReply();
            reply.addParameter("rootkey", rootKey.ref());
            reply.finish();
            from.send(reply);
        }
    }

    /**
     * Message handler for the 'xfer' request: transfer money from one account
     * to another.
     *
     * @param from  The sender of the request.
     * @param key  Key from authorizing access.
     * @param xid  Optional client-side response tag.
     * @param rep  Optioanl reference to client object to reply to.
     * @param memo  Transaction annotation, for logging.
     * @param src  Ref of transfer source account.
     * @param dst  Ref of transfer destination account.
     * @param amount  Quantity of money to transfer.
     */
    @JSONMethod({ "key", "xid", "rep", "memo", "src", "dst", "amount" })
    public void xfer(final WorkshopActor from, String key, OptString xid,
                     OptString rep, OptString memo, final String src,
                     final String dst, final int amount)
        throws MessageHandlerException
    {
        final RequestEnv env =
            init(from, "xfer", key, xid, rep, false, memo, false);
        if (env == null) {
            return;
        }
        if (env.operationAuthorityFailure("xfer")) {
            return;
        }
        myBank.withTwoAccounts(src, dst, new DualAccountUpdater() {
            private Account mySrcAccount;
            private Account myDstAccount;
            public boolean modify(Account srcAccount, Account dstAccount) {
                if (env.invalidAccountFailure(srcAccount, "src")) {
                    return false;
                }
                mySrcAccount = srcAccount;
                if (env.invalidAccountFailure(dstAccount, "dst")) {
                    return false;
                }
                myDstAccount = dstAccount;
                if (!srcAccount.currency().equals(dstAccount.currency())) {
                    env.fail("curmismatch",
                             "source and destination currencies differ");
                    return false;
                }
                if (env.currencyAuthorityFailure(srcAccount.currency())) {
                    return false;
                }
                if (env.frozenAccountFailure(srcAccount, "src")) {
                    return false;
                }
                if (env.frozenAccountFailure(dstAccount, "dst")) {
                    return false;
                }
                if (env.amountValidationFailure(amount)) {
                    return false;
                }
                if (srcAccount.availBalance() < amount) {
                    env.fail("nsf",
                             "insufficient funds in source account");
                    return false;
                }
                if (!srcAccount.ref().equals(dstAccount.ref())) {
                    srcAccount.withdraw(amount);
                    dstAccount.deposit(amount);
                }
                return true;
            }
            public void complete(String failure) {
                if (!env.accountWriteFailure(failure, "xfer")) {
                    JSONLiteral reply = env.beginReply();
                    reply.addParameter("src", src);
                    reply.addParameter("srcbal",
                                       mySrcAccount.availBalance());
                    reply.addParameter("dst", dst);
                    reply.addParameter("dstbal",
                                       myDstAccount.availBalance());
                    reply.finish();
                    from.send(reply);
                }
            }
        });
    }

    /**
     * Message handler for the 'mint' request: create money and deposit it in
     * an account.
     *
     * @param from  The sender of the request.
     * @param key  Key from authorizing access.
     * @param xid  Optional client-side response tag.
     * @param rep  Optioanl reference to client object to reply to.
     * @param memo  Transaction annotation, for logging.
     * @param dst  Ref of destination account for new money.
     * @param amount  Quantity of money to create.
     */
    @JSONMethod({ "key", "xid", "rep", "memo", "dst", "amount" })
    public void mint(final WorkshopActor from, String key, OptString xid,
                     OptString rep, OptString memo, final String dst,
                     final int amount)
        throws MessageHandlerException
    {
        final RequestEnv env =
            init(from, "mint", key, xid, rep, false, memo, false);
        if (env == null) {
            return;
        }
        if (env.operationAuthorityFailure("mint")) {
            return;
        }
        if (env.amountValidationFailure(amount)) {
            return;
        }
        myBank.withAccount(dst, new AccountUpdater() {
            private Account myDstAccount;
            public boolean modify(Account dstAccount) {
                myDstAccount = dstAccount;
                if (env.invalidAccountFailure(dstAccount, "dst")) {
                    return false;
                }
                if (env.currencyAuthorityFailure(dstAccount.currency())) {
                    return false;
                }
                if (env.frozenAccountFailure(dstAccount, "dst")) {
                    return false;
                }
                dstAccount.deposit(amount);
                return true;
            }
            public void complete(String failure) {
                if (!env.accountWriteFailure(failure, "dst")) {
                    JSONLiteral reply = env.beginReply();
                    reply.addParameter("dst", dst);
                    reply.addParameter("dstbal", myDstAccount.availBalance());
                    reply.finish();
                    from.send(reply);
                }
            }
        });
    }

    /**
     * Message handler for the 'unmint' request: remove money from an account
     * and then destroy it.
     *
     * @param from  The sender of the request.
     * @param key  Key from authorizing access.
     * @param xid  Optional client-side response tag.
     * @param rep  Optioanl reference to client object to reply to.
     * @param memo  Transaction annotation, for logging.
     * @param src  Ref of the account from which the money should be taken.
     * @param amount  Quantity of money to destroy.
     */
    @JSONMethod({ "key", "xid", "rep", "memo", "src", "amount" })
    public void unmint(final WorkshopActor from, String key, OptString xid,
                       OptString rep, OptString memo, final String src,
                       final int amount)
        throws MessageHandlerException
    {
        final RequestEnv env =
            init(from, "unmint", key, xid, rep, false, memo, false);
        if (env == null) {
            return;
        }
        if (env.operationAuthorityFailure("mint")) {
            return;
        }
        myBank.withAccount(src, new AccountUpdater() {
            private Account mySrcAccount;
            public boolean modify(Account srcAccount) {
                mySrcAccount = srcAccount;
                if (env.invalidAccountFailure(srcAccount, "src")) {
                    return false;
                }
                if (env.currencyAuthorityFailure(srcAccount.currency())) {
                    return false;
                }
                if (env.amountValidationFailure(amount)) {
                    return false;
                }
                if (srcAccount.availBalance() < amount) {
                    env.fail("nsf", "insufficient funds in source account");
                    return false;
                }
                srcAccount.withdraw(amount);
                return true;
            }
            public void complete(String failure) {
                if (!env.accountWriteFailure(failure, "src")) {
                    JSONLiteral reply = env.beginReply();
                    reply.addParameter("srcbal", mySrcAccount.availBalance());
                    reply.addParameter("src", src);
                    reply.finish();
                    from.send(reply);
                }
            }
        });
    }

    /**
     * Message handler for the 'encumber' request: reserve money in an account
     * for a future transaction.
     *
     * @param from  The sender of the request.
     * @param key  Key from authorizing access.
     * @param xid  Optional client-side response tag.
     * @param rep  Optioanl reference to client object to reply to.
     * @param memo  Transaction annotation, for logging.
     * @param src  Ref of account whose funds are to be encumbered.
     * @param amount  Quantity of money to encumber.
     * @param expiresStr  Date after which the encumbrance will be released.
     */
    @JSONMethod({ "key", "xid", "rep", "memo", "src", "amount", "expires" })
    public void encumber(final WorkshopActor from, String key, OptString xid,
                         OptString rep, OptString memo, String src,
                         final int amount, String expiresStr)
        throws MessageHandlerException
    {
        final RequestEnv env =
            init(from, "encumber", key, xid, rep, true, memo, false);
        if (env == null) {
            return;
        }
        final ExpirationDate expires =
            env.getValidExpiration(expiresStr, false);
        if (expires == null) {
            return;
        }
        if (env.operationAuthorityFailure("xfer")) {
            return;
        }
        myBank.withAccount(src, new AccountUpdater() {
            private Account mySrcAccount;
            private Encumbrance myEnc;
            public boolean modify(Account srcAccount) {
                mySrcAccount = srcAccount;
                if (env.invalidAccountFailure(srcAccount, "src")) {
                    return false;
                }
                if (env.currencyAuthorityFailure(srcAccount.currency())) {
                    return false;
                }
                if (env.frozenAccountFailure(srcAccount, "src")) {
                    return false;
                }
                if (env.amountValidationFailure(amount)) {
                    return false;
                }
                if (srcAccount.availBalance() < amount) {
                    env.fail("nsf", "insufficient funds in source account");
                    return false;
                }
                myEnc = new Encumbrance(myBank.generateRef("enc"), srcAccount,
                                        amount, expires, env.memo);
                srcAccount.encumber(myEnc);
                return true;
            }
            public void complete(String failure) {
                if (!env.accountWriteFailure(failure, "src")) {
                    JSONLiteral reply = env.beginReply();
                    reply.addParameter("enc", myEnc.ref());
                    reply.addParameter("srcbal", mySrcAccount.availBalance());
                    reply.finish();
                    from.send(reply);
                }
            }
        });
    }

    /**
     * Message handler for the 'releaseenc' request: release an encumbrance on
     * an account, making the funds once again available to the account owner.
     *
     * @param from  The sender of the request.
     * @param key  Key from authorizing access.
     * @param xid  Optional client-side response tag.
     * @param rep  Optioanl reference to client object to reply to.
     * @param memo  Transaction annotation, for logging.
     * @param encRef  Ref of the encumbrance to release.
     */
    @JSONMethod({ "key", "xid", "rep", "memo", "enc" })
    public void releaseenc(final WorkshopActor from, String key, OptString xid,
                           OptString rep, OptString memo, final String encRef)
        throws MessageHandlerException
    {
        final RequestEnv env =
            init(from, "releaseenc", key, xid, rep, false, memo, false);
        if (env == null) {
            return;
        }
        if (env.operationAuthorityFailure("xfer")) {
            return;
        }
        myBank.withEncumberedAccount(encRef, new AccountUpdater() {
            private Encumbrance myEnc;
            public boolean modify(Account account) {
                if (env.invalidAccountFailure(account, "src")) {
                    return false;
                }
                myEnc = account.getEncumbrance(encRef);
                if (env.invalidEncumbranceFailure(myEnc)) {
                    return false;
                }
                if (!myEnc.isExpired()) {
                    myEnc.release();
                }
                return true;
            }
            public void complete(String failure) {
                if (!env.accountWriteFailure(failure, "src")) {
                    JSONLiteral reply = env.beginReply();
                    reply.addParameter("src", myEnc.account().ref());
                    reply.addParameter("srcbal",
                                       myEnc.account().availBalance());
                    reply.addParameter("active", !myEnc.isExpired());
                    reply.finish();
                    from.send(reply);
                }
            }
        });
    }

    /**
     * Message handler for the 'xferenc' request: redeem an encumbrance by
     * transferring the encumbered funds to some other account
     *
     * @param from  The sender of the request.
     * @param key  Key from authorizing access.
     * @param xid  Optional client-side response tag.
     * @param rep  Optioanl reference to client object to reply to.
     * @param memo  Transaction annotation, for logging.
     * @param dst  Ref of transfer destination account.
     * @param encRef  Ref of the encumbrance that will be the source of funds.
     */
    @JSONMethod({ "key", "xid", "rep", "memo", "dst", "enc" })
    public void xferenc(final WorkshopActor from, String key, OptString xid,
                        OptString rep, OptString memo, final String dst,
                        final String encRef)
        throws MessageHandlerException
    {
        final RequestEnv env =
            init(from, "xferenc", key, xid, rep, false, memo, false);
        if (env == null) {
            return;
        }
        if (env.operationAuthorityFailure("xfer")) {
            return;
        }
        myBank.withEncumbranceAndAccount(encRef, dst, new DualAccountUpdater(){
            private Encumbrance myEnc;
            private Account mySrcAccount;
            private Account myDstAccount;
            public boolean modify(Account srcAccount, Account dstAccount) {
                if (env.invalidAccountFailure(srcAccount, "src")) {
                    return false;
                }
                mySrcAccount = srcAccount;
                myEnc = srcAccount.getEncumbrance(encRef);
                if (env.invalidEncumbranceFailure(myEnc)) {
                    return false;
                }
                if (env.invalidAccountFailure(dstAccount, "dst")) {
                    return false;
                }
                myDstAccount = dstAccount;
                if (!srcAccount.currency().equals(dstAccount.currency())) {
                    env.fail("curmismatch",
                             "source and destination currencies differ");
                    return false;
                }
                if (env.currencyAuthorityFailure(dstAccount.currency())) {
                    return false;
                }
                if (env.frozenAccountFailure(dstAccount, "dst")) {
                    return false;
                }
                if (srcAccount.ref().equals(dstAccount.ref())) {
                    myEnc.release();
                } else {
                    int amount = myEnc.redeem();
                    dstAccount.deposit(amount);
                }
                return true;
            }
            public void complete(String failure) {
                if (!env.accountWriteFailure(failure, "xfer")) {
                    JSONLiteral reply = env.beginReply();
                    reply.addParameter("src", mySrcAccount.ref());
                    reply.addParameter("srcbal", mySrcAccount.availBalance());
                    reply.addParameter("dst", dst);
                    reply.addParameter("dstbal", myDstAccount.availBalance());
                    reply.finish();
                    from.send(reply);
                }
            }
        });
    }

    /**
     * Message handler for the 'unmint' request: redeem an encumbrance by
     * destroying the encumbered funds
     *
     * @param from  The sender of the request.
     * @param key  Key from authorizing access.
     * @param xid  Optional client-side response tag.
     * @param rep  Optioanl reference to client object to reply to.
     * @param memo  Transaction annotation, for logging.
     * @param encRef  Ref of the encumbrance that will be the source of funds.
     */
    @JSONMethod({ "key", "xid", "rep", "memo", "enc" })
    public void unmintenc(final WorkshopActor from, String key, OptString xid,
                          OptString rep, OptString memo, final String encRef)
        throws MessageHandlerException
    {
        final RequestEnv env =
            init(from, "unmintenc", key, xid, rep, false, memo, false);
        if (env == null) {
            return;
        }
        if (env.operationAuthorityFailure("mint")) {
            return;
        }
        myBank.withEncumberedAccount(encRef, new AccountUpdater() {
            private Encumbrance myEnc;
            public boolean modify(Account account) {
                if (env.invalidAccountFailure(account, "src")) {
                    return false;
                }
                myEnc = account.getEncumbrance(encRef);
                if (env.invalidEncumbranceFailure(myEnc)) {
                    return false;
                }
                if (env.currencyAuthorityFailure(account.currency())) {
                    return false;
                }
                myEnc.redeem();
                return true;
            }
            public void complete(String failure) {
                if (!env.accountWriteFailure(failure, "src")) {
                    JSONLiteral reply = env.beginReply();
                    reply.addParameter("src", myEnc.account().ref());
                    reply.addParameter("srcbal", myEnc.account().availBalance());
                    reply.finish();
                    from.send(reply);
                }
            }
        });
    }

    /**
     * Message handler for the 'queryenc' request: obtain information about an
     * encumbrance.
     *
     * @param from  The sender of the request.
     * @param key  Key from authorizing access.
     * @param xid  Optional client-side response tag.
     * @param rep  Optioanl reference to client object to reply to.
     * @param memo  Transaction annotation, for logging.
     * @param encRef  Ref of the encumbrance that is of interest.
     */
    @JSONMethod({ "key", "xid", "rep", "memo", "enc" })
    public void queryenc(final WorkshopActor from, String key, OptString xid,
                         OptString rep, OptString memo, final String encRef)
        throws MessageHandlerException
    {
        final RequestEnv env =
            init(from, "queryenc", key, xid, rep, true, memo, false);
        if (env == null) {
            return;
        }
        if (env.operationAuthorityFailure("xfer")) {
            return;
        }
        myBank.withEncumberedAccount(encRef, new AccountUpdater() {
            public boolean modify(Account account) {
                if (env.invalidAccountFailure(account, "src")) {
                    return false;
                }
                Encumbrance enc = account.getEncumbrance(encRef);
                if (env.invalidEncumbranceFailure(enc)) {
                    return false;
                }
                if (env.currencyAuthorityFailure(account.currency())) {
                    return false;
                }
                JSONLiteral reply = env.beginReply();
                reply.addParameter("enc", enc.ref());
                reply.addParameter("curr", account.currency());
                reply.addParameter("account", account.ref());
                reply.addParameter("amount", enc.amount());
                reply.addParameter("expires", enc.expires().toString());
                reply.addParameterOpt("memo", enc.memo());
                reply.finish();
                from.send(reply);
                /* Don't write, hence even success is a form of failure. */
                return false;
            }
            public void complete(String failure) {
                /* never called */
            }
        });
    }

    /**
     * Message handler for the 'makeaccounts' request: create new accounts.
     * This will create one new account for each currency specified.
     *
     * @param from  The sender of the request.
     * @param key  Key from authorizing access.
     * @param xid  Optional client-side response tag.
     * @param rep  Optioanl reference to client object to reply to.
     * @param memo  Transaction annotation, for logging.
     * @param currs  Currencies in which the new accounts will be denominated.
     * @param owner  Ref of the user who is to be the owner of the new account.
     */
    @JSONMethod({ "key", "xid", "rep", "memo", "currs", "owner" })
    public void makeaccounts(WorkshopActor from, String key, OptString xid,
                             OptString rep, OptString memo, String[] currs,
                             String owner)
        throws MessageHandlerException
    {
        RequestEnv env =
            init(from, "makeaccounts", key, xid, rep, true, memo, true);
        if (env == null) {
            return;
        }
        if (env.operationAuthorityFailure("acct")) {
            return;
        }
        if (env.currencyValidationFailure(currs)) {
            return;
        }
        if (env.currencyAuthorityFailure(currs)) {
            return;
        }
        JSONLiteralArray replyAccounts = new JSONLiteralArray();
        for (String curr : currs) {
            Account account = myBank.makeAccount(curr, owner, env.memo);
            replyAccounts.addElement(account.ref());
        }
        replyAccounts.finish();
        JSONLiteral reply = env.beginReply();
        reply.addParameter("accounts", replyAccounts);
        reply.finish();
        from.send(reply);
    }

    /**
     * Message handler for the 'deleteaccount' request: destroy an account.
     *
     * @param from  The sender of the request.
     * @param key  Key from authorizing access.
     * @param xid  Optional client-side response tag.
     * @param rep  Optioanl reference to client object to reply to.
     * @param memo  Transaction annotation, for logging.
     * @param account  Ref of the account that is to be deleted.
     */
    @JSONMethod({ "key", "xid", "rep", "memo", "account" })
    public void deleteaccount(final WorkshopActor from, String key,
                              OptString xid, OptString rep, OptString memo,
                              final String account)
        throws MessageHandlerException
    {
        final RequestEnv env =
            init(from, "deleteaccount", key, xid, rep, false, memo, false);
        if (env == null) {
            return;
        }
        if (env.operationAuthorityFailure("acct")) {
            return;
        }
        myBank.withAccount(account, new AccountUpdater() {
            public boolean modify(Account srcAccount) {
                if (env.invalidAccountFailure(srcAccount, "src")) {
                    return false;
                }
                if (env.currencyAuthorityFailure(srcAccount.currency())) {
                    return false;
                }
                if (srcAccount.totalBalance() > 0) {
                    env.fail("notempty", "account still contains funds");
                    return false;
                }
                srcAccount.delete();
                return true;
            }
            public void complete(String failure) {
                if (!env.accountWriteFailure(failure, "src")) {
                    JSONLiteral reply = env.beginReply();
                    reply.addParameter("account", account);
                    reply.finish();
                    from.send(reply);
                }
            }
        });
    }

    /**
     * Message handler for the 'queryaccounts' request: obtain information
     * about one or more accounts.
     *
     * @param from  The sender of the request.
     * @param key  Key from authorizing access.
     * @param xid  Optional client-side response tag.
     * @param rep  Optioanl reference to client object to reply to.
     * @param memo  Transaction annotation, for logging.
     * @param accounts  Refs of the accounts of interest
     * @param encs  Flag that is true if reply should include encumbrance info.
     */
    @JSONMethod({ "key", "xid", "rep", "memo", "accounts", "encs" })
    public void queryaccounts(final WorkshopActor from, String key,
                              OptString xid, OptString rep, OptString memo,
                              final String[] accounts, final OptBoolean encs)
        throws MessageHandlerException
    {
        final RequestEnv env =
            init(from, "queryaccounts", key, xid, rep, true, memo, false);
        if (env == null) {
            return;
        }
        if (env.operationAuthorityFailure("acct")) {
            return;
        }
        if (accounts.length == 0) {
            env.fail("noaccounts", "account list provided was empty");
            return;
        }
        AccountUpdater lookupHandler = new AccountUpdater() {
            private int myResultCount = 0;
            private boolean amFailed = false;
            private JSONLiteral[] myAccountDescs =
                new JSONLiteral[accounts.length];
            public boolean modify(Account srcAccount) {
                if (amFailed) {
                    return false;
                }
                if (env.invalidAccountFailure(srcAccount, "src")) {
                    amFailed = true;
                    return false;
                }
                if (env.currencyAuthorityFailure(srcAccount.currency())) {
                    amFailed = true;
                    return false;
                }
                JSONLiteral accountDesc = new JSONLiteral();
                accountDesc.addParameter("account", srcAccount.ref());
                accountDesc.addParameter("curr", srcAccount.currency());
                accountDesc.addParameter("total", srcAccount.totalBalance());
                accountDesc.addParameter("avail", srcAccount.availBalance());
                accountDesc.addParameter("frozen", srcAccount.isFrozen());
                accountDesc.addParameter("memo", srcAccount.memo());
                accountDesc.addParameter("owner", srcAccount.owner());
                if (encs.value(false)) {
                    JSONLiteralArray encsList = new JSONLiteralArray();
                    for (Encumbrance enc : srcAccount.encumbrances()) {
                        JSONLiteral encDesc = new JSONLiteral();
                        encDesc.addParameter("enc", enc.ref());
                        encDesc.addParameter("amount", enc.amount());
                        encDesc.addParameter("expires",
                                             enc.expires().toString());
                        encDesc.addParameterOpt("memo", enc.memo());
                        encDesc.finish();
                        encsList.addElement(encDesc);
                    }
                    encsList.finish();
                    accountDesc.addParameter("encs", encsList);
                }
                accountDesc.finish();
                for (int i = 0; i < accounts.length; ++i) {
                    if (accounts[i].equals(srcAccount.ref())) {
                        myAccountDescs[i] = accountDesc;
                        break;
                    }
                }
                ++myResultCount;
                if (myResultCount == accounts.length) {
                    JSONLiteral reply = env.beginReply();
                    reply.addParameter("accounts", myAccountDescs);
                    reply.finish();
                    from.send(reply);
                }
                /* Don't write, hence even success is a form of failure. */
                return false;
            }
            public void complete(String failure) {
                /* never called */
            }
        };
        for (String account : accounts) {
            myBank.withAccount(account, lookupHandler);
        }
    }

    /**
     * Message handler for the 'freezeaccount' request: block an account from
     * participating in transactions.
     *
     * @param from  The sender of the request.
     * @param key  Key from authorizing access.
     * @param xid  Optional client-side response tag.
     * @param rep  Optioanl reference to client object to reply to.
     * @param memo  Transaction annotation, for logging.
     * @param account  Ref of the account to be frozen.
     */
    @JSONMethod({ "key", "xid", "rep", "memo", "account" })
    public void freezeaccount(final WorkshopActor from, String key,
                              OptString xid, OptString rep, OptString memo,
                              final String account)
        throws MessageHandlerException
    {
        final RequestEnv env =
            init(from, "freezeaccount", key, xid, rep, false, memo, false);
        if (env == null) {
            return;
        }
        if (env.operationAuthorityFailure("acct")) {
            return;
        }
        myBank.withAccount(account, new AccountUpdater() {
            public boolean modify(Account srcAccount) {
                if (env.invalidAccountFailure(srcAccount, "src")) {
                    return false;
                }
                if (env.currencyAuthorityFailure(srcAccount.currency())) {
                    return false;
                }
                srcAccount.setFrozen(true);
                return true;
            }
            public void complete(String failure) {
                if (!env.accountWriteFailure(failure, "src")) {
                    JSONLiteral reply = env.beginReply();
                    reply.addParameter("account", account);
                    reply.finish();
                    from.send(reply);
                }
            }
        });
    }

    /**
     * Message handler for the 'unfreezeaccount' request: remove the blockage
     * on a previously frozen account.
     *
     * @param from  The sender of the request.
     * @param key  Key from authorizing access.
     * @param xid  Optional client-side response tag.
     * @param rep  Optional reference to client object to reply to.
     * @param memo  Transaction annotation, for logging.
     * @param account  Ref of the account to be unfrozen.
     */
    @JSONMethod({ "key", "xid", "rep", "memo", "account" })
    public void unfreezeaccount(final WorkshopActor from, String key,
                                OptString xid, OptString rep, OptString memo,
                                final String account)
        throws MessageHandlerException
    {
        final RequestEnv env =
            init(from, "unfreezeaccount", key, xid, rep, false, memo, false);
        if (env == null) {
            return;
        }
        if (env.operationAuthorityFailure("acct")) {
            return;
        }
        myBank.withAccount(account, new AccountUpdater() {
            public boolean modify(Account srcAccount) {
                if (env.invalidAccountFailure(srcAccount, "src")) {
                    return false;
                }
                if (env.currencyAuthorityFailure(srcAccount.currency())) {
                    return false;
                }
                srcAccount.setFrozen(false);
                return true;
            }
            public void complete(String failure) {
                if (!env.accountWriteFailure(failure, "src")) {
                    JSONLiteral reply = env.beginReply();
                    reply.addParameter("account", account);
                    reply.finish();
                    from.send(reply);
                }
            }
        });
    }

    /**
     * Message handler for the 'makecurrency' request: create a new currency.
     *
     * @param from  The sender of the request.
     * @param key  Key from authorizing access.
     * @param xid  Optional client-side response tag.
     * @param rep  Optioanl reference to client object to reply to.
     * @param memo  Transaction annotation, for logging.
     * @param curr  Name for the new currency.
     */
    @JSONMethod({ "key", "xid", "rep", "memo", "curr" })
    public void makecurrency(WorkshopActor from, String key, OptString xid,
                             OptString rep, OptString memo, String curr)
        throws MessageHandlerException
    {
        RequestEnv env =
            init(from, "makecurrency", key, xid, rep, false, memo, true);
        if (env == null) {
            return;
        }
        if (env.operationAuthorityFailure("full")) {
            return;
        }
        if (myBank.getCurrency(curr) != null) {
            env.fail("currexists", "currency already exists");
            return;
        }
        myBank.makeCurrency(curr, env.memo);
        JSONLiteral reply = env.beginReply();
        reply.addParameter("curr", curr);
        reply.finish();
        from.send(reply);
    }

    /**
     * Message handler for the 'querycurrencies' request: obtain information
     * about existing currencies.
     *
     * @param from  The sender of the request.
     * @param key  Key from authorizing access.
     * @param xid  Optional client-side response tag.
     * @param rep  Optioanl reference to client object to reply to.
     * @param memo  Transaction annotation, for logging.
     */
    @JSONMethod({ "key", "xid", "rep", "memo" })
    public void querycurrencies(WorkshopActor from, String key, OptString xid,
                                OptString rep, OptString memo)
        throws MessageHandlerException
    {
        RequestEnv env =
            init(from, "querycurrencies", key, xid, rep, true, memo, false);
        if (env == null) {
            return;
        }
        if (env.operationAuthorityFailure("full")) {
            return;
        }
        JSONLiteral reply = env.beginReply();
        JSONLiteralArray currList = new JSONLiteralArray();
        for (Currency curr : myBank.currencies()) {
            JSONLiteral currDesc = new JSONLiteral();
            currDesc.addParameter("curr", curr.name());
            currDesc.addParameter("memo", curr.memo());
            currDesc.finish();
            currList.addElement(currDesc);
        }
        currList.finish();
        reply.addParameter("currencies", currList);
        reply.finish();
        from.send(reply);
    }

    /**
     * Message handler for the 'makekey' request: create a new key.
     *
     * @param from  The sender of the request.
     * @param key  Key from authorizing access.
     * @param xid  Optional client-side response tag.
     * @param rep  Optioanl reference to client object to reply to.
     * @param memo  Transaction annotation, for logging.
     * @param auth  The desired authority of the new key.
     * @param currs  Optional currencies scoping the new key.
     * @param optExpires  Date after which the new key will become invalid.
     */
    @JSONMethod({ "key", "xid", "rep", "memo", "auth", "currs", "expires" })
    public void makekey(WorkshopActor from, String key, OptString xid,
                        OptString rep, OptString memo, String auth,
                        String[] currs, OptString optExpires)
        throws MessageHandlerException
    {
        RequestEnv env =
            init(from, "makekey", key, xid, rep, true, memo, true);
        if (env == null) {
            return;
        }
        String requiredAuth;
        if (auth.equals("curr")) {
            requiredAuth = "full";
        } else if (auth.equals("acct") ||
                   auth.equals("mint") ||
                   auth.equals("xfer")) {
            requiredAuth = "curr";
        } else {
            env.fail("badkeyauth", "invalid 'auth' parameter");
            return;
        }
        if (env.operationAuthorityFailure(requiredAuth)) {
            return;
        }
        if (env.currencyValidationFailure(currs)) {
            return;
        }
        if (env.currencyAuthorityFailure(currs)) {
            return;
        }
        ExpirationDate expires =
            env.getValidExpiration(optExpires.value(null), true);
        if (expires == null) {
            return;
        }
        Key newKey = myBank.makeKey(env.key, auth, currs, expires, env.memo);
        JSONLiteral reply = env.beginReply();
        reply.addParameter("newkey", newKey.ref());
        reply.finish();
        from.send(reply);
    }

    /**
     * Message handler for the 'cancelkey' request: invalidate an existing key.
     *
     * @param from  The sender of the request.
     * @param key  Key from authorizing access.
     * @param xid  Optional client-side response tag.
     * @param rep  Optioanl reference to client object to reply to.
     * @param memo  Transaction annotation, for logging.
     * @param cancel  Ref of the key to be cancelled.
     */
    @JSONMethod({ "key", "xid", "rep", "memo", "cancel" })
    public void cancelkey(WorkshopActor from, String key, OptString xid,
                          OptString rep, OptString memo, String cancel)
        throws MessageHandlerException
    {
        RequestEnv env =
            init(from, "cancelkey", key, xid, rep, false, memo, false);
        if (env == null) {
            return;
        }
        Key toCancel = myBank.getKey(cancel);
        if (toCancel == null) {
            env.fail("badkey", "invalid key specified by 'cancel' parameter");
            return;
        }
        if (!toCancel.hasAncestor(env.key)) {
            env.fail("autherr", "bad authorization key");
            return;
        }
        myBank.deleteKey(toCancel);
        JSONLiteral reply = env.beginReply();
        reply.addParameter("cancel", cancel);
        reply.finish();
        from.send(reply);
    }

    /**
     * Message handler for the 'dupkey' request: make a separately cancellable
     * copy of an existing key.
     *
     * @param from  The sender of the request.
     * @param key  Key from authorizing access (which will be copied).
     * @param xid  Optional client-side response tag.
     * @param rep  Optioanl reference to client object to reply to.
     * @param memo  Transaction annotation, for logging.
     * @param optExpires  Date after which the new key will become invalid.
     */
    @JSONMethod({ "key", "xid", "rep", "memo", "expires" })
    public void dupkey(WorkshopActor from, String key, OptString xid,
                       OptString rep, OptString memo, OptString optExpires)
        throws MessageHandlerException
    {
        RequestEnv env =
            init(from, "makekey", key, xid, rep, true, memo, true);
        if (env == null) {
            return;
        }
        ExpirationDate expires =
            env.getValidExpiration(optExpires.value(null), true);
        if (expires == null) {
            return;
        }
        Key newKey = myBank.makeKey(env.key, env.key.auth(),
                                    env.key.currencies(), expires, env.memo);
        JSONLiteral reply = env.beginReply();
        reply.addParameter("newkey", newKey.ref());
        reply.finish();
        from.send(reply);
    }
}
