# beta15: native AD candidates

Baseline: beta14 6ba2b8db, user-reported completed=59. Version: 3.0-beta15 / 23.

## Cause and scope

Semantic.ad previously called adRejection for every scene node, including merged OCR lines. adRejection accepts non-clickable nodes with text after the geometry/exclusion checks. A broad OCR title line crossing a native card boundary can therefore conflict with the native card and produce ambiguous_cards. Fully contained OCR lines alone do not necessarily reproduce the problem: containment already suppresses that conflict. The supplied log does not include every OCR bound, so the regression uses the reported native bounds and a boundary-crossing OCR fixture, not a claim of exact phone coordinates.

Only Accessibility nodes now enter the primary AD candidate list. Existing adClickTarget ancestor selection, geometry and excluded-label/child checks remain unchanged. The full scene, including OCR, still supplies title and exclusion evidence for native cards. OCR merge, points and anchor logic remain unchanged.

Advanced AD diagnostics count nativeCandidates and ocrSupport (OCR lines passing the existing card geometry/content checks); only native candidates determine ambiguity. AD titles are not included in the AD diagnostic detail. WindowGeometry, AutomationService, POINTS, OCR tickets, BACK/history, timeout and AD retry policies are unchanged.

## Regression checks

Nine tests cover two/three OCR title lines with a native card, two genuinely different native cards, excluded children from either source, OCR points with native anchor/card starting AD, missing points waiting until normal OCR observation, before/after merge consistency and title-free diagnostics, OCR-only rejection, and OCR title evidence for a blank native card.

Running these new tests against beta14 reproduced five failures, including native ad becoming null after OCR merge. The existing full-width points/anchor test now expects no AD when its fixture contains only an OCR title and no native card. No service OCR-request behavior was changed; the missing-points test exercises Semantic and Engine, not Android service instrumentation.

Phone validation remains the user's task. This release is not marked stable and is not merged into main.

Validation: testDebugUnitTest lintDebug assembleDebug passed in one invocation. 237 tests, zero failures/errors; lint zero errors and five existing warnings; debug APK assembled. No phone/device tests were run.
