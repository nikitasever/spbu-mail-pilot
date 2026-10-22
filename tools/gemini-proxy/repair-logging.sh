#!/usr/bin/env bash
# Repair file logging without changing passwords, certificates, ports or Xray.
set -euo pipefail
[[ $(id -u) -eq 0 ]] || { echo 'Run as root'; exit 1; }
CONF=/etc/spbu-gemini-proxy/squid.conf
[[ -f $CONF ]] || { echo 'Proxy configuration is missing'; exit 1; }
install -d -o proxy -g proxy -m 750 /var/log/spbu-gemini-proxy
sed -i 's|^access_log .*|access_log stdio:/var/log/spbu-gemini-proxy/access.log squid|; s|^cache_log .*|cache_log /var/log/spbu-gemini-proxy/cache.log|' "$CONF"
mkdir -p /etc/systemd/system/spbu-gemini-squid.service.d
printf '[Service]\nReadWritePaths=/var/log/spbu-gemini-proxy\n' > /etc/systemd/system/spbu-gemini-squid.service.d/logs.conf
runuser -u proxy -- /usr/sbin/squid -k parse -f "$CONF"
systemctl daemon-reload
systemctl restart spbu-gemini-squid
systemctl is-active spbu-gemini-squid
echo 'Logs: /var/log/spbu-gemini-proxy/{access,cache}.log'
echo 'After diagnosis, set access_log none to disable request metadata logging.'
