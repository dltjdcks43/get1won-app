# beta6: HOME points 인식과 짧은 진단

기준 beta5 / 7d19b186ebb7b846a4533defbf524eb389843fab. 시작 전 fetch 결과 local/origin 동일, 미커밋 변경 없음. 사용자 실제 로그는 HOME에서 points missing, anchor/ad found, lastAction none, 첫 cycle 미완료다. 오류 후 별도 Samsung launcher 세션은 이번 분석에서 제외했다.

## 확인한 코드 흐름 및 원인 경로

Engine HOME은 f.home()와 ad를 요구하며 f.home()은 points와 anchor를 요구한다. 따라서 보고된 points=null에서는 AD decision을 만들지 않는 것이 코드와 일치한다.

v2.2는 contains(내포인트)였지만 beta5는 전체 label 또는 정해진 잔액 형식만 허용하고 pointsContext로 부모 문맥까지 검증했다. A~E/G~J 기본 형태는 이미 처리 가능했고 F의 분리된 `내`+`포인트`는 결합하지 않았다. 또한 정상 부모 description에 `잔액`이 포함되면 기존 정규식이 거부하여 자식의 정확한 label까지 탈락할 수 있었다. 회귀 테스트로 두 경로를 검증했다. 실제 기기의 원문 tree가 없으므로 이번 휴대폰에서 어느 형태가 발생했는지는 확정하지 않는다.

## 최소 변경

- points 문구 전체 검증은 유지하면서 선택적인 잔액 표기/콜론을 허용한다. 잔액 없는 `내 포인트 출금`, 알림 설정, 이벤트, 광고 문구는 허용하지 않는다.
- 현재 장면의 `내`와 `포인트` 두 label만 화면 순서/거리/줄 겹침/높이 비율로 결합한다. 두 fragment와 공통 부모 문맥을 확인하고, 실제 부모 체인으로 입증되는 같은 영역은 중복 제거한다. 멀리 떨어진 조각과 실제 복수 points는 임의 선택하지 않는다.
- native 부모 문맥에서 확인된 잘못된 내 포인트 메뉴가 OCR/priority query 복사본으로 되살아나지 않도록 가까운 동일 영역의 문맥도 검사한다. 큰 페이지 전체의 description으로 작은 정상 label을 배제하지 않는 기존 한계를 유지한다.
- pointsEvidence는 후보 수와 none/label_missing/context_rejected/ambiguous_distinct_targets를 반환한다. 후보 수는 인식 전 문맥 후보 수이며 중복된 관측이 포함될 수 있다. 중단 요약에는 한 줄만 추가하고 잔액 원문을 기록하지 않는다. 최대 13줄, 이 문제처럼 anchor가 있으면 12줄이다. 기존 상세 selector/bounds 로그도 유지한다.
- Engine 변경은 timeout 문구뿐이다. HOME 요소 누락은 실제 missing 항목, 요소가 모두 있지만 입력을 시작하지 못했다면 HOME 광고 입력 미시작을 표시한다. 광고 제출 뒤 AD_ENTRY timeout은 기존 화면 전환 실패 문구를 유지한다.

anchor 공간 인식/ad 탐색/광고 target/완료/BACK/points 클릭 후 흐름/history/HOME_AFTER_HISTORY/cycle/window/OCR 모델과 ticket 처리는 변경하지 않았다. main/V2도 변경하지 않았다.

## 테스트와 기기 확인 범위

Beta6RegressionTest 8개: A~J 및 잔액 부모/세로 OCR split, 각각 points+anchor+ad→AD decision, fresh Accessibility+OCR merge, 잘못된 메뉴/광고/이벤트의 AD 차단, native 문맥과 OCR/query 중복, split child 부모 중복, 먼 fragment/실제 먼 중복, HOME/AD_ENTRY timeout 구분, 짧은 요약의 후보 이유와 개인정보 미노출.

초기 검증에서 기존 상세 진단의 selector 누락 1건을 발견해 정보 출력을 복구했다. 기존 테스트를 삭제/완화하지 않았다. 실기기 검증은 사용자에게 맡기며 이번 수정이 기기에서 해결됐다고 단정하지 않는다.

버전 3.0-beta6 / versionCode 14. v3-universal 게시와 해당 커밋의 GitHub Actions/APK를 공통 기준으로 사용한다.

최종 로컬 검증: testDebugUnitTest 141개 통과(실패/오류/skip 0), lint 오류0·기존 경고5, assembleDebug 성공.
