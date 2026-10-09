# beta9: POINTS native target resolution

Baseline: beta8 / code 16 / `476a6e7d`. Release: 3.0-beta9 / code 17.

## Evidence and cause

Phone report: cycle 27 / completed 24, `POINTS attempt=1 accepted=true`, but
the notification/nearby-benefits page opened instead of points history. The exact
native handle used on that phone was not recorded, so this is a reproduced code
path, not proof of the precise device node.

`Semantic.clickParent` previously searched the whole scene for the smallest
clickable box containing a detached OCR/priority label. The regression fixture
demonstrates that it picks the overlapping "페이스페이 알림 받고 포인트 받기"
container instead of the separate points row.

## Change

- POINTS alone takes another fresh native snapshot before dispatch. Priority
  query matches collect their real parent chain (bounded to 8 levels and existing
  query node limits); ordinary recognition snapshots remain unchanged.
- `PointsTarget` reuses existing points semantics on fresh Accessibility evidence,
  associates it with the selected location, and follows only actual native parents.
  Native split labels can identify their shared parent, but are never click handles.
- No scene-wide containment fallback, OCR-coordinate gesture, or synthetic handle.
  A missing/ambiguous/unverified native target pauses. All retries resolve again.
- Target subtree and directly connected local label context reject notification,
  points-reward promotion, nearby-benefits, event, consent and attendance wording.
  Unrelated overlapping siblings/page-level promotion do not reject a real row.
  Duplicate query/tree ancestor observations retain local subtree checks.
- POINTS failures add two bounded summary lines: selected source and native target
  source/bounds/clickable/context. No balance or raw page text is included.
- `accepted=true` remains request submission only; existing fresh history semantics
  confirm success. HOME points/anchor/ad, Engine, OCR tickets, cycle/window, and
  reward/history BACK rules are unchanged.

The optional wrong-page automatic BACK recovery was considered but not added:
page wording does not establish that a BACK is safe, and it would extend existing
BACK conditions/state transitions. An unknown page still follows the existing
bounded timeout and pause; no new automatic BACK is issued.

## Validation

12 new regression tests: old wrong-container reproduction; OCR/priority native
resolution; split native label; no detached/OCR fallback; all excluded contexts;
descendant and local-parent rejection; distant duplicates/stale evidence;
duplicate query ancestors and partial tree; separate row under page promotion;
native clickable label priority; accepted wrong page versus actual history;
two-line summary with no balance.

Full unit tests, lint and assembleDebug are run locally and by GitHub Actions.
Actual phone testing is performed by the user; no device-success claim is made.

Local final result: 168 tests passed (12 new), zero failures/errors/skips; lint zero errors and five pre-existing warnings; assembleDebug successful.
