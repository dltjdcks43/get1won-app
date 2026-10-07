# V3 변경 코드 재검토

빌드 전 Engine, Semantic, Android adapter, window OCR, ticket/gate, tree walker, manifest/UI와 테스트 변경을 다시 검토했습니다. 빌드 중 발견한 가상 시계 테스트 오류도 검증 조건을 유지한 채 수정했습니다.

- Engine: observe에서 action을 예약하여 중복 event가 동일 클릭/BACK을 만들지 않음. defer는 미실행 예약만 해제. generation/cycle/action ID가 다른 응답은 무시. gesture 응답 전에는 화면 성공도 확정하지 않음. BACK 카운트는 실제 복귀에서 증가. cycle 증가는 HOME_AFTER_HISTORY에서 한 번만 발생.
- Points: WAIT_FOR_POINTS/POINTS_ENTRY는 광고 anchor를 요구하지 않음. 화면 모순(waiting/completion/history), 중복된 별개 target, 기하/안전성 오류는 계속 거부. 가시 target을 tree 크기만으로 폐기하는 경로 없음. resourceId는 조회 순서에만 이용하며 ID만으로 label을 합성하지 않도록 최종 검토에서 보수적으로 조정.
- Tree/window: 획득한 query 결과·큐·root·window·handle의 release 경로 확인. query 도중 예외가 나도 아직 처리하지 않은 반환 node들을 finally에서 해제. snapshot 밖으로 Android node를 보관하지 않음. parent visibility와 child traversal 분리. 큰 결과는 partial로 사용.
- Gesture: accepted/completed/cancelled 구분, 3초 callback deadline. 현재 snapshot에서만 bounds/parent를 사용하고 실패 후에는 새 observation. callback에서 window 재검사. overlay 이동은 입력 성공/시도로 세지 않음.
- OCR: 소유권을 hardware buffer → hardware bitmap → software bitmap → ML Kit 완료 callback 순서로 분리. buffer/bitmap 해제, worker 외부 OCR 금지. Stop은 ticket을 즉시 무효화하고 사용 중 이미지 수명은 task 완료까지 유지. 닫힌 screenshot 응답은 buffer만 해제. dispose 중복 방지. 외부 ML Kit task가 영원히 완료되지 않는 OS/library 장애는 앱에서 강제 완료시킬 수 없으며 제어 흐름은 timeout pause.
- Freshness: event마다 OCR ticket을 버리지 않음. generation/cycle/state/action epoch/window/package/frame age/geometry를 검사. 화면 공유 timebase 변환과 surface 재연결 없음.
- CPU/메모리: 2,000 node/80ms 일반 traversal, 제한된 우선 query 수, OCR 한 이미지/최대 400행, 16MP 캡처 처리 상한, 최근 로그 120개. screenshot 복사와 ML Kit는 worker. OS binder 호출 자체가 지연되는 경우 강제 중단 보장은 없음.
- Privacy: log에서 원문 화면 텍스트/광고 제목을 제거. 화면/이미지 저장 및 네트워크 권한 없음. 진단은 앱 package/window/bounds/상태와 오류 정보.
- UI: 이름, footer, 설정/반복/조작창을 유지. MediaProjection 권한 화면만 공식 accessibility screenshot 방식에 맞게 수정. 준비 실패 후 사용 시작으로 OCR 초기화 재시도 가능.

## 테스트 구성

- 기존 Engine/Semantic/OcrTicket 회귀 유지. V3와 반대였던 points-only 금지 및 fingerprint 변화 무조건 거부 기대값만 새 명세에 맞게 갱신.
- UniversalTest: A–H, K–Z, 반복 1/10/100/계속, 대기 중 BACK 거부, gesture stale/timeout, history 대체 특징, description/hierarchy/광고 parent.
- TreeWalkTest: I/J, 600개 초과, 최대 예산 초과 partial, 보이지 않는 부모, deadline 시 큐 해제.
- ObservationGateTest: 실제 adapter가 호출하는 merge/window/failure 경계. S/T/U/V/K/Q, Accessibility+OCR 병합, 무관한 문구 변경, resize, stale frame.
- 가상 시계 테스트의 최초 실패는 deadline 전에 두 번째 node 방문이 가능했던 테스트 시각 배열 문제. 입력을 정확한 deadline으로 수정하고 partial/큐 해제 assertion은 그대로 유지.
- 실기기/에뮬레이터/connectedAndroidTest는 실행하지 않음. 실제 제조사 클릭, window geometry, ML Kit 속도/정확도는 beta 기기 검증으로 남음.

자체 점검: 광고 anchor 지연이나 600개 tree 제한만으로 이미 찾은 points를 거부하는 의존성은 제거했습니다. 다만 실제 target을 OS/OCR 어느 쪽에서도 찾을 수 없거나 모호한 화면은 클릭하지 않습니다. Android 요청 수락 또는 gesture 완료만으로 내역 진입 성공을 세는 경로는 없습니다.
