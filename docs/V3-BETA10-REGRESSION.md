# beta10: OCR-only points label linked to native structure

Baseline: 3.0-beta9 / code 17 / fdd4b811. Release: 3.0-beta10 / code 18.

Phone report: OCR points present after reward return, but no native points label;
beta9 resolver returned no target. This change adds only a missing-native-label
fallback in PointsTarget. Existing HOME semantics, adapter fresh snapshot, engine,
window/cycle/OCR tickets, AD, completion and BACK remain unchanged.

## Structural proof

Only OCR evidence normalized to 내포인트, with absent (not rejected/ambiguous)
native label, can enable fallback. A fully visible enabled clickable native
container must contain the OCR evidence and have both a native balance and native
출금 in its actual parent-ID subtree. Balance patterns support integer/comma
amounts with 원/P/포인트, not any particular balance.

Search is local: container height <= six OCR text heights and one quarter of the
screen; balance/withdrawal occupy the label's row or nearby rows (top no more than
two text heights below its bottom). Detached or distant text does not count.
Ancestors of an independently proven narrower subtree are removed. Distinct
remaining regions are ambiguous, including siblings with identical geometry;
geometry never invents parent relationships.

Target/subtree promotions and same-footprint direct parent promotion are vetoed,
including 페이스페이 혜택. Promotions elsewhere on the page do not veto the row.
No OCR/synthetic handle or coordinate gesture is used. The existing fresh native
snapshot and history-only success checks remain in effect.

Failure context distinguishes no_native_label, no_points_structure,
ambiguous_points_regions and promotion_rejected. Summary stays at two extra lines
without recording balances.

## Tests

12 regression tests cover required A-F cases (OCR-only success, nested wrapper
choice, promo rejection, missing withdrawal, distant/unlinked balance, two distinct
regions), local versus unrelated promotion, native/enabled/clickable/visible
requirements, stale/non-OCR evidence, ambiguous native labels, merged OCR POINTS
decision with history confirmation, and summary privacy/length.

Actual phone success is not claimed; phone validation belongs to the user.

Local validation: 180 tests passed (12 new), zero failures/errors/skips. Lint zero errors/five existing warnings. assembleDebug succeeded.
