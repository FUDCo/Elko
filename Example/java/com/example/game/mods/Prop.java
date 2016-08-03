package com.example.game.mods;

import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.MessageHandlerException;
import org.elkoserver.json.EncodeControl;
import org.elkoserver.json.JSONLiteral;
import org.elkoserver.server.context.CartesianPosition;
import org.elkoserver.server.context.Item;
import org.elkoserver.server.context.ItemMod;
import org.elkoserver.server.context.Mod;
import org.elkoserver.server.context.Msg;
import org.elkoserver.server.context.User;

public class Prop extends Mod implements ItemMod {
    private String myKind;

    private static final int GRAB_DISTANCE = 5;

    @JSONMethod({ "kind" })
    public Prop(String kind) {
        myKind = kind;
    }

    public JSONLiteral encode(EncodeControl control) {
        JSONLiteral result = new JSONLiteral("prop", control);
        result.addParameter("kind", myKind);
        result.finish();
        return result;
    }

    @JSONMethod
    public void grab(User from) throws MessageHandlerException {
        ensureInContext(from);
        Item item = (Item) object();
        if (!item.isPortable()) {
            throw new MessageHandlerException(
                "attempt to grab non-portable item " + item);
        }

        CartesianPosition itemPos = (CartesianPosition) object().position();
        CartesianPosition userPos = (CartesianPosition) from.position();
        int dx = itemPos.x() - userPos.x();
        int dy = itemPos.y() - userPos.y();
        if (dx*dx + dy*dy > GRAB_DISTANCE*GRAB_DISTANCE) {
            throw new MessageHandlerException(
                "attempt to grab too far away item " + item);
        }

        item.setContainer(from);
        item.setPosition(null);
        context().sendToNeighbors(from, Msg.msgDelete(item));
        from.send(Movement.msgMove(item, 0, 0, from));
    }

    @JSONMethod
    public void drop(User from) throws MessageHandlerException {
        ensureHolding(from);
        Item item = (Item) object();
        if (!item.isPortable()) {
            throw new MessageHandlerException(
                "attempt to drop non-portable item " + item);
        }
        CartesianPosition pos = (CartesianPosition) from.position();
        item.setPosition(pos);
        item.setContainer(context());
        item.sendObjectDescription(context().neighbors(from), context());
        from.send(Movement.msgMove(item, pos.x(), pos.y(), context()));
    }
}
