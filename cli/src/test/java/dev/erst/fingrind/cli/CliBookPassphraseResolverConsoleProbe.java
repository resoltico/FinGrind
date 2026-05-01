package dev.erst.fingrind.cli;

import java.util.Arrays;
import java.util.Objects;

/** Child-JVM probe that exercises the real {@link System#console()} prompt path under a PTY. */
public final class CliBookPassphraseResolverConsoleProbe {
  private CliBookPassphraseResolverConsoleProbe() {}

  public static void main(String[] arguments) {
    char[] password =
        CliBookPassphraseResolver.systemTerminal().readPassword("book.sqlite").requireAccepted();
    try {
      java.io.Console console = Objects.requireNonNull(System.console(), "console");
      console.format("accepted-length=%d%n", password.length);
      console.flush();
    } finally {
      Arrays.fill(password, '\0');
    }
  }
}
