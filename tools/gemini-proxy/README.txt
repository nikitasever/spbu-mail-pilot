Gemini-only TLS CONNECT proxy for 79.137.206.221

Prepared installer, not deployed or tested against the server.
Requires Debian/Ubuntu, systemd, python3, apt network access.
Review install.sh, copy to server and run: sudo bash install.sh

Uses a dedicated Squid instance at 127.0.0.1:31290 and stunnel TLS at TCP 19443.
Does not modify Xray/3x-ui or firewall configuration.
Allows authenticated CONNECT only to generativelanguage.googleapis.com:443.
Other destinations and plain HTTP requests are denied. No TLS interception.
Creates dedicated systemd units and /etc/spbu-gemini-proxy configuration.
Installs distribution packages; a newly installed default Squid instance is disabled.
An existing Squid service is not stopped or disabled.

Client settings (secret): /root/spbu-gemini-client.conf, mode 0600.
Public certificate: /root/spbu-gemini-proxy-public.crt, safe to share.
Certificate pin is SHA256 of DER certificate (not SPKI).
Certificate validity: 365 days. Renewal requires coordinated client pin update.
Android support is not yet implemented; existing APK cannot use this setup yet.
No Gemini API key is stored on proxy. No live Gemini access tested.

After installation check:
systemctl status spbu-gemini-squid spbu-gemini-tls --no-pager
ss -ltn | grep -E ':19443|:31290'
If firewall is enabled, allow inbound TCP 19443 using its existing rules.
Do not open TCP 31290 publicly. Keep existing SSH/Xray rules.

Stop and remove dedicated proxy if needed:
systemctl disable --now spbu-gemini-tls spbu-gemini-squid
Then remove only the two spbu-gemini units and /etc/spbu-gemini-proxy.
Do not uninstall shared packages without checking their other uses.

Installer fix (2026-10-21): squid.conf must be root:proxy 0640, because the
dedicated Squid systemd service runs as proxy. Config parsing now runs as that
same user instead of root. TLS private key remains root-only. For an existing
installation use repair-permissions.sh; it does not rotate credentials/certificates.

Logging repair (2026-10-22): some systemd environments cannot reopen
/dev/stderr or /dev/stdout from an unprivileged Squid worker. New installs
use a private LogsDirectory for cache.log. For an existing installation,
run repair-logging.sh as root. It grants write access only to the dedicated
log directory and enables standard CONNECT metadata logs for diagnosis;
passwords, API keys and tunneled request bodies are not logged. Set
access_log none after diagnosis; retain cache.log for service errors.
