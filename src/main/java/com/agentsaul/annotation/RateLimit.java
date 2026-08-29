package com.agentsaul.annotation;

import java.lang.annotation.*;

/**
 * Rate limit annotation for controller methods.
 * Requires aop dependency (spring-boot-starter-aop already present).
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /** Maximum number of requests within the window. */
    int limit() default 20;

    /** Time window in seconds. */
    int windowSeconds() default 60;

    /** Scope of rate limiting: IP or USER. */
    Scope scope() default Scope.USER;

    enum Scope {
        IP, USER
    }
}
