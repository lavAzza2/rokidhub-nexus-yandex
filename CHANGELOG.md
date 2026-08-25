# Changelog

## 1.0.0 — 2026-08-25

- First public release for Rokid Nexus and Rokid RV101.
- Voice control and temperature queries for Yandex Smart Home through Nexus STT, HUD surfaces, and TTS.
- Direct mode uses the official Yandex LoginSDK and calls the Yandex IoT API from the phone.
- The Yandex OAuth token is encrypted with Android Keystore and is not sent to RokidHub in direct mode.
- Optional cloud mode uses a separately revocable RokidHub installation credential and never gives the plugin the server-side Yandex token.
- Russian command matching for on/off actions and temperature queries.

