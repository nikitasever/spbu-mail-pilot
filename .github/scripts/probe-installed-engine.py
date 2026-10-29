import json,subprocess,sys,os,shutil
from pathlib import Path
binary=Path(sys.argv[1]);root=binary.parent
print('Installed executable:',binary)
print('Installed root entries:',[p.name for p in root.iterdir()])
java=root/'runtime/bin/java.exe';classes=root/'engine/classes';libraries=root/'engine/lib'
print('Runtime/classes/libs:',java.exists(),classes.is_dir(),libraries.is_dir())
print('Native DLLs:',(root/'runtime/bin/java.dll').exists(),(root/'runtime/bin/server/jvm.dll').exists())
assert java.exists() and classes.is_dir() and libraries.is_dir(),'Installed resources missing'
cp=str(classes)+';'+str(libraries/'*')
reply=subprocess.run([str(java),'-Djava.awt.headless=true','-cp',cp,'ru.local.spbumailpilot.DesktopEngine'],input='{"id":"probe","method":"capabilities","args":{}}\n',text=True,encoding='utf-8',capture_output=True,timeout=15)
print('Installed Java exit:',reply.returncode)
if reply.returncode: print(reply.stderr)
assert reply.returncode==0 and json.loads(reply.stdout)['result']['sending'] is True,'Installed Java protocol failed'
probe=subprocess.run([str(binary),'--verify-engine'],capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=110)
print('Installed shell probe exit:',probe.returncode)
if probe.returncode:print(probe.stderr)
assert probe.returncode==0,'Installed shell protocol failed'

# Exercise the shell launcher after a move into a Unicode path on the install volume.
unicode_root=root.parent/'Проверка пути почты'
try:
    shutil.copytree(root,unicode_root)
    moved=subprocess.run([str(unicode_root/binary.name),'--verify-engine'],capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=110)
    print('Unicode installation shell probe exit:',moved.returncode)
    if moved.returncode: print(moved.stderr)
    assert moved.returncode==0,'Unicode-path shell protocol failed'
finally:
    shutil.rmtree(unicode_root,ignore_errors=True)
