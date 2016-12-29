package opentracing;

import brave.Clock;
import brave.Span;
import brave.propagation.TraceContext;
import brave.internal.Internal;
import io.opentracing.References;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

// compilation test mostly. there's a lot of options that don't make sense in zipkin
final class BraveSpanBuilder implements Tracer.SpanBuilder {
  final brave.Tracer tracer;
  final Clock clock;
  final String operationName;
  final Map<String, String> tags = new LinkedHashMap<>();

  long timestamp;
  TraceContext parent;

  BraveSpanBuilder(brave.Tracer tracer, String operationName) {
    this.tracer = tracer;
    this.clock = Internal.instance.clock(tracer);
    this.operationName = operationName;
  }

  @Override public Tracer.SpanBuilder asChildOf(SpanContext spanContext) {
    return addReference(References.CHILD_OF, spanContext);
  }

  @Override public Tracer.SpanBuilder asChildOf(io.opentracing.Span span) {
    return asChildOf(span.context());
  }

  @Override public Tracer.SpanBuilder addReference(String reference, SpanContext spanContext) {
    if (parent != null) return this;// yolo pick the first parent!
    if (References.CHILD_OF.equals(reference) || References.FOLLOWS_FROM.equals(reference)) {
      this.parent = ((BraveTracer.BraveSpanContext) spanContext).context;
    }
    return this;
  }

  @Override public Tracer.SpanBuilder withTag(String key, String value) {
    tags.put(key, value);
    return this;
  }

  @Override public Tracer.SpanBuilder withTag(String key, boolean value) {
    return withTag(key, Boolean.toString(value));
  }

  @Override public Tracer.SpanBuilder withTag(String key, Number value) {
    return withTag(key, value.toString());
  }

  @Override public Tracer.SpanBuilder withStartTimestamp(long timestamp) {
    this.timestamp = timestamp;
    return this;
  }

  @Override public Iterable<Map.Entry<String, String>> baggageItems() {
    return Collections.emptySet();
  }

  @Override public io.opentracing.Span start() {
    Span result = parent == null ? tracer.newTrace() : tracer.nextSpan(parent);
    if (operationName != null) result.name(operationName);
    for (Map.Entry<String, String> tag : tags.entrySet()) {
      result.tag(tag.getKey(), tag.getValue());
    }
    if (timestamp != 0) {
      return new BraveSpan(result.start(timestamp), timestamp);
    }
    return new BraveSpan(result.start(), clock.currentTimeMicroseconds());
  }
}
