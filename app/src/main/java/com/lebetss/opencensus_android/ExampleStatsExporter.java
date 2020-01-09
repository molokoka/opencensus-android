package com.lebetss.opencensus_android;

import io.opencensus.common.Timestamp;
import io.opencensus.exporter.metrics.util.IntervalMetricReader;
import io.opencensus.exporter.metrics.util.MetricExporter;
import io.opencensus.exporter.metrics.util.MetricReader;
import io.opencensus.metrics.LabelKey;
import io.opencensus.metrics.LabelValue;
import io.opencensus.metrics.Metrics;
import io.opencensus.metrics.export.Metric;
import io.opencensus.metrics.export.MetricDescriptor;
import io.opencensus.metrics.export.Point;
import io.opencensus.metrics.export.TimeSeries;
import io.opencensus.stats.Aggregation;
import io.opencensus.stats.Aggregation.Distribution;
import io.opencensus.stats.BucketBoundaries;
import io.opencensus.stats.Measure;
import io.opencensus.stats.Measure.MeasureLong;
import io.opencensus.stats.Stats;
import io.opencensus.stats.StatsRecorder;
import io.opencensus.stats.View;
import io.opencensus.stats.View.Name;
import io.opencensus.stats.ViewManager;
import io.opencensus.tags.TagKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;

public final class ExampleStatsExporter extends MetricExporter {
    private static final String EXAMPLE_STATS_EXPORTER = "ExampleStatsExporter";
    private static final Logger logger = Logger.getLogger(ExampleStatsExporter.class.getName());
    private static final Measure.MeasureLong M_LATENCY_MS =
            Measure.MeasureLong.create("example/latency", "A measure to test the exporter", "ms");
    private static final StatsRecorder statsRecorder = Stats.getStatsRecorder();
    private final IntervalMetricReader intervalMetricReader;

    /** Entry point from the command line to test the exporter. */
    public static void main(String... args) {
        ExampleStatsExporter exporter = ExampleStatsExporter.createAndRegister();
        registerAllViews();
        // Collect some data to test the exporter
        Random rand = new Random();
        try {
            for (int i = 0; i < 100; i++) {
                long latency = rand.nextInt(20); // A random value to test the exporter
                statsRecorder.newMeasureMap().put(M_LATENCY_MS, latency).record();
                Thread.sleep(latency);
            }
            exporter.stop();
        } catch (InterruptedException e) {
            logger.info("Got an error: " + e.getMessage());
        }
    }

    /**
     * Creates and registers the ExampleStatsExporter.
     */
    public static ExampleStatsExporter createAndRegister() {
        return new ExampleStatsExporter();
    }

    private ExampleStatsExporter() {
        IntervalMetricReader.Options.Builder options = IntervalMetricReader.Options.builder();
        MetricReader reader =
                MetricReader.create(
                        MetricReader.Options.builder()
                                .setMetricProducerManager(Metrics.getExportComponent().getMetricProducerManager())
                                .setSpanName(EXAMPLE_STATS_EXPORTER)
                                .build());
        intervalMetricReader = IntervalMetricReader.create(this, reader, options.build());
    }

    /**
     * Exports the list of given {@link Metric} objects.
     *
     * @param metrics the list of {@link Metric} to be exported.
     */
    @Override
    public void export(Collection<Metric> metrics) {
        logger.info("Exporting  metrics");
        for (Metric metric : metrics) {
            MetricDescriptor md = metric.getMetricDescriptor();
            MetricDescriptor.Type type = md.getType();
            logger.info("Name: " + md.getName() + ", type: " + type);
            List<LabelKey> keys = md.getLabelKeys();
            StringBuffer keysSb = new StringBuffer("Keys: ");
            for (LabelKey k : keys) {
                keysSb.append(k.getKey() + " ");
            }
            logger.info("Keys: " + keysSb);
            StringBuffer sb = new StringBuffer();
            sb.append("Seconds\tNanos\tValue\n");
            List<TimeSeries> tss = metric.getTimeSeriesList();
            for (TimeSeries ts : tss) {
                Timestamp start = ts.getStartTimestamp();
                logger.info("Start " + start + "\n");
                List<LabelValue> lvs = ts.getLabelValues();
                StringBuffer lvSb = new StringBuffer("Keys: ");
                for (LabelValue v : lvs) {
                    lvSb.append(v.getValue() + " ");
                }
                logger.info("Label values: " + lvSb + "\n");
                for (Point p : ts.getPoints()) {
                    Timestamp t = p.getTimestamp();
                    long s = t.getSeconds();
                    long nanos = t.getNanos();
                    String line = s + "\t" + nanos + "\t" + p.getValue();
                    sb.append(line);
                }
                logger.info("Timeseries to export:\n" + sb);
            }
        }
    }

    private static void registerAllViews() {
        // Defining the distribution aggregations
        Aggregation latencyDistribution =
                Aggregation.Distribution.create(BucketBoundaries.create(Arrays.asList(0.0, 5.0, 10.0, 15.0, 20.0)));

        // Define the views
        List<TagKey> noKeys = new ArrayList<TagKey>();
        View[] views =
                new View[]{
                        View.create(
                                Name.create("ocjavaexporter/latency"),
                                "The distribution of latencies",
                                M_LATENCY_MS,
                                latencyDistribution,
                                noKeys)
                };

        // Create the view manager
        ViewManager vmgr = Stats.getViewManager();

        // Then finally register the views
        for (View view : views) {
            vmgr.registerView(view);
        }
    }

    public void stop() {
        intervalMetricReader.stop();
    }
}