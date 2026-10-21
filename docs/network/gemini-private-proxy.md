# Private proxy for Gemini (2026-10-21)

Opt-in, shared across accounts on the phone. Native AI settings accept the server-generated
server password (1–256 characters, nonblank, no control characters), with fixed username `gemini`. Stored separately
from the Google key, AES-GCM/Android Keystore; existing credentials are never displayed.
Unsaved values clear on pause and are absent from diagnostic state and saved-state bundles.

Server: 79.137.206.221:19443. Certificate SHA256 (DER leaf):
318b2e48ba51b8c936e8ff22ebe3bf1b3241d46579f88a1f0150a26ffc17d3f4
User supplied certificate fingerprint via server console screenshot. Verify on device
before relying on it; public certificate expiry requires coordinated pin update.

Only NativeDigestModel opts into the proxy transport. MailReader, MailSender, CalDAV,
directory, attachments, previews and global network defaults are unchanged. Legacy WebView
continues its existing direct route. Both Compose variants receive the same source change.

OkHttp's raw socket factory creates the outer TLS socket for the explicit HTTP proxy.
The local pin trust manager verifies certificate validity and exact leaf digest BEFORE
CONNECT/authentication bytes. The outer connection allows TLS 1.2/1.3. HTTP CONNECT then
creates a tunnel to generativelanguage.googleapis.com:443. Google HTTPS is layered on top
using OkHttp's default platform trust and hostname verification; no trust-all origin,
global Authenticator, global ProxySelector, SSL defaults or MITM certificates are installed.
Google API key is solely an origin header, absent from CONNECT headers.

No redirects, no application retries, no direct fallback on proxy failure. Request generation,
account/app-lock guards, bounded response/error readers and cancellation are retained. Proxy
settings changes cancel pending analyses and invalidate snapshots. No mail/key leaves the
phone by opening or saving settings.

Verification: standalone local TLS fixture accepts pinned certificate and rejects foreign
certificate before application bytes. Actual OkHttp test checks authenticated CONNECT through
TLS, absence of Google key on the proxy hop, followed by independent origin TLS handshake.
Existing Gemini parser/error/privacy tests retained. Real server reachability, firewall,
credentials, Google account eligibility and RF mobile routes require device testing; local
fixtures do not establish any of these. No guarantee that Google accepts a given server IP.

Native.4 removes the original installer-password format restriction. Passwords retain their exact value (no trimming); proxy Basic authentication uses UTF-8, matching htpasswd terminal input. Existing saved hexadecimal passwords remain valid.
