package com.agentsaul.config;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.binder.system.UptimeMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MicrometerConfig {

    private static final Logger log = LoggerFactory.getLogger(MicrometerConfig.class);

    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        log.info("TimedAspect registered — @Timed annotations will be processed");
        return new TimedAspect(registry);
    }

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> agentSaulMetrics() {
        return registry -> {
            registry.config().commonTags(
                    "application", "agentsaul",
                    "java.version", System.getProperty("java.version")
            );
            log.info("MeterRegistry customized with common tags");
        };
    }

    @Bean
    public JvmMemoryMetrics jvmMemoryMetrics() {
        JvmMemoryMetrics metrics = new JvmMemoryMetrics();
        metrics.bindTo(MeterRegistryHolder.DEFERRED);
        return metrics;
    }

    @Bean
    public JvmGcMetrics jvmGcMetrics() {
        JvmGcMetrics metrics = new JvmGcMetrics();
        metrics.bindTo(MeterRegistryHolder.DEFERRED);
        return metrics;
    }

    @Bean
    public JvmThreadMetrics jvmThreadMetrics() {
        JvmThreadMetrics metrics = new JvmThreadMetrics();
        metrics.bindTo(MeterRegistryHolder.DEFERRED);
        return metrics;
    }

    @Bean
    public ClassLoaderMetrics classLoaderMetrics() {
        ClassLoaderMetrics metrics = new ClassLoaderMetrics();
        metrics.bindTo(MeterRegistryHolder.DEFERRED);
        return metrics;
    }

    @Bean
    public ProcessorMetrics processorMetrics() {
        ProcessorMetrics metrics = new ProcessorMetrics();
        metrics.bindTo(MeterRegistryHolder.DEFERRED);
        return metrics;
    }

    @Bean
    public UptimeMetrics uptimeMetrics() {
        UptimeMetrics metrics = new UptimeMetrics();
        metrics.bindTo(MeterRegistryHolder.DEFERRED);
        return metrics;
    }
}
