package com.edispark.pagination;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that a paginated collection is ordered from the most recent item to the oldest.
 *
 * <p>Implementations must use the collection's business timestamp with a stable identity
 * tiebreaker so that ordering remains deterministic across page boundaries.</p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface NewestFirstPagination {
}
