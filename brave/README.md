Brave Api (v4)
==============

This module includes a work-in-progress shiny new api

## Basics

When tracing local code, just run it inside a span.
```java
try (Span span = tracer.newTrace().name("encode").start()) {
  doSomethingExpensive();
}
```

In the above example, the span is the root of the trace. In many cases,
you will be a part of an existing trace. When this is the case, call
`nextSpan` instead of `newTrace`

```java
try (Span span = tracer.nextSpan(parent.context()).name("encode").start()) {
  doSomethingExpensive();
}
```

Once you have a span, you can add tags to it, which can be used as lookup
keys or details. For example, you might add a tag with your runtime version:

```java
span.tag("clnt/finagle.version", "6.36.0");
```

## RPC tracing
RPC tracing is often done automatically by interceptors. Under the scenes,
they add tags and events that relate to their role in an RPC operation.

Here's an example of a client span:
```java
// before you send a request, add metadata that describes the operation
span = tracer.newTrace().name("get").type(CLIENT);
span.tag("clnt/finagle.version", "6.36.0");
span.tag(TraceKeys.HTTP_PATH, "/api");
span.remoteEndpoint(Endpoint.builder()
    .serviceName("backend")
    .ipv4(127 << 24 | 1)
    .port(8080).build());

// when the request is scheduled, start the span
span.start();

// if you have callbacks for when data is on the wire, note those events
span.annotate(Constants.WIRE_SEND);
span.annotate(Constants.WIRE_RECV);

// when the response is complete, finish the span
span.finish();
```

## Sampling
Sampling is an up-front decision, meaning that the decision to report
data is made at the first operation in a trace, and that decision is
propagated downstream.

By default, there's a global sampler that applies a single rate to all
traced operations. `Tracer.Builder.sampler` is how you indicate this,
and it defaults to trace every request.

### Custom sampling

You may want to apply different policies depending on what the operation
is. For example, you might not want to trace requests to static resources
such as images, or you might want to trace all requests to a new api.
Most users will use a framework interceptor which automates this sort of
policy. Here's how they might work internally.

```java
Span newTrace(Request input) {
  SamplingFlags flags = SamplingFlags.NONE;
  if (input.url().startsWith("/experimental")) {
    flags = SamplingFlags.SAMPLED;
  } else if (input.url().startsWith("/static")) {
    flags = SamplingFlags.NOT_SAMPLED;
  }
  return tracer.newTrace(flags);
}
```

## Performance
Brave has been built with performance in mind. Using the core Span api,
you can record spans in sub-microseconds. When a span is sampled, there's
effectively no overhead (as it is a noop).

Unlike previous implementations, Brave 4 only needs one timestamp per
span. All annotations are recorded on an offset basis, using the less
expensive and more precise `System.nanoTime()` function.

## Acknowledgements
Brave 4's design lends from past experience and similar open source work.
Quite a lot of decisions were driven by portability with Brave 3, and the
dozens of individuals involved in that. There are notable new aspects
that are borrowed or adapted from others.

Brave 4 allows you to pass around a Span object which can report itself
to Zipkin when finished. This is better than using thread contexts in
some cases, particularly where many async hops are in use. The Span api
is derived from OpenTracing, narrowed to more cleanly match Zipkin's
abstraction. As a result, a bridge from Brave 4 to OpenTracing v0.20.2
is relatively little code. It should be able to implement future
versions of OpenTracing as well.

Much of Brave 4's architecture is borrowed from Finagle, whose design
implies a separation between the propagated trace context and the data
collected in process. For example, Brave's MutableSpanMap is the same
overall design as Finagle's. The internals of MutableSpanMap were adapted
from [WeakConcurrentMap](https://github.com/raphw/weak-lock-free).

Brave 4's pubic namespance is more defensive that the past, using a package
accessor design from [OkHttp](https://github.com/square/okhttp).
