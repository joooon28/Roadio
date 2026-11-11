# DEV-14 반경 진입 시 이야기 자동 발견과 듣기 제안

- 날짜: 2026-11-09
- 상태: Pleos AVD에서 가상 GPS 이동 후 실제 모바일 캡슐의 자동 듣기 제안 확인. DEV-14 제안 경로의 청취와 주행 제한 전환 실측은 남아 있다.

## 문제와 선택

엄마와 아이가 아빠의 퇴근길에 남긴 음성은 아빠가 앱을 검색할 타이밍을 알아야만 찾을 수 있었다. 차량 위치를 받는 순간을 발견에 사용하고, 앱이 이야기 제안을 보여줄 수 있는 조건에서는 듣기 또는 건너뛰기를 선택하도록 설계했다. 음성은 듣기를 선택한 뒤 시작한다.

- 기존 수동 검색은 위치나 서버 연결을 점검하는 데 유용하므로 유지했다. 자동 발견은 Pleos 위치에서 작동하며, 앱에 직접 입력한 시연 좌표는 기존 수동 경로로 분리했다.
- 새 지오펜스 목록 API와 위치 데이터 배포 대신 기존 서버의 주변 검색을 사용했다. 서버가 100m 반경과 접근 권한·만료를 판정하고, 앱은 FusedLocation으로 받은 좌표가 바뀌면 순차 조회한다. 앱이 전면에서 활성화된 동안만 요청하고 성공한 같은 좌표는 반복 요청하지 않는다. 응답 지연에 더해 조회 사이에 2초를 두므로 아주 짧은 통과는 놓칠 수 있다.
- 서버가 돌려준 이야기 중 한 건을 연결 세션 메모리에 보관한다. 범위를 지나도 만료 전에는 제안을 유지하며, 처리한 이야기는 해당 세션에서 반복 제안하지 않는다. 계정·서버·위치 출처가 바뀌면 제안 상태를 초기화한다. 다운로드 시 서버가 접근 권한과 삭제·만료를 다시 확인한다.
- 공식 주행 제한 신호를 읽어 제한 중이거나 확인 불가하면 화면 조작과 제안을 차단한다. 화면이 다시 사용 가능해지고 앱이 활성화되면 보관 중인 제안을 표시할 수 있다. 차량 플랫폼이 앱을 백그라운드로 보내면 위치 변경에 따른 조회도 멈춘다. 현 앱의 전면 실행 범위에 맞춘 방식이며 백그라운드 발견과 앱 재시작 후 보관은 별도 범위다.

## 결과와 재현 방법

- Android Studio에서 `:app:assembleDebug` 성공, Pleos Connect v2.1.0 API 34 AVD에 설치 및 P·0 km/h 상태에서 앱 화면 표시 확인.
- Kotlin/Compose 컴파일러로 변경 코드 확인 후 JUnit 10개 통과. DEV-14의 3개 테스트는 빈 결과→발견→반경 이탈 후 제안 유지, 중복 억제와 다른 이야기 선택, 만료 시 제외를 검증한다.
- 초기 자동화 환경에서는 ADB 소켓이 차단되어 좌표 주입과 주행 제한 전환을 실행하지 못했다. 이후 사용자 환경에서 가상 GPS를 옮겨 자동 제안 표시를 확인했다. DEV-02에서 이 AVD 이미지의 가상 GPS 변경과 SDK 콜백은 확인했지만, 당시 주행 제한 명령은 `Can't find service: hmg_car_service`로 실패했다.
- **실행 데이터:** 사용자가 iPhone에서 녹음한 `위치 확인 테스트`를 Railway에 등록했다. 서버 DB를 읽기 전용으로 조회해 캡슐이 활성 상태이고 공개 범위가 `내 이웃(SELECTED)`임을 확인했다.
- **시연 절차:** 등록 핀 북쪽 약 250m를 출발점으로 잡고, 차량 앱에서 `Pleos 위치 확인`으로 위치 수신을 시작한 뒤 `adb emu geo fix <경도> <위도>`로 등록 핀까지 이동한다. 차량 앱의 수동 좌표 입력과 `주변 이야기 검색`은 사용하지 않는다. 정확한 핀 좌표와 계정 정보는 저장소 기록에서 제외한다.
- **관찰 결과:** 사용자가 등록 핀으로 가상 GPS를 옮긴 뒤 `위치 확인 테스트`의 `듣기 / 건너뛰기` 제안이 자동으로 떴다고 확인했다. 반경 밖에서 제안이 없었는지는 별도 관찰 기록이 없다.
- **남은 수동 검증:** 제안이 뜰 때 소리가 나지 않는지, `듣기`를 누른 뒤 해당 iPhone 녹음이 들리는지 확인한다. 반경 이탈 후 제안 유지 및 건너뛰기 후 재제안 억제는 단위 테스트에서 확인했고 AVD 수동 검증은 남아 있다.
- 주행 제한은 GPS 이동과 별도로 Virtual Console의 기어 전환 또는 공식 ADB 명령을 사용한다. 실제 제한이 활성화됐는지 확인한 뒤 제안과 터치 조작이 차단되는지, 제한 해제 후 대기 중인 제안이 표시되는지 관찰한다. 제한 전환에 실패하면 주행 상태 검증은 미완료로 남긴다.
- 실제 차량의 주행 중 제안과 터치, 실차 오디오, Pleos 배포 권한은 미검증이다. 사용성 과업에서는 제안이 왜 떴는지와 버튼을 누를 때만 소리가 난다는 점을 이해하는지 확인한다. 재사용 의사나 사용자 반응은 아직 측정하지 않았다.

## 내비게이션 동시 표시 선행 확인 (2026-11-10)

- Pleos 공식 문서는 고정 Driving View 옆 앱 영역에 최대 두 앱을 표시하며, 기본 내비게이션과 다른 앱의 동시 배치를 지원한다고 설명한다. 세로 앱은 한 창을 사용한다. 시안의 `Driving View | 내비게이션 | Roadio`는 문서상 가능한 화면 모델이다. [앱 구조](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/app-structure-screen-model), [창 정책](https://document.pleos.ai/en/docs/connect/guide/docs-design/getting-started/window)
- Roadio `MainActivity`는 `screenOrientation`을 지정하지 않아 문서상 기본 세로 창 대상이다. 현재 UI는 앱 창 안의 스크롤 화면이며 실제 분할 창에서의 가독성은 확인되지 않았다.
- 정상 권한으로 실행한 `adb devices -l`에 연결된 기기가 없었다. 따라서 실제 AVD 동시 표시, 창 순서·크기, 경로 안내 유지 여부는 이번에 관찰하지 못했다.
- **AVD 확인 절차:** Pleos AVD에서 기본 내비게이션을 띄운 뒤 앱 라이브러리에서 Roadio를 연다. 필요하면 창 막대로 내비게이션을 가운데, Roadio를 오른쪽에 배치한다. Driving View와 두 앱, 경로·회전 안내 및 Roadio의 `듣기 / 건너뛰기`가 함께 보이는지 캡처하고, 창 전환 뒤 위치 조회가 유지되는지 확인한다.
- **선택 재생:** DEV-13의 수동 검색→선택 재생과 사용자 청취 보고는 확인됐다. DEV-14 자동 제안에서 `듣기` 후 같은 녹음의 청취, 제안 시 무음, 내비게이션 안내와의 오디오 포커스는 별도 미검증이다.
- **주행 제한:** P·0 km/h 표시와 제한 상태 코드 경로는 확인됐다. Virtual Console 또는 공식 ADB 명령으로 제한 활성화를 확인한 뒤 제안·터치 차단과 해제 후 복귀를 관찰해야 한다. 이전 `hmg_car_service` 명령 실패로 주행 제한 동작을 판정할 수 없다.

### AVD 실측 결과와 정정 (2026-11-11)

- Pleos AVD 연결 후 Activity/Window 상태와 2560×1440 화면을 직접 확인했다. Driving View는 `Rect(0, 0–853, 1300)`, Navigation과 Roadio는 모두 `Rect(853, 90–2535, 1300)`이었다.
- Roadio가 보일 때 Navigation 태스크는 존재하지만 `visible=false`, `STOPPED`였다. 두 앱이 같은 오른쪽 슬롯을 공유하므로 현재 에뮬레이터에서 `Driving View | Navigation | Roadio`를 동시에 표시할 수 없었다. 앞 절의 “문서상 가능한 화면 모델”은 듀얼 Application Window 폼팩터의 가능성으로 한정하며 이 AVD의 관찰 결과로 사용하지 않는다.
- 왼쪽 Driving View는 일반 앱 슬롯이 아니라 Pleos 홈이 관리하는 시스템 영역이다. 일반 Roadio 앱이 Navigation을 그 영역으로 옮기거나 Task Organizer 배치를 강제하는 공개 권한은 확인되지 않았다.
- 시스템 Navigation 전면 상태에서 Roadio가 자동 발견을 계속하려면 현재 `repeatOnLifecycle(RESUMED)` 기반 조회를 foreground/background service로 옮겨야 한다. 발견 시 시스템 알림을 게시하고 `듣기 / 건너뛰기` action을 제공하는 안을 후속 범위로 둔다. 일반 Toast보다 시스템 알림이 상태와 action에 적합하지만 주행 중 heads-up 표시는 Pleos 주의 분산 심사 정책 검토가 필요하다.
- NaviHelper는 목적지, 경유지, 경로 옵션과 재탐색을 지원한다. 이는 경로 요청 기능이며 차량 GPS를 경로 위로 이동시키는 기능은 아니다. 가상 주행 시연은 Android Emulator의 위치 좌표를 시간순으로 주입하는 별도 절차로 구분한다.

## 공식 근거

2026-11-09 확인. 공식 문서는 차량 위치 콜백과 주행 제한 리스너를 제공한다. Roadio의 FusedLocation 1.1.3은 기존 AVD 기술 검증 범위에서 사용했다. Roadio는 Distraction Optimization을 선언하지 않으며 Pleos 공식 정책상 이 상태의 앱은 주행 중 사용이 제한된다.

- [Pleos FusedLocation SDK](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/FusedLocation/intro)
- [Pleos 주행 규제 개발 가이드](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide)
- [Pleos 지역별 주행 규제 정책](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/driving-restiction-policy)
- [Pleos Virtual Console 설정](https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/use-vehicle-emulator)
- [Pleos 주행 제한 ADB 명령](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-developer-guide/adb-command-reference-guide)

## 로컬 촬영용 자동 연결 (2026-11-10)

- **문제와 선택:** 반복 촬영마다 주소와 차량 토큰을 입력하면 화면에 개발 설정이 드러나고 촬영 준비가 길어진다. Git에서 제외한 `local.properties`에 기존 차량 시연 토큰을 한 번 설정하고, 촬영용 디버그 APK가 기본 Railway 주소를 사용하도록 했다. 별도 공개 샘플과 심사용 서버 권한은 초기 대안에서 제외했다.
- **촬영 화면:** 초기 촬영용 빌드는 서버 주소·토큰과 수동 좌표 입력을 숨기는 방향으로 설계했다. 촬영 편의를 위해 최종 소스에서 수동 좌표 입력을 다시 제공하고 서버 주소·토큰 입력만 숨긴다. 화면 조작이 허용되면 Pleos 위치 확인을 시작한다. 일반 개발 화면은 수동 좌표로 서버 조회를 점검할 수 있다.
- **빌드 오류와 조치:** Gradle 동기화 중 nullable 빌드 필드 접근 오류를 디버그 빌드 타입에서 필드를 설정하도록 수정했다. 이후 DEX 병합에서 동일한 Pleos `R$string`을 포함한 생성 JAR 두 개를 확인했다. 차량 모듈의 생성 산출물을 임시 폴더로 옮긴 뒤 Android Studio에서 빌드에 성공했다.
- **설치·관찰 결과:** AVD에는 이전 버전이 남아 있었고, 새 APK에 Railway 주소와 촬영 설정이 포함된 것을 확인했다. Android Studio가 생성한 테스트용 APK를 `adb install -r -t`로 직접 설치해 차량 앱을 실행했다. 주소나 토큰을 입력하지 않고 `근처에서 이야기를 발견했어요`와 `듣기 / 건너뛰기`가 나타난 것을 UI 계층에서 확인했다. 이 관찰은 수동 좌표 입력 영역을 숨기는 후속 변경 전 빌드에서 수행했다.
- **재현 과업과 한계:** 새 촬영 APK에서 앱의 좌표 입력으로 반경 밖→안 이동 시 자동 제안과 청취를 확인한다. 이 경로는 앱 시연용 위치를 쓰고, Pleos SDK 경로는 에뮬레이터 GPS로 별도 재현한다. 이전 APK의 Pleos 경로에서 자동 제안은 관찰했으며, 이번 수동 좌표 변경의 AVD 검증은 남아 있다. 촬영 토큰은 디버그 APK에 포함되므로 로컬 촬영용으로 관리한다. [로컬 설정과 실행 절차](README.md#로컬-촬영용-차량-앱-자동-연결)
