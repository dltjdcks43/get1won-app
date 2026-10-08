# beta8: points selector와 anchor row/ink 중복

기준 beta7 / 05c4dd46f2472871629715cc02864ed83f9a8ae1. fetch 후 local/origin 동일, clean에서 시작했다. 최신 사용자 자료는 points2/context_rejected 중단과 별도 viva.republica.toss 화면 분석의 anchor2/ambiguous 두 문제다. 두 관찰을 하나의 실패로 합치지 않았다.

## 재현과 변경

Beta8RegressionTest 8개를 beta7 원본에 먼저 실행해 4개 실패를 확인했다(work/beta8-before.log). 명확한 내 포인트 text와 무관한 알림 description, 동일 위치 복사본, 잘못된 후보 거부로 실제 먼 중복이 가려지는 경우, Accessibility row와 OCR ink의 높이 차이를 재현했다.

1. pointsContext 자신의 의미는 label(Node)로 판단한다. nonblank text가 있으면 description의 unrelated event 단어로 veto하지 않는다. text가 비어 있으면 description이 실제 label이므로 잘못된 알림 메뉴는 계속 거부한다. 동일 위치 native/query 복사본이 다른 후보의 context로 들어올 때도 명확한 정상 points text를 description으로 뒤집지 않는다. 실제 local container의 잘못된 points 문맥 검사는 유지한다.

2. 공유 samePosition 구현은 변경하지 않았다. unique의 기본 동작도 같은 비교를 그대로 사용한다. anchor에서만 별도 비교 함수를 사용한다. 기존 위치 비교가 실패한 경우 Accessibility/OCR 쌍, 정규화한 동일 문구, 글자 행의 중심 및 충분한 가로/세로 겹침, 높이 비율 최대4배와 화면 높이1/8 한도를 모두 만족할 때 같은 target으로 처리한다. 예: native row [0,750,1080,890], OCR ink [120,803,690,838]. 실제 기기의 bounds를 확보한 것은 아니며 이 geometry는 코드상 실패를 재현하는 모델 입력이다.

모든 후보 쌍의 일치를 확인하는 방식은 유지한다. 큰 native row가 서로 떨어진 두 OCR anchor를 연결해 임의 선택하는 것도 거부한다. source가 같거나 문구가 다른 후보에는 추가 완화를 적용하지 않는다. 실제 native parent description/child text 중복은 기존 hierarchy 제거로 처리한다.

## 보존

광고 target/ad 탐색, Engine 전체, 광고 진입/완료/BACK, 포인트 내역, HOME_AFTER_HISTORY/cycle/window/OCRticket 수명 및 중단 요약을 변경하지 않았다. 버전만 3.0-beta8/code16으로 증가시켰다. main/V2 보존.

## 검증 범위

정상 A~D 및 AD decision, 비정상 E/F/G, 같은 위치 points 2개/query/OCR, 실제 먼 points, row/ink anchor, parent/child/OCR anchor, 실제 먼 anchor 및 큰 row에 의한 잘못된 연결 방지, 다른 문구/같은 source 제한을 테스트한다. 기존 테스트 삭제/완화 없음.

실기기 성공은 사용자가 확인한다. 모델 재현/테스트 성공을 휴대폰 해결로 표현하지 않는다. 최종 commit의 GitHub Actions와 APK를 v3-universal의 공통 기준으로 사용한다.

최종 로컬 검증: testDebugUnitTest 156개 통과(실패/오류/skip0), lint 오류0·기존경고5, assembleDebug 성공.
