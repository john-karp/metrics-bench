package metricsbench.util;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.step.StepRegistryConfig;
import io.micrometer.core.instrument.util.DoubleFormat;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;
import io.micrometer.core.lang.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;


/**
 * @author Jon Schneider
 */
public class Slf4jMeterRegistry extends StepMeterRegistry {

    private final Logger logger = LoggerFactory.getLogger(Slf4jMeterRegistry.class);
    private final HierarchicalNameMapper nameMapper;

    public Slf4jMeterRegistry(StepRegistryConfig config) {
        this(config, Clock.SYSTEM, HierarchicalNameMapper.DEFAULT);
    }

    /**
     * @param clock The clock to use for timings.
     */
    public Slf4jMeterRegistry(StepRegistryConfig config, Clock clock) {
        this(config, clock, HierarchicalNameMapper.DEFAULT);
    }

    /**
     * @param clock      The clock to use for timings.
     * @param nameMapper The name mapper to use in converting dimensional metrics to hierarchical names.
     */
    public Slf4jMeterRegistry(StepRegistryConfig config, Clock clock, HierarchicalNameMapper nameMapper) {
        super(config, clock);

        // Technically, Ganglia doesn't have any constraints on metric or tag names, but the encoding of Unicode can look
        // horrible in the UI. So be aware...
        this.nameMapper = nameMapper;
        this.config().namingConvention(NamingConvention.camelCase);

        logger.warn("Enabled: {}", config.enabled());
        if (config.enabled()) {
            start();
        }
    }

    @Override
    protected void publish() {
        for (Meter meter : this.getMeters()) {
            consume(meter,
                    this::announceGauge,
                    this::announceCounter,
                    this::announceTimer,
                    this::announceSummary,
                    this::announceLongTaskTimer,
                    this::announceTimeGauge,
                    this::announceFunctionCounter,
                    this::announceFunctionTimer,
                    this::announceMeter);
        }
    }

    private static void consume(Meter meter,
                                Consumer<Gauge> visitGauge,
                                Consumer<Counter> visitCounter,
                                Consumer<Timer> visitTimer,
                                Consumer<DistributionSummary> visitSummary,
                                Consumer<LongTaskTimer> visitLongTaskTimer,
                                Consumer<TimeGauge> visitTimeGauge,
                                Consumer<FunctionCounter> visitFunctionCounter,
                                Consumer<FunctionTimer> visitFunctionTimer,
                                Consumer<Meter> visitMeter) {
        if (meter instanceof TimeGauge) {
            visitTimeGauge.accept((TimeGauge) meter);
        } else if (meter instanceof Gauge) {
            visitGauge.accept((Gauge) meter);
        } else if (meter instanceof Counter) {
            visitCounter.accept((Counter) meter);
        } else if (meter instanceof Timer) {
            visitTimer.accept((Timer) meter);
        } else if (meter instanceof DistributionSummary) {
            visitSummary.accept((DistributionSummary) meter);
        } else if (meter instanceof LongTaskTimer) {
            visitLongTaskTimer.accept((LongTaskTimer) meter);
        } else if (meter instanceof FunctionCounter) {
            visitFunctionCounter.accept((FunctionCounter) meter);
        } else if (meter instanceof FunctionTimer) {
            visitFunctionTimer.accept((FunctionTimer) meter);
        } else {
            visitMeter.accept(meter);
        }
    }

    private void announceMeter(Meter meter) {
        for (Measurement measurement : meter.measure()) {
            announce(meter, measurement.getValue(), measurement.getStatistic().toString().toLowerCase());
        }
    }

    private void announceFunctionTimer(FunctionTimer functionTimer) {
        announce(functionTimer, functionTimer.count(), "count");
        announce(functionTimer, functionTimer.totalTime(getBaseTimeUnit()), "sum");
        announce(functionTimer, functionTimer.mean(getBaseTimeUnit()), "avg");
    }

    private void announceFunctionCounter(FunctionCounter functionCounter) {
        announce(functionCounter, functionCounter.count());
    }

    private void announceTimeGauge(TimeGauge timeGauge) {
        announce(timeGauge, timeGauge.value(getBaseTimeUnit()));
    }

    private void announceLongTaskTimer(LongTaskTimer longTaskTimer) {
        announce(longTaskTimer, longTaskTimer.activeTasks(), "activeTasks");
        announce(longTaskTimer, longTaskTimer.duration(getBaseTimeUnit()), "duration");
    }

    private void announceSummary(DistributionSummary summary) {
        HistogramSnapshot snapshot = summary.takeSnapshot();
        announce(summary, snapshot.count(), "count");
        announce(summary, snapshot.total(), "sum");
        announce(summary, snapshot.mean(), "avg");
        announce(summary, snapshot.max(), "max");
    }

    private void announceTimer(Timer timer) {
        HistogramSnapshot snapshot = timer.takeSnapshot();
        announce(timer, snapshot.count(), "count");
        announce(timer, snapshot.total(getBaseTimeUnit()), "sum");
        announce(timer, snapshot.mean(getBaseTimeUnit()), "avg");
        announce(timer, snapshot.max(getBaseTimeUnit()), "max");
    }

    private void announceCounter(Counter counter) {
        announce(counter, counter.count());
    }

    private void announceGauge(Gauge gauge) {
        announce(gauge, gauge.value());
    }

    private void announce(Meter meter, double value) {
        announce(meter, value, null);
    }

    private void announce(Meter meter, double value, @Nullable String suffix) {
        Meter.Id id = meter.getId();
        String baseUnit = id.getBaseUnit();
        logger.info("{}: {} {}",
                    nameMapper.toHierarchicalName(id.withName(id.getName() + "." + suffix),
                                                  config().namingConvention()),
                    DoubleFormat.decimalOrNan(value),
                    baseUnit == null ? "" : baseUnit);
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.MILLISECONDS;
    }
}
