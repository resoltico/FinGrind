package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.BookAccess;
import dev.erst.fingrind.contract.ContractDecision;
import dev.erst.fingrind.contract.ContractErrors;
import dev.erst.fingrind.sqlite.SqliteBookKeyFileGenerator;
import dev.erst.fingrind.sqlite.SqliteBookPassphrase;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.Arena;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
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
            resolver
                .resolve(
                    new BookAccess(
                        Path.of("book.sqlite"), new BookAccess.PassphraseSource.KeyFile(keyFile)))
                .requireAccepted();
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
            resolver
                .resolve(
                    new BookAccess(
                        Path.of("book.sqlite"), BookAccess.PassphraseSource.StandardInput.INSTANCE))
                .requireAccepted();
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
            new ByteArrayInputStream(new byte[0]),
            prompt -> ContractDecision.accepted("prompt-passphrase".toCharArray()));
    Path bookPath = tempDirectory.resolve("books").resolve("acme.sqlite");

    try (SqliteBookPassphrase passphrase =
            resolver
                .resolve(
                    new BookAccess(
                        bookPath, BookAccess.PassphraseSource.InteractivePrompt.INSTANCE))
                .requireAccepted();
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
  void resolve_rejectsMalformedUtf16PromptPassphrase() {
    char[] enteredPassword = new char[] {'A', '\uD800', 'B'};
    CliBookPassphraseResolver resolver =
        new CliBookPassphraseResolver(
            new ByteArrayInputStream(new byte[0]),
            prompt -> ContractDecision.accepted(enteredPassword));

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                resolver
                    .resolve(
                        new BookAccess(
                            Path.of("book.sqlite"),
                            BookAccess.PassphraseSource.InteractivePrompt.INSTANCE))
                    .requireAccepted());

    assertTrue(
        Objects.requireNonNull(exception.getMessage()).contains("must contain a UTF-8 passphrase"));
    assertArrayEquals(new char[enteredPassword.length], enteredPassword);
  }

  @Test
  void resolve_rejectsMissingPromptPassphrase() {
    CliBookPassphraseResolver resolver =
        new CliBookPassphraseResolver(
            new ByteArrayInputStream(new byte[0]),
            prompt ->
                missingPrompt(
                    "FinGrind did not receive a book passphrase from the interactive console."));

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                resolver
                    .resolve(
                        new BookAccess(
                            Path.of("book.sqlite"),
                            BookAccess.PassphraseSource.InteractivePrompt.INSTANCE))
                    .requireAccepted());

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
              public ContractDecision<char[]> readPassword(String prompt) {
                readCount++;
                if (readCount == 1) {
                  assertTrue(prompt.startsWith("New FinGrind book passphrase for "));
                  return ContractDecision.accepted("confirmed-secret".toCharArray());
                }
                assertTrue(prompt.startsWith("Confirm new FinGrind book passphrase for "));
                return ContractDecision.accepted("confirmed-secret".toCharArray());
              }
            });

    try (SqliteBookPassphrase passphrase =
            resolver
                .resolve(
                    bookPath,
                    BookAccess.PassphraseSource.InteractivePrompt.INSTANCE,
                    CliBookPassphraseResolver.PromptStyle.CONFIRMED_NEW_SECRET)
                .requireAccepted();
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
              public ContractDecision<char[]> readPassword(String prompt) {
                readCount++;
                return readCount == 1
                    ? ContractDecision.accepted("secret".toCharArray())
                    : missingPrompt(
                        "FinGrind did not receive a confirmed book passphrase from the interactive console.");
              }
            });

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                resolver
                    .resolve(
                        Path.of("book.sqlite"),
                        BookAccess.PassphraseSource.InteractivePrompt.INSTANCE,
                        CliBookPassphraseResolver.PromptStyle.CONFIRMED_NEW_SECRET)
                    .requireAccepted());

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
              public ContractDecision<char[]> readPassword(String prompt) {
                readCount++;
                return ContractDecision.accepted(
                    readCount == 1 ? "first".toCharArray() : "second".toCharArray());
              }
            });

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                resolver
                    .resolve(
                        Path.of("book.sqlite"),
                        BookAccess.PassphraseSource.InteractivePrompt.INSTANCE,
                        CliBookPassphraseResolver.PromptStyle.CONFIRMED_NEW_SECRET)
                    .requireAccepted());

    assertEquals(
        "FinGrind did not receive matching book passphrases from the interactive console.",
        exception.getMessage());
    String message = Objects.requireNonNull(exception.getMessage());
    assertFalse(message.contains("first"));
    assertFalse(message.contains("second"));
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
                resolver
                    .resolve(
                        new BookAccess(
                            Path.of("book.sqlite"),
                            BookAccess.PassphraseSource.StandardInput.INSTANCE))
                    .requireAccepted());

    assertEquals(
        "Failed to read the FinGrind book passphrase from standard input.", exception.getMessage());
    assertFalse(Objects.requireNonNull(exception.getMessage()).contains("boom"));
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
                resolver
                    .resolve(
                        new BookAccess(
                            Path.of("book.sqlite"),
                            BookAccess.PassphraseSource.StandardInput.INSTANCE))
                    .requireAccepted());

    assertTrue(
        Objects.requireNonNull(exception.getMessage())
            .contains(
                "must contain a single-line UTF-8 text passphrase without control characters"));
    assertFalse(exception.getMessage().contains("line-1"));
    assertFalse(exception.getMessage().contains("line-2"));
  }

  @Test
  void systemConsoleReader_reportsNoInteractiveConsoleInTheGradleTestEnvironment() {
    assertTrue(CliBookPassphraseResolver.systemConsoleReader().isEmpty());
  }

  @Test
  void systemConsoleReader_wrapsPromptingConsoleWhenAvailable() {
    Optional<CliBookPassphraseResolver.Terminal> terminal =
        CliBookPassphraseResolver.systemConsoleReader(
            (format, arguments) -> {
              assertEquals("%s", format);
              assertEquals(1, arguments.length);
              assertEquals("book.sqlite", arguments[0]);
              return "console-secret".toCharArray();
            });

    assertTrue(terminal.isPresent());
    assertEquals(
        "console-secret",
        new String(terminal.orElseThrow().readPassword("book.sqlite").requireAccepted()));
  }

  @Test
  void systemConsoleReader_reportsMissingPromptingConsoleWhenUnavailable() {
    assertTrue(CliBookPassphraseResolver.systemConsoleReader(missingPromptingConsole()).isEmpty());
  }

  @Test
  void promptingConsoleTerminal_readsPasswordFromTypedPromptingConsole() {
    CliBookPassphraseResolver.Terminal terminal =
        new CliBookPassphraseResolver.PromptingConsoleTerminal(
            (format, arguments) -> {
              assertEquals("%s", format);
              assertEquals(1, arguments.length);
              assertEquals("book.sqlite", arguments[0]);
              return "console-secret".toCharArray();
            });

    assertEquals(
        "console-secret", new String(terminal.readPassword("book.sqlite").requireAccepted()));
  }

  @Test
  void promptingConsoleTerminal_rejectsNullPasswordReads() {
    CliBookPassphraseResolver.Terminal terminal =
        new CliBookPassphraseResolver.PromptingConsoleTerminal((format, arguments) -> null);

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> terminal.readPassword("book.sqlite").requireAccepted());

    assertEquals(
        "FinGrind did not receive a book passphrase from the interactive console.",
        exception.getMessage());
  }

  @Test
  void promptingConsoleTerminal_wrapsReadPasswordFailures() {
    CliBookPassphraseResolver.Terminal terminal =
        new CliBookPassphraseResolver.PromptingConsoleTerminal(
            (format, arguments) -> {
              assertEquals("%s", format);
              assertEquals(1, arguments.length);
              assertEquals("book.sqlite", arguments[0]);
              throw new IllegalStateException("boom");
            });

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> terminal.readPassword("book.sqlite").requireAccepted());

    assertEquals(
        "Failed to prompt for a book passphrase from the interactive console.",
        exception.getMessage());
  }

  @Test
  void promptStyle_singlePromptDoesNotExposeConfirmationPrompt() {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                CliBookPassphraseResolver.PromptStyle.SINGLE.confirmationPrompt(
                    Path.of("book.sqlite")));

    assertEquals("This prompt style does not support confirmation.", exception.getMessage());
  }

  @Test
  void systemTerminal_rejectsWhenNoInteractiveConsoleIsAvailable() {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                CliBookPassphraseResolver.systemTerminal()
                    .readPassword("prompt")
                    .requireAccepted());

    assertEquals(
        "FinGrind cannot prompt for a book passphrase because no interactive console is available.",
        exception.getMessage());
  }

  @Test
  void consoleBackedTerminal_readsPasswordFromProvidedReader() {
    CliBookPassphraseResolver.ConsoleBackedTerminal terminal =
        new CliBookPassphraseResolver.ConsoleBackedTerminal(readerSupplier("secret"));
    char[] password = terminal.readPassword("book.sqlite").requireAccepted();

    assertEquals("secret", new String(password));
  }

  @Test
  void consoleBackedTerminal_rejectsMissingConsoleReader() {
    CliBookPassphraseResolver.ConsoleBackedTerminal terminal =
        new CliBookPassphraseResolver.ConsoleBackedTerminal(Optional::empty);
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class, () -> terminal.readPassword("prompt").requireAccepted());

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

  private static ContractDecision<char[]> failPrompt(String prompt) {
    throw new AssertionError("Unexpected prompt usage: " + prompt);
  }

  private static ContractDecision<char[]> missingPrompt(String message) {
    return ContractDecision.rejected(
        ContractErrors.Descriptor.INTERACTIVE_PROMPT_FAILED.failure(message, null, null));
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
              return ContractDecision.accepted(password.toCharArray());
            });
  }

  private static CliBookPassphraseResolver.@Nullable PromptingConsole missingPromptingConsole() {
    return null;
  }
}
