package dev.erst.fingrind.jazzer.tool;

import dev.erst.fingrind.jazzer.support.JazzerRunTarget;
import java.util.Arrays;

/** Renders the top-level operator usage text for the local Jazzer CLI. */
final class JazzerCliUsageText {
  private JazzerCliUsageText() {}

  static String usageText() {
    return String.join(
        System.lineSeparator(),
        "Usage:",
        "  JazzerCli "
            + JazzerCliMainArguments.PROJECT_ROOT_OPTION
            + " <jazzer-project-dir> "
            + JazzerCliCommand.REPLAY.usageSynopsis(),
        "  JazzerCli "
            + JazzerCliMainArguments.PROJECT_ROOT_OPTION
            + " <jazzer-project-dir> "
            + JazzerCliCommand.LIST_FINDINGS.usageSynopsis(),
        "  JazzerCli "
            + JazzerCliMainArguments.PROJECT_ROOT_OPTION
            + " <jazzer-project-dir> "
            + JazzerCliCommand.PROMOTE_SEED.usageSynopsis(),
        "  JazzerCli "
            + JazzerCliMainArguments.PROJECT_ROOT_OPTION
            + " <jazzer-project-dir> "
            + JazzerCliCommand.SEED_AUDIT.usageSynopsis(),
        "  JazzerCli " + JazzerCliCommand.ACTIVE_TARGET_KEYS.usageSynopsis(),
        "  JazzerCli --help",
        "",
        "Commands:",
        "  replay         Replay one raw local input against one replayable harness.",
        "  list-findings  Replay-classify raw local finding artifacts for one or all harnesses.",
        "  promote-seed   Commit one ad hoc replay input into the deterministic seed floor.",
        "  seed-audit     Summarize committed seeds and fail if duplicate raw inputs exist.",
        "  active-target-keys  Print the active fuzz target keys in canonical topology order.",
        "",
        "Replayable targets:",
        "  " + supportedReplayTargets());
  }

  private static String supportedReplayTargets() {
    return Arrays.stream(JazzerRunTarget.values())
        .filter(JazzerRunTarget::replayable)
        .map(JazzerRunTarget::key)
        .sorted()
        .reduce((left, right) -> left + ", " + right)
        .orElse("(none)");
  }
}
