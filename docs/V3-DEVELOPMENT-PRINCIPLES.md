# V3 development and promotion policy

- 실기기에서 검증된 정상 경로를 단일 오류 하나 때문에 대규모 재작성하지 않는다.
- 회귀 수정은 최소 범위로 한다.
- 새 안전장치가 기존 정상 cycle 성공률을 떨어뜨리면 그 안전장치는 기본 경로에 넣지 않는다.
- stable 이후 핵심 자동화 변경은 별도 experimental branch에서 먼저 검증한다.

## Known-best baseline and stable gate

beta8 (`476a6e7d`) is the **known-best baseline**, based on the user's report of
24 completed phone cycles. It is not stable: an unrelated clickable-parent POINTS
misclick was also reported.

beta13 remains experimental on `v3-universal`. Do not create a stable tag or merge
into main until the user reports at least **50 consecutive real-device cycles**
with no misclicks, no premature pause, and correct points-history entry. Only then
may the version be considered a stable candidate. Unit tests, simulated cycles,
lint and CI builds are not substitutes for this phone evidence.
