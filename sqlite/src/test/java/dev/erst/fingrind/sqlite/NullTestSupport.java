package dev.erst.fingrind.sqlite;

import java.util.Objects;
import org.jspecify.annotations.NullUnmarked;

/** Narrow null escape hatch for explicit null-rejection tests in {@code @NullMarked} code. */
public final class NullTestSupport {
  private NullTestSupport() {}

  @NullUnmarked
  @SuppressWarnings("TypeParameterUnusedInFormals")
  public static <T> T nullOf() {
    return null;
  }

  @NullUnmarked
  @SuppressWarnings("TypeParameterUnusedInFormals")
  public static <T> T nullOf(Class<?> type) {
    Objects.requireNonNull(type, "type");
    return nullOf();
  }

  public static String messageOf(Throwable throwable) {
    Objects.requireNonNull(throwable, "throwable");
    return Objects.requireNonNull(throwable.getMessage(), "throwable message");
  }

  public static Throwable causeOf(Throwable throwable) {
    Objects.requireNonNull(throwable, "throwable");
    return Objects.requireNonNull(throwable.getCause(), "throwable cause");
  }
}
