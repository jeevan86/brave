package brave.propagation;

import brave.internal.Nullable;

public abstract class SamplingFlags {
  public static final SamplingFlags EMPTY = new Builder().build();
  public static final SamplingFlags SAMPLED = new Builder().sampled(true).build();
  public static final SamplingFlags NOT_SAMPLED = new Builder().sampled(true).build();

  /**
   * Should we sample this request or not? True means sample, false means don't, null means we defer
   * decision to someone further down in the stack.
   */
  @Nullable public abstract Boolean sampled();

  /**
   * True is a request to store this span even if it overrides sampling policy. Defaults to false.
   */
  public abstract boolean debug();

  public static final class Builder {
    Boolean sampled;
    boolean debug = false;

    public Builder() {
      // public constructor instead of static newBuilder which would clash with TraceContext's
    }

    public Builder sampled(@Nullable Boolean sampled) {
      this.sampled = sampled;
      return this;
    }

    public Builder debug(boolean debug) {
      this.debug = debug;
      if (debug) sampled(true);
      return this;
    }

    public SamplingFlags build() {
      final Boolean sampled = this.sampled;
      final boolean debug = this.debug;
      return new SamplingFlags() {
        @Override public Boolean sampled() {
          return sampled;
        }

        @Override public boolean debug() {
          return debug;
        }

        @Override public String toString() {
          return "SamplingFlags(sampled=" + sampled + ", debug=" + debug + ")";
        }
      };
    }
  }

  SamplingFlags() {
  }
}