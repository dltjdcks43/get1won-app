# beta17: POINTS native-to-gesture retry

Baseline beta16 6400066c. Version 3.0-beta17 / 25.

Native balance recognition exposed an existing routing issue: PointsDispatch selected NATIVE whenever the current target had an exact clickable Accessibility handle, independently of attempt. Consequently both attempts could deliver ACTION_CLICK successfully without entering history.

PointsDispatch now accepts the current decision's attempt and permits NATIVE only on attempt 1. Attempt 2 uses the current observed target's center gesture. Existing Engine reobservation after one second, two-attempt limit and history-only success confirmation are unchanged. No old target is cached or reused. The service still checks current decision, package/window/bounds equality, stable window/display geometry and overlay movement before input. Callbacks remain delivery results, not history success.

Logs distinguish native/ACTION_CLICK, gesture_fallback/GESTURE on attempt 2 and target_gesture/GESTURE for first-attempt non-native input. Existing history_confirmed/not_confirmed diagnostics remain.

Seven new policy/model tests cover native first attempt, accepted native with no history and a moved fresh target on retry, gesture completion requiring history, bounded second-attempt failure, stale/window/overlay deferral, offscreen guards, and OCR gesture on both attempts. Historical beta13/14 test calls only receive the new attempt argument. Semantic, Engine, WindowGeometry, WindowOcr and AD routing/selection are unchanged. Phone/service instrumentation was not performed; user phone validation is required. No main merge/stable tag.

Validation: one testDebugUnitTest lintDebug assembleDebug invocation passed. 252 tests, zero failures/errors; lint zero errors and five existing warnings; debug APK assembled.
