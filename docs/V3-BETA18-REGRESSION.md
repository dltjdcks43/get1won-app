# beta18: strong HOME evidence after return

Baseline beta17 4da1388d; user reports 65 completed phone cycles. Version 3.0-beta18 / 26.

The existing Semantic.blocks combines nearby anchor and card text. A HOME anchor containing 구경 and an adjacent card containing 3초 can produce a waiting observation. The regression fixture reproduces this with the reported anchor/card geometry. Engine.pointsSurface previously vetoed points whenever waiting or complete was present, despite simultaneous points + anchor + ad and no history.

pointsSurface now accepts points with no history when either (a) anchor and ad are both present in the same observation, or (b) waiting and complete are both absent, preserving the original weaker-surface path. No waiting/complete detector or reward/BACK condition changed. Partial HOME evidence cannot override waiting/complete, and history always vetoes pointsSurface.

The override is logged only when actually used for a POINTS decision/retry or HOME_AFTER_HISTORY transition. A state-local marker prevents repeated logs on deferred observations and resets on state transitions. The existing POINTS_ENTRY use of pointsSurface gets the same evidence rule; its retry interval/count and history-only success remain unchanged.

Nine regression tests cover ordinary HOME, the actual nearby anchor/card waiting collision, complete false positive, real waiting/reward completion, history veto, insufficient HOME evidence, repeated deferred-log suppression, HOME_AFTER_HISTORY cycle transition, and native-first/gesture-retry/history-confirmed behavior. Existing beta13–17 tests remain unchanged. Semantic, POINTS dispatch, AD policies, geometry, window/OCR validity, overlay handling and deadlines remain unchanged.

Tests are unit/model checks, not phone verification. No main merge or stable tag.

Validation: one testDebugUnitTest lintDebug assembleDebug invocation passed. 261 tests, zero failures/errors; lint zero errors and five existing warnings; debug APK assembled. Existing beta13–17 tests all pass unchanged.
