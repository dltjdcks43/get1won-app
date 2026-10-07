# beta3: HOME 인식과 첫 광고 입력

## 기준 및 확인한 코드 경로

2026-10-08 원격 fetch → v3-universal checkout → ff-only pull 결과 최신 기준은 `830bea4f0969983cb1291cce117cda173ba777d4`였다. main의 v2.2 `0a84f8b2ed6d34155f2c80af821802ba06e83c47`, 최근 history, README, V3 연구/리뷰/beta2 기록을 비교했다. main은 변경하지 않는다.

- beta2 points 정규식은 `내 포인트 5,285원 출금`을 거부한다. v2.2 contains 방식은 허용했다. 실제 기기의 node dump가 없으므로 이 형태가 사용자 기기의 확정 원인이라고 말할 수는 없다.
- beta2는 최초 HOME points+anchor+ad가 모두 있어야 AD 결정을 한다. 따라서 `AD attempt=1 accepted=true`와 이후 `points=not found`는 동일 관찰의 결과라고 볼 수 없다. `stop()`은 IDLE로 바꾸지만 lastAction/lastSuccess/lastSemanticResult를 지우지 않는다. 제시된 로그로는 중간 관찰이나 중지 원인을 복원할 수 없다.
- AD_ENTRY 재시도도 points+anchor를 요구하여, 첫 요청 후 points만 사라지면 fresh anchor/ad가 있어도 gesture fallback까지 가지 못했다. 최초 진입 조건은 그대로 두고 이 재시도 경로만 수정한다.
- beta2 광고 선택과 clickParent의 포함 bounds 기반 fallback은 실제 조상이 아닌 겹친 node도 선택할 수 있었다. 범용 clickParent의 label 높이 3배 제한도 실제 큰 광고 부모를 놓칠 수 있다. 광고는 별도 card 조건과 실제 parent chain으로 선택하고 adapter에서 다시 범용 clickParent로 바꾸지 않는다. 실제 accepted/no-transition의 원인이 잘못된 node였는지는 실기기 로그가 필요하다.

## 변경

- Semantic.java: 정확한 points label 및 잔액 뒤 선택적 출금만 허용. 별도 child label은 그대로 인식. 좁은 부모 영역(자식 높이의 4배 이내)에 붙은 points 메뉴/이벤트 문구는 거부하며 priority-query 동일 복제에도 적용한다. 전체 페이지 description은 지역 label을 무효화하지 않는다. 기존 hierarchy 중복 제거와 distant ambiguity를 유지한다. 실제 구조 없이 동일한 단독 label만 있는 메뉴는 완전히 구별할 수 없다.
- 광고 후보의 실제 조상 중 광고 위치/형태/제외문구 조건을 만족하는 clickable container를 우선한다. 조건에 맞는 조상이 없으면 원래 후보를 유지하며, 무관한 겹친 container로 올리지 않는다. 거대한 전체 화면이나 다른 1원 이벤트 부모를 허용하지 않는다.
- Engine.java: 1초 후 fresh anchor+ad만으로도 제한된 재시도 가능. 대기/완료/내역이 함께 보이면 이 추가 경로는 사용하지 않는다. HOME anchor가 남아 있는 동안 points가 없어진 것만으로 광고 진입 성공을 확인하지 않는다. 3회/30초 제한 유지. accepted 또는 gesture completed는 성공이 아니다.
- AutomationService.java: 광고 대상 그대로 dispatch, 대상 ID/parent chain/bounds/clickable/window 및 overlay defer 로그 추가. 기존 fresh snapshot/gesture callback 구조 유지.
- Diagnostics.java: 예외 발생 시점의 상태와 전체 stack trace를 먼저 문자열로 확보해 비동기 저장. acceptOcr 경계에는 메서드명/ticket/state와 원본 cause를 추가한다.
- 3.0-beta3 / versionCode 11로 식별한다. 앱 UI/반복 설정과 후반 동작은 재작성하지 않는다.

## window와 OCR 검토

ObservationGate, OcrTicket, TreeWalk, WindowOcr는 변경하지 않았다. 같은 package의 window 변경에 따른 generation/action epoch 증가, 예약/gesture 및 pending OCR/캐시 폐기, snapshot-scoped node 해제와 새 관찰 원칙을 유지한다.

acceptOcr → merge → mergeOcr → inspect → evaluate를 검토했으나, 제시된 오류를 특정할 stack trace가 없고 이 경로에서 원인을 확정할 명백한 예외 결함은 확인하지 못했다. OCR 모델/좌표계/수명주기를 추측으로 바꾸지 않았다. `마지막 오류 보기`는 원본 예외의 파일명·메서드·행 번호와 cause stack을 보존한다.

## 검증과 다음 기기 확인

Beta3RegressionTest: 요청된 points 정상/모호/메뉴/이벤트 사례, 부모+child+query 복제, 분리 child, 전체 페이지 description, 실제 광고 조상과 잘못된 부모 제외, accepted 후 1초 fresh 이동 target 재시도, gesture 완료와 semantic 성공 분리, HOME anchor 잔존 시 잘못된 성공 차단, overlay defer 재관찰을 검사한다. 기존 단위 테스트를 유지한다.

이 PC의 `gradlew.bat testDebugUnitTest lintDebug assembleDebug`는 Java 미설치로 실행 시작에 실패했다. GitHub Actions의 `bash ./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug`가 검증과 debug APK 생성을 수행한다. 해당 commit의 Actions 결과와 artifact를 확인해야 한다.

실기기 테스트/ADB/connectedAndroidTest는 수행하지 않았다. 먼저 HOME points+anchor+ad → 올바른 광고 container 입력 → 광고 대기/완료 semantic 진입의 로그를 확인한다. 이후 광고 완료/BACK/points/내역/BACK/HOME/다음 cycle을 검증한다. 첫 단계가 해결되었다고 단정하지 않는다. 다음 PC는 최신 remote를 fetch/pull하고 해당 commit의 CI 및 기기 로그부터 이어서 확인한다.
