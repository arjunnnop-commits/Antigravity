#!/bin/bash
# proot ke andar ek hi baar chalta hai
set -e
export DEBIAN_FRONTEND=noninteractive

cat > /etc/apt/sources.list <<'EOF'
deb http://ports.ubuntu.com/ubuntu-ports jammy main universe multiverse
deb http://ports.ubuntu.com/ubuntu-ports jammy-updates main universe multiverse
deb http://ports.ubuntu.com/ubuntu-ports jammy-security main universe multiverse
EOF
echo 'APT::Sandbox::User "root";' > /etc/apt/apt.conf.d/99proot
echo 'Acquire::Retries "5";' > /etc/apt/apt.conf.d/99retries
echo 'force-unsafe-io' > /etc/dpkg/dpkg.cfg.d/99unsafeio

apt-get update
PKGS="ca-certificates curl wget procps sudo xauth dbus-x11 x11-xserver-utils xdg-utils
 xfce4 xfce4-terminal adwaita-icon-theme tigervnc-standalone-server tigervnc-common novnc websockify
 fonts-noto-core fonts-noto-color-emoji fonts-dejavu-core
 libgtk-3-0 libnss3 libxss1 libasound2 libgbm1 libxshmfence1 libsecret-1-0 libx11-xcb1 libxkbfile1
 libdrm2 libatk-bridge2.0-0 libcups2 libxkbcommon0 libxcomposite1 libxdamage1 libxrandr2
 libpango-1.0-0 libcairo2 git openssh-client unzip nano"
apt-get install -y --no-install-recommends $PKGS || { dpkg --configure -a || true; apt-get install -f -y || true; apt-get install -y --no-install-recommends $PKGS; }

# Antigravity launcher (Electron ko proot me --no-sandbox aur software rendering chahiye)
cat > /usr/local/bin/antigravity <<'EOF'
#!/bin/bash
BIN=$(find /opt/antigravity -maxdepth 3 -type f -name Antigravity | head -1)
[ -z "$BIN" ] && { echo "Antigravity installed nahi hai"; exit 1; }
exec "$BIN" --no-sandbox --disable-gpu --disable-dev-shm-usage "$@"
EOF
chmod +x /usr/local/bin/antigravity

cat > /usr/share/applications/antigravity-pc.desktop <<'EOF'
[Desktop Entry]
Type=Application
Name=Antigravity
Exec=/usr/local/bin/antigravity %U
Terminal=false
Categories=Development;
EOF
mkdir -p /root/.config/autostart /root/Desktop
cp /usr/share/applications/antigravity-pc.desktop /root/Desktop/
chmod +x /root/Desktop/antigravity-pc.desktop
{ cat /usr/share/applications/antigravity-pc.desktop; echo "X-GNOME-Autostart-Delay=8"; } > /root/.config/autostart/antigravity-pc.desktop

apt-get clean
echo "SETUP DONE"
