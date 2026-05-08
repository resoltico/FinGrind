package dev.erst.fingrind.report.pdf;

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
}
