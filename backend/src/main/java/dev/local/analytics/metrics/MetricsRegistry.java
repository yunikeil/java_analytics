package dev.local.analytics.metrics;

import io.javalin.http.Context;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;

public class MetricsRegistry {
    private static final String START_NANOS_ATTRIBUTE = "metricsStartNanos";
    private static final double[] BUCKETS = {0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1, 2.5, 5, 10};

    private final Instant startedAt = Instant.now();
    private final Map<Key, HttpMetric> httpMetrics = new ConcurrentHashMap<>();

    public void markStart(Context ctx) {
        ctx.attribute(START_NANOS_ATTRIBUTE, System.nanoTime());
    }

    public void record(Context ctx) {
        Long start = ctx.attribute(START_NANOS_ATTRIBUTE);
        if (start == null) {
            return;
        }
        double seconds = (System.nanoTime() - start) / 1_000_000_000.0;
        String path = ctx.path().replaceAll("/[0-9a-fA-F-]{36}", "/{id}");
        Key key = new Key(String.valueOf(ctx.method()), path, String.valueOf(ctx.statusCode()));
        httpMetrics.computeIfAbsent(key, ignored -> new HttpMetric()).observe(seconds);
    }

    public String render() {
        StringBuilder out = new StringBuilder();
        out.append("# HELP analytics_trainer_uptime_seconds Backend process uptime in seconds\n");
        out.append("# TYPE analytics_trainer_uptime_seconds gauge\n");
        out.append("analytics_trainer_uptime_seconds ").append(Duration.between(startedAt, Instant.now()).toSeconds()).append('\n');

        out.append("# HELP analytics_trainer_http_requests_total Total HTTP requests handled by backend\n");
        out.append("# TYPE analytics_trainer_http_requests_total counter\n");
        httpMetrics.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(Key::path)
                        .thenComparing(Key::method)
                        .thenComparing(Key::status)))
                .forEach(entry -> appendCounter(out, entry.getKey(), entry.getValue()));

        out.append("# HELP analytics_trainer_http_request_duration_seconds HTTP request duration in seconds\n");
        out.append("# TYPE analytics_trainer_http_request_duration_seconds histogram\n");
        httpMetrics.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(Key::path)
                        .thenComparing(Key::method)
                        .thenComparing(Key::status)))
                .forEach(entry -> appendHistogram(out, entry.getKey(), entry.getValue()));
        return out.toString();
    }

    private void appendCounter(StringBuilder out, Key key, HttpMetric metric) {
        out.append("analytics_trainer_http_requests_total")
                .append(labels(key, null))
                .append(' ')
                .append(metric.count.sum())
                .append('\n');
    }

    private void appendHistogram(StringBuilder out, Key key, HttpMetric metric) {
        for (int i = 0; i < BUCKETS.length; i++) {
            out.append("analytics_trainer_http_request_duration_seconds_bucket")
                    .append(labels(key, formatBucket(BUCKETS[i])))
                    .append(' ')
                    .append(metric.buckets[i].sum())
                    .append('\n');
        }
        out.append("analytics_trainer_http_request_duration_seconds_bucket")
                .append(labels(key, "+Inf"))
                .append(' ')
                .append(metric.count.sum())
                .append('\n');
        out.append("analytics_trainer_http_request_duration_seconds_sum")
                .append(labels(key, null))
                .append(' ')
                .append(metric.sum.sum())
                .append('\n');
        out.append("analytics_trainer_http_request_duration_seconds_count")
                .append(labels(key, null))
                .append(' ')
                .append(metric.count.sum())
                .append('\n');
    }

    private String labels(Key key, String le) {
        StringBuilder labels = new StringBuilder("{method=\"")
                .append(escape(key.method()))
                .append("\",path=\"")
                .append(escape(key.path()))
                .append("\",status=\"")
                .append(escape(key.status()))
                .append('"');
        if (le != null) {
            labels.append(",le=\"").append(le).append('"');
        }
        return labels.append('}').toString();
    }

    private String formatBucket(double bucket) {
        if (bucket == Math.rint(bucket)) {
            return String.valueOf((long) bucket);
        }
        return String.valueOf(bucket);
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record Key(String method, String path, String status) {
    }

    private static final class HttpMetric {
        private final LongAdder count = new LongAdder();
        private final DoubleAdder sum = new DoubleAdder();
        private final LongAdder[] buckets = new LongAdder[BUCKETS.length];

        private HttpMetric() {
            for (int i = 0; i < buckets.length; i++) {
                buckets[i] = new LongAdder();
            }
        }

        private void observe(double seconds) {
            count.increment();
            sum.add(seconds);
            for (int i = 0; i < BUCKETS.length; i++) {
                if (seconds <= BUCKETS[i]) {
                    buckets[i].increment();
                }
            }
        }
    }
}

