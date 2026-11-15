# OpenDrop S23 architecture

## Status
Initial Android UI scaffold. Local transfers and native AirDrop are NOT implemented yet.

## Planned modules
- Android UI: Jetpack Compose, system file picker and share intents.
- Local transport: authenticated, encrypted LAN transfers with explicit acceptance, no cloud.
- AWDL experimental: separate native component; disabled unless hardware checks succeed.
- Root adapter: KernelSU opt-in only, never needed by local transport.
- Hardware diagnostics: read-only kernel/driver capability detection before attempting AWDL.

## Feasibility gates
1. Identify exact S23 model and Qualcomm WLAN driver.
2. Verify monitor mode, frame injection, channel switching, and firmware constraints.
3. Prototype AWDL discovery without altering firmware.
4. Validate bidirectional file transfers on actual Apple devices.

Do not claim native AirDrop support before end-to-end testing. OWL's active monitor and injection requirements may not be met by the stock Qualcomm driver.
