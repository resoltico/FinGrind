package dev.erst.fingrind.jazzer.tool;

import java.util.Objects;

/** Supported top-level Jazzer operator commands exposed by the local CLI wrapper. */
enum JazzerCliCommand {
  REPLAY("replay <target-key> <input-path> [--json]"),
  LIST_FINDINGS("list-findings [<target-key>] [--json]"),
  PROMOTE_SEED(
      "promote-seed <target-key> <input-path> --name <seed-name> --intent <coverage-intent> [--json]"),
  SEED_AUDIT("seed-audit [<target-key>] [--json]"),
  ACTIVE_TARGET_KEYS("active-target-keys");

  private final String usageSynopsis;

  JazzerCliCommand(String usageSynopsis) {
    this.usageSynopsis = usageSynopsis;
  }

  static JazzerCliCommand fromToken(String token) {
    return switch (Objects.requireNonNull(token, "token must not be null")) {
      case "replay" -> REPLAY;
      case "list-findings" -> LIST_FINDINGS;
      case "promote-seed" -> PROMOTE_SEED;
      case "seed-audit" -> SEED_AUDIT;
      case "active-target-keys" -> ACTIVE_TARGET_KEYS;
      default -> throw new IllegalArgumentException("Unknown Jazzer subcommand: " + token);
    };
  }

  String token() {
    return switch (this) {
      case REPLAY -> "replay";
      case LIST_FINDINGS -> "list-findings";
      case PROMOTE_SEED -> "promote-seed";
      case SEED_AUDIT -> "seed-audit";
      case ACTIVE_TARGET_KEYS -> "active-target-keys";
    };
  }

  String usageSynopsis() {
    return usageSynopsis;
  }

  String usage() {
    return "Usage: JazzerCli " + usageSynopsis;
  }
}
