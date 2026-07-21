package dev.erst.fingrind.jazzer.tool;

import dev.erst.fingrind.core.SourceChannel;
import java.util.Objects;

/** Stable replay details for committed CLI-request seeds. */
public record CliRequestReplayDetails(ParsedPostingCommandDetails request, SourceChannel sourceChannel)
    implements ReplayDetails {
  public CliRequestReplayDetails {
    Objects.requireNonNull(request, "request must not be null");
    Objects.requireNonNull(sourceChannel, "sourceChannel must not be null");
  }
}

/** Replay details for CLI-request inputs that never produced a parsed posting command. */
record UnparsedCliRequestReplayDetails() implements ReplayDetails {}
