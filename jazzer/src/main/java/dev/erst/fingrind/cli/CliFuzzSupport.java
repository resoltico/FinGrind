package dev.erst.fingrind.cli;

import dev.erst.fingrind.application.PostEntryCommand;
import dev.erst.fingrind.core.PostingId;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/** Shared helpers for FinGrind Jazzer harnesses that start from CLI request JSON. */
public final class CliFuzzSupport {
  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-04-07T12:00:00Z"), ZoneOffset.UTC);

  private CliFuzzSupport() {}

  /** Parses one CLI request payload from bytes using the same reader used by the production CLI. */
  public static PostEntryCommand readPostEntryCommand(byte[] input) {
    Objects.requireNonNull(input, "input must not be null");
    return new CliRequestReader(new ByteArrayInputStream(input))
        .readPostEntryCommand(Path.of("-"), FIXED_CLOCK);
  }

  /** Returns a deterministic posting-id supplier for one fuzz iteration. */
  public static Supplier<PostingId> postingIdSupplier(byte[] input) {
    Objects.requireNonNull(input, "input must not be null");
    String postingId = UUID.nameUUIDFromBytes(input).toString();
    return () -> new PostingId(postingId);
  }
}
