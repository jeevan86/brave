package brave;

import brave.internal.Internal;
import brave.internal.Nullable;
import brave.internal.Platform;
import brave.internal.recorder.Recorder;
import brave.propagation.SamplingFlags;
import brave.propagation.TraceContext;
import brave.sampler.Sampler;
import zipkin.Endpoint;
import zipkin.reporter.Reporter;

public final class Tracer {
  public static Builder newBuilder() {
    return new Builder();
  }

  public static final class Builder {
    Endpoint localEndpoint;
    Reporter<zipkin.Span> reporter;
    Clock clock;
    Sampler sampler = Sampler.ALWAYS_SAMPLE;
    boolean traceId128Bit = false;

    public Builder localEndpoint(Endpoint localEndpoint) {
      if (localEndpoint == null) throw new NullPointerException("localEndpoint == null");
      this.localEndpoint = localEndpoint;
      return this;
    }

    public Builder reporter(Reporter<zipkin.Span> reporter) {
      if (reporter == null) throw new NullPointerException("reporter == null");
      this.reporter = reporter;
      return this;
    }

    public Builder clock(Clock clock) {
      if (clock == null) throw new NullPointerException("clock == null");
      this.clock = clock;
      return this;
    }

    public Builder sampler(Sampler sampler) {
      this.sampler = sampler;
      return this;
    }

    public Builder traceId128Bit(boolean traceId128Bit) {
      this.traceId128Bit = traceId128Bit;
      return this;
    }

    public Tracer build() {
      if (clock == null) clock = Platform.get();
      if (localEndpoint == null) localEndpoint = Platform.get().localEndpoint();
      if (reporter == null) reporter = Platform.get();
      return new Tracer(this);
    }
  }

  static {
    Internal.instance = new Internal() {
      @Override public Recorder recorder(Tracer tracer) {
        return tracer.recorder;
      }

      @Override public Clock clock(Tracer tracer) {
        return tracer.clock;
      }
    };
  }

  final Clock clock;
  final Endpoint localEndpoint;
  final Recorder recorder;
  final Sampler sampler;
  final boolean traceId128Bit;

  Tracer(Builder builder) {
    this.clock = builder.clock;
    this.localEndpoint = builder.localEndpoint;
    this.recorder = new Recorder(localEndpoint, clock, builder.reporter);
    this.sampler = builder.sampler;
    this.traceId128Bit = builder.traceId128Bit;
  }

  public Span joinSpan(TraceContext context) {
    return ensureSampled(context);
  }

  /**
   * Creates a new trace. If there is an existing trace, use {@link #nextSpan(TraceContext)}
   * instead.
   */
  public Span newTrace() {
    return ensureSampled(nextContext(null, SamplingFlags.EMPTY));
  }

  /**
   * Like {@link #newTrace()}, but supports parameterized sampling, for example limiting on
   * operation or url pattern.
   *
   * <p>For example, to sample all requests for a specific url:
   * <pre>{@code
   * Span newTrace(Request input) {
   *   SamplingFlags flags = SamplingFlags.NONE;
   *   if (input.url().startsWith("/experimental")) {
   *     flags = SamplingFlags.SAMPLED;
   *   } else if (input.url().startsWith("/static")) {
   *     flags = SamplingFlags.NOT_SAMPLED;
   *   }
   *   return tracer.newTrace(flags);
   * }
   * }</pre>
   */
  public Span newTrace(SamplingFlags samplingFlags) {
    return ensureSampled(nextContext(null, samplingFlags));
  }

  /** Converts the context as-is to a Span object */
  public Span toSpan(TraceContext context) {
    if (context == null) throw new NullPointerException("context == null");
    if (context.sampled()) {
      return new RealSpan(context, clock, recorder);
    }
    return new NoopSpan(context);
  }

  /**
   * Creates a new span within an existing trace. If there is no existing trace, use {@link
   * #newTrace()} instead.
   */
  public Span nextSpan(TraceContext parent) {
    if (parent == null) throw new NullPointerException("parent == null");
    if (Boolean.FALSE.equals(parent.sampled())) {
      return new NoopSpan(parent);
    }
    return ensureSampled(nextContext(parent, parent));
  }

  Span ensureSampled(TraceContext context) {
    // If the sampled flag was left unset, we need to make the decision here
    if (context.sampled() == null) {
      context = context.toBuilder()
          .sampled(sampler.isSampled(context.traceId()))
          .shared(false)
          .build();
    } else if (context.sampled()) {
      // We know an instrumented caller initiated the trace if they sampled it
      context = context.toBuilder().shared(true).build();
    }

    return toSpan(context);
  }

  TraceContext nextContext(@Nullable TraceContext parent, SamplingFlags samplingFlags) {
    long nextId = Platform.get().randomLong();
    if (parent != null) {
      return parent.toBuilder().spanId(nextId).parentId(parent.spanId()).build();
    }
    return TraceContext.newBuilder()
        .samplingFlags(samplingFlags)
        .traceIdHigh(traceId128Bit ? Platform.get().randomLong() : 0L)
        .traceId(nextId)
        .spanId(nextId).build();
  }
}
