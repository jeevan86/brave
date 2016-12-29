package com.github.kristofa.brave;

import brave.Tracer;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

import static com.github.kristofa.brave.Brave4Adapter.toSpan;
import static com.github.kristofa.brave.Brave4Adapter.toTraceContext;
import static org.assertj.core.api.Assertions.assertThat;

public class Brave4AdapterTest {

  List<zipkin.Span> spans = new ArrayList<>();
  Tracer brave4 = Tracer.newBuilder().reporter(spans::add).build();
  Brave brave3 = Brave4Adapter.newBrave(brave4);

  @Test public void startSpanWithBrave3AndFinishInBrave4() {
    SpanId spanId = brave3.localTracer().startNewSpan("codec", "encode");

    brave.Span span = brave4.joinSpan(toTraceContext(spanId));

    span.finish();

    // we hopped apis, but the span completed and arrived in-tact!
    assertThat(spans).extracting(s -> s.name)
        .containsExactly("encode");
  }

  @Test public void startSpanWithBrave4AndFinishInBrave3() {
    brave.Span span = brave4.newTrace().name("encode").start();

    brave3.localSpanThreadBinder().setCurrentSpan(toSpan(span.context()));

    brave3.localTracer().finishSpan();

    // we hopped apis, but the span completed and arrived in-tact!
    assertThat(spans).extracting(s -> s.name)
        .containsExactly("encode");
  }
}
