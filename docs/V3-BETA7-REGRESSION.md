# beta7: 주변 프로모션에 의한 points context 오거부

기준 1db918ddc6dbe6f08d2cb188509511460be6e711 / beta6. 시작 전 fetch 확인: local/origin 동일, clean. 실제 사용자 결과는 18 cycle 완료 후 19번째 HOME에서 points candidates=2/context_rejected, anchor/ad found이다. authoritative evidence는 보존된 마지막 중단 요약이며 새 세션/launcher 기록은 사용하지 않는다.

## 수정 전 재현

beta6 pointsContext는 후보 높이 4배 이내 ancestor에서 `(내포인트 포함 && 정상 형식 아님) || pointsEvent`를 적용했다. 즉 ancestor가 해당 points를 설명하는지 입증하지 않아도 알림 단어만 있으면 거부했다. 새 Beta7RegressionTest를 beta6 원본에 먼저 실행하여 7개 중 4개 실패를 확인했다(프로모션 page, 혜택알림 ancestor, points+promo 집계 description, 정상 points를 제거해 실제 먼 중복까지 잘못 처리하는 경우). work/beta7-before.log에 기록했다.

이 경로는 코드/모델에서 재현했다. 휴대폰의 실제 Accessibility dump는 없으므로 실제 트리 구성이 테스트와 동일하다고 단정하지 않는다.

## 변경 범위

Semantic.pointsContext와 그 local context helper만 수정했다. 후보 자체의 잘못된 label/description은 계속 거부한다. ancestor/주변 region은 points를 명시적으로 설명하는 문구여야 하며 bounds 포함/동일 위치, 실제 parent chain 또는 그 위치의 native label과의 연결을 확인한다. 높이 한도만으로 context를 결정하지 않는다.

더 안쪽의 실제 descendant container에 현재 label 근처의 잔액과 출금 child가 함께 있으면 그 region 밖 page/promo description은 해당 points의 local context로 사용하지 않는다. OCR/query 복사본에도 같은 영역 구분이 적용된다. 실제 points region의 내 포인트 알림 설정/이벤트/광고/출석/동의 문구는 계속 거부한다.

후보 2개라는 이유로 거부하지 않으며 기존 samePosition/hierarchy/unique 구현은 변경하지 않았다. 실제로 먼 두 points는 ambiguous_distinct_targets를 유지한다.

anchor/ad/클릭/광고 완료/BACK/points 클릭 후 흐름/history/HOME_AFTER_HISTORY/cycle/OCR ticket/window/timeout 로직은 변경하지 않았다. 마지막 중단 요약도 길이와 항목을 그대로 보존한다. 추가 context source 행은 선택 사항이므로 넣지 않았다. 버전만 3.0-beta7 / code15로 증가시켰다.

## 새 테스트

A: Page→Promo+PointsRegion→내 포인트/잔액/출금에서 AD decision.
B: 작고 낮은 ancestor의 혜택 알림 description에서도 정상 points.
추가: points와 promo가 섞인 page 집계 description을 local balance 영역으로 구분.
E: OCR 및 priority query 복사본, native label 없이 OCR만 보이는 경우.
같은 위치의 정확히 두 관측은 하나의 target.
C/D 및 광고/출석/동의: 실제 local 메뉴는 native/OCR 모두 reject, AD 없음.
실제 먼 duplicate는 ambiguous 유지.

실제 beta7 기기 검증은 사용자가 진행한다. beta6 18 cycle 성공 이력과 모델 테스트 결과를 구분하며 실기기 해결을 주장하지 않는다.

최종 로컬 검증: testDebugUnitTest 148개 통과(실패/오류/skip0), lint 오류0·기존경고5, assembleDebug 성공. 기존 테스트 삭제/완화 없음.
