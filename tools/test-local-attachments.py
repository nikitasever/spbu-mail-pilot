#!/usr/bin/env python3
"""Deterministic local MIME fixtures; no IMAP, credentials or network access."""
import os
from pathlib import Path
import subprocess
import tempfile
root=Path(__file__).resolve().parent.parent
cp=os.pathsep.join(str(p) for p in (root/'lib').glob('*.jar'))
names=['AttachmentExport','AttachmentText','EmlDocument','MailAttachments','AttachmentParts','AttachmentFiles','AttachmentNames','AttachmentTransfer','BulkAttachments','MailText','MimeWords']
tests=['AttachmentExportTest','AttachmentTextTest','EmlDocumentTest','AttachmentTest']
with tempfile.TemporaryDirectory(prefix='local-attachment-tests-') as out:
    sources=[str(root/'src/ru/local/spbumailpilot'/(n+'.java')) for n in names]+[str(root/'tests'/(n+'.java')) for n in tests]
    subprocess.run([os.environ.get('JAVAC','javac'),'-cp',cp,'-d',out,*sources],check=True)
    for test in tests:subprocess.run(['java','-cp',out+os.pathsep+cp,'ru.local.spbumailpilot.'+test],check=True,timeout=35)
