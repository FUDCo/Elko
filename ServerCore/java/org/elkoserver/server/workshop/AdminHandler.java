package org.elkoserver.server.workshop;

import org.elkoserver.foundation.actor.BasicProtocolHandler;
import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.MessageHandlerException;
import org.elkoserver.foundation.json.OptBoolean;

/**
 * Singleton handler for the workshop 'admin' protocol.
 *
 * The 'admin' protocol consists of these requests:
 *
 *   'reinit' - Requests the workshop to reinitialize itself.
 *
 *   'shutdown' - Requests the workshop to shut down, with an option to force
 *      abrupt termination.
 */
class AdminHandler extends BasicProtocolHandler {
    /** The workshop for this handler. */
    private Workshop myWorkshop;

    /**
     * Constructor.
     */
    AdminHandler(Workshop workshop) {
        myWorkshop = workshop;
    }

    /**
     * Get this object's reference string.  This singleton object is always
     * known as 'admin'.
     *
     * @return a string referencing this object.
     */
    public String ref() {
        return "admin";
    }

    /**
     * Handle the 'reinit' verb.
     *
     * Request that the workshop be reset.
     *
     * @param from  The administrator sending the message.
     */
    @JSONMethod
    public void reinit(WorkshopActor from) throws MessageHandlerException {
        from.ensureAuthorizedAdmin();
        myWorkshop.reinit();
    }

    /**
     * Handle the 'shutdown' verb.
     *
     * Request that the workshop be shut down.
     *
     * @param from  The administrator sending the message.
     * @param kill  If true, shutdown immediately instead of cleaning up.
     */
    @JSONMethod({ "kill" })
    public void shutdown(WorkshopActor from, OptBoolean kill)
        throws MessageHandlerException
    {
        from.ensureAuthorizedAdmin();
        myWorkshop.shutdown(kill.value(false));
    }
}
