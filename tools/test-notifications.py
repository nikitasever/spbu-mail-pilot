#!/usr/bin/env python3
"""Local notification domain/worker fixtures. Android lifecycle still needs a device check."""
import os
from pathlib import Path
import subprocess
import tempfile
root=Path(__file__).resolve().parent.parent
cp=os.pathsep.join(str(p) for p in (root/'lib').glob('*.jar'))
names=['BackgroundMailLoop','AccountMailChecks','NoticeWork','MailNotificationText','MailNoticeTarget','ReminderPolicy']
with tempfile.TemporaryDirectory(prefix='notification-tests-') as out:
    sources=[str(root/'src/ru/local/spbumailpilot'/(n+'.java')) for n in names]
    sources += [str(root/'tests'/(n+'Test.java')) for n in names]
    subprocess.run([os.environ.get('JAVAC','javac'),'-cp',cp,'-d',out,*sources],check=True)
    for name in names:
        subprocess.run(['java','-cp',out+os.pathsep+cp,'ru.local.spbumailpilot.'+name+'Test'],check=True,timeout=20)
