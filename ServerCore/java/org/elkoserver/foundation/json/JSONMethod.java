package org.elkoserver.foundation.json;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark methods as targets for JSON method dispatch and
 * constructors as decoders for JSON-driven object creation.
 *
 * The annotation value carries the JSON property names corresponding to
 * the parameters in the method or constructor parameter list.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
public @interface JSONMethod {
    /** Array of parameter names matching JSON parameter names to corresponding
        parameter positions in the declared argument list. */
    String[] value() default { };
}
