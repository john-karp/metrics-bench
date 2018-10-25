package metricsbench.apps;

import metricsbench.util.Misc;
import io.dropwizard.metrics5.Counter;
import io.dropwizard.metrics5.MetricName;
import io.dropwizard.metrics5.Slf4jReporter;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class DropwizardApp {

    io.dropwizard.metrics5.MetricRegistry registry;

    private static final int N_COUNTERS = 64;
    private static final int N_THREADS = 16;
    private static final int N_ITERATIONS = Integer.MAX_VALUE;

    private final Counter counters[] = new Counter[N_COUNTERS];

    private static final String[] ints;
    static {
        ints = new String[N_COUNTERS];
        for (int i=0; i < ints.length; i++) {
            ints[i] = Integer.toString(i);
        }
    }

    public static void main(String[] args) throws InterruptedException {
        new DropwizardApp().run();
        Misc.printGarbageCollectionTime();
    }

    void run() throws InterruptedException {
        registry = new io.dropwizard.metrics5.MetricRegistry();
        Slf4jReporter reporter = Slf4jReporter.forRegistry(registry).build();
        reporter.start(60L, TimeUnit.SECONDS);

        for (int i = 0; i < N_COUNTERS; i++) {
            counters[i] = registry.counter(new MetricName("counter", Map.of("id", Integer.toString(i))));
        }

        ExecutorService executor = Executors.newFixedThreadPool(N_THREADS);
        ArrayList<Callable<Boolean>> tasks = new ArrayList<>(N_THREADS);
        for (int i = 0; i < N_THREADS; i++) {
            tasks.add(task);
        }
        executor.invokeAll(tasks);
        executor.shutdown();
        reporter.stop();
    }

    Callable task = () -> {
        for (int j = 0; j < N_ITERATIONS; j++) {
            int i = ThreadLocalRandom.current().nextInt(N_COUNTERS);
            counters[i].inc();
        }
        return false;
    };

}
