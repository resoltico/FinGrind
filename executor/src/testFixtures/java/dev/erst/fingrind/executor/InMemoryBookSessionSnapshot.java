package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.tax.DeclaredTaxRegistration;
import dev.erst.fingrind.contract.tax.TaxRegistrationId;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.ClosedFiscalYearRecord;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.bookkeeping.SweptInterimResult;
import dev.erst.fingrind.executor.spi.StoredRequestPosting;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Immutable rollback snapshot for one in-memory executor fixture session. */
record InMemoryBookSessionSnapshot(
    boolean initialized,
    Instant initializedAt,
    BookIdentity bookIdentity,
    Map<AccountCode, RegisteredAccount> accountsByCode,
    Map<TaxRegistrationId, DeclaredTaxRegistration> taxRegistrationsById,
    Map<IdempotencyKey, StoredRequestPosting> postingsByIdempotencyKey,
    Map<PostingId, CommittedPosting> postingsByPostingId,
    Map<PostingId, CommittedPosting> reversalsByPriorPostingId,
    List<SweptInterimResult> transferredPeriodResults,
    List<ClosedFiscalYearRecord> closedFiscalYears) {}
