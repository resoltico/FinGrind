package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.BookAccess;
import dev.erst.fingrind.sqlite.SqliteBookKeyFileGenerator;
import dev.erst.fingrind.sqlite.SqliteBookPassphrase;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.Arena;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for {@link CliBookPassphraseResolver}. */
class CliBookPassphraseResolverTest {
  @TempDir Path tempDirectory;

  @Test
  void resolve_readsUtf8PassphraseFromKeyFile() throws Exception {
    Path keyFile = tempDirectory.resolve("book.key");
    writeSecureString(keyFile, "swordfish\n");
    CliBookPassphraseResolver resolver =
        new CliBookPassphraseResolver(
            new ByteArrayInputStream(new byte[0]), prompt -> failPrompt(prompt));

    try (SqliteBookPassphrase passphrase =
            resolver.resolve(
                new BookAccess(
                    Path.of("book.sqlite"), new BookAccess.PassphraseSource.KeyFile(keyFile)));
        Arena arena = Arena.ofConfined()) {
      assertEquals(keyFile.toAbsolutePath().normalize().toString(), passphrase.sourceDescription());
      assertEquals(
          "swordfish",
          new String(
              passphrase
                  .copyToCString(arena)
                  .asSlice(0, passphrase.byteLength())
                  .toArray(java.lang.foreign.ValueLayout.JAVA_BYTE),
              StandardCharsets.UTF_8));
    }
  }

  @Test
  void resolve_readsUtf8PassphraseFromStandardInput() throws Exception {
    CliBookPassphraseResolver resolver =
        new CliBookPassphraseResolver(
            new ByteArrayInputStream("stdin-passphrase\n".getBytes(StandardCharsets.UTF_8)),
            prompt -> failPrompt(prompt));

    try (SqliteBookPassphrase passphrase =
            resolver.resolve(
                new BookAccess(
                    Path.of("book.sqlite"), BookAccess.PassphraseSource.StandardInput.INSTANCE));
        Arena arena = Arena.ofConfined()) {
      assertEquals("standard input", passphrase.sourceDescription());
      assertEquals(
          "stdin-passphrase",
          new String(
              passphrase
                  .copyToCString(arena)
                  .asSlice(0, passphrase.byteLength())
                  .toArray(java.lang.foreign.ValueLayout.JAVA_BYTE),
              StandardCharsets.UTF_8));
    }
  }

  @Test
  void resolve_readsPromptPassphraseFromTerminal() throws Exception {
    CliBookPassphraseResolver resolver =
        new CliBookPassphraseResolver(
            new ByteArrayInputStream(new byte[0]), prompt -> "prompt-passphrase".toCharArray());
    Path bookPath = tempDirectory.resolve("books").resolve("acme.sqlite");

    try (SqliteBookPassphrase passphrase =
            resolver.resolve(
                new BookAccess(bookPath, BookAccess.PassphraseSource.InteractivePrompt.INSTANCE));
        Arena arena = Arena.ofConfined()) {
      assertTrue(
          passphrase
              .sourceDescription()
              .contains(bookPath.toAbsolutePath().normalize().toString()));
      assertEquals(
          "prompt-passphrase",
          new String(
              passphrase
                  .copyToCString(arena)
                  .asSlice(0, passphrase.byteLength())
                  .toArray(java.lang.foreign.ValueLayout.JAVA_BYTE),
              StandardCharsets.UTF_8));
    }
  }

  @Test
  void resolve_rejectsMissingPromptPassphrase() {
    CliBookPassphraseResolver resolver =
        new CliBookPassphraseResolver(new ByteArrayInputStream(new byte[0]), prompt -> null);

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                resolver.resolve(
                    new BookAccess(
                        Path.of("book.sqlite"),
                        BookAccess.PassphraseSource.InteractivePrompt.INSTANCE)));

    assertEquals(
        "FinGrind did not receive a book passphrase from the interactive console.",
        exception.getMessage());
  }

  @Test
  void resolve_readsConfirmedPromptPassphraseFromTerminal() throws Exception {
    Path bookPath = tempDirectory.resolve("confirmed.sqlite");
    CliBookPassphraseResolver resolver =
        new CliBookPassphraseResolver(
            new ByteArrayInputStream(new byte[0]),
            new CliBookPassphraseResolver.Terminal() {
              private int readCount;

              @Override
              public char[] readPassword(String prompt) {
                readCount++;
                if (readCount == 1) {
                  assertTrue(prompt.startsWith("New FinGrind book passphrase for "));
                  return "confirmed-secret".toCharArray();
                }
                assertTrue(prompt.startsWith("Confirm new FinGrind book passphrase for "));
                return "confirmed-secret".toCharArray();
              }
            });

    try (SqliteBookPassphrase passphrase =
            resolver.resolve(
                bookPath,
                BookAccess.PassphraseSource.InteractivePrompt.INSTANCE,
                CliBookPassphraseResolver.PromptStyle.CONFIRMED_NEW_SECRET);
        Arena arena = Arena.ofConfined()) {
      assertTrue(
          passphrase
              .sourceDescription()
              .contains(bookPath.toAbsolutePath().normalize().toString()));
      assertEquals(
          "confirmed-secret",
          new String(
              passphrase
                  .copyToCString(arena)
                  .asSlice(0, passphrase.byteLength())
                  .toArray(java.lang.foreign.ValueLayout.JAVA_BYTE),
              StandardCharsets.UTF_8));
    }
  }

  @Test
  void resolve_rejectsMissingConfirmedPromptPassphrase() {
    CliBookPassphraseResolver resolver =
        new CliBookPassphraseResolver(
            new ByteArrayInputStream(new byte[0]),
            new CliBookPassphraseResolver.Terminal() {
              private int readCount;

              @Override
              public char[] readPassword(String prompt) {
                readCount++;
                return readCount == 1 ? "secret".toCharArray() : null;
              }
            });

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                resolver.resolve(
                    Path.of("book.sqlite"),
                    BookAccess.PassphraseSource.InteractivePrompt.INSTANCE,
                    CliBookPassphraseResolver.PromptStyle.CONFIRMED_NEW_SECRET));

    assertEquals(
        "FinGrind did not receive a confirmed book passphrase from the interactive console.",
        exception.getMessage());
  }

  @Test
  void resolve_rejectsMismatchedConfirmedPromptPassphrases() {
    CliBookPassphraseResolver resolver =
        new CliBookPassphraseResolver(
            new ByteArrayInputStream(new byte[0]),
            new CliBookPassphraseResolver.Terminal() {
              private int readCount;

              @Override
              public char[] readPassword(String prompt) {
                readCount++;
                return readCount == 1 ? "first".toCharArray() : "second".toCharArray();
              }
            });

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                resolver.resolve(
                    Path.of("book.sqlite"),
                    BookAccess.PassphraseSource.InteractivePrompt.INSTANCE,
                    CliBookPassphraseResolver.PromptStyle.CONFIRMED_NEW_SECRET));

    assertEquals(
        "FinGrind did not receive matching book passphrases from the interactive console.",
        exception.getMessage());
  }

  @Test
  void resolve_wrapsStandardInputReadFailure() {
    CliBookPassphraseResolver resolver =
        new CliBookPassphraseResolver(
            new InputStream() {
              @Override
              public int read() throws IOException {
                throw new IOException("boom");
              }

              @Override
              public int read(byte[] buffer, int offset, int length) throws IOException {
                throw new IOException("boom");
              }
            },
            prompt -> failPrompt(prompt));

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                resolver.resolve(
                    new BookAccess(
                        Path.of("book.sqlite"),
                        BookAccess.PassphraseSource.StandardInput.INSTANCE)));

    assertEquals(
        "Failed to read the FinGrind book passphrase from standard input.", exception.getMessage());
  }

  @Test
  void resolve_rejectsControlCharactersFromStandardInput() {
    CliBookPassphraseResolver resolver =
        new CliBookPassphraseResolver(
            new ByteArrayInputStream("line-1\nline-2\n".getBytes(StandardCharsets.UTF_8)),
            prompt -> failPrompt(prompt));

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                resolver.resolve(
                    new BookAccess(
                        Path.of("book.sqlite"),
                        BookAccess.PassphraseSource.StandardInput.INSTANCE)));

    assertTrue(
        exception
            .getMessage()
            .contains(
                "must contain a single-line UTF-8 text passphrase without control characters"));
  }

  @Test
  void systemConsoleReader_reportsNoInteractiveConsoleInTheGradleTestEnvironment() {
    assertTrue(CliBookPassphraseResolver.systemConsoleReader().isEmpty());
  }

  @Test
  void systemConsoleReader_wrapsConsoleLikeHandle() {
    CliBookPassphraseResolver.Terminal terminal =
        CliBookPassphraseResolver.systemConsoleReader(new FakeConsoleHandle()).orElseThrow();

    assertEquals("console-secret", new String(terminal.readPassword("book.sqlite")));
  }

  @Test
  void systemConsoleReader_rejectsHandlesWithoutReadPasswordMethod() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> CliBookPassphraseResolver.systemConsoleReader(new Object()));

    assertEquals(
        "Interactive console handle does not expose readPassword(String, Object...).",
        exception.getMessage());
  }

  @Test
  void systemConsoleReader_wrapsReadPasswordInvocationFailures() {
    CliBookPassphraseResolver.Terminal terminal =
        CliBookPassphraseResolver.systemConsoleReader(new ThrowingConsoleHandle()).orElseThrow();

    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> terminal.readPassword("book.sqlite"));

    assertEquals(
        "Failed to prompt for a book passphrase from the interactive console.",
        exception.getMessage());
    assertEquals("boom", exception.getCause().getCause().getMessage());
  }

  @Test
  @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
  void promptStyle_singlePromptDoesNotExposeConfirmationPrompt() throws Exception {
    Method method =
        CliBookPassphraseResolver.PromptStyle.class.getDeclaredMethod(
            "confirmationPrompt", Path.class);
    method.setAccessible(true);

    InvocationTargetException exception =
        assertThrows(
            InvocationTargetException.class,
            () ->
                method.invoke(
                    CliBookPassphraseResolver.PromptStyle.SINGLE, Path.of("book.sqlite")));

    assertEquals(
        "This prompt style does not support confirmation.", exception.getCause().getMessage());
  }

  @Test
  void systemTerminal_rejectsWhenNoInteractiveConsoleIsAvailable() {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> CliBookPassphraseResolver.systemTerminal().readPassword("prompt"));

    assertEquals(
        "FinGrind cannot prompt for a book passphrase because no interactive console is available.",
        exception.getMessage());
  }

  @Test
  void consoleBackedTerminal_readsPasswordFromProvidedReader() {
    CliBookPassphraseResolver.ConsoleBackedTerminal terminal =
        new CliBookPassphraseResolver.ConsoleBackedTerminal(readerSupplier("secret"));
    char[] password = terminal.readPassword("book.sqlite");

    assertEquals("secret", new String(password));
  }

  @Test
  void consoleBackedTerminal_rejectsMissingConsoleReader() {
    CliBookPassphraseResolver.ConsoleBackedTerminal terminal =
        new CliBookPassphraseResolver.ConsoleBackedTerminal(Optional::empty);
    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> terminal.readPassword("prompt"));

    assertEquals(
        "FinGrind cannot prompt for a book passphrase because no interactive console is available.",
        exception.getMessage());
  }

  @Test
  void consoleBackedTerminal_rejectsNullSupplierResult() {
    CliBookPassphraseResolver.ConsoleBackedTerminal terminal =
        new CliBookPassphraseResolver.ConsoleBackedTerminal(() -> null);

    NullPointerException exception =
        assertThrows(NullPointerException.class, () -> terminal.readPassword("prompt"));

    assertEquals("reader", exception.getMessage());
  }

  private static char[] failPrompt(String prompt) {
    throw new AssertionError("Unexpected prompt usage: " + prompt);
  }

  private static void writeSecureString(Path keyFile, String content) throws IOException {
    SqliteBookKeyFileGenerator.generate(keyFile);
    Files.writeString(keyFile, content, StandardCharsets.UTF_8);
  }

  private static Supplier<Optional<CliBookPassphraseResolver.Terminal>> readerSupplier(
      String password) {
    return () ->
        Optional.of(
            prompt -> {
              assertEquals("book.sqlite", prompt);
              return password.toCharArray();
            });
  }

  /** Console-shaped test double that records the reflected prompt format and arguments. */
  private static final class FakeConsoleHandle {
    @SuppressWarnings("UnusedMethod")
    char[] readPassword(String format, Object... arguments) {
      assertEquals("%s", format);
      assertEquals(1, arguments.length);
      assertEquals("book.sqlite", arguments[0]);
      return "console-secret".toCharArray();
    }
  }

  /** Console-shaped test double that fails once the reflected readPassword method is invoked. */
  private static final class ThrowingConsoleHandle {
    @SuppressWarnings({"UnusedMethod", "DoNotCallSuggester"})
    char[] readPassword(String format, Object... arguments) {
      assertEquals("%s", format);
      assertEquals(1, arguments.length);
      assertEquals("book.sqlite", arguments[0]);
      throw new IllegalStateException("boom");
    }
  }
}
