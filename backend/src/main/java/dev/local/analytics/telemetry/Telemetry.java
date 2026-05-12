package dev.local.analytics.telemetry;

import dev.local.analytics.config.AppConfig;
import io.javalin.http.Context;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;

import java.time.Duration;

public class Telemetry implements AutoCloseable {
    private final SdkTracerProvider provider;
    private final Tracer tracer;

    private Telemetry(SdkTracerProvider provider, Tracer tracer) {
        this.provider = provider;
        this.tracer = tracer;
    }

    public static Telemetry create(AppConfig config) {
        OtlpHttpSpanExporter exporter = OtlpHttpSpanExporter.builder()
                .setEndpoint(config.otlpEndpoint() + "/v1/traces")
                .setTimeout(Duration.ofSeconds(2))
                .build();
        SdkTracerProvider provider = SdkTracerProvider.builder()
                .setResource(Resource.create(Attributes.of(AttributeKey.stringKey("service.name"), config.serviceName())))
                .addSpanProcessor(BatchSpanProcessor.builder(exporter).build())
                .build();
        OpenTelemetry openTelemetry = OpenTelemetrySdk.builder().setTracerProvider(provider).buildAndRegisterGlobal();
        return new Telemetry(provider, openTelemetry.getTracer(config.serviceName()));
    }

    public void startHttpSpan(Context ctx) {
        String method = String.valueOf(ctx.method());
        Span span = tracer.spanBuilder(method + " " + ctx.path())
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("http.request.method", method)
                .setAttribute("url.path", ctx.path())
                .startSpan();
        Scope scope = span.makeCurrent();
        ctx.attribute("otelSpan", span);
        ctx.attribute("otelScope", scope);
    }

    public void finishHttpSpan(Context ctx) {
        Span span = ctx.attribute("otelSpan");
        Scope scope = ctx.attribute("otelScope");
        if (span != null) {
            span.setAttribute("http.response.status_code", ctx.statusCode());
            if (ctx.statusCode() >= 500) {
                span.setStatus(StatusCode.ERROR);
            }
            span.end();
        }
        if (scope != null) {
            scope.close();
        }
    }

    @Override
    public void close() {
        provider.close();
    }
}
