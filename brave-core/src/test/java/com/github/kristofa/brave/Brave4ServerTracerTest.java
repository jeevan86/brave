package com.github.kristofa.brave;

import brave.Tracer;

public class Brave4ServerTracerTest extends ServerTracerTest {
  @Override Brave newBrave() {
    return Brave4Adapter.newBrave(Tracer.newBuilder()
        .clock(clock::currentTimeMicroseconds)
        .localEndpoint(ZIPKIN_ENDPOINT)
        .reporter(spans::add).build());
  }
}
