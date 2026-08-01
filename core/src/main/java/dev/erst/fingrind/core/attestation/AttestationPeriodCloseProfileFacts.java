package dev.erst.fingrind.core.attestation;

import java.math.BigInteger;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Reads and validates the request facts shared by both generated period-close operation kinds. */
final class AttestationPeriodCloseProfileFacts {
  static final int COMMAND = 0x0100;
  static final int REQUEST_POSTING = 0x0120;
  static final int REQUEST_PERIOD_CLOSE = 0x0140;
  static final int POSTING = 0x0020;
  static final int JOURNAL_LINE = 0x0025;
  static final int INTERIM_SWEEP = 0x0040;
  static final int INTERIM_SWEEP_TOTAL = 0x0041;
  static final int INTERIM_SWEEP_POSTING = 0x0042;
  static final int FISCAL_CLOSE = 0x0043;
  static final int FISCAL_CLOSE_POSTING = 0x0044;
  static final String PERIOD_CLOSE = "period-close";
  static final String SYSTEM = "system";

  private AttestationPeriodCloseProfileFacts() {}

  static AttestationPreimage.Fact requirePeriodCloseRequest(
      AttestationOperationKind operationKind, AttestationPreimage requestPreimage) {
    String expectedOperationKind = operationKind.wireToken();
    AttestationPreimage.Fact command = exactlyOne(requestPreimage, COMMAND);
    AttestationPreimage.Fact posting = exactlyOne(requestPreimage, REQUEST_POSTING);
    AttestationPreimage.Fact close = exactlyOne(requestPreimage, REQUEST_PERIOD_CLOSE);
    boolean fiscalYearMatchesCloseEnd =
        operationKind == AttestationOperationKind.FISCAL_YEAR_CLOSE
            ? BigInteger.valueOf(date(close, 2).getYear()).equals(unsigned32(close, 3))
            : absent(close, 3);
    require(
        expectedOperationKind.equals(token(command, 0))
            && expectedOperationKind.equals(token(posting, 1))
            && PERIOD_CLOSE.equals(token(posting, 3))
            && expectedOperationKind.equals(token(close, 0))
            && date(posting, 2).equals(date(close, 2))
            && fiscalYearMatchesCloseEnd);
    return close;
  }

  static AttestationPreimage.Fact exactlyOne(AttestationPreimage preimage, int tag) {
    List<AttestationPreimage.Fact> records = AttestationPreimageFields.records(preimage, tag);
    require(records.size() == 1);
    return records.getFirst();
  }

  static String token(AttestationPreimage.Fact fact, int fieldIndex) {
    return AttestationPreimageValueReader.token(
        fact, fieldIndex, AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID);
  }

  static UUID uuid(AttestationPreimage.Fact fact, int fieldIndex) {
    return AttestationPreimageValueReader.uuid(
        fact, fieldIndex, AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID);
  }

  static BigInteger unsigned64(AttestationPreimage.Fact fact, int fieldIndex) {
    return AttestationPreimageValueReader.unsigned64(
        fact, fieldIndex, AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID);
  }

  static BigInteger unsigned32(AttestationPreimage.Fact fact, int fieldIndex) {
    return AttestationPreimageValueReader.unsigned32(
        fact, fieldIndex, AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID);
  }

  static int mutation(AttestationPreimage.Fact fact, int fieldIndex) {
    return AttestationPreimageValueReader.mutation(
        fact, fieldIndex, AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID);
  }

  static LocalDate date(AttestationPreimage.Fact fact, int fieldIndex) {
    return AttestationPreimageValueReader.date(
        fact, fieldIndex, AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID);
  }

  static String text(AttestationPreimage.Fact fact, int fieldIndex) {
    return AttestationPreimageValueReader.text(
        fact, fieldIndex, AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID);
  }

  static boolean absent(AttestationPreimage.Fact fact, int fieldIndex) {
    return !AttestationPreimageFields.requireField(fact, fieldIndex).isPresent();
  }

  static void require(boolean condition) {
    if (!condition) {
      throw failure();
    }
  }

  static AttestationAuthorizationException failure() {
    return new AttestationAuthorizationException(
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID);
  }
}
