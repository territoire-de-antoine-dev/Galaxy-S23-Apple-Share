# OpenDrop S23 | architecture v0.2

## Functional LAN path (not AirDrop)
- `MainActivity` : file picker, Android ACTION_SEND / ACTION_SEND_MULTIPLE share target, QR pairing, explicit upload approval.
- `TransferService` : user-initiated dataSync foreground service with persistent notification.
- `LocalTransferServer` : embedded HTTP/1.1 listener, random session secret placed in the URL fragment (not sent with the HTML request), browser JavaScript sends it via X-Pairing-Code for API requests.
- `ShareStore` : Android content URIs exposed as downloads over a same-LAN browser portal.
- Incoming uploads : capped at 500 MiB each, explicit phone-side approval, saved under Downloads/OpenDrop S23 via MediaStore.
- Receiving Apple device : opens the portal in Safari; no iOS app required.

## Security limitations in this prototype
- **HTTP is NOT encrypted**. The pairing secret and file contents can be intercepted by other users of the same network. Use only a trusted, private Wi-Fi network; do not use public Wi-Fi.
- QR code is a bearer secret; regenerate by stopping/restarting the service.
- File names are sanitized. Uploads have a size cap. Transports do not automatically run at boot.
- Do not expose the service over the public internet or forward the port.
- Next phase: encrypted transport with a verifiable trust model, logging and automated transfer tests.

## Experimental native AirDrop / AWDL
- `experimental/AwdlBridge.kt` is a feature boundary, **intentionally disabled**.
- Read-only information is provided by `HardwareDiagnostic`.
- No `su`, kernel patch, monitor mode, channel manipulation, or firmware change occurs.
- Root via KernelSU is optional for future diagnostics; it is not used for LAN transfers.
- Native AirDrop send/receive on an unmodified Apple device is NOT implemented.

## Native feasibility gates
1. Identify exact S23 variant, WLAN chip, kernel and driver.
2. Determine driver support for monitor mode, injection, channel switching and AWDL timings.
3. Prototype AWDL discovery in an isolated native transport without changing firmware.
4. Validate end-to-end transfer on actual iOS/macOS endpoints.
5. Only then enable the feature in the UI.

## Build
GitHub Actions runs Gradle 8.9 and Android Gradle Plugin 8.7.3 on Java 17 to create a debug APK. The APK uses the GitHub debug signing key; it is not a release build.
