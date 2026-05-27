package dev.erst.fingrind.jazzer.tool;

import org.jspecify.annotations.Nullable;

/** Stable JSON failure payload for the local Jazzer operator CLI. */
record JazzerCliCommandFailurePayload(
    String status, @Nullable String command, int exitCode, String message, String usage) {
  JazzerCliCommandFailurePayload {
    status = ReplayModelValidation.requireText(status, "status");
    message = ReplayModelValidation.requireText(message, "message");
    usage = ReplayModelValidation.requireText(usage, "usage");
  }
}
