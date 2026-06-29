package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.PublicPathHint;
import java.nio.file.AccessDeniedException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.core.TokenStreamLocation;

/** Shared request-read failure shaping for CLI JSON inputs. */
final class CliJsonRequestFailures {
  private CliJsonRequestFailures() {}

  static String normalizedMessage(RuntimeException exception) {
    return Objects.requireNonNullElse(exception.getMessage(), "Request is invalid.");
  }

  static CliRequestException requestReadFailure(Exception exception, String hint) {
    CliReadErrorDetails readErrorDetails = readErrorDetails(exception);
    return new CliRequestException(
        ContractErrors.Descriptor.INVALID_REQUEST.code(),
        readErrorDetails.message(),
        hint,
        exception,
        null,
        readErrorDetails.details());
  }

  static CliRequestException requestReadFailure(
      Path requestFile, Exception exception, String invalidJsonHint) {
    Objects.requireNonNull(requestFile, "requestFile");
    Objects.requireNonNull(exception, "exception");
    if (exception instanceof JacksonException) {
      return requestReadFailure(exception, invalidJsonHint);
    }
    return new CliRequestException(
        ContractErrors.Descriptor.INVALID_REQUEST.code(),
        requestTransportFailureMessage(requestFile, exception),
        requestTransportFailureHint(requestFile, exception),
        exception);
  }

  static CliRequestException duplicateObjectKeyFailure(String hint) {
    return new CliRequestException(
        ContractErrors.Descriptor.INVALID_REQUEST.code(),
        "Request JSON must not contain duplicate object keys.",
        hint,
        null);
  }

  static String readFailureMessage(Exception exception) {
    return readErrorDetails(exception).message();
  }

  static @Nullable JsonParseLocation parseLocation(JacksonException exception) {
    Objects.requireNonNull(exception, "exception");
    TokenStreamLocation location = exception.getLocation();
    if (location == null) {
      return null;
    }
    int lineNumber = location.getLineNr();
    if (lineNumber <= 0) {
      return null;
    }
    int columnNumber = location.getColumnNr();
    if (columnNumber <= 0) {
      return null;
    }
    return new JsonParseLocation(lineNumber, columnNumber);
  }

  private static CliReadErrorDetails readErrorDetails(Exception exception) {
    Objects.requireNonNull(exception, "exception");
    if (exception instanceof JacksonException jacksonException) {
      JsonParseLocation parseLocation = parseLocation(jacksonException);
      if (parseLocation != null) {
        return new CliReadErrorDetails(
            "Failed to read request JSON at line "
                + parseLocation.line()
                + ", column "
                + parseLocation.column()
                + ".",
            new dev.erst.fingrind.cli.json.CliErrorJsonModels.InvalidJsonDetails(
                Objects.requireNonNullElse(
                    jacksonException.getOriginalMessage(),
                    Objects.requireNonNullElse(
                        jacksonException.getMessage(), "Request JSON is invalid.")),
                parseLocation.line(),
                parseLocation.column()));
      }
    }
    return new CliReadErrorDetails("Failed to read request JSON.", null);
  }

  private static String requestTransportFailureMessage(Path requestFile, Exception exception) {
    if (exception instanceof CliRequestPayloadTooLargeException tooLargeException) {
      if (dev.erst.fingrind.contract.protocol.ProtocolOptions.STDIN_TOKEN.equals(
          requestFile.toString())) {
        return "Request JSON from standard input exceeded the supported "
            + tooLargeException.maxBytes()
            + "-byte UTF-8 limit.";
      }
      return "Request file exceeded the supported "
          + tooLargeException.maxBytes()
          + "-byte UTF-8 limit: "
          + publicPath(requestFile)
          + ".";
    }
    if (dev.erst.fingrind.contract.protocol.ProtocolOptions.STDIN_TOKEN.equals(
        requestFile.toString())) {
      return "Failed to read request JSON from standard input.";
    }
    if (exception instanceof NoSuchFileException) {
      return "Request file does not exist: " + publicPath(requestFile) + ".";
    }
    if (exception instanceof AccessDeniedException) {
      return "Request file is not readable: " + publicPath(requestFile) + ".";
    }
    return "Failed to read request file: " + publicPath(requestFile) + ".";
  }

  private static String requestTransportFailureHint(Path requestFile, Exception exception) {
    if (exception instanceof CliRequestPayloadTooLargeException tooLargeException) {
      return "Reduce the request JSON payload to within the supported "
          + tooLargeException.maxBytes()
          + "-byte UTF-8 limit, or split the work into smaller request documents.";
    }
    if (dev.erst.fingrind.contract.protocol.ProtocolOptions.STDIN_TOKEN.equals(
        requestFile.toString())) {
      return "Provide one readable JSON document on standard input, or pass --request-file <path> to read it from a file.";
    }
    return "Verify that the selected --request-file exists and is readable, or pass --request-file - to read one JSON document from standard input.";
  }

  private record CliReadErrorDetails(
      String message,
      dev.erst.fingrind.cli.json.CliErrorJsonModels.@Nullable ErrorDetails details) {
    private CliReadErrorDetails {
      Objects.requireNonNull(message, "message");
    }
  }

  record JsonParseLocation(int line, int column) {
    JsonParseLocation {
      if (line <= 0) {
        throw new IllegalArgumentException("line must be positive");
      }
      if (column <= 0) {
        throw new IllegalArgumentException("column must be positive");
      }
    }
  }

  private static String publicPath(Path path) {
    return PublicPathHint.fromPath(path).value();
  }
}
