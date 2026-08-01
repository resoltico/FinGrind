package dev.erst.fingrind.jazzer.tool;

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Reports a failed seed promotion whose materialized corpus artifacts are deliberately retained.
 */
public final class RegressionSeedPromotionRetainedArtifactsException extends IOException {
  private static final long serialVersionUID = 1L;

  private final transient RegressionSeedPromotionRetention retention;
  private final SerializedRetention serializedRetention;

  /** Preserves the exact retained corpus artifacts without attempting rollback. */
  public RegressionSeedPromotionRetainedArtifactsException(
      RegressionSeedPromotionRetention retention, Throwable cause) {
    super(message(retention, cause), Objects.requireNonNull(cause, "cause must not be null"));
    this.retention = Objects.requireNonNull(retention, "retention must not be null");
    this.serializedRetention = new SerializedRetention(this.retention);
  }

  /** Returns the attempted destinations and every materialized artifact retained in place. */
  public RegressionSeedPromotionRetention retention() {
    return retention == null ? serializedRetention.restore() : retention;
  }

  private static String message(RegressionSeedPromotionRetention retention, Throwable cause) {
    RegressionSeedPromotionRetention requiredRetention =
        Objects.requireNonNull(retention, "retention must not be null");
    Throwable requiredCause = Objects.requireNonNull(cause, "cause must not be null");
    String causeMessage =
        Objects.requireNonNullElse(
            requiredCause.getMessage(), requiredCause.getClass().getSimpleName());
    return "Seed promotion did not complete: "
        + causeMessage
        + ". Retained artifacts require review; run jazzer/bin/seed-audit, then reconcile them"
        + " through a deliberate version-control change. Do not retry or clean them in place: "
        + requiredRetention.retainedArtifactPaths();
  }

  /** Serializable replacement for live {@link Path} values retained by this exception. */
  private record SerializedRetention(
      String committedInputPath, String metadataPath, List<String> retainedArtifactPaths)
      implements Serializable {
    private static final long serialVersionUID = 1L;

    private SerializedRetention {
      Objects.requireNonNull(committedInputPath, "committedInputPath");
      Objects.requireNonNull(metadataPath, "metadataPath");
      retainedArtifactPaths =
          List.copyOf(
              Objects.requireNonNull(
                  retainedArtifactPaths, "retainedArtifactPaths must not be null"));
    }

    private SerializedRetention(RegressionSeedPromotionRetention retention) {
      this(
          Objects.requireNonNull(retention, "retention must not be null")
              .committedInputPath()
              .toString(),
          retention.metadataPath().toString(),
          retention.retainedArtifactPaths().stream().map(Path::toString).toList());
    }

    private RegressionSeedPromotionRetention restore() {
      return new RegressionSeedPromotionRetention(
          Path.of(committedInputPath),
          Path.of(metadataPath),
          retainedArtifactPaths.stream().map(Path::of).toList());
    }
  }
}
