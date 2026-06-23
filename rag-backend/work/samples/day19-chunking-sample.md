# Day 19 Chunking Sample

## Settlement Incident Timeline

The settlement platform receives payment events from upstream channels, validates the merchant contract, and writes normalized transaction records into the operational database. During peak traffic the ingestion service batches events by merchant, region, and payment method so downstream reconciliation can compare the clearing file with the transaction ledger.

When a merchant reports a settlement mismatch, the support engineer first checks whether all payment events reached the ledger. Missing events usually indicate delayed upstream delivery, while duplicated events usually indicate retry behavior without a stable idempotency key. The investigation record should include the merchant code, transaction code, clearing date, amount, channel, and the latest processing status.

## Reconciliation Checklist

1. Confirm that the clearing file has been imported for the affected date.
2. Compare the total transaction count and amount between the clearing file and the ledger.
3. Search for failed ingestion tasks with retry count and error message.
4. Check whether the merchant account mapping changed before settlement.
5. Verify that manual adjustments were approved and recorded.

The reconciliation service should not modify settlement balances directly during diagnosis. If a retry is required, the operator must submit a confirmed action that targets a specific failed task. If a rebuild is required, the operator should explain the expected impact and wait for approval before scheduling background work.

## Retrieval Notes

Dense retrieval is useful when the question describes the business symptom in natural language. Keyword retrieval is useful when the question includes exact codes, status names, or operation terms. Hybrid retrieval should preserve the top semantic matches while allowing exact terms such as FAILED, RECONCILIATION, merchantCode, clearingDate, and retryCount to improve ranking.

For evaluation, this document intentionally contains several repeated but distinct operational terms. A good chunking strategy should keep each checklist item close to its surrounding explanation, avoid splitting status names from their descriptions, and avoid producing very short fragments that lack enough context for retrieval.

## Operator Guidance

If the current readiness check reports that question answering is unavailable, inspect document status, indexing tasks, and retrieval configuration before recommending a write-side action. A failed indexing task is more specific than a general readiness warning. A re-embedding requirement is valid only when the embedding model or chunking configuration changed after documents were indexed.

The final diagnostic response should summarize the primary cause, cite the most relevant tool observations, and clearly separate immediate read-only findings from recommended actions that require confirmation.
