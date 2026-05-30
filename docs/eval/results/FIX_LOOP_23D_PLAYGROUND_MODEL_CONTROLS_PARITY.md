# FIX_LOOP 23D — Playground Model Controls Parity

**Loop:** diagnose → minimal fix → test → report  
**Date:** 2026-05-16

## Iteration 1

### Diagnose

- Read 22A/22B2/22C2/22D/21J reports and playground + ChatService source paths.
- Confirmed playground normal uses `chatStream` with `MAX_RESPONSE_SOURCES=5`.
- Compare uses `PlaygroundService` without that cap.

### Fix

- Backend playground debug source presentation via `playgroundDebugSources`.
- FE compare sidebar hide.
- FE model save form sync from response.

### Verify

- Unit tests extended and PASS.
- FE lint/build PASS.
- Manual Docker/UI verify deferred to operator.

## Next loop (if needed)

- Runtime golden: playground topK 3 vs 10 source count on real PDF.
- Log scrape: Model Settings topK=10 → `[RAG] retrieval topK source=MODEL_CONFIG effective=10`.
