# DEV-01 개발 환경·프로젝트 설정 검증 기록

기록일: 2026-11-03 · 범위: 현재 작업 폴더에서 확인한 사실과 남은 확인 항목

## 환경과 버전

| 항목 | 확인 결과 | 근거 |
| --- | --- | --- |
| Android Studio | 설치·실행 중, 앱 버전 2026.2 | Android Studio의 `TestDrive` 창과 앱 정보 확인 |
| Android SDK | API 34 Pleos 시스템 이미지, API 37 플랫폼, ADB 37.0.1 | 로컬 SDK 설치 파일 및 `adb version` |
| Pleos 에뮬레이터 | `Pleos_Connect_v2.1.0` AVD, Android 14 / API 34 | AVD 목록과 DEV-02 실행 결과 |
| Gradle | Wrapper 9.6.0 | `gradle/wrapper/gradle-wrapper.properties` |
| Java | JetBrains Runtime 21.0.11 | `/usr/libexec/java_home -V` |
| 프로젝트 | Android 앱 모듈 `app`, Gradle 프로젝트 이름 `TestDrive`, 저장 폴더 `DriveFit` | `settings.gradle.kts`, Android Studio 프로젝트 창 |
| Git | `main`, 첫 커밋 `acb7eb7` (`Initial commit`), 원격 `https://github.com/joooon28/DriveFit.git` | `git log -1`, `git remote -v` |

## 다시 빌드·실행하는 방법

1. Android Studio에서 이 저장소의 `DriveFit` 폴더를 연다.
2. Device Manager에서 `Pleos_Connect_v2.1.0` AVD를 실행하고 부팅 완료를 기다린다.
3. Android Studio에서 실행 대상 AVD와 `app` 구성을 선택한 뒤 **Run**을 누른다.
4. 터미널로 빌드만 확인하려면 저장소 루트에서 `./gradlew :app:assembleDebug --no-daemon`을 실행한다. APK는 `app/build/outputs/apk/debug/app-debug.apk`에 생성된다.
5. 앱을 종료한 뒤 같은 절차로 다시 실행해 재현성을 확인한다.

DEV-02 작업 중 Pleos AVD에 debug APK를 설치하고 앱을 여러 번 다시 실행했으며, `./gradlew :app:assembleDebug --no-daemon` 빌드가 성공했다. 이는 현재 앱의 재실행 근거다. **DEV-01 시점의 빈 앱을 실행했다는 별도 기록은 확인되지 않았다.**

## DEV-01 할 일별 근거

| 할 일 | 현재 확인된 내용 | 판정 |
| --- | --- | --- |
| Android Studio·Android SDK 설치 상태 확인 | Studio 실행, SDK 플랫폼·ADB 설치 확인 | 확인 |
| Pleos 에뮬레이터 설치·실행 | AVD 설치 및 DEV-02에서 부팅·앱 설치 확인 | 확인 |
| DriveFit 프로젝트 생성 또는 기존 프로젝트 확인 | 현재 Android 프로젝트 구조와 Studio 창 확인 | 확인 |
| 에뮬레이터에서 빈 앱 실행 | 최초 빈 앱 실행 시점의 기록 없음 | 미확인 |
| VS Code·Codex에서 동일 프로젝트 폴더 열기 | Codex 작업 폴더는 확인; VS Code의 열린 폴더는 확인하지 않음 | 일부 확인 |
| Git 저장소 연결 및 첫 커밋 | 로컬 커밋과 원격 설정 확인 | 확인 |
| 설치 버전과 실행 방법 기록 | 이 문서에 확인된 버전과 절차 기록 | 확인 |

## 결정·검증 기록

- 선택·이유: 기존 Android 프로젝트와 Pleos AVD를 사용해 앱 실행 환경을 구성했다. 프로젝트를 새로 만들 필요가 없었다.
- 제약·절충: 저장 폴더 이름은 `DriveFit`이지만 Gradle 프로젝트 이름과 앱 패키지는 현재 `TestDrive` / `com.joons.testdrive`다. 이름 통일은 별도 결정이 필요하다.
- 실제 결과: Android Studio 실행, Gradle 빌드, Pleos AVD 부팅·앱 설치 및 현재 앱 재실행 확인. Git 첫 커밋 `acb7eb7`과 `origin` 설정 확인.
- 오류·해결: 이 기록에서 확인 가능한 DEV-01 당시 오류·해결 내역은 없다.
- 남은 확인: 최초 빈 앱 실행 여부, VS Code 동일 폴더, 에뮬레이터 실행 스크린샷. 이 항목을 확인한 후 계획 문서의 DEV-01 체크박스·상태를 실제 결과에 맞춰 갱신한다.

## 관련 파일

- 프로젝트 설정: `settings.gradle.kts`, `app/build.gradle.kts`, `gradle/wrapper/gradle-wrapper.properties`
- 이어지는 Pleos 기능 검증: `DEV-02-validation.md`
