package dev.erst.fingrind.core;

import java.text.Normalizer;
import java.util.Objects;

/** Canonical, terminal-safe text retained as a human-facing accounting display value. */
public final class CanonicalDisplayText {
  private CanonicalDisplayText() {}

  /** Normalizes one display value to NFC and rejects control and bidirectional override text. */
  public static String require(String value, String fieldName) {
    String normalized =
        Normalizer.normalize(Objects.requireNonNull(value, fieldName).strip(), Normalizer.Form.NFC);
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException(fieldName + " must not be blank.");
    }
    normalized.codePoints().forEach(codePoint -> requireSafeCodePoint(codePoint, fieldName));
    return normalized;
  }

  /**
   * Converts legacy persisted display text into a visible, terminal-inert value before projection.
   *
   * <p>New commands must use {@link #require(String, String)}. This compatibility reader keeps
   * already-durable books readable without re-emitting control bytes into any human surface.
   */
  public static String sanitizePersisted(String value) {
    String normalized =
        Normalizer.normalize(Objects.requireNonNull(value, "value").strip(), Normalizer.Form.NFC);
    StringBuilder sanitized = new StringBuilder(normalized.length());
    normalized.codePoints().forEach(codePoint -> appendPersistedCodePoint(sanitized, codePoint));
    return sanitized.toString();
  }

  private static void appendPersistedCodePoint(StringBuilder sanitized, int codePoint) {
    try {
      requireSafeCodePoint(codePoint, "persisted display text");
      sanitized.appendCodePoint(codePoint);
    } catch (IllegalArgumentException rejected) {
      sanitized.append(String.format("\\u%04X", codePoint));
    }
  }

  private static void requireSafeCodePoint(int codePoint, String fieldName) {
    if (codePoint <= 0x1F
        || (codePoint >= 0x7F && codePoint <= 0x9F)
        || (codePoint >= 0x202A && codePoint <= 0x202E)
        || (codePoint >= 0x2066 && codePoint <= 0x2069)) {
      throw new IllegalArgumentException(
          fieldName + " must not contain control or bidi-override text.");
    }
  }
}
