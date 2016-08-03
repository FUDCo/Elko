package com.example.game.mods;

import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.MessageHandlerException;
import org.elkoserver.foundation.json.OptInteger;
import org.elkoserver.json.EncodeControl;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.json.Referenceable;
import org.elkoserver.server.context.CartesianPosition;
import org.elkoserver.server.context.Mod;
import org.elkoserver.server.context.Msg;
import org.elkoserver.server.context.User;
import org.elkoserver.server.context.ContextMod;

/**
 * A simple context mod to enable users in a context to move around.
 */
public class Movement extends Mod implements ContextMod {
    private int myMinX;
    private int myMinY;
    private int myMaxX;
    private int myMaxY;

    @JSONMethod({"minx", "miny", "maxx", "maxy"})
    public Movement(OptInteger minX, OptInteger minY,
                    OptInteger maxX, OptInteger maxY)
    {
        myMinX = minX.value(-100);
        myMinY = minY.value(-100);
        myMaxX = maxX.value(100);
        myMaxY = maxY.value(100);
    }

    public JSONLiteral encode(EncodeControl control) {
        if (control.toClient()) {
            return null;
        } else {
            JSONLiteral result = new JSONLiteral("movement", control);
            result.addParameter("minx", myMinX);
            result.addParameter("miny", myMinY);
            result.addParameter("maxx", myMaxX);
            result.addParameter("maxy", myMaxY);
            result.finish();
            return result;
        }
    }
    
    @JSONMethod({ "x", "y" })
    public void move(User from, int x, int y) throws MessageHandlerException {
        ensureSameContext(from);
        if (x < myMinX || myMaxX < x || y < myMinY || myMaxY < y) {
            from.send(Msg.msgError(object(), "move",
                                   "movement out of bounds"));
        } else {
            from.setPosition(new CartesianPosition(x, y));
            context().send(msgMove(from, x, y, null));
        }
    }

    static JSONLiteral msgMove(Referenceable who, int x, int y,
                               Referenceable into)
    {
        JSONLiteral msg = new JSONLiteral(who, "move");
        msg.addParameter("x", x);
        msg.addParameter("y", y);
        msg.addParameterOpt("into", into);
        msg.finish();
        return msg;
    }
}
