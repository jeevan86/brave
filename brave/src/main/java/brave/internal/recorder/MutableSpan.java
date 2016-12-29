package brave.internal.recorder;

import brave.Span;
import brave.propagation.TraceContext;
import brave.internal.Nullable;
import zipkin.Annotation;
import zipkin.BinaryAnnotation;
import zipkin.Constants;
import zipkin.Endpoint;

import static zipkin.Constants.LOCAL_COMPONENT;

final class MutableSpan {
  final Endpoint localEndpoint;
  final zipkin.Span.Builder span;
  boolean shared;
  // fields which are added late
  long startTimestamp;
  Endpoint remoteEndpoint;

  // flags which help us know how to reassemble the span
  Span.Kind kind;

  int flags;
  static final int FLAG_CS = 1 << 0;
  static final int FLAG_SR = 1 << 1;
  static final int FLAG_SS = 1 << 2;
  static final int FLAG_CR = 1 << 3;
  static final int FLAG_LOCAL_ENDPOINT = 1 << 4;

  boolean finished;

  // Since this is not exposed, this class could be refactored later as needed to act in a pool
  // to reduce GC churn. This would involve calling span.clear and resetting the fields below.
  MutableSpan(TraceContext context, Endpoint localEndpoint) {
    this.localEndpoint = localEndpoint;
    this.span = zipkin.Span.builder()
        .traceIdHigh(context.traceIdHigh())
        .traceId(context.traceId())
        .parentId(context.parentId())
        .id(context.spanId())
        .debug(context.debug())
        .name(""); // avoid a NPE
    shared = context.shared();
    startTimestamp = 0;
    remoteEndpoint = null;
    kind = null;
    finished = false;
  }

  synchronized MutableSpan start(long timestamp) {
    startTimestamp = timestamp;
    return this;
  }

  synchronized MutableSpan name(String name) {
    span.name(name);
    return this;
  }

  synchronized MutableSpan kind(Span.Kind kind) {
    this.kind = kind;
    return this;
  }

  synchronized MutableSpan annotate(long timestamp, String value) {
    span.addAnnotation(Annotation.create(timestamp, value, localEndpoint));
    flags |= FLAG_LOCAL_ENDPOINT;
    if (value.length() != 2) return this;
    if (value.equals(Constants.CLIENT_SEND)){
      flags |= FLAG_CS;
    } else if (value.equals(Constants.SERVER_RECV)){
      flags |= FLAG_SR;
    } else if (value.equals(Constants.SERVER_SEND)){
      flags |= FLAG_SS;
    } else if (value.equals(Constants.CLIENT_RECV)){
      flags |= FLAG_CR;
    }
    return this;
  }

  synchronized MutableSpan tag(String key, String value) {
    span.addBinaryAnnotation(BinaryAnnotation.create(key, value, localEndpoint));
    flags |= FLAG_LOCAL_ENDPOINT;
    return this;
  }

  synchronized MutableSpan remoteEndpoint(Endpoint remoteEndpoint) {
    this.remoteEndpoint = remoteEndpoint;
    return this;
  }

  /** Completes and reports the span */
  synchronized MutableSpan finish(@Nullable Long finishTimestamp, @Nullable Long duration) {
    if (finished) return this;
    finished = true;

    if (startTimestamp != 0) {
      span.timestamp(startTimestamp);
      if (finishTimestamp != null || duration != null) {
        finishTimestamp = addDuration(finishTimestamp, duration);
      }
    }
    if (kind != null) {
      String remoteEndpointType;
      String startAnnotation;
      String finishAnnotation;
      switch (kind) {
        case CLIENT:
          remoteEndpointType = Constants.SERVER_ADDR;
          startAnnotation = (flags & FLAG_CS) == 0 ? Constants.CLIENT_SEND : null;
          finishAnnotation = (flags & FLAG_CR) == 0 ? Constants.CLIENT_RECV : null;
          break;
        case SERVER:
          remoteEndpointType = Constants.CLIENT_ADDR;
          startAnnotation = (flags & FLAG_SR) == 0 ? Constants.SERVER_RECV : null;
          finishAnnotation = (flags & FLAG_SS) == 0 ? Constants.SERVER_SEND : null;
          // don't report server-side timestamp on shared-spans
          if (shared) span.timestamp(null).duration(null);
          break;
        default:
          throw new AssertionError("update kind mapping");
      }
      if (remoteEndpoint != null) {
        span.addBinaryAnnotation(BinaryAnnotation.address(remoteEndpointType, remoteEndpoint));
      }
      if (startAnnotation != null && startTimestamp != 0) {
        span.addAnnotation(Annotation.create(startTimestamp, startAnnotation, localEndpoint));
      }
      if (finishAnnotation != null && finishTimestamp != null) {
        span.addAnnotation(Annotation.create(finishTimestamp, finishAnnotation, localEndpoint));
      }
      flags |= FLAG_LOCAL_ENDPOINT;
    }

    if ((flags & FLAG_LOCAL_ENDPOINT) == 0) { // create a small dummy annotation
      span.addBinaryAnnotation(BinaryAnnotation.create(LOCAL_COMPONENT, "", localEndpoint));
    }
    return this;
  }

  synchronized zipkin.Span toSpan() {
    return span.build();
  }

  // one or the other will be null
  @Nullable Long addDuration(@Nullable Long finishTimestamp, @Nullable Long duration) {
    assert finishTimestamp != null || duration != null; // guard programming errors
    if (duration != null) {
      finishTimestamp = startTimestamp + duration;
    } else {
      duration = Math.max(finishTimestamp - startTimestamp, 1);
    }
    span.duration(duration);
    return finishTimestamp;
  }
}
