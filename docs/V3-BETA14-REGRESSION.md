# beta14: translated window geometry

Baseline: beta13 c8e7f0f0. Version: 3.0-beta14 / 22.

The reported 1080x2316 window [-382,0,698,2316] still has width 1080. WindowOcr's buffer-size comparison therefore passes, and its valid screen-space origin mapping adds -382 to OCR bounds. POINTS [81,583,261,627] becomes [-301,583,-121,627]. Unlike a buffer/window size mismatch on the 1440 device, dimensions alone cannot detect this translation.

WindowGeometry now requires positive window dimensions and full containment in the display. Offscreen geometry skips traversal, observation, OCR requests and dispatch, invalidates pending OCR and reobserves after 250ms without pausing. Identical transient bounds are logged once until geometry changes or recovers. Existing state deadlines are unchanged.

OCR request checks the live window again before creating a ticket. Acceptance checks fresh geometry before merging; existing ticket region equality rejects same-ID bounds changes. Genuine nonzero origins remain valid; WindowOcr's mapping is unchanged.

POINTS validates the target and its center against both window and display before selecting native input or creating a gesture. Invalid geometry defers without input. Runtime failures inside POINTS dispatch have a separate input/gesture diagnostic; OCR merge/semantic and action evaluation failures are distinguished.

The beta13 exact native target / observed target center flow, retry policy and actual history confirmation remain. No proof or fresh-OCR prerequisite was reintroduced. AD selection and retry policy, BACK conditions, cycle logic, OCR model and log copying/sharing remain unchanged. The common geometry guard also protects AD input.

Validation: one local testDebugUnitTest lintDebug assembleDebug invocation passed. 228 unit tests, 0 failures; 10 new beta14 tests cover full display, left/right translation, same-ID OCR region changes, negative targets, 250ms recovery, nonzero origin, device-size distinction, log deduplication and display containment. Lint: 0 errors, 5 existing warnings. These are policy/model tests, not Android service instrumentation or phone validation. User phone validation is still required; no stable tag or main merge.
