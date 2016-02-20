package org.elkoserver.server.presence;

import org.elkoserver.foundation.json.JSONMethod;

class GraphTable {
    final GraphDesc[] graphs;

    @JSONMethod({ "graphs" })
    GraphTable(GraphDesc[] graphs) {
        this.graphs = graphs;
    }
}
