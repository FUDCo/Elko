package org.elkoserver.foundation.server.metadata;

import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.json.EncodeControl;
import org.elkoserver.json.Encodable;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.json.JSONLiteralArray;

/**
 * Description of the load on a server.
 */
public class LoadDesc implements Encodable {
    /** Printable label for the server being described. */
    private String myLabel;

    /** The load factor. */
    private double myLoad;

    /** The broker-assigned provider ID number assigned to the server, or -1 if
        no provider ID has been assigned. */
    private int myProviderID;

    @JSONMethod({ "label", "load", "provider" })
    public LoadDesc(String label, double load, int providerID) {
        myLoad = load;
        myLabel = label;
        myProviderID = providerID;
    }

    /**
     * Get the label for the server being described.
     *
     * @return the server label.
     */
    public String label() {
        return myLabel;
    }

    /**
     * Get the reported load factor.
     *
     * @return the load factor.
     */
    public double load() {
        return myLoad;
    }

    /**
     * Get the provider ID for the server being described.
     *
     * @return the provider ID.
     */
    public int providerID() {
        return myProviderID;
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
        JSONLiteral literal = new JSONLiteral("loaddesc", control);
        literal.addParameter("label", myLabel);
        literal.addParameter("load", myLoad);
        literal.addParameter("provider", myProviderID);
        literal.finish();
        return literal;
    }

    /**
     * Encode this descriptor as a single-element JSONLiteralArray.
     */
    public JSONLiteralArray encodeAsArray() {
        JSONLiteralArray array = new JSONLiteralArray();
        array.addElement(this);
        array.finish();
        return array;
    }
}

