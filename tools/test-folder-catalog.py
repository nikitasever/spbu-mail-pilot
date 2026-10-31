#!/usr/bin/env python3
import os
from pathlib import Path
import subprocess,tempfile
root=Path(__file__).resolve().parent.parent
cp=os.pathsep.join(str(p) for p in (root/'lib').glob('*.jar'))
with tempfile.TemporaryDirectory(prefix='folder-catalog-test-') as out:
    subprocess.run([os.environ.get('JAVAC','javac'),'-cp',cp,'-sourcepath',str(root/'src')+os.pathsep+str(root/'native-ui/app/src/main/java'),'-d',out,str(root/'native-ui/tests/android/text/Html.java'),str(root/'tests/MailFolderCatalogTest.java'),str(root/'native-ui/tests/NativeFoldersTest.java'),str(root/'tests/ReadFlagTest.java')],check=True)
    for name in ['MailFolderCatalogTest','NativeFoldersTest']:
        subprocess.run(['java','-cp',out+os.pathsep+cp,'ru.local.spbumailpilot.'+name],check=True,timeout=25)
