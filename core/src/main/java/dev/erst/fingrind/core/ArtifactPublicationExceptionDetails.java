package dev.erst.fingrind.core;

import java.io.Serializable;
import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Captures artifact-publication facts in a serializable form while live exceptions retain paths.
 */
final class ArtifactPublicationExceptionDetails {
  private ArtifactPublicationExceptionDetails() {}

  static String pathText(Path path, String parameterName) {
    return Objects.requireNonNull(path, parameterName).toString();
  }

  static Path path(String serializedPath) {
    return Path.of(Objects.requireNonNull(serializedPath, "serializedPath"));
  }

  static @Nullable SerializedRetention capture(@Nullable ArtifactPublicationRetention retention) {
    return retention == null ? null : new SerializedRetention(retention);
  }

  static @Nullable ArtifactPublicationRetention restore(
      @Nullable SerializedRetention serializedRetention) {
    return serializedRetention == null ? null : serializedRetention.restore();
  }

  /** Serializable replacement for one retained stage whose live path implementation is opaque. */
  record SerializedRetention(String retainedStagePath) implements Serializable {
    private static final long serialVersionUID = 1L;

    SerializedRetention {
      Objects.requireNonNull(retainedStagePath, "retainedStagePath");
    }

    SerializedRetention(ArtifactPublicationRetention retention) {
      this(
          pathText(
              Objects.requireNonNull(retention, "retention").retainedStagePath(),
              "retention.retainedStagePath"));
    }

    ArtifactPublicationRetention restore() {
      return new ArtifactPublicationRetention(path(retainedStagePath));
    }
  }
}
