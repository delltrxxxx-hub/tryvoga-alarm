# Тривожний Будильник — нотатки для розробки

Kotlin + Jetpack Compose, один модуль `app`. Пакет `ua.tryvoga.alarm`. Інтерфейс — українською.
Власник репозиторію: `delltrxxxx-hub/tryvoga-alarm` (публічний — інакше оновлення в застосунку не завантажаться).

## Збірка на цьому ПК (Windows)

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
# без цього Gradle падає з "Unable to establish loopback connection" у пісочниці
$env:JAVA_TOOL_OPTIONS="-Djdk.net.unixdomain.tmpdir=C:\Users\DELLTRXXX\.gradle\uds"
.\gradlew.bat assembleRelease
```

Release підписується ключем `~/.android/tryvoga-release.jks`; паролі — у `~/.gradle/gradle.properties` (`TRYVOGA_*`).
**Ключ і паролі ніколи не комітити.** Якщо ключ загубиться — оновлення поверх стануть неможливими (лише перевстановлення).

## Як випустити оновлення («випусти оновлення»)

Застосунок при запуску питає `api.github.com/repos/delltrxxxx-hub/tryvoga-alarm/releases/latest`,
порівнює `tag_name` з `versionName` і пропонує завантажити перший `.apk` з релізу.

1. У `app/build.gradle.kts` збільшити **обидва**: `versionCode` (+1) і `versionName` (напр. 1.3 → 1.4).
2. `.\gradlew.bat assembleRelease` (див. вище).
3. Скопіювати `app/build/outputs/apk/release/app-release.apk` → `TryvogaAlarm-<версія>.apk`.
4. Закомітити та запушити зміни в `main`.
5. `gh release create v<версія> TryvogaAlarm-<версія>.apk --title "Тривожний Будильник <версія>" --notes "<що нового, українською>"`
   - Тег **обов'язково** `v` + той самий `versionName`.
   - Текст `--notes` показується користувачу у вікні оновлення — писати коротко, для людей.

## Дані тривог

- `AlertApi.kt`: основне джерело `siren.pp.ua/api/v3/alerts`, резерв `api.alerts.in.ua/v3/alerts/active.json`
  (`al=1` — жовта, без поля — червона; `at` — тип загрози). Обидва без токена.
- `assets/regions.json` — дерево Область → Район → Громада (id як у ukrainealarm/alerts.in.ua; Крим = 9999).
- `assets/ukraine_map.json` — спрощені межі областей (geoBoundaries ADM1, CC BY 4.0).
