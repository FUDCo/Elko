package org.elkoserver.server.presence;

import org.elkoserver.foundation.json.JSONMethod;
import org.elkoserver.json.JSONObject;

class GraphDesc {
    String myClassName;
    String myGraphName;
    JSONObject myConf;

    @JSONMethod({ "class", "name", "?conf" })
    GraphDesc(String className, String graphName, JSONObject conf) {
        myClassName = className;
        myGraphName = graphName;
        if (conf == null) {
            conf = new JSONObject();
        }
        myConf = conf;
    }

    SocialGraph init(PresenceServer master) {
        try {
            SocialGraph newGraph =
                (SocialGraph) Class.forName(myClassName).newInstance();
            newGraph.init(master, new Domain(myGraphName), myConf);
            return newGraph;
        } catch (ClassNotFoundException e) {
            master.appTrace().errori("class " + myClassName + " not found");
            return null;
        } catch (InstantiationException e) {
            master.appTrace().errorm("unable to instantiate " + myClassName +
                                     ": " + e);
            return null;
        } catch (IllegalAccessException e) {
            master.appTrace().errorm("unable to instantiate " + myClassName +
                                     ": " + e);
            return null;
        }
    }
}
