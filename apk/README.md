<div align="center">

# Driver Sense · 제출용 APK

공모전 GitHub 제출 규칙에 맞춰 실행할 수 있는 **release APK**를 이 폴더에 둔다.

</div>

| 항목 | 내용 |
|---|---|
| 파일 이름 | `팀명_앱이름_release.apk` |
| 패키지 | `kkam.app` |
| 실행 환경 | Pleos Connect Emulator v2.1.0 |
| 용량이 클 때 | GitHub Releases에 올리고 그 주소를 제출한다 |

## 설치와 실행

1. Pleos Connect Emulator를 켠다.
2. 설치하고 실행한다.
   ```bash
   adb install -r apk/팀명_앱이름_release.apk
   ```
   ```bash
   adb shell am start --user 10 -n kkam.app/.MainActivity
   ```
   → 처음 켜면 위치·속도 권한을 묻는다. 허용하면 실제 차량 신호로 주행 코치를 시작한다.
3. 내비 위 팝업을 보려면 겹침 창 권한을 켠다.
   ```bash
   adb shell appops set --user 10 kkam.app SYSTEM_ALERT_WINDOW allow
   ```

- release 빌드에는 데모 패널이 없다. 녹화된 주행 장면을 보려면 소스에서 debug 앱을 설치한다([루트 README의 데모로 보기](../README.md#데모로-보기)).
- 자세한 실행 방법은 [루트 README의 빌드와 실행](../README.md#빌드와-실행)에 있다.
