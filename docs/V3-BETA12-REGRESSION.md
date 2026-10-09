# beta12: native-first POINTS with fresh OCR center tap

Baseline beta11 / code 19 / dca5d324; release 3.0-beta12 / code 20.

## Direct comparison with beta8

Inspected beta8 `476a6e7d` and beta11 `dca5d324` AutomationService POINTS dispatch.
Beta8 selected `Semantic.clickParent(scene, decision.target())`, which could replace
detached OCR/priority evidence with an unrelated clickable container based on
scene-wide bounds containment. The first attempt used ACTION_CLICK if possible;
otherwise/retry it used a gesture. That permissiveness explains a path to continued
operation with incomplete native trees, but also the reproduced promotion-parent
misclick. The user's 24 completed cycles are phone evidence, not a result of our tests.

Beta11 took a fresh native snapshot and required PointsTarget to return a verified
native label/ancestor or balance-withdrawal subtree. A null result immediately
paused. The phone report's `context_rejected / no_points_structure` therefore
stopped an OCR-confirmed points screen before any POINTS input.

## beta12 change (POINTS dispatch only)

1. Keep PointsTarget unchanged and prefer a verified native ACTION_CLICK once.
2. If there is no native target, or a previous native attempt was rejected/left the
   page unchanged, request a NEW screenshot and OCR after the POINTS decision.
   Never use decision.target bounds or the OCR result that caused the decision.
3. After fresh native observation, reuse existing ObservationGate/OcrTicket checks.
   Require exact normalized OCR 내포인트, one spatial target (same-position OCR
   copies collapse), fully visible bounds in the upper 45% of the window and above
   the anchor, anchor+ad present, no waiting/completion/history. Near/overlapping
   promotional text vetoes the tap; distant top promotions do not. Native balance
   or withdrawal ancestry is not mandatory; HOME semantics are the corroboration.
4. Recheck package/window/size/frame age immediately before dispatch and tap the
   fresh OCR box center. Overlay movement defers dispatch and requires a new frame.
   This never performs global containment parent selection or a native action on OCR.

First fallback is in WAIT_FOR_POINTS. POINTS_ENTRY is allowed only along the
existing retry decision path after a submitted native/gesture attempt, as required
by the retry/native-failure cases. Existing one-second unchanged-screen retry
interval is retained; it is not used to infer screen completion. Target package
binding and same-app window tracking are unchanged. A window change invalidates
the pending frame, rather than permanently blocking later legitimate navigation.

The per-cycle policy permits at most two OCR gesture submissions (one retry), each
with its own new screenshot/OCR. Rejected/cancelled submissions count. Same-app
window changes do not reset this budget; a new cycle does. Unchanged/wrong pages
use existing bounded retry/timeout then pause; no new automatic BACK recovery.

ACTION_CLICK acceptance and gesture callbacks remain submission/completion signals
only. Fresh history semantics are still required for business success. No changes
to Engine, Semantic, PointsTarget, WindowOcr, ObservationGate/OcrTicket, AD, reward
completion, or reward/history BACK logic. Version metadata and documentation aside,
production changes are confined to AutomationService's POINTS path and PointsInput.

Diagnostics use the existing two-line stop-summary slots: mode=native or
mode=ocr_gesture with attempt/bounds, or a compact fallback rejection reason.
No balances or ad content are added. User-facing pause text stays short.

## Regression tests

13 new production-policy/Engine tests cover the requested ten cases: native
preference; missing native; context rejection with confirmed HOME; OCR ambiguity
and duplicate collapse; nearby promotion; unrelated promotion; stale frame/window/
package/generation/cycle; accepted/rejected native without transition then fresh
OCR; actual history after gesture; wrong page without success/automatic BACK.
A delayed native transition arriving during fallback OCR is handed back to the existing history success path without another tap.
Additional assertions cover the two-gesture cycle budget, changed fresh coordinates,
exact text, upper region, absent HOME and waiting/completion vetoes.

These are unit/model tests, not Android device gesture or real service integration
tests. Actual phone success remains for the user to verify.

Local final validation: 206 tests passed (13 new), zero failures/errors/skips; lint zero errors/five pre-existing warnings; assembleDebug successful.
