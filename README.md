# RokidHub · Yandex for Rokid Nexus

An unofficial open-source Nexus plugin for voice control of Yandex Smart Home from Rokid RV101 glasses. Open it from the Nexus launcher and say a command without the wake word “Alice”; the result is shown on the HUD and spoken through Nexus TTS.

The recommended direct mode signs in through the official Yandex LoginSDK. The OAuth token stays on the Android phone, encrypted by Android Keystore, and is not sent to RokidHub. An optional cloud mode uses a separate revocable RokidHub installation credential.

- Plugin ID: `rokidhub.yandex`
- Android package: `com.rokidhub.nexus.plugin.yandex`
- Nexus API: 3
- Capabilities: `surfaces`, `stt`, `tts`
- Privacy: [PRIVACY.md](PRIVACY.md)
- Installation guide and APK: https://rokidhub.com/nexus/

This project is not affiliated with or endorsed by Yandex, Rokid, or Anezium.

## Русский

### RokidHub · Яндекс — плагин Rokid Nexus

Headless Android APK для Rokid Nexus (Rokid RV101). При открытии на очках плагин
запускает Nexus STT, выполняет команду Яндекс Умного дома, показывает ответ на HUD
и озвучивает его через Nexus TTS.

По умолчанию используется direct mode: OAuth-токен Яндекса хранится только на
телефоне через Android Keystore. Cloud mode через RokidHub включается пользователем
явно в настройках плагина.

## Требования

- JDK 17 или новее;
- Android SDK Platform 36;
- Rokid Nexus phone/glasses hub с SDK API version 3;
- для direct mode — Android OAuth client Яндекса с:
  - package name `com.rokidhub.nexus.plugin.yandex`;
  - SHA-256 fingerprint сертификата сборки;
  - правами `iot:view` и `iot:control`.

Client ID не является секретом, но в репозитории его нет. Client secret этому APK
не нужен и не должен добавляться в Gradle, manifest, resources или исходный код.

## Проверка SHA-256 debug-сертификата

```powershell
keytool -list -v -alias androiddebugkey `
  -keystore "$env:USERPROFILE\.android\debug.keystore" `
  -storepass android -keypass android
```

Для release зарегистрируй fingerprint постоянного release certificate. Смена
сертификата означает новую identity и повторное одобрение плагина в Nexus.

Release-подпись передаётся отдельным локальным properties-файлом, который нельзя
добавлять в репозиторий:

```powershell
.\gradlew.bat assembleRelease `
  -PyandexClientId=<YANDEX_ANDROID_CLIENT_ID> `
  -PsigningProperties=C:\path\to\rokidhub-nexus-signing.properties
```

Публичный Store-релиз собирается только после чистого commit и tag: Nexus Registry
сверяет встроенную в APK Git revision с опубликованным исходным кодом.

## Сборка

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat testDebugUnitTest assembleDebug `
  -PyandexClientId=<YANDEX_ANDROID_CLIENT_ID>
```

APK:

`app\build\outputs\apk\debug\app-debug.apk`

Для локального cloud backend укажи HTTPS URL (например, URL dev tunnel):

```powershell
.\gradlew.bat assembleDebug `
  -PyandexClientId=<YANDEX_ANDROID_CLIENT_ID> `
  -ProkidHubBaseUrl=https://example-tunnel/api/v1/nexus
```

## Установка и первый запуск

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

1. Открой Rokid Nexus → Settings → Plugin access.
2. Одобри `RokidHub · Яндекс` и разрешения `surfaces`, `stt`, `tts`.
3. Открой настройки плагина внутри Nexus.
4. Для direct mode нажми «Войти через Яндекс».
5. Для cloud mode выбери его, открой плагин на очках и введи показанный код в
   кабинете RokidHub.
6. Запусти плагин из launcher Nexus на очках и произнеси команду без слова «Алиса».

Плагин не имеет `MAIN/LAUNCHER` activity и не появляется в launcher телефона — это
требование Nexus. Android-настройки открываются самим Nexus.

## Проверки

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

Unit tests проверяют локальное планирование on/off и temperature команд. Полный
цикл STT/HUD/TTS, LoginSDK и CXR/SPP требует телефона с Nexus и очков RV101.
