package dev.erst.fingrind.core;

import java.util.Objects;

/** Versioned semantic fingerprint for one normalized posting request model. */
public record RequestFingerprint(int version, String sha256Hex) {
  public static final int CURRENT_VERSION = 2;

  /** Validates one persisted request fingerprint. */
  public RequestFingerprint {
    if (version < 1) {
      throw new IllegalArgumentException("Request fingerprint version must be positive.");
    }
    Objects.requireNonNull(sha256Hex, "sha256Hex");
    if (!sha256Hex.matches("[0-9a-f]{64}")) {
      throw new IllegalArgumentException(
          "Request fingerprint sha256Hex must be one lowercase 64-character hex digest.");
    }
  }
}
