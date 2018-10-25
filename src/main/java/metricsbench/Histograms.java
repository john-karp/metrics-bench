package metricsbench;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SlidingTimeWindowReservoir;
import com.codahale.metrics.UniformReservoir;
import com.google.common.collect.Iterators;
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

import java.util.Iterator;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Histograms {

    static final int THREADS = 16;

    @State(Scope.Thread)
    public static class Data {

        Iterator<Long> dataIterator;

        @Setup(Level.Iteration)
        public void setup() {
            final Random r = new Random(1234567891L);
            dataIterator = Iterators.cycle(
                Stream.generate(() -> Math.round(Math.log10(2.0 + r.nextGaussian()))).limit(1048576)
                    .collect(Collectors.toList()));
        }
    }

    @State(Scope.Benchmark)
    public static class DropwizardState {

        MetricRegistry registry;
        //com.codahale.metrics.ScheduledReporter reporter;
        Histogram histogram;
        Histogram histogramSlidingTimeWindow;
        Histogram histogramUniform;

        @Setup(Level.Iteration)
        public void setup(Blackhole hole) {
            registry = new MetricRegistry();
            histogram = registry.histogram("histogram");
            histogramSlidingTimeWindow =
                registry.register("slidingTimeWindowHistogram",
                                  new Histogram(new SlidingTimeWindowReservoir(10, TimeUnit.SECONDS)));
            histogramUniform =
                registry.register("uniformHistogram",
                                  new Histogram(new UniformReservoir()));
        }

        @TearDown(Level.Iteration)
        public void tearDown(Blackhole hole) {
            hole.consume(histogram.getSnapshot().getMedian());
            hole.consume(histogramSlidingTimeWindow.getSnapshot().getMedian());
            hole.consume(histogramUniform.getSnapshot().getMedian());
        }
    }

    @State(Scope.Benchmark)
    public static class MicrometerState {

        io.micrometer.core.instrument.MeterRegistry registry;
        io.micrometer.core.instrument.DistributionSummary summary;

        @Setup(Level.Iteration)
        public void setup(Blackhole hole) {
            registry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
            summary = registry.summary("summary");
        }

        @TearDown(Level.Iteration)
        public void tearDown(Blackhole hole) {
            hole.consume(summary.percentile(0.5));
        }
    }

    @Threads(THREADS)
    @Benchmark
    public void micrometerHistogram(MicrometerState state, Data data) {
        state.summary.record(data.dataIterator.next());
    }

    @Threads(THREADS)
    @Benchmark
    public void dropwizardHistogram(DropwizardState state, Data data) {
        state.histogram.update(data.dataIterator.next());
    }

    @Threads(THREADS)
    @Benchmark
    public void dropwizardHistogramSlidingTimeWindow(DropwizardState state, Data data) {
        state.histogramSlidingTimeWindow.update(data.dataIterator.next());
    }

    @Threads(THREADS)
    @Benchmark
    public void dropwizardHistogramUniform(DropwizardState state, Data data) {
        state.histogramUniform.update(data.dataIterator.next());
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
            .include(Histograms.class.getSimpleName())
            .forks(1)
            .warmupIterations(3)
            .measurementIterations(5)
            .mode(Mode.AverageTime)
            .timeUnit(TimeUnit.NANOSECONDS)
            .build();
        new Runner(opt).run();
    }
}
