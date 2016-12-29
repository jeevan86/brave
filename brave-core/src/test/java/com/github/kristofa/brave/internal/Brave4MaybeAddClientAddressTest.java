package com.github.kristofa.brave.internal;

import brave.Tracer;
import com.github.kristofa.brave.Brave4Adapter;

public class Brave4MaybeAddClientAddressTest extends MaybeAddClientAddressTest {
  public Brave4MaybeAddClientAddressTest() {
    brave = Brave4Adapter.newBrave(Tracer.newBuilder().reporter(spans::add).build());
  }
}
