#!/usr/bin/env python3
"""Bundle a platform runtime; run with a full JDK 17+ on the target OS."""
import os,shutil,subprocess,sys
from pathlib import Path
root=Path(__file__).resolve().parents[1]
target=root/'build/runtime'
if target.exists():shutil.rmtree(target)
jlink=Path(os.environ['JAVA_HOME'])/'bin'/('jlink.exe' if os.name=='nt' else 'jlink')
subprocess.run([str(jlink),'--add-modules','java.base,java.logging,java.naming,java.desktop,jdk.crypto.ec','--strip-debug','--no-header-files','--no-man-pages','--output',str(target)],check=True)
if os.name=='nt':
    subprocess.run([sys.executable,str(root/'tools/windows-utf8-launcher.py'),str(target/'bin/java.exe')],check=True)
