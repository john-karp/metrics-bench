package metricsbench;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public class Counters {

    @State(Scope.Benchmark)
    public static class DropwizardState {

        com.codahale.metrics.MetricRegistry registry;
        com.codahale.metrics.Counter counter;

        @Setup(Level.Trial)
        public void setup() {
            registry = new com.codahale.metrics.MetricRegistry();
            counter = registry.counter("counter");
        }

        @TearDown(Level.Trial)
        public void tearDown(Blackhole hole) {
            hole.consume(counter.getCount());
        }
    }

    @State(Scope.Benchmark)
    public static class MicrometerState {

        io.micrometer.core.instrument.MeterRegistry registry;
        io.micrometer.core.instrument.Counter counter;
        io.micrometer.core.instrument.Counter counterWithTags;

        @Setup(Level.Trial)
        public void setup() {
            registry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
            counter = registry.counter("untagged");
            counterWithTags = registry.counter("tagged", "key1", "value1", "key2", "value2");
        }

        @TearDown(Level.Trial)
        public void tearDown(Blackhole hole) {
            for (io.micrometer.core.instrument.Meter m : registry.getMeters()) {
                if (m instanceof io.micrometer.core.instrument.Counter) {
                    hole.consume(((io.micrometer.core.instrument.Counter) m).count());
                }
            }
        }
    }

    @State(Scope.Benchmark)
    public static class Dropwizard5State {

        io.dropwizard.metrics5.MetricRegistry registry;
        io.dropwizard.metrics5.Counter counter;
        io.dropwizard.metrics5.Counter counterWithTags;

        @Setup(Level.Trial)
        public void setup() {
            registry = new io.dropwizard.metrics5.MetricRegistry();
            counter = registry.counter("untagged");
            counterWithTags =
                registry.counter(
                    new io.dropwizard.metrics5.MetricName("tagged", Map.of("key1", "value1", "key2", "value2")));
        }

        @TearDown(Level.Trial)
        public void tearDown(Blackhole hole) {
            for (io.dropwizard.metrics5.Counter c : registry.getCounters().values()) {
                hole.consume(c.getCount());
            }
        }
    }

    @Threads(16)
    @Benchmark
    public void dropwizardCounter(DropwizardState state) {
        state.counter.inc();
    }

    @Threads(16)
    @Benchmark
    public void dropwizard5Counter(Dropwizard5State state) {
        state.counter.inc();
    }

    @Threads(16)
    @Benchmark
    public void dropwizard5CounterFixedTags(Dropwizard5State state) {
        state.counterWithTags.inc();
    }

    @Threads(16)
    @Benchmark
    public void dropwizard5CounterTags(Dropwizard5State state) {
        state.registry
            .counter(new io.dropwizard.metrics5.MetricName("tagged", Map.of("key1", "value1", "key2", "value2"))).inc();
    }

    @Threads(16)
    @Benchmark
    public void micrometerCounter(MicrometerState state) {
        state.counter.increment();
    }

    @Threads(16)
    @Benchmark
    public void micrometerCounterTags(MicrometerState state) {
        state.registry.counter("dynamicTags", "key1", "value1", "key2", "value2").increment();
    }

    @Threads(16)
    @Benchmark
    public void micrometerCounterFixedTags(MicrometerState state) {
        state.counterWithTags.increment();
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
            .include(Counters.class.getSimpleName())
            .forks(1)
            .warmupIterations(2)
            .measurementIterations(2)
            .mode(Mode.AverageTime)
            .timeUnit(TimeUnit.NANOSECONDS)
            .build();
        new Runner(opt).run();
    }
}
