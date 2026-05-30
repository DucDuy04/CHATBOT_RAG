# Fix Loop 23G5 — OOS Pivot Source Cap

## Problem

23G4 verify: OOS FxRate `responseSources=5` (expected ≤2). LLM pivots from OOS question to listing document policy codes.

## Root Cause

`isPureRefusalLikeAnswer` checks refusal marker + absence of substantive content. Pivot answer has both refusal marker AND policy codes → not pure refusal → cap not applied.

## Key Insight

**Order matters**: OOS pivot = refusal FIRST, codes AFTER. Partial in-scope = codes FIRST, refusal AFTER.

## Fix

New `isLeadingRefusalAnswer`: checks if refusal marker appears before first policy code / numbered list item. If yes → leading refusal → cap to ≤2.

Production path: `applyAnswerAwareSourceCap` → `isLeadingRefusalAnswer` (was `isPureRefusalLikeAnswer`).
Playground debug path: unchanged (bypass via `playgroundDebugSources=true`).

## Result

| Test | Before | After |
|------|--------|-------|
| OOS FxRate production | 5 sources (FAIL) | **2 sources (PASS)** |
| OOS Omega production | 2 (PASS) | 2 (PASS) |
| In-scope | 2 (PASS) | 2 (PASS) |
| Playground topN=6 | 6 sources (PASS) | 6 sources (PASS) |

**Status: CLOSED — PASS**
