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
if reply.returncode:
    print(reply.stderr)
    ascii_root=Path(os.environ['RUNNER_TEMP'])/'desktop-ascii-probe'
    shutil.copytree(root,ascii_root)
    ascii_cp=str(ascii_root/'engine/classes')+';'+str(ascii_root/'engine/lib/*')
    compare=subprocess.run([str(ascii_root/'runtime/bin/java.exe'),'-cp',ascii_cp,'ru.local.spbumailpilot.DesktopEngine'],input='{"id":"probe","method":"capabilities","args":{}}\n',text=True,encoding='utf-8',capture_output=True,timeout=15)
    print('Same runtime under ASCII path:',compare.returncode,compare.stdout,compare.stderr)
    jdk=Path(os.environ.get('JAVA_HOME_21_X64','missing-jdk'))
    if (jdk/'bin/jlink.exe').exists():
        modern=root/'runtime21'
        subprocess.run([str(jdk/'bin/jlink.exe'),'--add-modules','java.base,java.logging,java.naming,java.desktop,jdk.crypto.ec','--strip-debug','--no-header-files','--no-man-pages','--output',str(modern)],check=True)
        compare=subprocess.run([str(modern/'bin/java.exe'),'-Dfile.encoding=UTF-8','-cp',cp,'ru.local.spbumailpilot.DesktopEngine'],input='{"id":"probe","method":"capabilities","args":{}}\n',text=True,encoding='utf-8',capture_output=True,timeout=15)
        print('JDK21 runtime under original Cyrillic path:',compare.returncode,compare.stdout,compare.stderr)
assert reply.returncode==0 and json.loads(reply.stdout)['result']['readOnly'] is True,'Installed Java protocol failed'
probe=subprocess.run([str(binary),'--verify-engine'],capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=110)
print('Installed shell probe exit:',probe.returncode)
if probe.returncode:print(probe.stderr)
assert probe.returncode==0,'Installed shell protocol failed'
