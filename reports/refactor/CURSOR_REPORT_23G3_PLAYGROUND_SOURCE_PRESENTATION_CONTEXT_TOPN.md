# CURSOR REPORT 23G3 — Playground Source Presentation Context Top-N

## Summary

Fixed Playground sidebar showing only 2 sources when Context Top-N=6 and answer was a partial list with refusal phrasing.

## Root cause

`applyAnswerAwareSourceCap` treated any answer containing refusal markers as OOS → `MAX_REFUSAL_RESPONSE_SOURCES=2`, after playground had already built up to `topN` sources.

## Changes

- `ChatService.applyAnswerAwareSourceCap(answer, sources, ChatRequest request)`
- Skip refusal cap when `playgroundDebugSources=true`
- `isPureRefusalLikeAnswer` / `hasSubstantiveFactualContent` for production partial vs pure refusal

## Files

- `Backend/.../ChatService.java`
- `Backend/.../ChatServiceSourcePresentationTest.java` (+3 tests)

## Verification

- Unit tests: PASS
- Runtime: Playground topK=6 → 6 sources in SSE `done` event

## Conclusion

**PASS**
