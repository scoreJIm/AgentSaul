package com.agentsaul.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Static holder so custom JVM/System meter binders can bind before
 * the real MeterRegistry is fully initialized by Spring.
 */
public final class MeterRegistryHolder {

    public static final MeterRegistry DEFERRED = new SimpleMeterRegistry();

    private MeterRegistryHolder() {}
}
