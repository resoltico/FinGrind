package dev.erst.fingrind.core.attestation;

import dev.erst.fingrind.core.FiscalYearStart;
import java.math.BigInteger;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Reconstructs the immutable close horizons and sequence numbers needed to verify later close
 * operations without consulting mutable bookkeeping state.
 */
final class AttestationPeriodCloseHistory {
  private static final int BOOK_IDENTITY = 0x0001;
  private static final int INTERIM_SWEEP = 0x0040;
  private static final int FISCAL_CLOSE = 0x0043;

  private final FiscalYearStart fiscalYearStart;
  private final LocalDate bookStartEffectiveDate;
  private final BigInteger nextSweepOrder;
  private final BigInteger nextCloseOrder;
  private final LocalDate nextFiscalYearEffectiveFrom;
  private final @Nullable LocalDate transferredThroughEffectiveDate;

  private AttestationPeriodCloseHistory(
      FiscalYearStart fiscalYearStart,
      LocalDate bookStartEffectiveDate,
      BigInteger nextSweepOrder,
      BigInteger nextCloseOrder,
      LocalDate nextFiscalYearEffectiveFrom,
      @Nullable LocalDate transferredThroughEffectiveDate) {
    this.fiscalYearStart = Objects.requireNonNull(fiscalYearStart, "fiscalYearStart");
    this.bookStartEffectiveDate =
        Objects.requireNonNull(bookStartEffectiveDate, "bookStartEffectiveDate");
    this.nextSweepOrder = Objects.requireNonNull(nextSweepOrder, "nextSweepOrder");
    this.nextCloseOrder = Objects.requireNonNull(nextCloseOrder, "nextCloseOrder");
    this.nextFiscalYearEffectiveFrom =
        Objects.requireNonNull(nextFiscalYearEffectiveFrom, "nextFiscalYearEffectiveFrom");
    this.transferredThroughEffectiveDate = transferredThroughEffectiveDate;
  }

  static AttestationPeriodCloseHistory genesis(AttestationPreimage genesisEffectPreimage) {
    AttestationPreimage checkedEffect =
        Objects.requireNonNull(genesisEffectPreimage, "genesisEffectPreimage");
    List<AttestationPreimage.Fact> identities =
        AttestationPreimageFields.records(checkedEffect, BOOK_IDENTITY);
    if (identities.size() != 1) {
      throw failure(AttestationAuthorizationFailure.GENESIS_INVALID);
    }
    AttestationPreimage.Fact identity = identities.getFirst();
    try {
      FiscalYearStart fiscalYearStart =
          new FiscalYearStart(
              unsigned8(identity, 10, AttestationAuthorizationFailure.GENESIS_INVALID),
              unsigned8(identity, 11, AttestationAuthorizationFailure.GENESIS_INVALID));
      LocalDate bookStartEffectiveDate =
          AttestationPreimageValueReader.date(
              identity, 12, AttestationAuthorizationFailure.GENESIS_INVALID);
      return new AttestationPeriodCloseHistory(
          fiscalYearStart,
          bookStartEffectiveDate,
          BigInteger.ONE,
          BigInteger.ONE,
          bookStartEffectiveDate,
          null);
    } catch (DateTimeException exception) {
      throw new AttestationAuthorizationException(
          AttestationAuthorizationFailure.GENESIS_INVALID, exception);
    }
  }

  /** Validates and incorporates one accepted close operation of either provenance. */
  AttestationPeriodCloseHistory accept(
      AttestationOperationKind operationKind, AttestationPreimage effectPreimage) {
    return accept(
        facts(
            operationKind, effectPreimage, AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID),
        AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID);
  }

  /**
   * Validates the nondiscretionary temporal facts of one system close and returns its accepted
   * successor state.
   */
  AttestationPeriodCloseHistory acceptSystem(
      AttestationOperationKind operationKind,
      AttestationOperationPayload payload,
      AttestationPreimage effectPreimage) {
    AttestationAuthorizationFailure failure =
        AttestationAuthorizationFailure.SYSTEM_DERIVATION_INVALID;
    CloseFacts closeFacts = facts(operationKind, effectPreimage, failure);
    LocalDate recordedOn =
        Objects.requireNonNull(payload, "payload")
            .recordedAt()
            .atZone(java.time.ZoneOffset.UTC)
            .toLocalDate();
    if (closeFacts.interimSweep() != null
        && operationKind == AttestationOperationKind.INTERIM_RESULT_SWEEP
        && !closeFacts
            .interimSweep()
            .effectiveTo()
            .equals(AttestationPeriodCloseCalendar.previousDay(recordedOn, failure))) {
      throw failure(failure);
    }
    if (closeFacts.fiscalClose() != null
        && closeFacts.fiscalClose().effectiveTo().isAfter(recordedOn)) {
      throw failure(failure);
    }
    return accept(closeFacts, failure);
  }

  LocalDate expectedNextSweepEffectiveFrom() {
    return transferredThroughEffectiveDate == null
        ? bookStartEffectiveDate
        : AttestationPeriodCloseCalendar.nextDay(
            transferredThroughEffectiveDate,
            AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID);
  }

  LocalDate expectedNextFiscalYearEffectiveFrom() {
    return nextFiscalYearEffectiveFrom;
  }

  private AttestationPeriodCloseHistory accept(
      CloseFacts closeFacts, AttestationAuthorizationFailure failure) {
    InterimSweep interimSweep = closeFacts.interimSweep();
    FiscalClose fiscalClose = closeFacts.fiscalClose();
    if (interimSweep != null) {
      requireInterimSweep(interimSweep, failure);
    }
    if (fiscalClose != null) {
      requireFiscalClose(fiscalClose, failure);
    }
    BigInteger acceptedNextSweepOrder =
        interimSweep == null ? nextSweepOrder : nextSweepOrder.add(BigInteger.ONE);
    BigInteger acceptedNextCloseOrder =
        fiscalClose == null ? nextCloseOrder : nextCloseOrder.add(BigInteger.ONE);
    LocalDate acceptedNextFiscalYearEffectiveFrom =
        fiscalClose == null
            ? nextFiscalYearEffectiveFrom
            : AttestationPeriodCloseCalendar.nextDay(fiscalClose.effectiveTo(), failure);
    LocalDate acceptedTransferredThrough = transferredThroughEffectiveDate;
    if (interimSweep != null) {
      acceptedTransferredThrough = laterOf(acceptedTransferredThrough, interimSweep.effectiveTo());
    }
    if (fiscalClose != null) {
      acceptedTransferredThrough = laterOf(acceptedTransferredThrough, fiscalClose.effectiveTo());
    }
    return new AttestationPeriodCloseHistory(
        fiscalYearStart,
        bookStartEffectiveDate,
        acceptedNextSweepOrder,
        acceptedNextCloseOrder,
        acceptedNextFiscalYearEffectiveFrom,
        acceptedTransferredThrough);
  }

  private void requireInterimSweep(
      InterimSweep interimSweep, AttestationAuthorizationFailure failure) {
    if (!interimSweep.order().equals(nextSweepOrder)
        || !interimSweep.effectiveFrom().equals(expectedNextSweepEffectiveFrom())
        || interimSweep.effectiveTo().isBefore(interimSweep.effectiveFrom())) {
      throw failure(failure);
    }
  }

  private void requireFiscalClose(
      FiscalClose fiscalClose, AttestationAuthorizationFailure failure) {
    LocalDate expectedEffectiveFrom = nextFiscalYearEffectiveFrom;
    LocalDate expectedEffectiveTo = fiscalYearStart.containingFiscalYearEnd(expectedEffectiveFrom);
    if (!fiscalClose.order().equals(nextCloseOrder)
        || !fiscalClose.effectiveFrom().equals(expectedEffectiveFrom)
        || !fiscalClose.effectiveTo().equals(expectedEffectiveTo)) {
      throw failure(failure);
    }
  }

  private static CloseFacts facts(
      AttestationOperationKind operationKind,
      AttestationPreimage effectPreimage,
      AttestationAuthorizationFailure failure) {
    AttestationOperationKind checkedOperationKind =
        Objects.requireNonNull(operationKind, "operationKind");
    AttestationPreimage checkedEffect = Objects.requireNonNull(effectPreimage, "effectPreimage");
    return switch (checkedOperationKind) {
      case INTERIM_RESULT_SWEEP ->
          new CloseFacts(
              interimSweep(exactlyOne(checkedEffect, INTERIM_SWEEP, failure), failure), null);
      case FISCAL_YEAR_CLOSE -> {
        List<AttestationPreimage.Fact> sweeps =
            AttestationPreimageFields.records(checkedEffect, INTERIM_SWEEP);
        InterimSweep interimSweep =
            sweeps.isEmpty()
                ? null
                : interimSweep(exactlyOne(checkedEffect, INTERIM_SWEEP, failure), failure);
        yield new CloseFacts(
            interimSweep, fiscalClose(exactlyOne(checkedEffect, FISCAL_CLOSE, failure), failure));
      }
      default -> new CloseFacts(null, null);
    };
  }

  private static InterimSweep interimSweep(
      AttestationPreimage.Fact fact, AttestationAuthorizationFailure failure) {
    return new InterimSweep(
        AttestationPreimageValueReader.unsigned64(fact, 1, failure),
        AttestationPreimageValueReader.date(fact, 2, failure),
        AttestationPreimageValueReader.date(fact, 3, failure));
  }

  private static FiscalClose fiscalClose(
      AttestationPreimage.Fact fact, AttestationAuthorizationFailure failure) {
    return new FiscalClose(
        AttestationPreimageValueReader.unsigned64(fact, 1, failure),
        AttestationPreimageValueReader.date(fact, 2, failure),
        AttestationPreimageValueReader.date(fact, 3, failure));
  }

  private static AttestationPreimage.Fact exactlyOne(
      AttestationPreimage preimage, int recordTypeTag, AttestationAuthorizationFailure failure) {
    List<AttestationPreimage.Fact> records =
        AttestationPreimageFields.records(preimage, recordTypeTag);
    if (records.size() != 1) {
      throw failure(failure);
    }
    return records.getFirst();
  }

  private static int unsigned8(
      AttestationPreimage.Fact fact, int fieldIndex, AttestationAuthorizationFailure failure) {
    byte[] encoded = AttestationPreimageFields.requireValue(fact, fieldIndex, failure).encoded();
    return Byte.toUnsignedInt(encoded[0]);
  }

  private static LocalDate laterOf(@Nullable LocalDate left, LocalDate right) {
    return left == null || right.isAfter(left) ? right : left;
  }

  private static AttestationAuthorizationException failure(
      AttestationAuthorizationFailure failure) {
    return new AttestationAuthorizationException(failure);
  }

  private record CloseFacts(
      @Nullable InterimSweep interimSweep, @Nullable FiscalClose fiscalClose) {}

  private record InterimSweep(BigInteger order, LocalDate effectiveFrom, LocalDate effectiveTo) {}

  private record FiscalClose(BigInteger order, LocalDate effectiveFrom, LocalDate effectiveTo) {}
}
