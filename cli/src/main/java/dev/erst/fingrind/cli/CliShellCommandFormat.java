package dev.erst.fingrind.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Renders shell command examples with deterministic wrapping and escaping preservation. */
final class CliShellCommandFormat {
  private static final String TEXT_LINE_SEPARATOR = "\n";
  private static final Pattern SHELL_TOKEN =
      Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*\"|'(?:[^'\\\\]|\\\\.)*'|\\S+");

  private CliShellCommandFormat() {}

  static String renderShellCommandBlock(List<String> commands, int width) {
    Objects.requireNonNull(commands, "commands");
    if (commands.isEmpty()) {
      return "";
    }
    return commands.stream()
        .map(command -> renderShellCommand(command, width))
        .collect(Collectors.joining(TEXT_LINE_SEPARATOR + TEXT_LINE_SEPARATOR));
  }

  private static String renderShellCommand(String command, int width) {
    Objects.requireNonNull(command, "command");
    List<String> tokens = shellTokens(command);
    if (tokens.isEmpty()) {
      return "$";
    }
    String firstPrefix = "$ ";
    String nextPrefix = "  ";
    int firstWidth = availableShellWidth(width, firstPrefix);
    int nextWidth = availableShellWidth(width, nextPrefix);
    List<String> lines = new ArrayList<>();
    StringBuilder currentLine = new StringBuilder();
    int currentWidth = firstWidth;
    for (String token : tokens) {
      if (currentLine.isEmpty()) {
        currentLine.append(token);
        continue;
      }
      if (currentLine.length() + 1 + token.length() <= currentWidth) {
        currentLine.append(' ').append(token);
        continue;
      }
      lines.add(currentLine + " \\");
      currentLine.setLength(0);
      currentWidth = nextWidth;
      currentLine.append(token);
    }
    lines.add(currentLine.toString());
    StringBuilder rendered = new StringBuilder(firstPrefix).append(lines.getFirst());
    for (int index = 1; index < lines.size(); index++) {
      rendered.append(TEXT_LINE_SEPARATOR).append(nextPrefix).append(lines.get(index));
    }
    return rendered.toString();
  }

  private static int availableShellWidth(int width, String prefix) {
    return width == Integer.MAX_VALUE ? Integer.MAX_VALUE : Math.max(12, width - prefix.length());
  }

  private static List<String> shellTokens(String command) {
    Matcher matcher = SHELL_TOKEN.matcher(command);
    List<String> tokens = new ArrayList<>();
    while (matcher.find()) {
      tokens.add(matcher.group());
    }
    return List.copyOf(tokens);
  }
}
