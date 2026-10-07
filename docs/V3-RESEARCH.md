# V3 소스 조사 / 라이선스 결정

2026-10-07 실제 원본 저장소를 shallow clone하여 다음 파일과 라이선스/README를 읽었습니다. **아래 프로젝트의 소스 코드는 직접 복사하지 않았고 runtime dependency도 추가하지 않았습니다.** 채택한 것은 아래의 설계 패턴이며 앱 코드로 독립 구현했습니다. Auto.js/AutoX 코드는 사용하지 않았습니다.

| 프로젝트 / 고정 revision | 읽은 구현 | 판단과 V3 적용 |
|---|---|---|
| [Orb Eye](https://github.com/KarryViber/orb-eye/tree/9700f6db1db8b7ceab4992a464747162330fae65) | `app/src/main/java/com/orb/eye/OrbAccessibilityService.java`: nodeToJson, text/description/resourceId selector, handleClick/findNodeByDesc/findNodeByBounds, handleWait, handleScreenshot | selector와 clickable parent 패턴을 Semantic/AutomationService에 독립 적용. README MIT 표시는 확인했지만 별도 LICENSE 파일은 확인되지 않아 직접 복사하지 않음. HTTP/base64, blocking latch, 요청 수락만 반환하는 click, null gesture callback은 채택하지 않음. screenshot buffer 수명도 그대로 따르지 않음. |
| [jev-android](https://github.com/dougsong/jev-android/tree/be8364ad1a7596172f8cd79c70d2462f4dd0f4dc) | `sdk/src/main/kotlin/io/github/jevandroid/AccessibilityRuntime.kt`: fresh capture/executeWithResult, gesture callbacks, window/occlusion, bounded traversal. `core`의 `JevAgent.kt` 및 `ProgressTracker.kt`: action 후 관찰과 UI_CHANGED/NO_VISIBLE_CHANGE 구분 | LICENSE MIT 확인. 요청 수락/gesture 완료/화면 목표 달성을 분리하는 패턴, fresh target과 bounded retry를 Engine/ObservationGate에 적용. 전체 fingerprint 동일성 강제, invisible-parent pruning, provider/network SDK는 채택하지 않음. |
| [Shizuku API](https://github.com/RikkaApps/Shizuku-API/tree/a27f6e4151ba7b39965ca47edb2bf0aeed7102e5) | `api/src/main/java/rikka/shizuku/Shizuku.java`: bindUserService, service binder/permission 경계. README의 shell/root 및 부팅 후 시작 요구 | LICENSE MIT 확인. shell/root fallback은 향후 선택적 가능성만 검토. 이번 설치→접근성 UX와 권한/세션 비용에 맞지 않아 dependency 제외. |
| [Maestro](https://github.com/mobile-dev-inc/Maestro/tree/60f9ba47b9df8a7508f3e663b7defd17d754c7ec) | `maestro-client/src/main/java/maestro/Maestro.kt`: waitUntilVisible, findElementWithTimeout, waitForAppToSettle | LICENSE Apache-2.0 확인. 새 hierarchy를 반복 관찰하며 deadline 안에서 조건을 만족시키는 패턴을 적용. runtime/driver와 고정 delay loop는 앱에 넣지 않음. |
| [uiautomator2](https://github.com/openatx/uiautomator2/tree/6de6d4ce6e944998544e3f71aab5dbb31bd663f8) | `uiautomator2/_selector.py`: click/must_wait/current visibleBounds, wait, click_gone | LICENSE MIT 확인. target 재탐색 후 최신 bounds 사용 및 제한된 재시도 패턴 적용. ADB/RPC/Python runtime와 click-until-gone는 제외. |

직접 재사용 코드가 없어 위 프로젝트의 라이선스를 앱 코드에 적용하거나 복제 코드 NOTICE를 만들지는 않았습니다. 기존 Android/ML Kit/JUnit 의존성은 그대로 유지합니다.

## Android 공식 근거

- [AccessibilityService](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService): API34 `takeScreenshotOfWindow`, metadata `canTakeScreenshot`, 접근성 overlay가 target 캡처를 덮지 않는 기능, 보안 window 오류.
- [ScreenshotResult](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService.ScreenshotResult): timestamp는 uptimeMillis 기준이고 HardwareBuffer는 사용 후 close해야 함. V2의 nanoTime 변환 제거.
- [GestureResultCallback](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService.GestureResultCallback): completed/cancelled가 제공되므로 반환값과 분리.
- [Android 14 AccessibilityInteractionController](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-14.0.0_r1/core/java/android/view/AccessibilityInteractionController.java): `takeScreenshotOfWindowUiThread`가 대상 ViewRoot surface의 layers를 캡처하고 secure flag를 거부하는 구현을 실제 확인. 캡처가 full-display라고 가정하지 않음. window 크기와 이미지가 일치할 때만 window origin을 더하여 OCR 좌표를 변환하며 다른 기하 조건은 거부. 확대/특수 inset 변환을 추측하지 않음.

원본들이 모든 요구를 완성형으로 해결한다고 판단하지 않았습니다. 특히 이벤트 도착/일반 화면 변화는 포인트 내역 진입이라는 업무 목표의 성공과 같지 않으므로 V3는 고유한 semantic 상태 검증을 유지합니다.
