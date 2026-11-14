# Antigravity PC (APK)

Phone par poora Linux desktop (XFCE) chalata hai proot Ubuntu arm64 ke andar, aur uske andar Google Antigravity.
Display noVNC se app ke andar hi dikhta hai (koi alag VNC app nahi chahiye). Root nahi chahiye.

## Build (PC/Android Studio ki zaroorat nahi)
1. GitHub par naya repo banao, ye poora folder push karo.
2. Actions tab -> "Build APK" chalne do (ya Run workflow).
3. Run ke Artifacts se `AntigravityPC-apk` download karo, zip se APK nikalo, install karo.

## Use
1. App kholo -> "Setup shuru karo" (Wi-Fi, ~5 GB free storage, 15-30 min, ek hi baar).
2. Baad me "Desktop start karo" -> desktop aa jayega, Antigravity autostart hota hai.
3. Back dabane par panel aata hai, desktop background me chalta rehta hai. "Desktop band karo" se poora band.
4. Landscape me chalao, bahut better lagta hai. Bluetooth keyboard/mouse support karta hai.

## Agar Antigravity install fail ho
Setup ke time URL auto-detect hota hai. Fail ho to https://antigravity.google/download se Linux ARM64 `.tar.gz` ka link copy karo,
app ke URL box me paste karo aur "Antigravity install/update" dabao.

## URLs badalne ho (proot/rootfs)
`Installer.kt` ke `Cfg` object me.

## Limits
- GPU acceleration nahi, Electron thoda slow hoga. 8 GB+ RAM wale phone par best.
- targetSdk 28 hai (proot exec ke liye). Sideload APK hai, Play Store ke liye nahi.
