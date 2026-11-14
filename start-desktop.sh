#!/bin/bash
export HOME=/root USER=root DISPLAY=:1
export XDG_RUNTIME_DIR=/tmp/xdg-root
mkdir -p "$XDG_RUNTIME_DIR" /root/.vnc /tmp/.X11-unix
chmod 700 "$XDG_RUNTIME_DIR"; chmod 1777 /tmp/.X11-unix
touch /root/.Xauthority
vncserver -kill :1 >/dev/null 2>&1 || true
rm -f /tmp/.X1-lock /tmp/.X11-unix/X1

printf 'agy12345\n' | vncpasswd -f > /root/.vnc/passwd
chmod 600 /root/.vnc/passwd

cat > /root/.vnc/xstartup <<'EOF'
#!/bin/sh
unset SESSION_MANAGER DBUS_SESSION_BUS_ADDRESS
export XDG_RUNTIME_DIR=/tmp/xdg-root
exec dbus-launch --exit-with-session startxfce4
EOF
chmod +x /root/.vnc/xstartup

vncserver :1 -geometry 1280x720 -depth 24 -localhost yes -xstartup /root/.vnc/xstartup
echo "VNC server up, noVNC start ho raha hai"
exec websockify --web /usr/share/novnc 6080 localhost:5901
