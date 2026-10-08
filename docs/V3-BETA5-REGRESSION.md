# beta5: 공간 기반 HOME anchor 증거

기준: 8e373862f23026215e6ab488ac3a891d30b5ba9f / beta4. fetch 결과 추가 remote commit 없고 clean 상태에서 진행했다. 사용자 실제 결과는 첫 cycle 완료 후 두 번째 HOME에서 points=OCR, ticket53/nodes20/anchorfalse, 30초 timeout이다. 별도 삼성 launcher 분석 결과는 원인 판단에 사용하지 않았다.

## 확인한 코드상 실패 경로

beta4는 fragment 문자열에서 특정 단어를 제거한 나머지가 없어야 조합에 참여시켰다. 따라서 `지금 구경하면!`, `1원을`, `받아` 같은 유효 증거도 버렸다. source가 다르면 가까운 Accessibility/OCR fragment도 무조건 제외했다. 실제 dump가 없으므로 사용자 기기에서 어떤 원문/좌표가 이 필터에 걸렸는지까지 확정하지 않는다. OCR53회 성공은 인식 작업 성공이지 anchor 텍스트 내용 보장을 뜻하지 않는다.

## 변경

- Semantic.anchorEvidence: 문장 전체 재구성 대신 `구경`/`1원`/`받`의 bit 증거를 현재 scene에서 수집한다. 같은 증거만 반복하는 조각은 합치지 않는다. 최대 세 개의 기여 조각이면 세 의미를 채울 수 있으며 fragment 개수만 늘리는 방식이 아니다.
- 전체 허용 문구 목록을 제거했다. 공백·문장부호는 anchor 처리에서만 정규화한다. `구경` 의미는 그대로 요구한다. OCR source에서만 `l원`, `I원`, `|원`을 숫자1 오독으로 허용하며 임의 edit-distance 또는 구경 오독 추정은 하지 않는다. 10원/11원은 1원으로 처리하지 않는다.
- 단일/Accessibility/OCR/혼합을 지원한다. 글자 높이 비율, 가로·세로 간격, 화면 순서, 영역 크기를 제한한다. 혼합 출처는 더 좁은 가로 간격 또는 같은 줄의 대응 bounds를 요구한다. 전체 화면 문자열 결합은 하지 않는다. 후보 한도 초과도 임의 선택 없이 실패로 처리한다.
- 동의/알림/출석/출금/포인트/확인하기/적립이벤트/이미 받았다는 문구는 제외한다. 결합 영역 안의 별도 제외 문구를 건너뛰어 세 토큰을 합치지 않는다. 실제 먼 중복은 기존 unique()의 ambiguous 처리를 유지한다. 실제 ancestor 확인 및 같은 위치 OCR 중복 처리도 보존한다.
- 중단 요약에 실패 시 `anchor candidates=N reject=...` 한 줄만 추가한다. browse_missing, one_won_missing, receive_missing, excluded_event, spatial_or_context_rejected, region_too_large, ambiguous_distinct_targets, candidate_limit을 구분한다. 후보 수는 완성된 의미 후보 수이며 OCR 전체 node 수가 아니다. 최대 12줄, raw 화면 텍스트는 저장하지 않는다.

## 검토 후 적용하지 않은 fallback

points 존재와 HOME_AFTER_HISTORY 복귀는 특정 광고 카드의 신원을 보장하지 않는다. 제공된 요약에는 AD badge/실제 카드 tree/안전한 section selector가 없다. 이전 좌표를 재사용하거나 첫 clickable 항목을 선택하는 fallback은 적용하지 않았다. 현재 anchor가 없으면 안전하게 계속 관찰 후 timeout한다. 충분한 실화면 구조 증거 없이 anchor/ad 의존 관계를 끊지 않았다.

## 유지한 흐름

Engine, ObservationGate, OcrTicket, WindowOcr와 광고 입력/진입/완료/BACK_REWARD/points/history/BACK_HISTORY/HOME_AFTER_HISTORY/cycle/window 처리 코드는 변경하지 않았다. matcher 개선은 현재 scene에만 적용하며 과거 anchor bounds를 저장하지 않는다.

## 검증과 인수인계

Beta5RegressionTest 8개 추가. 10개 입력 변형마다 실제 상태 순서로 첫 cycle을 완료하고, 다음 HOME에서 points=OCR 및 53번째 fresh OCR의 anchor/ad/다음 AD decision을 검증한다. 단일/2~3분할/혼합/문장부호/조사/공백/어긋난 bounds/OCR 숫자 오독, 실제 먼 중복/먼 fragment/타 이벤트/다른 금액/대형 영역/anchor없는 클릭 금지/12줄 요약을 포함한다.

로컬 testDebugUnitTest 133개 통과(실패/오류/skip 0), lint 오류 0·기존 경고 5, assembleDebug 성공. 이는 모델 검증이며 beta5 실기기 테스트는 하지 않았다. beta4 사용자 제공 첫 cycle 성공 결과와 구분한다.

버전 3.0-beta5 / versionCode 13. v3-universal 공통 기준으로 게시하며 main/V2는 보존한다. 해당 commit의 GitHub Actions 및 APK artifact를 기준으로 다음 PC에서 이어서 진행한다.
