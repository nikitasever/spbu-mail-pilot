#!/usr/bin/env bash
set -euo pipefail
umask 077
[[ $EUID == 0 ]] || { echo 'Запустите через sudo bash install.sh'; exit 1; }
command -v apt-get >/dev/null || { echo 'Скрипт рассчитан на Debian/Ubuntu.'; exit 1; }
DIR=/etc/spbu-gemini-proxy
[[ ! -e $DIR ]] || { echo "$DIR уже существует. Повторная установка остановлена."; exit 1; }
python3 - <<'PY'
import socket
for host,port in [('0.0.0.0',9443),('127.0.0.1',31290)]:
 with socket.socket() as s:s.bind((host,port))
PY
# Не останавливать существующий Squid, если он уже использовался.
SQUID_WAS_INSTALLED=false
dpkg-query -W -f='${Status}' squid 2>/dev/null | grep -q 'install ok installed' && SQUID_WAS_INSTALLED=true
apt-get update
DEBIAN_FRONTEND=noninteractive apt-get install -y squid stunnel4 apache2-utils openssl
if [[ $SQUID_WAS_INSTALLED == false ]]; then systemctl disable --now squid; fi
install -d -m 750 -o root -g proxy "$DIR"
PASSWORD=$(openssl rand -hex 24)
printf '%s\n' "$PASSWORD" | htpasswd -i -c -B "$DIR/passwords" gemini
chown root:proxy "$DIR/passwords"
chmod 640 "$DIR/passwords"
openssl req -x509 -newkey rsa:3072 -sha256 -nodes -days 365 \
 -keyout "$DIR/server.key" -out "$DIR/server.crt" \
 -subj '/CN=79.137.206.221' -addext 'subjectAltName=IP:79.137.206.221'
cat > "$DIR/squid.conf" <<'CONF'
http_port 127.0.0.1:31290
visible_hostname spbu-gemini-proxy
pid_filename none
cache_effective_user proxy
cache_effective_group proxy
auth_param basic program /usr/lib/squid/basic_ncsa_auth /etc/spbu-gemini-proxy/passwords
auth_param basic realm Gemini private proxy
auth_param basic credentialsttl 1 minute
acl authenticated proxy_auth REQUIRED
acl tunnel method CONNECT
acl tls_port port 443
acl gemini dstdomain generativelanguage.googleapis.com
http_access allow authenticated tunnel tls_port gemini
http_access deny all
cache deny all
access_log none
cache_store_log none
cache_log /dev/stderr
logfile_rotate 0
forwarded_for delete
via off
CONF
cat > "$DIR/stunnel.conf" <<'CONF'
foreground = yes
pid =
setuid = proxy
setgid = proxy
sslVersionMin = TLSv1.2
cert = /etc/spbu-gemini-proxy/server.crt
key = /etc/spbu-gemini-proxy/server.key
[gemini]
accept = 0.0.0.0:9443
connect = 127.0.0.1:31290
TIMEOUTconnect = 10
TIMEOUTidle = 90
CONF
cat > /etc/systemd/system/spbu-gemini-squid.service <<'CONF'
[Unit]
Description=Gemini-only authenticated CONNECT proxy
After=network.target
[Service]
Type=simple
User=proxy
Group=proxy
ExecStart=/usr/sbin/squid -N -f /etc/spbu-gemini-proxy/squid.conf
Restart=on-failure
RestartSec=5
NoNewPrivileges=yes
PrivateTmp=yes
ProtectHome=yes
ProtectSystem=strict
[Install]
WantedBy=multi-user.target
CONF
cat > /etc/systemd/system/spbu-gemini-tls.service <<'CONF'
[Unit]
Description=TLS entry for Gemini private proxy
After=network.target spbu-gemini-squid.service
Requires=spbu-gemini-squid.service
[Service]
Type=simple
ExecStart=/usr/bin/stunnel4 /etc/spbu-gemini-proxy/stunnel.conf
Restart=on-failure
RestartSec=5
NoNewPrivileges=yes
PrivateTmp=yes
ProtectHome=yes
ProtectSystem=strict
[Install]
WantedBy=multi-user.target
CONF
/usr/sbin/squid -k parse -f "$DIR/squid.conf"
systemctl daemon-reload
systemctl enable --now spbu-gemini-squid spbu-gemini-tls
sleep 1
systemctl is-active --quiet spbu-gemini-squid spbu-gemini-tls
PIN=$(openssl x509 -in "$DIR/server.crt" -outform DER | openssl dgst -sha256 -binary | openssl base64 -A)
# Пароль остаётся только на сервере, в файле root:root 0600.
printf 'host=79.137.206.221\nport=9443\nusername=gemini\npassword=%s\ncertificateSha256Base64=%s\n' "$PASSWORD" "$PIN" > /root/spbu-gemini-client.conf
unset PASSWORD
install -m 644 "$DIR/server.crt" /root/spbu-gemini-proxy-public.crt
printf '\nГотово. Конфигурация: /root/spbu-gemini-client.conf (не публиковать).\nПубличный сертификат: /root/spbu-gemini-proxy-public.crt\n'
printf 'Если включён firewall, разрешите TCP 9443. Скрипт firewall и Xray не меняет.\n'
printf 'Сертификат действует 365 дней: до истечения потребуется обновить сертификат и pin в клиентах.\n'
