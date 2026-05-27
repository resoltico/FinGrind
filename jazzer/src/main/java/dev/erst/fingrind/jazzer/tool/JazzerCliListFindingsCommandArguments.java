package dev.erst.fingrind.jazzer.tool;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** Parsed `list-findings` subcommand arguments. */
record JazzerCliListFindingsCommandArguments(@Nullable String targetKey, boolean jsonOutput) {
  static JazzerCliListFindingsCommandArguments parse(List<String> args) {
    String targetKey = null;
    int index = 0;
    if (!args.isEmpty() && !args.getFirst().startsWith("-")) {
      targetKey = args.getFirst();
      index = 1;
    }
    boolean jsonOutput = false;
    if (index < args.size()) {
      String argument = args.get(index);
      if ("--json".equals(argument)) {
        jsonOutput = true;
        index++;
      } else {
        throw new IllegalArgumentException("Unexpected list-findings argument: " + argument);
      }
    }
    if (index < args.size()) {
      throw new IllegalArgumentException("Unexpected list-findings argument: " + args.get(index));
    }
    return new JazzerCliListFindingsCommandArguments(targetKey, jsonOutput);
  }
}
