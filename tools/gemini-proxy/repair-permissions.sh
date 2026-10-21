#!/usr/bin/env bash
set -euo pipefail
[[ $EUID == 0 ]] || { echo 'Run as root'; exit 1; }
DIR=/etc/spbu-gemini-proxy
[[ -f $DIR/squid.conf && -f $DIR/passwords ]] || { echo 'Expected proxy configuration is missing'; exit 1; }
chown root:proxy "$DIR/squid.conf" "$DIR/passwords"
chmod 640 "$DIR/squid.conf" "$DIR/passwords"
runuser -u proxy -- /usr/sbin/squid -k parse -f "$DIR/squid.conf"
systemctl reset-failed spbu-gemini-squid spbu-gemini-tls
systemctl restart spbu-gemini-squid spbu-gemini-tls
sleep 2
systemctl --no-pager --full status spbu-gemini-squid spbu-gemini-tls
