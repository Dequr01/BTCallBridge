# BTCallBridge

Bluetooth-only call mirroring from host (SIM) → client (No SIM).

## Architecture
- **Host (app-host)**: Runs on the phone with the SIM card. Listens for incoming calls and bridges them via Bluetooth RFCOMM.
- **Client (app-client)**: Runs on the phone without a SIM. Receives call notifications and provides a UI to answer/reject.
- **Core (core)**: Shared protocol and constants.

## Setup
1. Pair both devices via Android Bluetooth Settings manually.
2. Build and install `app-host` on the phone with the SIM.
3. Build and install `app-client` on the phone without the SIM.
4. Launch `app-host` and grant all permissions (including Battery Optimization exemption).
5. Launch `app-client`, select the Host device from the list, and grant permissions.

## Features
- No Internet/WiFi needed.
- Pure Bluetooth RFCOMM (Classic SPP).
- Bidirectional PCM Audio at 8kHz.
- Full-screen incoming call UI.
- Auto-start on boot.
- Reconnection logic.
