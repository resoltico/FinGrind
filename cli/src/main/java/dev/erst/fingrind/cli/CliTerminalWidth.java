package dev.erst.fingrind.cli;

/** Terminal-cell width support for the human text projection. */
final class CliTerminalWidth {
  private CliTerminalWidth() {}

  static int cells(String value) {
    return CliTextSafety.visible(value).codePoints().map(CliTerminalWidth::cellWidth).sum();
  }

  private static int cellWidth(int codePoint) {
    int type = Character.getType(codePoint);
    if (type == Character.NON_SPACING_MARK
        || type == Character.COMBINING_SPACING_MARK
        || type == Character.ENCLOSING_MARK) {
      return 0;
    }
    return isWide(codePoint) ? 2 : 1;
  }

  private static boolean isWide(int codePoint) {
    return isWideBmp(codePoint) || isWideSupplementary(codePoint);
  }

  private static boolean isWideBmp(int codePoint) {
    return isWideEastAsianCore(codePoint) || isWideFullWidth(codePoint);
  }

  private static boolean isWideEastAsianCore(int codePoint) {
    return (codePoint >= 0x1100 && codePoint <= 0x115F)
        || (codePoint >= 0x2E80 && codePoint <= 0xA4CF)
        || (codePoint >= 0xAC00 && codePoint <= 0xD7A3)
        || (codePoint >= 0xF900 && codePoint <= 0xFAFF);
  }

  private static boolean isWideFullWidth(int codePoint) {
    return (codePoint >= 0xFE10 && codePoint <= 0xFE6F)
        || (codePoint >= 0xFF01 && codePoint <= 0xFF60)
        || (codePoint >= 0xFFE0 && codePoint <= 0xFFE6);
  }

  private static boolean isWideSupplementary(int codePoint) {
    return (codePoint >= 0x1F300 && codePoint <= 0x1FAFF)
        || (codePoint >= 0x20000 && codePoint <= 0x3FFFD);
  }
}
