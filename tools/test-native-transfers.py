#!/usr/bin/env python3
"""Local transfer state/cancellation and SMTP acknowledgement fixtures; no accounts or network."""
import os
from pathlib import Path
import subprocess
import tempfile
root=Path(__file__).resolve().parent.parent
cp=os.pathsep.join(str(p) for p in (root/'lib').glob('*.jar'))
app=root/'native-ui/app/src/main/java/ru/local/spbumailpilot'
sources=[app/(n+'.java') for n in ['NativeTransferCancellation','NativeTransferProgress','NativeTransferFailure','NativeDraftFlight','NativeReadGate']]
sources += [root/'native-ui/tests'/(n+'.java') for n in ['NativeTransferStateTest','NativeReadGateTest']]
sources += [root/'src/ru/local/spbumailpilot/TransferMessage.java',root/'tests/TransferMessageTest.java']
with tempfile.TemporaryDirectory(prefix='native-transfer-tests-') as out:
    subprocess.run([os.environ.get('JAVAC','javac'),'-cp',cp,'-d',out,*map(str,sources)],check=True)
    for name in ['NativeTransferStateTest','NativeReadGateTest','TransferMessageTest']:
        subprocess.run(['java','-cp',out+os.pathsep+cp,'ru.local.spbumailpilot.'+name],check=True,timeout=20)
