package dev.erst.fingrind.cli;

import java.io.IOException;

/** Signals that one request JSON payload exceeded the supported UTF-8 byte limit. */
final class CliRequestPayloadTooLargeException extends IOException {
  private static final long serialVersionUID = 1L;

  private final int maxBytes;

  CliRequestPayloadTooLargeException(int maxBytes) {
    super("Request JSON exceeded the supported " + maxBytes + "-byte UTF-8 limit.");
    this.maxBytes = maxBytes;
  }

  int maxBytes() {
    return maxBytes;
  }
}
