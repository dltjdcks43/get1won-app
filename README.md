# 1원 받기 V2

V1 전체 작업은 `archive/v1-before-v2` 브랜치에 보존했습니다. V2는 기존 실행 구조를 재사용하지 않고 관측/의미 분석/전환 확인으로 나눴습니다.

## 구조

- `Semantic`: 한 application window의 immutable 관측. 공백 정규화, 의미 토큰, 가까운 분할 문장 결합, 겹치는 Accessibility/OCR 관측 병합. 멀리 떨어진 중복은 모호성 유지.
- `Engine`: HOME → 광고 진입 확인 → 완료 대기 → HOME → 내역 → HOME. 플랫폼의 요청 수락과 화면 전환을 분리합니다. Android node/광고 위치를 저장하지 않습니다.
- `AutomationService`: foreground TYPE_APPLICATION만 읽고 overlay/자기 앱은 제외. Accessibility 이벤트 + 400ms 보조 확인, 이벤트 폭주 시 분석 간격 최소 300ms. 트리 최대 600개. 관측 후 즉시 동작, Android node 참조는 해당 관측에서만 유효.
- `CaptureService`: 필요한 의미 정보가 없을 때만 번들 한국어 ML Kit. 동일 정지 화면 1회와 상태당 5초 후 재확인 1회. 화면 변경은 새 요청 가능. 한 번에 OCR 1개, 요청 간격 최소 500ms. MediaProjection 화면을 파일/서버에 저장하지 않습니다.
- `OcrTicket`: generation/cycle/state/revision/window/package/새 root fingerprint/프레임 생성 시각을 검증해 오래된 콜백을 폐기합니다.
- `Diagnostics`: 마지막 state/cycleId/action/semantic 결과와 Java stack, Android의 과거 process exit/ANR trace를 앱 내부에 보관합니다. 고급 설정의 마지막 오류 보기에서 읽습니다.

광고 anchor는 `구경` + `1원` + `받`이며 가까운 세로 분할 문장을 합칩니다. 제목/상품명/금액은 타겟 선택 기준이 아닙니다. anchor 아래 가까운 가로형 콘텐츠를 찾고 clickable row를 우선합니다. 접근성이 부족하면 OCR 제목 bounds를 사용합니다. 고정 화면 좌표나 테스트 ID는 사용하지 않습니다.

클릭은 ACTION_CLICK 우선, 그 외 현재 bounds의 80ms gesture입니다. HOME이 1초 후 그대로면 새 관측으로만 최대 3회 시도합니다. 3초+구경이 있는 동안 BACK 금지. 1원+받았 완료를 유효하게 관측하면 추가 대기 없이 BACK합니다. 30초 timeout은 PAUSED이며 BACK 조건이 아닙니다. 다음 cycle은 이전 관측을 버린 뒤 새 스캔에서 시작합니다.

실행 전 일반 조작창, 실행 중 가장자리의 작은 실행 중/중지 창을 사용합니다. target과 겹치면 이동하고 다시 관측합니다. OCR마다 hide/show하지 않습니다. 서비스 종료는 서로 stop을 재호출하지 않습니다.

## 사용

접근성 설정 → 사용 시작/화면 확인 허용 → 대상 앱을 직접 열기 → 조작창 시작. 중지는 자동화를 멈춥니다. 화면 확인 종료는 알림 또는 고급 설정에서 가능합니다. 시작한 앱을 벗어나면 일시정지합니다.

## 검증

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

별도 `fixture` 앱은 자연스러운 TextView/Canvas/클릭 행만 노출합니다. 광고 제목·이미지·금액·문장을 매 cycle 변경하며, 2번째는 분할 anchor와 Canvas 제목으로 OCR fallback을 요구합니다. 자동화 APK에는 fixture 코드가 포함되지 않습니다.

기기가 연결되면 다음 명령은 딱 3cycle의 실제 Android UI 흐름을 검증합니다. 테스트 fixture의 내부 audit 파일은 테스트 assertion에서만 읽으며 자동화 엔진은 접근하지 않습니다.

```sh
./gradlew :fixture:installDebug :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.get1won.ThreeCycleTest
```

API matrix와 50/100회 테스트는 제거했습니다. `.github/workflows/three-cycles.yml`은 수동 실행 전용입니다. JVM의 3cycle 상태 전환 테스트는 실제 폰/gesture/OCR 검증을 대체하지 않습니다. 실기기 연결이 없으면 3cycle 및 crash/ANR 0회는 미검증입니다.
