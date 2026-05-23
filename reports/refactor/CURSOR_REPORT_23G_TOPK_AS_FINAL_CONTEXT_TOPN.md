# CURSOR REPORT 23G — Top-K as Final Context Top-N

## Summary

Task 23G redefines UI **Top-K / Context Top-N** as the number of chunks entering the LLM **after** rerank/scoring, while Qdrant vector anchor retrieval uses a **fixed K=30** (`rag.retrieval.vector-anchor-k`).

## Semantics

| | Before | After |
|---|--------|-------|
| `topK` in request | Qdrant limit | `finalContextTopN` |
| Qdrant K | User topK | Fixed 30 |
| Final selection | Document-order budget | Score Top-N → document order |

## Files touched

- Backend: `RagRetrievalService`, `RerankService`, `ChatService`, `application.yml`, tests
- Frontend: playground + chatbot model labels

## Verification

- Compile: PASS
- Targeted unit tests: PASS (23 tests)
- Full `mvn test`: 2 integration errors (no MySQL)
- Frontend lint/build: PASS
- Runtime: NOT RUN this session

## Verdict

**PARTIAL PASS** (implementation complete; runtime matrix deferred).
