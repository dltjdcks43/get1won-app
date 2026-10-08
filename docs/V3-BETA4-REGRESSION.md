# beta4: HOME anchor fragment 복원 및 중단 요약

기준: v3-universal beta3 / 9d5e0a7dd0096cc1d807496e2ffdac1ab865c55a.
사용자 실제 기기 결과: 5 cycle 성공, 6번째 HOME에서 points=OCR, anchor/ad 미발견으로 timeout. node dump가 없어 이번 기기의 정확한 분할/좌표는 확정할 수 없다.

## 코드상 확인과 최소 변경

기존 blocks()는 세로 인접한 두 node만 결합한다. 따라서 가로 split과 `여기서 구경하면` + `1원` + `받아요` 세 fragment에서는 anchor가 누락된다. 핵심 matcher(구경/1원/받)는 변경하지 않았다.

Semantic의 anchor 전용 결합은 같은 출처의 짧은 안내 문구만 2~3개 결합한다. 순방향 가로/세로 인접, 높이 비율, 거리, 전체 영역을 제한하고 겹친 node를 결합하지 않는다. 후보 fragment 128개를 초과하면 합성을 생략하며 일부를 임의 선택하지 않는다. 전체 node와 함께 unique()에 전달하므로 기존 Accessibility/OCR 위치 허용 및 distant ambiguity가 유지된다. 실제 부모 체인으로 입증된 description만 합성 child label로 대체한다. 일반 unique(), samePosition(), waiting/complete 결합 규칙은 그대로다.

HOME points-only는 기존 30초 기한 동안 fresh snapshot/OCR 관찰을 계속한다. Engine/OcrTicket/ObservationGate와 광고 클릭·완료·BACK·포인트·cycle·window 코드는 변경하지 않았다. 이전 cycle의 bounds를 저장하거나 재사용하지 않는다.

고급 설정에 `마지막 중단 요약`을 추가했다. PAUSED 시점의 버전/commit, state, cycle/completed, reason, lastSuccess, lastAction, points/anchor/ad 출처, OCR, 마지막 관찰된 상태 전환을 11줄로 캡처하여 별도 파일에 저장한다. IDLE/재시작은 직전 중단 요약을 덮어쓰지 않는다. OCR에는 ticket/cycle을 표시하며 상세 로그/오류 보기 기능은 유지한다. 원문 화면이나 좌표는 요약에 저장하지 않는다.

## 검증

Beta4RegressionTest 10개: 단일 변형/공백, 가로·세로 2분할 및 tree 순서 반전, 요청 C/D 3분할, split Accessibility+OCR 한 줄, bounds 오차 및 실제 먼 중복, 부모 description+child 및 split child, 먼 fragment/다른 이벤트 배제, 30초 내 늦은 anchor 및 timeout, 5회 순환 후 새로운 6 HOME와 이전 cycle ticket 거부, 11줄 요약/source 확인.

실제 휴대폰 성공은 사용자 제공 beta3 5 cycle 결과뿐이다. beta4의 테스트는 모델 검증이며 기기 성공을 단정하지 않는다. 기기에서 반복 후 문제가 재현되면 `고급 설정 > 마지막 중단 요약`을 기준으로 후속 분석한다.

버전 3.0-beta4 / code 12. main/V2 변경 없음. GitHub Actions가 testDebugUnitTest/lintDebug/assembleDebug와 commit별 APK artifact를 생성한다.

로컬 검증: testDebugUnitTest 125개 통과(실패/오류/skip 0), lint 오류 0·기존 경고 5, assembleDebug 성공. 샌드박스 localhost daemon 연결 실패 후 일반 환경에서 요청한 세 작업을 실행해 성공했다.
