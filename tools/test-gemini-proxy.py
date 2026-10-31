#!/usr/bin/env python3
"""Local TLS fixtures only; no real keys, Google calls or proxy credentials."""
import os
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parent.parent
with tempfile.TemporaryDirectory(prefix='gemini-proxy-tests-') as tmp:
    fixture = str(Path(tmp) / 'fixture.p12')
    subprocess.run(['keytool', '-genkeypair', '-alias', 'fixture', '-keyalg', 'RSA',
                    '-storetype', 'PKCS12', '-keystore', fixture, '-storepass', 'changeit',
                    '-keypass', 'changeit', '-dname', 'CN=localhost', '-ext', 'SAN=dns:generativelanguage.googleapis.com', '-validity', '2', '-noprompt'],
                   check=True, capture_output=True)
    cp = os.pathsep.join(str(p) for p in (root / 'lib').glob('*.jar'))
    sources = [str(root / 'src/ru/local/spbumailpilot' / (n + '.java')) for n in
               ['GeminiProxyTransport', 'GeminiDigest', 'AiConfiguration', 'DigestBatch']]
    sources += [str(root / 'tests' / (n + '.java')) for n in ['GeminiProxyTransportTest', 'GeminiProxyEndToEndTest', 'GeminiDigestTest']]
    subprocess.run([os.environ.get('JAVAC', 'javac'), '-cp', cp, '-d', tmp, *sources], check=True)
    for name, args in [('GeminiProxyTransportTest', [fixture]), ('GeminiProxyEndToEndTest', [fixture]), ('GeminiDigestTest', [])]:
        subprocess.run(['java', '-cp', tmp + os.pathsep + cp, 'ru.local.spbumailpilot.' + name, *args],
                       check=True, timeout=35)
