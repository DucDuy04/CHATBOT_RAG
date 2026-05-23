# FIX_LOOP 23F5 — Relevance-Aware Final Budget

## Summary

Fixed final context truncation after expansion: replaced pure document-order cap with query-term relevance selection + document-order re-sort.

## Verification loop

| Step | Status |
|------|--------|
| Unit tests `FinalContextSelectorTest` | PASS |
| Docker rebuild | PASS |
| Runtime A Eta/Theta topK=20 | PASS |
| Runtime B aggregate 8 policies | PASS (8/8) |
| Runtime C OOS Omega | PASS |
| Backend full `mvn test` | 2 pre-existing integration errors (Qdrant context) |

## Next

- Optional: persist rerank score on chunk for tie-break.
- Optional: PDF/table regression script for 23B3 corpus.
