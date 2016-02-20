package org.elkoserver.server.workshop.test;

import org.elkoserver.json.JSONLiteral;
import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.foundation.json.MessageHandlerException;
import org.elkoserver.foundation.json.OptString;
import org.elkoserver.server.workshop.WorkerObject;
import org.elkoserver.server.workshop.WorkshopActor;

public class EchoWorker extends WorkerObject {
    private String myPrefix;

    @JSONMethod({ "prefix", "service" })
    public EchoWorker(OptString prefix, OptString serviceName) {
        super(serviceName.value("echo"));
        myPrefix = prefix.value("you said: ");
    }

    @JSONMethod({ "rep", "text" })
    public void echo(WorkshopActor from, OptString rep, OptString text)
        throws MessageHandlerException
    {
        from.ensureAuthorizedClient();

        JSONLiteral response = new JSONLiteral(rep.value(this.ref()), "echo");
        response.addParameter("text", myPrefix + text.value("<nothing>"));
        response.finish();
        from.send(response);
    }
}
