package com.paytm.seatreservation.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.binder.system.FileDescriptorMetrics;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    @Bean
    public JvmMemoryMetrics jvmMemoryMetrics(MeterRegistry registry) {
        return new JvmMemoryMetrics(registry);
    }

    @Bean
    public JvmGcMetrics jvmGcMetrics(MeterRegistry registry) {
        return new JvmGcMetrics(registry);
    }

    @Bean
    public JvmThreadMetrics jvmThreadMetrics(MeterRegistry registry) {
        return new JvmThreadMetrics(registry);
    }

    @Bean
    public ProcessorMetrics processorMetrics(MeterRegistry registry) {
        return new ProcessorMetrics(registry);
    }

    @Bean
    public FileDescriptorMetrics fileDescriptorMetrics(MeterRegistry registry) {
        return new FileDescriptorMetrics(registry);
    }
}