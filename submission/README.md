# Roadio Pleos 시연 APK

- APK: `Roadio-Pleos-demo-2026-11-14.apk`
- 패키지: `com.joons.roadio`
- SHA-256: `b26c569469b6aaa9c69e29834af2cbf1f409c6e82a9e095212f4f1dad7872d99`
- 기준 소스: `4c43915` 이후 제출 APK 기록 커밋
- 대상 환경: Pleos Connect `Car_on_arm64_emulator`

## 설치

Pleos Connect 에뮬레이터를 실행한 뒤 Android SDK의 ADB로 설치합니다.

```bash
adb install -r submission/Roadio-Pleos-demo-2026-11-14.apk
```

`-r` 옵션은 기존 시연 계정과 앱 데이터를 유지합니다. 깨끗한 최초 설치가 필요하면 에뮬레이터에서 기존 Roadio 앱을 제거한 뒤 다시 설치합니다.

## 시연 범위

1. 모바일 Roadio 웹앱에서 이야기 또는 주행 팁을 녹음합니다.
2. 위치·제목·공개 범위를 설정해 Railway 서버에 등록합니다.
3. Pleos 가상 GPS를 등록 좌표 밖에서 안으로 이동합니다.
4. 차량에서 유형별 발견 알림음과 이야기 발견 화면을 확인합니다.
5. 듣기를 선택해 재생·일시정지·재생 위치 이동·다시 듣기를 확인합니다.

차속·진행 방향·방향지시등 조건, 이번 주행 경로 공유, 내비게이션 분할 화면은 기획서의 향후 발전 범위이며 이 APK의 시연 완료 기능으로 주장하지 않습니다.
