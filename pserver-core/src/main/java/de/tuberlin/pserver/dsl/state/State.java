package de.tuberlin.pserver.dsl.state;

import de.tuberlin.pserver.math.Format;
import de.tuberlin.pserver.math.Layout;
import de.tuberlin.pserver.types.PartitionType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface State {;

    LocalScope localScope() default LocalScope.SHARED;

    GlobalScope globalScope() default GlobalScope.REPLICATED;

    PartitionType partitionType() default PartitionType.ROW_PARTITIONED;

    String path() default "";

    long rows() default 0;

    long cols() default 0;

    Layout layout() default Layout.ROW_LAYOUT;

    Format format() default Format.DENSE_FORMAT;
}