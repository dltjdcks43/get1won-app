# V3 beta2: v2.2 기준 회귀 복구

기준은 main의 `0a84f8b2ed6d34155f2c80af821802ba06e83c47`, 비교 대상은 V3 beta1 `2ea51eb0`입니다. main 및 이전 APK를 변경하지 않았습니다.

## 확정한 코드 회귀와 검증 범위

v2.2의 Semantic/Engine 원본을 별도 Java 비교 프로그램으로 실행하여 같은 화면 입력에 대한 광고 결정을 비교했습니다.

| 입력 조건 | v2.2 | beta1 | beta2 |
|---|---|---|---|
| 내 포인트와 잔액이 같은 text | 광고 결정 | points 없음 | 광고 결정 |
| 안내 문구가 여러 description 줄로 분리 | 광고 결정 | anchor 없음 | 광고 결정 |
| 광고 title text + description의 1원 | 광고 결정 | ad 없음 | 광고 결정 |

이들은 실제 코드에서 재현되는 회귀입니다. 사용자가 제공한 실기기 증상만으로 어떤 입력 형태가 해당 휴대폰에서 발생했는지까지 확정할 수는 없습니다. 실기기 Accessibility dump/OCR 로그를 확보하지 않았고 물리 터치를 검증하지 않았습니다. 컴파일 성공을 기기 동작 성공으로 보고하지 않습니다.

## 최소 변경

- Semantic: v2.2의 text 우선/빈 text에서 description fallback을 광고 제외·제목 판정과 줄 결합에 복구. points는 단독 label 또는 label 뒤 잔액만 허용하여 출금/확인/알림 메뉴는 제외.
- 같은 UI의 부모 description과 자식 label이 중복 후보가 되는 경우 실제 ancestor chain과 포함 bounds로 부모 후보를 제거. 같은 부모 아래 서로 다른 두 label은 여전히 ambiguous. priority-query 복제 node도 원래 bounds/label/description/ID 정보로 같은 ancestor로 처리.
- Engine HOME: v2.2의 `home && ad != null` 광고 결정 조건 복구. 적립 waiting 상태에서 BACK 금지 및 실제 완료 확인 원칙은 유지.
- AutomationService: v2.2처럼 active/focused application window를 선택. 광고 clickable parent 선택은 테스트로 기존 동작을 확인했으며 재설계하지 않음.
- ObservationGate/Engine: package는 고정하되 동일 package의 새 window는 허용. generation/action epoch 증가, 예약/진행 중 gesture 무효화, pending OCR 및 ID 캐시 폐기. 이전 Android node는 기존 snapshot scope에서 해제. 새 window snapshot에서만 다음 행동 결정.
- 동작 직전에 window가 바뀌면 기존 target을 실행하지 않고 재관찰. 늦은 gesture/OCR 응답이나 다시 돌아온 예전 window ID가 오래된 결과를 되살리지 않음. 다른 package는 pause. 같은 package라도 기대한 화면이 30초 내 나타나지 않으면 기존 timeout으로 pause.
- HOME 고급 로그: points/anchor/ad, source/bounds, text/description 선택, 의미 문구 일부, 후보 모호성, 광고 탈락 사유별 수. 숫자/잔액/광고 제목은 생략. screenshot 크기 불일치 로그에 실제 image 크기와 window bounds 추가. OCR 알고리즘/모델/좌표 변환은 변경하지 않음.

## 단계별 검증

V22RegressionTest는 다음 단계를 순서대로 3 modeled cycles 검증합니다. 이는 **기기 테스트가 아닙니다**.

1. HOME points/anchor/ad 발견.
2. 광고 입력 요청 생성 및 gesture 제출. 아직 성공 카운트 0.
3. 같은 package의 다른 window 허용, 과거 gesture callback 무효화, 새 waiting 관찰로 광고 진입 확인.
4. 완료 관찰 후 BACK 요청, 실제 홈 복귀로만 복귀 성공.
5. 광고 anchor 없이 points 발견/클릭 요청. 아직 내역 진입 성공 아님.
6. 새로운 window의 복수 내역 특징 확인 후에만 내역 성공 및 BACK.
7. 새 홈 복귀로 cycle 증가, 다음 광고 결정. 정확히 3 cycle 종료.

추가 회귀: 부모/자식 중복, 실제 distant duplicate 거부, description-only 다른 1원 이벤트 제외, 광고 clickable container, HOME 내역 요약 때문에 광고를 막지 않음, HOME 상세 로그, window ID 재사용 시 옛 OCR 거부, 예약 action 무효화. 기존 단위 테스트는 유지했고 기존 "같은 앱 다른 window면 pause" 기대만 새 사용자 요구대로 변경했습니다.

실기기에서 남은 확인은 첫 광고의 실제 터치/OS 전환, 실제 screenshot geometry와 OCR 속도/정확도, 실제 앱의 광고→내역→다음 cycle입니다. beta2는 이 검증을 위한 수정 APK입니다.
