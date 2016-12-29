package opentracing;

import brave.propagation.TraceContext;
import brave.internal.HexCodec;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;
import java.util.Collections;
import java.util.Map;

// compilation test mostly. there's a lot of options that don't make sense in zipkin
final class BraveTracer implements Tracer {

  static final String TRACE_ID_NAME = "X-B3-TraceId";
  static final String SPAN_ID_NAME = "X-B3-SpanId";
  static final String PARENT_SPAN_ID_NAME = "X-B3-ParentSpanId";
  static final String SAMPLED_NAME = "X-B3-Sampled";
  static final String FLAGS_NAME = "X-B3-Flags";

  final brave.Tracer tracer;

  BraveTracer(brave.Tracer tracer) {
    this.tracer = tracer;
  }

  @Override public SpanBuilder buildSpan(String operationName) {
    return new BraveSpanBuilder(tracer, operationName);
  }

  @Override public <C> void inject(SpanContext spanContext, Format<C> format, C carrier) {
    if (format != Format.Builtin.HTTP_HEADERS) {
      throw new UnsupportedOperationException(format + " != Format.Builtin.HTTP_HEADERS");
    }
    TraceContext traceContext = ((BraveSpanContext) spanContext).context;
    TextMap textMap = (TextMap) carrier;
    textMap.put(TRACE_ID_NAME, traceContext.traceIdString());
    if (traceContext.parentId() != null) {
      textMap.put(PARENT_SPAN_ID_NAME, HexCodec.toLowerHex(traceContext.parentId()));
    }
    textMap.put(SPAN_ID_NAME, HexCodec.toLowerHex(traceContext.spanId()));
    if (traceContext.sampled() != null) {
      textMap.put(SAMPLED_NAME, traceContext.sampled() ? "1" : "0");
    }
    if (traceContext.debug()) {
      textMap.put(FLAGS_NAME, "1");
    }
  }

  @Override public <C> SpanContext extract(Format<C> format, C carrier) {
    if (format != Format.Builtin.HTTP_HEADERS) {
      throw new UnsupportedOperationException(format.toString());
    }
    TraceContext.Builder result = TraceContext.newBuilder();
    TextMap textMap = (TextMap) carrier;
    for (Map.Entry<String, String> entry : textMap) {
      if (entry.getKey().equalsIgnoreCase(SAMPLED_NAME)) {
        result.sampled(entry.getValue().equals("1")
            || entry.getValue().toLowerCase().equals("true"));
      } else if (entry.getKey().equalsIgnoreCase(TRACE_ID_NAME)) {
        String traceId = entry.getValue();
        result.traceIdHigh(
            traceId.length() == 32 ? HexCodec.lowerHexToUnsignedLong(traceId, 0) : 0);
        result.traceId(HexCodec.lowerHexToUnsignedLong(traceId));
      } else if (entry.getKey().equalsIgnoreCase(PARENT_SPAN_ID_NAME)) {
        result.parentId(HexCodec.lowerHexToUnsignedLong(entry.getValue()));
      } else if (entry.getKey().equalsIgnoreCase(SPAN_ID_NAME)) {
        result.spanId(HexCodec.lowerHexToUnsignedLong(entry.getValue()));
      } else if (entry.getKey().equalsIgnoreCase(FLAGS_NAME)) {
        result.debug(entry.getValue().equals("1"));
      }
    }
    return new BraveSpanContext(result.build());
  }

  static final class BraveSpanContext implements SpanContext {
    final TraceContext context;

    BraveSpanContext(TraceContext context) {
      this.context = context;
    }

    @Override public Iterable<Map.Entry<String, String>> baggageItems() {
      return Collections.EMPTY_SET;
    }
  }
}
