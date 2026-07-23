package dev.erst.fingrind.cli;

import static dev.erst.fingrind.cli.CliJsonFieldAccess.requiredText;
import static dev.erst.fingrind.cli.CliJsonStructureAccess.requireObjectNode;
import static dev.erst.fingrind.cli.CliJsonStructureAccess.requireRootObject;

import dev.erst.fingrind.contract.protocol.ProtocolInteractionLimits;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.core.attestation.AttestationCompromiseReview;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** Reads strict non-persisted compromise-review declarations from one bounded JSON file. */
final class CliAttestationReviewFileReader {
  private static final String REVIEWS = "compromiseReviews";
  private static final String CREDENTIAL_KEY_ID = "credentialKeyId";
  private static final String FIRST_AFFECTED_ORDER = "firstAffectedOrder";
  private static final String LAST_AFFECTED_ORDER = "lastAffectedOrder";
  private static final Pattern CANONICAL_UNSIGNED_DECIMAL = Pattern.compile("0|[1-9][0-9]*");

  private final ObjectMapper objectMapper = CliJsonObjectMappers.configuredObjectMapper();

  List<AttestationCompromiseReview> read(Path reviewFilePath) {
    Path path = reviewFilePath.toAbsolutePath().normalize();
    try {
      if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
        throw invalid("The review declaration file must be a regular file.");
      }
      byte[] bytes = readBoundedBytes(path);
      if (CliJsonObjectMappers.hasDuplicateObjectKeys(bytes)) {
        throw invalid("The review declaration JSON must not contain duplicate object keys.");
      }
      ObjectNode root = requireRootObject(objectMapper.readTree(bytes));
      CliJsonStructureAccess.rejectUnexpectedFields(root, null, Set.of(REVIEWS));
      JsonNode reviewsNode = CliJsonStructureAccess.requiredArray(root, REVIEWS);
      List<AttestationCompromiseReview> reviews = new ArrayList<>();
      int index = 0;
      for (JsonNode reviewNode : reviewsNode) {
        reviews.add(readReview(requireObjectNode(reviewNode, REVIEWS + "[" + index + "]"), index));
        index++;
      }
      return AttestationCompromiseReview.canonicalize(reviews);
    } catch (CliArgumentsException exception) {
      throw exception;
    } catch (IOException | JacksonException | IllegalArgumentException exception) {
      throw invalid(
          java.util.Objects.requireNonNullElse(
              exception.getMessage(), "The review declaration file is not valid JSON."),
          exception);
    }
  }

  private static AttestationCompromiseReview readReview(ObjectNode review, int index) {
    String context = REVIEWS + "[" + index + "]";
    CliJsonStructureAccess.rejectUnexpectedFields(
        review, context, Set.of(CREDENTIAL_KEY_ID, FIRST_AFFECTED_ORDER, LAST_AFFECTED_ORDER));
    String credentialKeyId = requiredText(review, CREDENTIAL_KEY_ID);
    BigInteger firstAffectedOrder =
        requireCanonicalUnsignedDecimal(requiredText(review, FIRST_AFFECTED_ORDER), context);
    JsonNode lastAffectedOrderNode =
        CliJsonStructureAccess.nullableField(review, LAST_AFFECTED_ORDER);
    BigInteger lastAffectedOrder =
        lastAffectedOrderNode == null || lastAffectedOrderNode.isNull()
            ? null
            : requireCanonicalUnsignedDecimal(requiredText(review, LAST_AFFECTED_ORDER), context);
    return new AttestationCompromiseReview(credentialKeyId, firstAffectedOrder, lastAffectedOrder);
  }

  private static BigInteger requireCanonicalUnsignedDecimal(String value, String context) {
    if (!CANONICAL_UNSIGNED_DECIMAL.matcher(value).matches()) {
      throw new IllegalArgumentException(
          "Field must be a canonical unsigned-decimal string: " + context);
    }
    return new BigInteger(value);
  }

  private static byte[] readBoundedBytes(Path path) throws IOException {
    try (var input = Files.newInputStream(path)) {
      byte[] bytes = input.readNBytes(ProtocolInteractionLimits.REQUEST_PAYLOAD_MAX_BYTES + 1);
      if (bytes.length > ProtocolInteractionLimits.REQUEST_PAYLOAD_MAX_BYTES) {
        throw invalid(
            "The review declaration file exceeds "
                + ProtocolInteractionLimits.REQUEST_PAYLOAD_MAX_BYTES
                + " bytes.");
      }
      return bytes;
    }
  }

  private static CliArgumentsException invalid(String message) {
    return CliArgumentValueParser.invalid(ProtocolOptions.Attestation.REVIEW_FILE, message);
  }

  private static CliArgumentsException invalid(String message, Throwable cause) {
    return CliArgumentValueParser.invalid(ProtocolOptions.Attestation.REVIEW_FILE, message, cause);
  }
}
