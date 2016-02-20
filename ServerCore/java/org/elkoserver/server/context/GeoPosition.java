package org.elkoserver.server.context;

import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.json.EncodeControl;
import org.elkoserver.json.JSONLiteral;

/**
 * Position class representing a latitude/longitude position on the surface of
 * the earth.
 */
public class GeoPosition implements Position {
    public final double lat;
    public final double lon;

    /**
     * JSON-driven constructor.
     *
     * @param lat  Latitude (decimal degrees)
     * @param lon  Longitude (decimal degrees)
     */
    @JSONMethod({ "lat", "lon" })
    public GeoPosition(double lat, double lon) {
        this.lat = lat;
        this.lon = lon;
    }

    /**
     * Encode this position for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     *    should be done.
     *
     * @return a JSON literal representing this position.
     */
    public JSONLiteral encode(EncodeControl control) {
        JSONLiteral result = new JSONLiteral("geopos", control);
        result.addParameter("lat", lat);
        result.addParameter("lon", lon);
        result.finish();
        return result;
    }

    /**
     * Generate a new geo-position and assign it to an object.
     *
     * @param obj  The object to be given the new position
     * @param lat  The new latitude
     * @param lon  The new longitude
     */
    static public void setPosition(BasicObject obj, double lat, double lon) {
        obj.setPosition(new GeoPosition(lat, lon));
    }

    public String toString() {
        return "(lat: " + lat + ", lon: " + lon + ")";
    }
}
