package com.github.kristofa.brave;

import brave.Tracer;
import brave.propagation.TraceContext;
import com.github.kristofa.brave.internal.InternalSpan;
import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;

/**
 * This is a bridge between the new {@linkplain Tracer} api in {@code io.zipkin.brave:brave} and the
 * former model defined prior.
 *
 * <p>This class uses package-protected hooks to integrate, and is optional. For example, those not
 * using {@linkplain Tracer} do not have a runtime dependency on {@code io.zipkin.brave:brave}.
 */
public final class Brave4Adapter {
  /** The endpoint in {@linkplain ServerClientAndLocalSpanState} is no longer used. */
  static final Endpoint DUMMY_ENDPOINT = Endpoint.builder().serviceName("not used").build();

  public static Brave newBrave(Tracer tracer) {
    if (tracer == null) throw new NullPointerException("tracer == null");
    return newBrave(tracer, new ThreadLocalServerClientAndLocalSpanState(DUMMY_ENDPOINT));
  }

  /**
   * Constructs a new Brave instance that sends traces to the provided tracer.
   *
   * @param state for in-process propagation. Note {@link CommonSpanState#endpoint()} is ignored.
   */
  public static Brave newBrave(Tracer tracer, ServerClientAndLocalSpanState state) {
    if (tracer == null) throw new NullPointerException("tracer == null");
    if (state == null) throw new NullPointerException("state == null");
    return new Brave.Builder(state)
        .spanFactory(new Brave4SpanFactory(tracer))
        .recorder(new Brave4Recorder(tracer)).build();
  }

  public static TraceContext toTraceContext(SpanId spanId) {
    if (spanId == null) throw new NullPointerException("spanId == null");
    return TraceContext.newBuilder()
        .traceIdHigh(spanId.traceIdHigh)
        .traceId(spanId.traceId)
        .parentId(spanId.nullableParentId())
        .spanId(spanId.spanId)
        .debug(spanId.debug())
        .sampled(spanId.sampled())
        .shared(spanId.shared).build();
  }

  public static Span toSpan(TraceContext context) {
    if (context == null) throw new NullPointerException("context == null");
    return InternalSpan.instance.toSpan(toSpanId(context));
  }

  static final class Brave4SpanFactory extends SpanFactory {
    final Tracer delegate;

    Brave4SpanFactory(Tracer tracer) {
      this.delegate = tracer;
    }

    @Override Span nextSpan(SpanId maybeParent) {
      brave.Span span = maybeParent != null
          ? delegate.nextSpan(toTraceContext(maybeParent))
          : delegate.newTrace();
      return Brave.toSpan(toSpanId(span.context()));
    }

    @Override Span joinSpan(SpanId spanId) {
      TraceContext context = toTraceContext(spanId);
      return Brave.toSpan(toSpanId(delegate.joinSpan(context).context()));
    }
  }

  static final class Brave4Recorder extends Recorder {
    final Tracer tracer;

    Brave4Recorder(Tracer tracer) {
      this.tracer = tracer;
    }

    @Override void name(Span span, String name) {
      brave4(span).name(name);
    }

    @Override void start(Span span) {
      brave4(span).start();
    }

    @Override void start(Span span, SpanKind kind) {
      brave4(span, kind).start();
    }

    @Override void start(Span span, long timestamp) {
      brave4(span).start(timestamp);
    }

    @Override void annotate(Span span, String value) {
      brave4(span).annotate(value);
    }

    @Override void annotate(Span span, long timestamp, String value) {
      brave4(span).annotate(timestamp, value);
    }

    @Override void remoteAddress(Span span, SpanKind kind, Endpoint endpoint) {
      brave4(span, kind).remoteEndpoint(zipkin.Endpoint.builder()
          .serviceName(endpoint.service_name)
          .ipv4(endpoint.ipv4)
          .ipv6(endpoint.ipv6)
          .port(endpoint.port)
          .build());
    }

    brave.Span brave4(Span span, SpanKind kind) {
      brave.Span brave4 = brave4(span);
      switch (kind) {
        case CLIENT:
          brave4.kind(brave.Span.Kind.CLIENT);
          break;
        case SERVER:
          brave4.kind(brave.Span.Kind.SERVER);
          break;
        default:
          throw new AssertionError(kind + " is not yet supported");
      }
      return brave4;
    }

    @Override void tag(Span span, String key, String value) {
      brave4(span).tag(key, value);
    }

    @Override void finish(Span span) {
      brave4(span).finish();
    }

    @Override void finish(Span span, SpanKind kind) {
      brave4(span, kind).finish();
    }

    @Override void finish(Span span, long duration) {
      brave4(span).finish(duration);
    }

    @Override void flush(Span span) {
      brave4(span).flush();
    }

    // If garbage becomes a concern, we can introduce caching or thread locals.
    brave.Span brave4(Span span) {
      return tracer.toSpan(toTraceContext(InternalSpan.instance.context(span)));
    }
  }

  static SpanId toSpanId(TraceContext context) {
    return SpanId.builder()
        .traceIdHigh(context.traceIdHigh())
        .traceId(context.traceId())
        .parentId(context.parentId())
        .spanId(context.spanId())
        .debug(context.debug())
        .sampled(context.sampled())
        .shared(context.shared()).build();
  }
}
