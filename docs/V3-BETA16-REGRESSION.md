# beta16: native points balance summaries

Baseline: beta15 9097aed1. Version: 3.0-beta16 / 24.

The old points matcher ended at the balance unit, optional withdrawal and arrow. Native summaries ending in 내역보기 failed the matcher and pointsContext, leaving recognition dependent on a separate OCR 내포인트 label. The diagnostic # represents masked digits, not a literal input character.

The matcher now accepts normalized 내포인트, or 내포인트 + optional 잔액/colon + digits (optional groups of comma and three digits) + optional unit (원/P/p/포인트) with optional 내역보기 + optional 출금/arrow. 내역보기 requires the unit; existing unitless numeric summaries remain supported. Promotions, missing amounts, malformed commas and reward-action suffixes are rejected.

No pointsContext, samePosition, unique, dispatch or OCR pipeline changes were necessary. Existing native-first/smaller-bounds selection collapses the reported wide text and small clickable description on the same row and selects the latter. Distant actual targets remain ambiguous. Native and OCR accepted-candidate counts and selected source are added to advanced diagnostics; amounts remain masked.

Eight new tests cover text/description summaries, comma/unit/whitespace variants, HOME and AD action without OCR, native/OCR duplicate observations, exact reported native row bounds, promotions/malformed summaries and distant duplicates. beta15 AD tests and beta14 geometry tests remain unchanged. AD selection/retry, POINTS, history/BACK and timeouts are unchanged.

These are unit/model tests; phone validation is still the user's task. No stable tag or main merge.

Validation: one testDebugUnitTest lintDebug assembleDebug invocation passed; 245 tests, zero failures/errors; lint zero errors and five existing warnings; debug APK assembled. Phone validation not performed.
