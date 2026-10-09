# beta11: local usable native target and structural fallback gating

Baseline beta10 / code 18 / 3241cf6b. Version 3.0-beta11 / code 19.

The phone report showed OCR points and `context=no_native_label`, without enough
detail to distinguish rejected, ambiguous or position-mismatched native evidence.
Code inspection confirms all of those could bypass beta10's structural proof.
Tests reproduce these gating paths; the exact native phone candidate is unknown.

## Minimal change

Only PointsTarget production logic and build metadata changed. HOME recognition,
adapter fresh snapshot, Engine, OCR tickets, window/cycle, AD, completion, and
reward/history BACK remain unchanged.

- For OCR evidence, examine native candidates corresponding to that location.
  Remote context-rejected or ambiguous labels do not globally veto independent
  local balance/withdrawal proof. Detached non-clickable priority copies do not
  prevent that proof either.
- Corresponding promotional candidates block fallback. Two distinct actual native
  click targets at the OCR location block fallback, including equal-geometry
  siblings. True ancestors and duplicate query handles are distinguished from
  separate targets. Native split-label parent resolution is preserved.
- A local clickable balance/withdrawal row may start below the OCR heading:
  horizontal overlap >=80% of the heading width, top between half a text height
  above heading top and one text height below heading bottom, bottom within three
  text heights below heading bottom. Existing container height/screen limits and
  actual native subtree proof remain mandatory. Distance alone never selects it.
- Existing structural promotion veto, missing/remote balance or withdrawal,
  distinct-region ambiguity and native-handle-only dispatch remain in effect.
- Existing two diagnostic lines are retained. The selected line now reports
  `native=<reason> structure=<outcome>`; target source/bounds/context remain on the
  second line. No balance values are logged.

## Regression verification

13 new tests cover missing label, unrelated rejected candidate, distant ambiguity,
position mismatch/detached priority, local native ambiguity, corresponding promo,
non-containing row with both required descendants, promo row, distant/laterally
unrelated structure, structural ambiguity, query duplicates, native split labels,
and bounded private diagnostics.

Two older tests asserted global native ambiguity as an unconditional OCR veto,
which beta11 explicitly replaces. They now retain the native-only conservative
case and the actual nearby distinct-target rejection respectively. Other prior
tests remain unchanged. No OCR gesture or arbitrary containment fallback added.

Device success is not claimed; actual phone validation is performed by the user.

Local final validation: 193 tests passed (13 new), zero failures/errors/skips; lint zero errors/five pre-existing warnings; assembleDebug successful.
