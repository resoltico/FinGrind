package dev.erst.fingrind.core;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Constructs portable leaf names for freshly allocated artifact-publication stages. */
final class ArtifactStageFileName {
  private static final String WINDOWS_FORBIDDEN_CHARACTERS = "<>:\"/\\|?*";
  private static final Set<String> WINDOWS_RESERVED_DEVICE_BASE_NAMES =
      Set.of(
          "CON",
          "PRN",
          "AUX",
          "NUL",
          "COM1",
          "COM2",
          "COM3",
          "COM4",
          "COM5",
          "COM6",
          "COM7",
          "COM8",
          "COM9",
          "COM\u00B9",
          "COM\u00B2",
          "COM\u00B3",
          "LPT1",
          "LPT2",
          "LPT3",
          "LPT4",
          "LPT5",
          "LPT6",
          "LPT7",
          "LPT8",
          "LPT9",
          "LPT\u00B9",
          "LPT\u00B2",
          "LPT\u00B3");

  private ArtifactStageFileName() {}

  /** Composes one UUID-based stage leaf and rejects names no supported filesystem can create. */
  static String compose(String prefix, UUID stageIdentifier, String suffix) {
    String checkedPrefix = requireNonemptyPart(prefix, "prefix");
    String checkedSuffix = requireNonemptyPart(suffix, "suffix");
    return requireValidLeaf(
        checkedPrefix + Objects.requireNonNull(stageIdentifier, "stageIdentifier") + checkedSuffix);
  }

  /** Returns one valid stage leaf, rejecting path syntax and Windows-reserved file names. */
  static String requireValidLeaf(String leaf) {
    String checkedLeaf = Objects.requireNonNull(leaf, "leaf");
    if (checkedLeaf.isEmpty()) {
      throw invalidLeaf("must not be empty");
    }
    if (containsForbiddenCharacter(checkedLeaf)) {
      throw invalidLeaf(
          "must not contain a path separator, control character, or Windows-reserved character");
    }
    if (endsWithWindowsForbiddenSuffix(checkedLeaf)) {
      throw invalidLeaf("must not end with a space or period");
    }
    if (hasWindowsReservedDeviceBaseName(checkedLeaf)) {
      throw invalidLeaf("must not use a Windows-reserved device name");
    }
    return checkedLeaf;
  }

  private static String requireNonemptyPart(String value, String parameterName) {
    String checkedValue = Objects.requireNonNull(value, parameterName);
    if (checkedValue.isEmpty()) {
      throw new IllegalArgumentException(parameterName + " must be a nonempty stage-name part.");
    }
    return checkedValue;
  }

  private static boolean containsForbiddenCharacter(String leaf) {
    return leaf.codePoints().anyMatch(ArtifactStageFileName::isForbiddenCodePoint);
  }

  private static boolean isForbiddenCodePoint(int codePoint) {
    return Character.isISOControl(codePoint)
        || isUnpairedSurrogate(codePoint)
        || WINDOWS_FORBIDDEN_CHARACTERS.indexOf(codePoint) >= 0;
  }

  private static boolean isUnpairedSurrogate(int codePoint) {
    return Character.getType(codePoint) == Character.SURROGATE;
  }

  private static boolean endsWithWindowsForbiddenSuffix(String leaf) {
    return leaf.endsWith(".") || leaf.endsWith(" ");
  }

  private static boolean hasWindowsReservedDeviceBaseName(String leaf) {
    int extensionStart = leaf.indexOf('.');
    String baseName = extensionStart < 0 ? leaf : leaf.substring(0, extensionStart);
    return WINDOWS_RESERVED_DEVICE_BASE_NAMES.contains(baseName.toUpperCase(Locale.ROOT));
  }

  private static IllegalArgumentException invalidLeaf(String reason) {
    return new IllegalArgumentException("Artifact stage name " + reason + ".");
  }
}
