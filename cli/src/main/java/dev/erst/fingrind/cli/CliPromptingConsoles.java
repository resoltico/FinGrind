package dev.erst.fingrind.cli;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Shared prompt-console adapters for CLI book-passphrase reads. */
final class CliPromptingConsoles {
  private CliPromptingConsoles() {}

  static @Nullable CliPromptingConsole systemPromptingConsole() {
    return systemPromptingConsole(
        availableSystemConsole(), java.io.Console::isTerminal, java.io.Console::readPassword);
  }

  private static java.io.@Nullable Console availableSystemConsole() {
    return System.console();
  }

  static @Nullable CliPromptingConsole availableSystemPromptingConsole(
      @Nullable CliSystemPromptingConsole systemConsole) {
    if (systemConsole == null || !systemConsole.isTerminal()) {
      return null;
    }
    return systemConsole;
  }

  static <T> @Nullable CliPromptingConsole systemPromptingConsole(
      @Nullable T source,
      CliTerminalStateExtractor<? super T> terminalStateExtractor,
      CliFormattedPasswordPromptReader<? super T> passwordPromptReader) {
    Objects.requireNonNull(terminalStateExtractor, "terminalStateExtractor");
    Objects.requireNonNull(passwordPromptReader, "passwordPromptReader");
    if (source == null) {
      return null;
    }
    return interactiveSystemPromptingConsole(
        () -> terminalStateExtractor.isTerminal(source),
        prompt -> passwordPromptReader.readPassword(source, "%s", prompt));
  }

  static @Nullable CliPromptingConsole interactiveSystemPromptingConsole(
      CliTerminalState terminalState, CliPasswordReader passwordReader) {
    Objects.requireNonNull(terminalState, "terminalState");
    Objects.requireNonNull(passwordReader, "passwordReader");
    return availableSystemPromptingConsole(wrap(terminalState, passwordReader));
  }

  static CliSystemPromptingConsole wrap(
      CliTerminalState terminalState, CliPasswordReader passwordReader) {
    return new WrappedConsole(terminalState, passwordReader);
  }

  /** Bridges one terminal-state probe and one password reader into one prompting console. */
  private static final class WrappedConsole implements CliSystemPromptingConsole {
    private final CliTerminalState terminalState;
    private final CliPasswordReader passwordReader;

    private WrappedConsole(CliTerminalState terminalState, CliPasswordReader passwordReader) {
      this.terminalState = Objects.requireNonNull(terminalState, "terminalState");
      this.passwordReader = Objects.requireNonNull(passwordReader, "passwordReader");
    }

    @Override
    public boolean isTerminal() {
      return terminalState.isTerminal();
    }

    @Override
    public char @Nullable [] readPassword(String prompt) {
      Objects.requireNonNull(prompt, "prompt");
      return passwordReader.readPassword(prompt);
    }
  }
}

/** Typed console seam for password prompts used by the interactive CLI flow. */
@FunctionalInterface
interface CliPromptingConsole {
  /** Reads one password for the supplied prompt and may return {@code null} on EOF. */
  char @Nullable [] readPassword(String prompt);
}

/** Typed system-console seam that exposes prompt and terminal state together. */
interface CliSystemPromptingConsole extends CliPromptingConsole {
  /** Reports whether the backing console is interactive for password prompting. */
  boolean isTerminal();
}

/** Typed boolean seam for one console-terminal check. */
@FunctionalInterface
interface CliTerminalState {
  /** Reports whether the wrapped console is interactive for prompting. */
  boolean isTerminal();
}

/** Typed password-reader seam matching the JDK console prompt-aware read contract. */
@FunctionalInterface
interface CliPasswordReader {
  /** Reads one password for the supplied prompt and may return {@code null} on EOF. */
  char @Nullable [] readPassword(String prompt);
}

/** Typed prompt-aware password reader for one system-console-like source object. */
@FunctionalInterface
interface CliFormattedPasswordPromptReader<T> {
  /** Reads one password from the supplied source for the supplied prompt format and value. */
  char @Nullable [] readPassword(T source, String promptFormat, String prompt);
}

/** Typed terminal-state reader for one system-console-like source object. */
@FunctionalInterface
interface CliTerminalStateExtractor<T> {
  /** Reports whether the supplied source is interactive for password prompting. */
  boolean isTerminal(T source);
}
