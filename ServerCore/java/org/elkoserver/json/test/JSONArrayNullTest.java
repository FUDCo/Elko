package org.elkoserver.json.test;

import org.elkoserver.json.Encodable;
import org.elkoserver.json.EncodeControl;
import org.elkoserver.json.JSONArray;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.json.JSONLiteralArray;
import org.elkoserver.json.JSONObject;

/** Standalone regression; run with java org.elkoserver.json.test.JSONArrayNullTest. */
public class JSONArrayNullTest {
    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }

    public static void main(String[] args) throws Exception {
        JSONObject parsed = JSONObject.parse(
            "{\"items\":[null,1,null,[null,2,null],null],\"empty\":[]}");
        JSONArray items = parsed.getArray("items");
        for (EncodeControl control : new EncodeControl[] {
                EncodeControl.forClient, EncodeControl.forRepository }) {
            JSONLiteralArray literal = items.literal(control);
            check(literal.size() == 5, "literal element count includes nulls");
            JSONArray decoded = JSONObject.parse(
                "{\"items\":" + literal.sendableString() + "}").getArray("items");
            check(decoded.size() == 5, "standalone array retains positions");
            check(decoded.get(0) == null && decoded.get(2) == null &&
                  decoded.get(4) == null, "first, middle and last nulls");
            JSONArray nested = decoded.getArray(3);
            check(nested.size() == 3 && nested.get(0) == null &&
                  nested.get(2) == null, "nested nulls");
        }
        JSONObject roundTrip = JSONObject.parse(parsed.sendableString());
        check(roundTrip.getArray("items").size() == 5,
              "array inside object retains positions");
        check(roundTrip.getArray("empty").isEmpty(), "empty array");
        JSONArray onlyNull = new JSONArray();
        onlyNull.add(null);
        check("[null]".equals(onlyNull.sendableString()), "all-null array");

        // Application encoders intentionally omit objects whose encoding is null.
        // Preserve that behavior, including the literal builder's null filtering.
        Encodable invisible = new Encodable() {
            public JSONLiteral encode(EncodeControl control) { return null; }
        };
        JSONLiteralArray filtered = new JSONLiteralArray();
        filtered.addElement((Object) null);
        filtered.addElement(invisible);
        filtered.addElement("visible");
        check("[\"visible\"]".equals(filtered.sendableString()),
              "literal builder still filters absent application objects");
        JSONArray applicationArray = new JSONArray();
        applicationArray.add(invisible);
        applicationArray.add("visible");
        check("[\"visible\"]".equals(applicationArray.sendableString()),
              "application Encodable returning null is still omitted");
        System.out.println("PASS: parsed JSON null positions and application filtering");
    }
}
