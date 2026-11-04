# DEV-02 기술 검증 기록

검증일: 2026-11-03 · 환경: Pleos Connect v2.1.0 AVD (Android 14 / API 34)

## 사용 버전과 실행 방법

- Vehicle SDK 2.3.2, SpeechToText SDK 3.3.1. Pleos Maven 저장소에서 의존성을 받아 빌드했다.
- `./gradlew :app:clean :app:assembleDebug --no-daemon` 성공.
- Pleos AVD를 부팅하고 `app/build/outputs/apk/debug/app-debug.apk`를 설치했다.
- 앱 첫 화면의 **DEV-02 SDK 검증**에서 수동으로 다시 조회하거나, 검증 진입점 `adb shell am start -n com.joons.testdrive/.MainActivity --ez dev02_probe true`를 사용할 수 있다.
- 앱 화면과 `adb logcat -d -s DriveFitDev02:I AndroidRuntime:E`에서 콜백을 확인한다.

## 기능 검증표

### 다섯 SDK 제공·에뮬레이터 확인 현황 (2026-11-03 추가 확인)

| SDK | 문서상 제공 기능 | 현재 앱·에뮬레이터 결과 | 판정 |
| --- | --- | --- | --- |
| Vehicle 2.3.2 | 차량 상태 조회·제어 | 운전석 문 값 `0→1→0` 수신 | 해당 조회 사용 확인 |
| NaviHelper 3.0.5 | 목적지·경유지·경로 및 내비 정보 | 의존성 빌드 성공, `initialize()` 후 서비스 연결 콜백 수신 | 연결 확인; 실제 경로 API 미검증 |
| TextToSpeech 3.3.1 | 텍스트 음성 합성·재생 | 의존성 빌드 성공, `ON_DEVICE` 초기화 성공 콜백 | 초기화 확인; `speak()`·청취 미검증 |
| SpeechToText 3.3.1 | 음성을 텍스트로 변환 | `ON_DEVICE` 한국어 합성 PCM 최종 결과 확인 | 합성 오디오 인식 확인; 실제 마이크 미검증 |
| FusedLocation 1.1.2 | 위치 조회·업데이트 | 공식 문서의 Maven 좌표로 빌드했으나 의존성 해석 실패 | 현재 프로젝트에서 실행 미검증 |

FusedLocation의 실패 메시지는 `Could not find ai.pleos.playground:FusedLocation:1.1.2`였고, Gradle이 Google·Maven Central·Pleos Maven 저장소를 검색했다. 빌드를 유지하기 위해 해당 의존성과 진단 호출은 앱에서 제외했다. 문서상 제공과 Maven에서 지금 받을 수 있는지는 별개다. NaviHelper의 연결 성공은 목적지 설정·경로 검색 성공을 뜻하지 않고, TextToSpeech 초기화 성공은 음성 재생 성공을 뜻하지 않는다. 두 SDK의 현재 Pleos 프로젝트 승인 상태는 이번 추가 확인에서 재조회하지 않았다.

검증 명령: `./gradlew :app:clean :app:assembleDebug --no-daemon` → 빌드 성공. Pleos AVD 재부팅 후 APK 설치, `adb shell am start -n com.joons.testdrive/.MainActivity --ez sdk_probe true`, `adb logcat -d -s DriveFitSdkProbe:I AndroidRuntime:E`로 콜백 확인. 로그:

```text
DriveFitSdkProbe: FusedLocation: Maven 의존성 1.1.2 해석 실패로 실행 미검증
DriveFitSdkProbe: NaviHelper initialize 호출 완료
DriveFitSdkProbe: TextToSpeech ON_DEVICE 초기화 성공
DriveFitSdkProbe: NaviHelper 서비스 연결 성공
```

참고 문서: [SDK 릴리즈 노트](https://document.pleos.ai/en/docs/pleos-only/release-note/sdk-release-note), [NaviHelper 개요](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/intro), [TextToSpeech 모드](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/TextToSpeech/intro), [FusedLocation 가져오기](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/FusedLocation/project-setup), [FusedLocation 권한](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/FusedLocation/declaring-permissions).

| 기능 | API·권한 | 결과 | 판정 |
| --- | --- | --- | --- |
| 운전석 문 위치 조회 | Vehicle `getDoor().getCurrentDoorPosition(ROW_1_LEFT)` · `CAR_DOORS` | 에뮬레이터에서 `0 → 1 → 0` 콜백 | 사용 확인 (에뮬레이터) |
| 문 상태 변경 반영 | Pleos Virtual Console 운전석 문 버튼 → Vehicle SDK 재조회 | 브라우저에서 Console을 연 후 앱 콜백 `0 → 1 → 0`; 문은 닫힘으로 복원 | 사용 확인 (에뮬레이터) |
| 정차·기어 상태 | AAOS `GEAR_SELECTION`, `PARKING_BRAKE_ON`, `PERF_VEHICLE_SPEED` | 에뮬레이터 차량 서비스 속성 목록에 존재; 앱 직접 호출은 미검증 | 미확인 (앱 접근) |
| 한국어 음성 SDK 초기화 | SpeechToText `ON_DEVICE`, `Locale.KOREA` · `STT_SERVICE` | `초기화 성공: ko-KR, ON_DEVICE` | 사용 확인 (에뮬레이터) |
| 합성 한국어 PCM 인식 | SpeechToText `sendAudio()` + `completeAudioSend()` | 최종 결과 `승차감이 편안해요` | 사용 확인 (마이크 미사용) |
| 마이크 인식 요청 | SpeechToText `request()` | 요청 접수 및 `onReady` 수신; 무음 상태에서 최종 텍스트 없음 | 일부 확인 (캡처 미확인) |
| 실제 발화 인식 | SpeechToText `request()` | 실제 마이크 음성 입력 미시험 | 미확인 |

DriveFit에 필요한 추가 후보는 정차 판단용 기어 선택 상태·속도·주차 브레이크, 차량 식별용 기본 정보다. SDK 소스에서 `getCurrentGearSelectionState()`를 찾았으나 deprecated 및 hidden으로 표시되어 이 API를 채택하지 않았다.

### Vehicle Playground 화면 대조

2026-11-03 Pleos AVD의 `ai.pleos.playground.sample.vehicle` 앱을 직접 실행했다. **Door / Door Position** 화면은 SDK 2.3.2, Vehicle Service 2.3.22, `SDK initialized`를 표시했다. 선택 영역 `ROW_1_LEFT`에서 `Vehicle support: Supported`, capability `READ_WRITE`, 현재 값 `0`, `getCurrentDoorPosition: Complete`를 확인했다. [화면 캡처](docs/evidence/vehicle-playground-door-position.png). 이는 이 AVD의 **Door Position 항목**을 확인한 결과이며 Vehicle SDK 전체 기능이나 실차 지원 판정은 아니다.

### 정차 판단 후보 API

| 데이터 | 공개 API·읽기 권한 | 호출 방법 | 현재 판정 |
| --- | --- | --- | --- |
| 선택 기어 | AAOS `VehiclePropertyIds.GEAR_SELECTION` · `android.car.permission.CAR_POWERTRAIN` | `CarPropertyManager.getProperty(GEAR_SELECTION, 0)`; 먼저 `getPropertyList()`로 접근 가능 여부 확인 | 에뮬레이터 서비스에 존재, 앱 호출 미확인 |
| 주차 브레이크 | AAOS `VehiclePropertyIds.PARKING_BRAKE_ON` · `android.car.permission.CAR_POWERTRAIN` | `CarPropertyManager.getProperty(PARKING_BRAKE_ON, 0)`; 먼저 접근 가능 여부 확인 | 에뮬레이터 서비스에 존재, 앱 호출 미확인 |
| 차량 속도 | AAOS `VehiclePropertyIds.PERF_VEHICLE_SPEED` · `android.car.permission.CAR_SPEED` | 권한 승인 후 `CarPropertyManager.getProperty(PERF_VEHICLE_SPEED, 0)`; 단위 m/s | 에뮬레이터 서비스에 존재, 앱 권한·호출 미확인 |
| 운전석 문 위치 | Pleos Vehicle `getCurrentDoorPosition(ROW_1_LEFT)` · `pleos.car.permission.CAR_DOORS` | `Vehicle.initialize()` 후 `getDoor()`로 조회, 비동기 콜백 수신 | 조회 및 변경 반영 확인 |

Pleos의 [SDK API 정책](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-planning-guide/pleos-connect-sdk-api)은 표준 차량 데이터에 AAOS API를 우선 권장한다. 속성 정의가 있다는 것과 현재 AVD·실차에서 앱이 접근할 수 있다는 것은 다르다. AAOS 후보의 접근 가능 여부는 앱에서 아직 검증하지 않았다.

`adb shell dumpsys car_service`의 속성 목록에는 `GEAR_SELECTION` (`0x11400400`), `PARKING_BRAKE_ON` (`0x11200402`), `PERF_VEHICLE_SPEED` (`0x11600207`)가 읽기 속성으로 나타났다. 이는 현재 AVD 서비스의 지원 근거이며 DriveFit 앱의 권한 승인이나 실차 지원 근거는 아니다.

### 프로젝트 접근 상태

2026-11-03 Pleos Playground의 **차량 시승 서비스** 프로젝트에서 확인했다. Vehicle State의 차량 제원·주행 정보·도어 상태 조회는 **사용 가능**이다. SpeechToText와 Vehicle Control은 **준비 중**이다. 따라서 에뮬레이터에서 음성 SDK 초기화가 성공한 결과를 배포 권한으로 해석하지 않는다. 민감한 프로젝트 키·Client Secret은 이 기록에 저장하지 않았다.

## 관찰한 로그

```text
DriveFitDev02: 초기화 성공: ko-KR, ON_DEVICE
DriveFitDev02: 조회 성공: 0
DriveFitDev02: 합성 한국어 PCM 전송: 47070 bytes
DriveFitDev02: 중간 결과: 승차감
DriveFitDev02: 최종 결과: 승차감이 편안해요
DriveFitDev02: 마이크 인식 요청 전달
DriveFitDev02: 음성 서비스 준비 완료
DriveFitDev02: 조회 성공: 1
DriveFitDev02: 조회 성공: 0
```

처음에는 포트만 연결하고 Virtual Console 웹 화면을 열지 않아 `body.emulator_active=false`, `body.dds_ready.all=false`였고 변경 요청이 `emulator_inactive`로 거부됐다. `http://127.0.0.1:8088/`을 Chrome에서 열자 둘 다 `true`로 바뀌었다. 문 버튼을 열림으로 바꾼 후 앱을 재실행해 `1`을 읽었고, 다시 닫아 `0`을 읽었다.

합성 음성 파일 `app/src/main/assets/dev02_ko_test.pcm`은 macOS Yuna 음성으로 “승차감이 편안해요”를 만든 뒤 16 kHz·16비트·모노 PCM으로 변환한 검증용 입력이다. `sendAudio()`는 차량 마이크를 사용하지 않으므로 이 성공은 실제 마이크 접근·음질·주행 환경의 인식률을 증명하지 않는다. 무음 상태에서 `request()`는 예외 없이 접수됐고 `onReady()`가 왔으나, 음성이 없어서 캡처 및 인식 성공 여부는 확인할 수 없다.

## DEV-03 이후 구현·시연 범위 결정

- 차량 문 상태 조회: Pleos AVD 데모에서 실제 SDK 값으로 표시한다. 실차에서도 같은 API가 지원된다고 단정하지 않는다.
- 정차 판단: AAOS 기어·주차 브레이크·속도의 앱 직접 접근이 확인되기 전까지 실제 차량 상태로 자동 판정하지 않는다. 시연에서는 사용자가 정차를 확인하는 흐름과 명시된 샘플 데이터를 사용한다.
- 시승 소감 입력: 텍스트 입력을 기본 방식으로 유지한다. SpeechToText의 합성 PCM 인식은 기술 검증 근거로만 사용한다. Pleos 프로젝트에서 SpeechToText가 `준비 중`인 동안 배포 기능·시연 핵심 경로로 의존하지 않는다.
- 후속 검증: 발화 가능한 환경에서 Host Microphone Access, 실제 마이크 캡처, 한국어 최종 텍스트를 확인한 뒤 음성 입력을 선택 기능으로 연결할지 결정한다.

## 다음 확인

1. 발화 가능한 환경에서 에뮬레이터의 Host Microphone Access를 켜고 앱에서 **한국어 음성 인식 시작**을 누른 뒤 실제 마이크 캡처와 최종 텍스트 또는 오류를 기록한다.
2. 정차 판단에 필요한 AAOS 기어·속도·주차 브레이크 속성의 현재 AVD 접근 가능 여부와 앱 조회 결과를 확인한다.
3. 검증되지 않은 차량·음성 기능은 DEV-03 이후 샘플 데이터 또는 텍스트 입력으로 대체한다.

## 공식 문서

- [Vehicle SDK 가져오기](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/project-setup)
- [첫 차량 API 호출](https://document.pleos.ai/en/docs/pleos-only/getting-started)
- [Vehicle SDK 권한](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-permission)
- [SpeechToText SDK 가져오기](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/SpeechToText/importing-sdk)
- [SpeechToText 모드](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/SpeechToText/intro)
- [SpeechToText 언어 설정](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/SpeechToText/set-locale)
- [SpeechToText PCM 오디오 전달](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/SpeechToText/sending-audio-data)
- [Virtual Console 사용](https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/use-vehicle-emulator)
- [Pleos 프로젝트 SDK 접근 상태 안내](https://document.pleos.ai/en/docs/pleos-only/create-my-project)
- [AAOS 차량 속성·권한](https://developer.android.com/reference/android/car/VehiclePropertyIds)
- [CarPropertyManager 접근 가능 목록·조회](https://developer.android.com/reference/android/car/hardware/property/CarPropertyManager)
