# Privacy

RokidHub · Yandex is an unofficial community plugin for Rokid Nexus.

## Direct mode (recommended)

- Sign-in is performed by the official Yandex LoginSDK.
- The Yandex OAuth access token is stored only on the Android phone and encrypted with Android Keystore.
- The token is sent only to Yandex API endpoints required to read or control the user's smart home.
- The token is not sent to RokidHub.
- The user can delete the local token from the plugin settings or revoke access in Yandex ID.

## Cloud mode (optional)

- Cloud mode is disabled by default and must be selected manually.
- The plugin receives a separate, revocable RokidHub installation credential.
- The plugin never receives the Yandex OAuth token stored by RokidHub.
- The installation can be revoked from the RokidHub account.

## Speech and display

Speech-to-text, HUD rendering, and text-to-speech are provided through permissions explicitly granted by the user in Rokid Nexus. The plugin does not log speech transcripts and does not run in the background when its Nexus session is closed.

Questions: https://rokidhub.com/nexus/

