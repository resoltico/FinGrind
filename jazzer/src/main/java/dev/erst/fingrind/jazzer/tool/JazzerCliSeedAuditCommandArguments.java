package dev.erst.fingrind.jazzer.tool;

import dev.erst.fingrind.jazzer.support.JazzerRunTarget;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Parsed `seed-audit` subcommand arguments. */
record JazzerCliSeedAuditCommandArguments(@Nullable String targetKey, boolean jsonOutput) {
  static JazzerCliSeedAuditCommandArguments parse(List<String> args) {
    String targetKey = null;
    int index = 0;
    if (!args.isEmpty() && !args.getFirst().startsWith("-")) {
      targetKey = args.getFirst();
      JazzerRunTarget target = JazzerRunTarget.fromKey(targetKey);
      if (!target.replayable()) {
        throw new IllegalArgumentException(
            "Seed audit requires a single-harness target, not " + target.key());
      }
      index = 1;
    }
    boolean jsonOutput = false;
    if (index < args.size()) {
      String argument = args.get(index);
      if ("--json".equals(argument)) {
        jsonOutput = true;
        index++;
      } else {
        throw new IllegalArgumentException("Unexpected seed-audit argument: " + argument);
      }
    }
    if (index < args.size()) {
      throw new IllegalArgumentException("Unexpected seed-audit argument: " + args.get(index));
    }
    return new JazzerCliSeedAuditCommandArguments(targetKey, jsonOutput);
  }
}
