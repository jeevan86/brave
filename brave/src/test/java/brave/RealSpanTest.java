package brave;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

import static brave.Span.Kind.SERVER;
import static org.assertj.core.api.Assertions.assertThat;

public class RealSpanTest {
  List<zipkin.Span> spans = new ArrayList();
  Tracer tracer = Tracer.newBuilder().reporter(spans::add).build();

  @Test public void autoCloseOnTryFinally() {
    try (Span span = tracer.newTrace().name("foo").start()) {
      span.tag("holy", "toledo");
    }

    assertThat(spans).hasSize(1);
  }

  @Test public void autoCloseOnTryFinally_doesntDoubleClose() {
    try (Span span = tracer.newTrace().kind(SERVER).name("getOrCreate").start()) {
      span.finish(); // user closes and also auto-close closes
    }

    assertThat(spans).hasSize(1);
  }
}
