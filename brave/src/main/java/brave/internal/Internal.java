package brave.internal;

import brave.Clock;
import brave.Tracer;
import brave.internal.recorder.Recorder;
import zipkin.Endpoint;

/**
 * Allows internal classes outside the package {@code brave} to use non-public methods. This allows
 * us access internal methods while also making obvious the hooks are not for public use. The only
 * implementation of this interface is in {@link brave.Tracer}.
 *
 * <p>Originally designed by OkHttp team, derived from {@code okhttp3.internal.Internal}
 */
public abstract class Internal {

  public static void initializeInstanceForTests() {
    // Needed in tests to ensure that the instance is actually pointing to something.
    Tracer.newBuilder().build();
  }

  public abstract Recorder recorder(Tracer tracer);

  public abstract Clock clock(Tracer tracer);

  public static Internal instance;
}
