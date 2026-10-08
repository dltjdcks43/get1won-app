# 1원 받기 3.0-beta4

Android 14/API 34 이상. `v3-universal` 실험 브랜치이며 main/v2.2에 병합하지 않습니다. versionCode 12. 앱 이름과 제작자 표시, 반복 설정, 조작창 디자인은 유지합니다.

## 사용

접근성을 켠 뒤 **사용 시작** → 대상 앱을 직접 열기 → 조작창 **시작**. 시작 시 foreground application package를 고정합니다. 같은 앱의 새 window는 이전 입력/OCR을 무효화하고 새로 읽습니다. 화면 공유 동의는 필요하지 않습니다. 반복은 1/10/100/계속(0)이며 중지는 session과 이후 callback을 무효화합니다.

설정 → 고급 설정 → 최근 동작 로그에서 state, cycle/completed, target package/window, node 수/부분 탐색, points/anchor/ad/waiting/completion/history, 인식 source/bounds, screenshot/OCR, 클릭 accepted, gesture submitted/completed/cancelled, BACK 요청과 실제 화면 확인, 재시도/중지 원인/마지막 성공 단계를 확인할 수 있습니다. APK 실제 버전과 코드, debug Git SHA도 표시합니다.

## 구조와 실제 확인

- `AutomationService`: Android 접근성 adapter. 화면 관찰과 Engine 변경, 입력 dispatch/callback은 main thread에서 직렬 실행합니다.
- `Semantic`: text 및 contentDescription, 실제 hierarchy/clickable parent와 현재 bounds를 사용합니다. `내 포인트` 단독 label, label+잔액, label+잔액+출금은 허용하며 출금/확인/알림 메뉴와 다른 이벤트 문구는 제외합니다. resourceId는 같은 session에서 정확한 label과 함께 관찰한 ID만 우선 조회에 사용합니다. ID만으로 사라진 label을 만들어내지 않고 text/description 또는 OCR의 현재 의미를 확인합니다.
- `TreeWalk`: points 우선 platform text query 후 일반 BFS를 최대 2,000 node/80ms로 제한합니다. 제한까지 얻은 결과는 유지하고 비가시 부모 아래 자식도 탐색합니다. 이 한계 밖에 있는 target은 OCR fallback으로 찾습니다. 한 번의 OS binder 호출 소요시간까지 앱이 보장할 수는 없습니다.
- `Engine`: action 예약 ID로 중복 dispatch를 막습니다. Android 요청 수락은 성공 카운트가 아닙니다. gesture callback도 입력 완료 여부만 나타내며 **기대한 다음 화면**이 확인되어야 성공입니다.
- `ObservationGate`/`OcrTicket`: generation/cycle/state/action epoch/package/window/현재 bounds와 screenshot 나이를 확인합니다. 무관한 접근성 이벤트나 전체 트리 fingerprint 변화는 OCR을 무효화하지 않습니다.
- `WindowOcr`: 필요한 의미 정보가 없을 때만 `takeScreenshotOfWindow` + 번들 한국어 ML Kit를 사용합니다. 변환/OCR은 별도 worker, 한 이미지씩 처리합니다. **MediaProjection, VirtualDisplay, ImageReader, surface detach/attach 및 timestamp timebase 변환은 제거**했습니다.

```
HOME (points + anchor + 광고)
 → AD_ENTRY (실제 광고 진입 기다림)
 → REWARD (waiting은 BACK 거부, 실제 완료만 BACK)
 → WAIT_FOR_POINTS (광고 anchor와 무관하게 points 탐색)
 → POINTS_ENTRY (요청 성공이 아닌 실제 내역 기다림)
 → HISTORY (복수 의미 특징 + 서로 다른 transaction 2행)
 → HOME_AFTER_HISTORY (points 홈 복귀 확인)
 → completed/cycle 증가, 시도 초기화 → HOME
```

적립 후 points만 먼저 보이면 바로 points 단계로 진행합니다. 내역 복귀 후에도 points만으로 cycle을 마치고 새 HOME에서 광고의 늦은 렌더링을 기다립니다. 광고 제목/휴대전화 절대 좌표는 하드코딩하지 않습니다. 기존 anchor 아래 광고 탐색과 다른 1원 이벤트 제외 규칙을 유지합니다.

첫 시도는 clickable node `ACTION_CLICK`, 거부되거나 화면이 바뀌지 않으면 **새 관찰에서** gesture fallback을 사용합니다. 내역 대기 중 points만 남고 HOME/내역 판정이 불완전하면 재클릭 전에 OCR로 내역 증거를 보충합니다. 클릭은 최대 3회, 각 단계는 최대 30초입니다. 재시도는 1초 응답 관찰 기회를 둡니다. 실제 다음 화면은 즉시 받아들이며 sleep으로 진행을 강제하지 않습니다. 보조 관찰 250ms, OCR 요청 간격 최소 500ms는 관찰 부하 제한이지 성공 조건이 아닙니다. gesture 응답은 3초, screenshot/OCR 응답은 8초까지 기다린 후 중지합니다. 타임아웃은 BACK/다음 cycle을 발생시키지 않습니다.

## 범위 및 제한

- INTERNET 권한 없음. 이미지/원문 OCR/포인트 잔액을 저장하거나 전송하지 않습니다. 진단에는 상태·bounds·앱/window 식별정보만 포함하며 최근 상태/오류는 앱 내부에 보관합니다.
- 공식 screenshot API는 접근성 overlay 아래 target window를 캡처합니다. 직접 gesture는 조작창과 겹치면 창을 옮기고 새 화면에서 다시 target을 구합니다.
- screenshot 크기가 현재 window bounds와 다르거나 캡처 중 크기가 달라지면 좌표를 추측하지 않고 폐기합니다. 특수 surface/insets, magnification, 제조사 차이는 실기기 확인이 필요합니다.
- 다른 application은 pause합니다. 같은 앱의 window 변경은 허용하되 이전 snapshot/target/action/OCR을 버리고 새 window에서 다시 분석합니다. 동작 직전에 window가 바뀌어도 오래된 좌표를 누르지 않습니다. 시스템 활성 overlay는 관찰을 보류하며 자체 accessibility overlay는 target 선택에서 제외합니다.
- 보안 window, 접근성 정보를 숨기는 앱, OCR 오인식/실패, 두 개의 실제 동명 target, 빈 내역/한 행뿐인 내역은 안전하게 중지될 수 있습니다. 모든 Android 앱/제조사에서 성공을 보장하지 않습니다.
- 실기기 ADB, connectedAndroidTest 및 실제 3-cycle 검증은 이번 beta에서 **실시하지 않았습니다**. unit/state/semantic 테스트는 OS 입력·렌더링 자체의 검증을 대신하지 않습니다.

## 검증

`./gradlew testDebugUnitTest lintDebug assembleDebug`

`EngineTest`, `SemanticTest`, `OcrTicketTest`의 기존 회귀와 `UniversalTest`, `TreeWalkTest`, `ObservationGateTest`의 A–Z 모델 검증을 수행합니다. 구버전의 “points만 있으면 클릭 금지” 테스트는 V3 요구에 맞게 반대로 검증하며 삭제하지 않았습니다. `ThreeCycleTest`는 window 캡처 흐름에 맞게 갱신하되 이번에는 실행하지 않습니다.

조사 출처·라이선스·채택/배제 근거는 [V3 연구 기록](docs/V3-RESEARCH.md), 코드 검토는 [리뷰 기록](docs/V3-REVIEW.md)을 참조하세요. CI는 unit/lint/build만 실행하며 debug APK artifact 이름은 `get1won-v3-<commit8>-debug`입니다. 로컬 APK와 CI APK는 debug 서명 키가 다를 수 있습니다.

## beta2 회귀 복구

v2.2 원본과 동일 입력으로 첫 광고 결정 회귀를 재현했습니다. text 우선/빈 text에서 description fallback, description 줄 결합, points+잔액, HOME 광고 결정 조건을 복구했습니다. 같은 UI의 부모/자식 의미 중복은 실제 hierarchy로 제거하되 서로 떨어진 복수 label은 계속 ambiguous로 처리합니다. 고급 로그는 탈락 원인 및 selector/bounds를 표시하며 잔액·광고 제목은 생략합니다. 자세한 비교 및 검증 범위는 [beta2 회귀 기록](docs/V3-BETA2-REGRESSION.md)을 참고하세요.

## beta3 첫 광고 진입 수정

포인트 잔액 뒤 출금이 합쳐진 label을 허용하고, 같은 영역의 중복 및 메뉴 문맥을 검사합니다. 광고 클릭은 실제 hierarchy의 광고 카드 조건을 만족하는 부모를 사용합니다. 첫 요청 뒤 points가 누락되어도 fresh anchor+ad가 남아 있고 내역/대기/완료와 모순되지 않으면 1초 후 새 대상에 gesture fallback을 시도합니다. 성공은 실제 광고 대기/완료 화면으로만 확인합니다. 요청 대상 ID/부모 체인/bounds와 1초 후 관찰을 고급 로그에 남깁니다. [beta3 조사·인수인계](docs/V3-BETA3-REGRESSION.md)를 참고하세요. 실기기 검증은 아직 수행하지 않았습니다.

## beta4 HOME anchor 재인식

beta3에서 실제 5 cycle 완료 후 HOME anchor를 놓친 보고를 기준으로, anchor만 같은 출처의 인접한 2~3개 짧은 문구를 결합합니다. 가로/세로 거리·겹침·높이를 제한하고 먼 후보는 합치지 않습니다. 완료 문구, 광고 입력, BACK, cycle/window 처리는 유지합니다. 고급 설정의 **마지막 중단 요약**은 버전·중단 원인·인식 출처·OCR·전환을 11줄로 보존합니다. [beta4 기록](docs/V3-BETA4-REGRESSION.md)을 참고하세요.
