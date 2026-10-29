#!/usr/bin/env python3
import os,subprocess,sys
from pathlib import Path
root=Path(__file__).resolve().parents[2];desktop=root/'desktop'
subprocess.run([sys.executable,str(desktop/'tools/build-engine.py')],check=True)
classes=desktop/'build/engine/classes'
cp=str(classes)+os.pathsep+os.pathsep.join(str(p) for p in (desktop/'build/engine/lib').glob('*.jar'))
subprocess.run([os.environ.get('JAVAC','javac'),'-encoding','UTF-8','-cp',cp,'-d',str(desktop/'build/tests'),str(desktop/'tests/DesktopEngineTest.java'),str(desktop/'tests/DesktopOutgoingTest.java'),str(desktop/'tests/DesktopCalendarTest.java')],check=True)
subprocess.run([os.environ.get('JAVA_BIN','java'),'-Djava.awt.headless=true','-cp',str(desktop/'build/tests')+os.pathsep+cp,'ru.local.spbumailpilot.DesktopEngineTest'],check=True,timeout=20)
subprocess.run([os.environ.get('JAVA_BIN','java'),'-Djava.awt.headless=true','-cp',str(desktop/'build/tests')+os.pathsep+cp,'ru.local.spbumailpilot.DesktopOutgoingTest'],check=True,timeout=20)
subprocess.run([os.environ.get('JAVA_BIN','java'),'-Djava.awt.headless=true','-cp',str(desktop/'build/tests')+os.pathsep+cp,'ru.local.spbumailpilot.DesktopCalendarTest'],check=True,timeout=20)
process=subprocess.Popen([os.environ.get('JAVA_BIN','java'),'-Djava.awt.headless=true','-cp',cp,'ru.local.spbumailpilot.DesktopEngine'],stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
process.stdin.write('{"id":"check","method":"capabilities","args":{}}\n');process.stdin.flush()
import json
assert json.loads(process.stdout.readline())['result']['sending'] is True
process.stdin.close();assert process.wait(timeout=3)==0
print('PASS: NDJSON-only stdout and prompt JVM exit on parent stdin EOF.')
