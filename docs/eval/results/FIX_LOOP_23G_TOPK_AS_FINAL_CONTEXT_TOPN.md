# FIX_LOOP 23G — Top-K as Final Context Top-N

## Checklist

| Item | Status |
|------|--------|
| UI topK → finalContextTopN | DONE |
| fixedVectorAnchorK=30, not UI-controlled | DONE |
| Score before final cap | DONE |
| Select Top-N by score | DONE |
| Sort selected by document order | DONE |
| No BUDGET_STOPWORDS | DONE (not in codebase) |
| No FinalContextSelector | DONE (class absent) |
| FE labels Context Top-N | DONE |
| Unit tests 23G | PASS |
| Runtime Playground matrix | PENDING |

## Next loop

1. Rebuild `chatbot-backend` image.
2. Run eval on `docs/eval/manual/TOPK_LOW_HIGH_DIFFERENCE_TEST_DOCUMENT.txt`.
3. Confirm logs: `finalContexts` tracks UI Top-N; `vectorAnchors` ~30 stable.
