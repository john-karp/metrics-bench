package metricsbench.apps;

import metricsbench.util.Misc;
import metricsbench.util.Slf4jMeterRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.step.StepRegistryConfig;

import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

public class MicrometerApp {

    static io.micrometer.core.instrument.MeterRegistry registry;

    private static final int N_COUNTERS = 64;
    private static final int N_THREADS = 16;
    private static final int N_ITERATIONS = Integer.MAX_VALUE;

    private final Counter counters[] = new Counter[N_COUNTERS];

    public static void main(String[] args) throws InterruptedException {
        new MicrometerApp().run();
        Misc.printGarbageCollectionTime();
    }

    void run() throws InterruptedException {
        registry = new Slf4jMeterRegistry(new StepRegistryConfig() {
            @Override
            public String prefix() {
                return "foo";
            }

            @Override
            public String get(String s) {
                return null;
            }

            @Override
            public Duration step() {
                return Duration.ofSeconds(1L);
            }
        });

        for (int i = 0; i < N_COUNTERS; i++) {
            counters[i] = registry.counter("counter", "id", Integer.toString(i));
        }

        ExecutorService executor = Executors.newFixedThreadPool(N_THREADS);
        ArrayList<Callable<Boolean>> tasks = new ArrayList<>(N_THREADS);
        for (int i = 0; i < N_THREADS; i++) {
            tasks.add(task);
        }
        executor.invokeAll(tasks);
        registry.close();
        executor.shutdown();
    }

    Callable task = () -> {
        for (int j = 0; j < N_ITERATIONS; j++) {
            Counter counter = counters[ThreadLocalRandom.current().nextInt(N_COUNTERS)];
            counter.increment();
        }
        return false;
    };

}
