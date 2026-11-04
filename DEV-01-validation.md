# DEV-01 개발 환경·저장소 기준선 검증 기록

검증일: 2026-11-04 · 프로젝트: Roadio · 브랜치: `main`

## 작업 목표·근거

DriveFit 작업을 보존하고 Roadio 차량 앱의 반복 가능한 Android 빌드·Pleos AVD 실행 기준선을 만든다. 기획 범위는 `Roadio_기획서_기능정의서_UX기준안.md`를 따른다.

## 할 일·확인 결과

- [x] DriveFit 구현·검증 기록을 `archive/drivefit` 브랜치에 보존하고 원격에서 확인
- [x] `main`의 앱 이름·패키지·시작 화면을 Roadio로 전환
- [x] 기획 문서 본문의 로드캐스트/RoadCast 명칭을 로디오/Roadio로 정리
- [x] Android SDK·Pleos Connect AVD 설치 상태 확인
- [x] Roadio debug APK 빌드와 기본 단위 테스트 실행
- [x] Pleos AVD에 APK 설치 후 Roadio 시작 화면 확인
- [x] GitHub 원격 주소를 `joooon28/Roadio.git`으로 맞추고 두 브랜치 확인
- [x] 실행 방법·버전·남은 제약 기록

**완료 기준 결과:** 동일한 명령으로 빌드와 테스트가 통과했고 Pleos AVD에서 Roadio 시작 화면이 표시됐다. DriveFit 보존 브랜치와 Roadio `main`은 원격에 각각 존재한다.

## 작업 과정

1. 기존 DriveFit 구현과 검증 기록을 `archive/drivefit`에 보존했다.
2. `main`을 Roadio 이름·패키지·시작 화면으로 전환하고 빌드·기본 테스트를 통과했다.
3. 제공된 기획 문서의 서비스명을 Roadio로 맞추고 DEV-01~13 개발 순서를 정했다.
4. 2026-11-04 Pleos AVD를 부팅해 APK 설치·화면 표시를 확인하고 캡처를 저장했다.
5. Git 원격을 Roadio 주소로 변경하고 `main`과 보존 브랜치의 원격 존재를 확인했다.

## 환경·버전

| 항목 | 확인 내용 |
| --- | --- |
| 프로젝트 | Android Kotlin·Jetpack Compose, `com.joons.roadio` |
| Gradle wrapper | 9.6.0 |
| Android Gradle Plugin | 9.4.1 |
| Kotlin | 2.2.10 |
| 앱 설정 | compile SDK 37, min SDK 28, target SDK 37 |
| 에뮬레이터 | Pleos Connect v2.1.0 AVD, Android 14 / API 34 |
| Android Emulator | 37.2.12.0 |
| Git 원격 | `https://github.com/joooon28/Roadio.git` |

## 재현 절차·실제 결과

1. 저장소 루트에서 `./gradlew :app:assembleDebug :app:testDebugUnitTest --no-daemon` 실행 → **BUILD SUCCESSFUL**.
2. Android Studio에서 `Pleos_Connect_v2.1.0` AVD를 켜거나 `emulator -avd Pleos_Connect_v2.1.0 -no-snapshot-load`로 실행.
3. `adb install -r app/build/outputs/apk/debug/app-debug.apk` → `Success`.
4. `adb shell am start -n com.joons.roadio/.MainActivity` → 시작 성공.
5. 화면 계층에서 `Roadio`, 모바일·차량 역할, 위치 연동 검증 전 안내를 확인. [시작 화면](docs/evidence/roadio-dev01-home.png).
6. `git ls-remote origin refs/heads/main refs/heads/archive/drivefit`에서 두 브랜치 확인 (`main`: `66aa853`, `archive/drivefit`: `893c1f2`).

## 결정·검증 기록

- **구조 대안·선택 이유:** 새 저장소 생성 대신 기존 Git 이력을 유지했다. DriveFit은 보존 브랜치, Roadio 개발은 `main`에 둔다.
- **제약·절충:** 현재 Android 앱은 서비스 소개 화면만 표시한다. 모바일 녹음 앱·서버, 실제 위치 검색·차량 오디오 재생은 구현 전이다.
- **오류·해결:** 로컬 Git 원격이 이전 `DriveFit.git`을 가리켜 `Roadio.git`으로 변경했고 원격 HEAD와 두 브랜치를 조회해 연결을 확인했다.
- **남은 한계·후속 작업:** FusedLocation 의존성·권한·실행과 차량 오디오·주행 중 화면 정책은 DEV-02에서 검증한다. 기존 DriveFit의 SDK 테스트 결과를 Roadio의 기능 검증으로 자동 승계하지 않는다.
