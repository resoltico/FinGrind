package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliPostingEntryPayload;
import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import org.jspecify.annotations.Nullable;

/** Entry point for mapping and rendering caller-authored posting entry facts. */
final class CliPostingEntryPayloadSupport {
  private CliPostingEntryPayloadSupport() {}

  static @Nullable CliPostingEntryPayload entryPayload(@Nullable BookkeepingEntry entry) {
    return CliPostingEntryPayloadMapper.entryPayload(entry);
  }

  static String renderEntryFacts(CliPostingEntryPayload entry) {
    return CliPostingEntryTextRenderer.renderEntryFacts(entry);
  }
}
