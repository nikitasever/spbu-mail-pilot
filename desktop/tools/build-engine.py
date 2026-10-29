#!/usr/bin/env python3
"""Compile shared protocol sources with a desktop HTML adapter, without changing Android."""
import os
from pathlib import Path
import subprocess
import shutil

root=Path(__file__).resolve().parents[2]
desktop=root/'desktop'
build=desktop/'build'
generated=build/'generated/ru/local/spbumailpilot'
classes=build/'engine/classes'
for path in (generated,classes):
    if path.exists():shutil.rmtree(path)
    path.mkdir(parents=True)
for name in ('MailReader','MailPreview'):
    source=(root/f'src/ru/local/spbumailpilot/{name}.java').read_text(encoding="utf-8")
    assert source.count('import android.text.Html;')==1
    source=source.replace('import android.text.Html;','').replace('Html.','DesktopHtml.')
    (generated/f'{name}.java').write_text(source,encoding="utf-8")
cp=os.pathsep.join(str(p) for p in (root/'lib').glob('*.jar'))
sourcepath=os.pathsep.join(map(str,[build/'generated',desktop/'core/src',root/'src']))
sources=[*generated.glob('*.java'),*desktop.glob('core/src/ru/local/spbumailpilot/*.java')]
subprocess.run([os.environ.get('JAVAC','javac'),'-source','17','-target','17','-encoding','UTF-8','-cp',cp,'-sourcepath',sourcepath,'-d',str(classes),*map(str,sources)],check=True)
# The existing json-test jar is the standalone org.json dependency, not an Android stub.
libraries=build/'engine/lib'
if libraries.exists():shutil.rmtree(libraries)
libraries.mkdir(parents=True)
for name in ['android-mail-1.6.7.jar','android-activation-1.6.7.jar','json-test-20240303.jar','okhttp-4.12.0.jar','okio-jvm-3.6.0.jar','kotlin-stdlib-1.9.10.jar','kotlin-stdlib-common-1.9.10.jar','kotlin-stdlib-jdk7-1.9.10.jar','kotlin-stdlib-jdk8-1.9.10.jar','rfc5545-datetime-0.3.jar','jems2-2.23.1.jar','lib-recur-0.17.1.jar']:
    shutil.copy2(root/'lib'/name,libraries/name.replace('json-test-', 'json-'))
print('Desktop protocol classes compiled; Android sources unchanged.')
