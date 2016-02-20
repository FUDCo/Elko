package org.elkoserver.server.workshop.bank;

import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.json.Encodable;
import org.elkoserver.json.EncodeControl;

/**
 * Object representing a defined currency: a type token denominating a
 * monetary value.
 */
public class Currency implements Encodable {
    /** The name of the currency. */
    private String myName;

    /** Arbitrary annotation associated with the currency at creation time. */
    private String myMemo;

    /**
     * Constructor.
     *
     * @param name  The currency name.
     * @param memo  Annotation on currency.
     */
    @JSONMethod({ "name", "memo" })
    Currency(String name, String memo) {
        myName = name;
        myMemo = memo;
    }

    /**
     * Encode this currency descriptor for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     *    should be done.
     *
     * @return a JSON literal representing this currency.
     */
    public JSONLiteral encode(EncodeControl control) {
        JSONLiteral result = new JSONLiteral(control);
        result.addParameter("name", myName);
        result.addParameter("memo", myMemo);
        result.finish();
        return result;
    }

    /**
     * Obtain this currency's memo string, an arbitrary annotation associated
     * with the currency when it was created.
     *
     * @return the currency's memo.
     */
    public String memo() {
        return myMemo;
    }

    /**
     * Obtain this currency's name.
     *
     * @return the currency name.
     */
    public String name() {
        return myName;
    }
}
