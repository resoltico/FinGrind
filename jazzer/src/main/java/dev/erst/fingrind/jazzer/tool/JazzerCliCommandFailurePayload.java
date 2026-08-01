package dev.erst.fingrind.jazzer.tool;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Stable JSON failure payload for the local Jazzer operator CLI. */
record JazzerCliCommandFailurePayload(
    String status,
    @Nullable String command,
    int exitCode,
    String message,
    List<String> retainedArtifactPaths,
    String usage) {
  JazzerCliCommandFailurePayload {
    status = ReplayModelValidation.requireText(status, "status");
    message = ReplayModelValidation.requireText(message, "message");
    retainedArtifactPaths =
        List.copyOf(
            Objects.requireNonNull(
                retainedArtifactPaths, "retainedArtifactPaths must not be null"));
    for (String retainedArtifactPath : retainedArtifactPaths) {
      ReplayModelValidation.requireText(retainedArtifactPath, "retainedArtifactPath");
    }
    usage = ReplayModelValidation.requireText(usage, "usage");
  }
}
