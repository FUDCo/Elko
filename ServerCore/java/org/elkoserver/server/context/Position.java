package org.elkoserver.server.context;

import org.elkoserver.json.Encodable;

/**
 * Interface type for position description information.  The interface is
 * agnostic with respect to any actual position semantics, which will be
 * entirely provided by the classes that actually implement it.
 */
public interface Position extends Encodable {
}
