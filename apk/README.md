<div align="center">

# Driver Sense · 제출용 APK

공모전 GitHub 제출 규칙에 맞춘 APK다. 네이버 지도 SDK 때문에 파일이 100MB를 넘어 이 폴더에 넣지 못하고 **[GitHub Releases v0.1](https://github.com/Driver-sense-team/Driver-sense/releases/tag/v0.1)** 에 올렸다.

</div>

| 항목 | 내용 |
|---|---|
| 제출용 | `Driver-sense-team_DriverSense_release.apk` (125MB, 데모 패널 없음) |
| 데모용 | `Driver-sense-team_DriverSense_demo.apk` (134MB, 오른쪽 위 "데모" 버튼으로 녹화된 주행 장면 재생) |
| 패키지 | `kkam.app` |
| 실행 환경 | Pleos Connect Emulator v2.1.0 |
| 서명 | 팀 전용 키 (인증서 CN=Driver-sense-team). 두 APK는 같은 키라 서로 덮어 설치된다 |

## 설치와 실행

1. Pleos Connect Emulator를 켠다.
2. 설치하고 실행한다.
   ```bash
   adb install -r --user 10 Driver-sense-team_DriverSense_release.apk
   ```
   ```bash
   adb shell am start --user 10 -n kkam.app/.MainActivity
   ```
   → 처음 켜면 위치·속도 권한을 묻는다. 허용하면 실제 차량 신호로 주행 코치를 시작한다.
3. 내비 위 팝업을 보려면 겹침 창 권한을 켠다.
   ```bash
   adb shell appops set --user 10 kkam.app SYSTEM_ALERT_WINDOW allow
   ```

- release 빌드에는 데모 패널이 없다. 녹화된 주행 장면을 보려면 데모용 APK를 설치한다. 소스에서 debug 앱을 빌드해도 같다([루트 README의 데모로 보기](../README.md#데모로-보기)).
- 자세한 실행 방법은 [루트 README의 빌드와 실행](../README.md#빌드와-실행)에 있다.
