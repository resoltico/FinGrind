package dev.erst.fingrind.executor;

import dev.erst.fingrind.core.PostingId;
import java.security.SecureRandom;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.random.RandomGenerator;

/** Project-owned UUID v7 posting identifier generator. */
public final class UuidV7PostingIdGenerator implements PostingIdGenerator {
  private static final long MAX_TIMESTAMP_MILLIS = 0x0000FFFFFFFFFFFFL;
  private static final long VERSION_7_MASK = 0x0000000000007000L;
  private static final long RANDOM_MOST_SIGNIFICANT_MASK = 0x0000000000000FFFL;
  private static final long VARIANT_2_MASK = 0x8000000000000000L;
  private static final long RANDOM_LEAST_SIGNIFICANT_MASK = 0x3FFFFFFFFFFFFFFFL;

  private final LongSupplier timestampSource;
  private final RandomGenerator randomSource;

  /**
   * Creates the production UUID v7 generator backed by wall-clock milliseconds and SecureRandom.
   */
  public UuidV7PostingIdGenerator() {
    this(System::currentTimeMillis, new SecureRandom());
  }

  UuidV7PostingIdGenerator(LongSupplier timestampSource, RandomGenerator randomSource) {
    this.timestampSource = timestampSource;
    this.randomSource = randomSource;
  }

  @Override
  public PostingId nextPostingId() {
    long timestampMillis = timestampSource.getAsLong();
    if (timestampMillis < 0 || timestampMillis > MAX_TIMESTAMP_MILLIS) {
      throw new IllegalStateException(
          "UUID v7 requires a non-negative 48-bit millisecond timestamp.");
    }

    long mostSignificantBits =
        (timestampMillis << 16)
            | VERSION_7_MASK
            | (randomSource.nextLong() & RANDOM_MOST_SIGNIFICANT_MASK);
    long leastSignificantBits =
        VARIANT_2_MASK | (randomSource.nextLong() & RANDOM_LEAST_SIGNIFICANT_MASK);
    return new PostingId(new UUID(mostSignificantBits, leastSignificantBits).toString());
  }
}
