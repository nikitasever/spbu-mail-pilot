import argparse, hashlib, os, pathlib, subprocess, sys, urllib.request, zipfile
ROOT=pathlib.Path(__file__).resolve().parent
os.chdir(ROOT)
def run(*args): subprocess.run([str(a) for a in args],check=True)
def dependencies():
    directory=ROOT/'lib';directory.mkdir(exist_ok=True)
    packages={'android-mail-1.6.7.jar':'com/sun/mail/android-mail/1.6.7/android-mail-1.6.7.jar','android-activation-1.6.7.jar':'com/sun/mail/android-activation/1.6.7/android-activation-1.6.7.jar','json-test-20240303.jar':'org/json/json/20240303/json-20240303.jar'}
    for name,path in packages.items():
        target=directory/name
        url='https://repo.maven.apache.org/maven2/'+path
        checksum={'android-activation-1.6.7.jar': '285713b2b549f382f26a57919ecddc4c83d9755737969fb7c006c905322bb253', 'android-mail-1.6.7.jar': '12c4240aa3a4ccda37c49b4e6b880a797a21e03a4f8c7421a1b51e0db552b79c', 'json-test-20240303.jar': '3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed'}[name]
        if not target.exists() or hashlib.sha256(target.read_bytes()).hexdigest()!=checksum:
            urllib.request.urlretrieve(url,target)
        if hashlib.sha256(target.read_bytes()).hexdigest()!=checksum: raise RuntimeError('Dependency checksum mismatch: '+name)
    return [directory/name for name in packages]
def main():
    parser=argparse.ArgumentParser();parser.add_argument('--test',action='store_true');parser.add_argument('--keystore',default='pilot-test.keystore');parser.add_argument('--output',default='build/spbu-mail-pilot.apk');args=parser.parse_args()
    sdk=pathlib.Path(os.environ.get('ANDROID_HOME',os.environ.get('ANDROID_SDK_ROOT','C:/android-sdk')))
    tools=sdk/'build-tools/35.0.0';android=sdk/'platforms/android-34/android.jar';windows=os.name=='nt'
    def tool(name):return tools/(name+('.bat' if name in ['d8','apksigner'] else '.exe') if windows else name)
    jars=dependencies();classpath=os.pathsep.join(map(str,[android,*jars[:2]]));build=ROOT/'build';classes=build/'classes';classes.mkdir(parents=True,exist_ok=True)
    for file in classes.rglob('*.class'):file.unlink()
    sources=list((ROOT/'src').rglob('*.java'))
    run('javac','-encoding','UTF-8','-source','8','-target','8','-classpath',classpath,'-d',classes,*sources)
    if args.test:
        testclasses=build/'tests';testclasses.mkdir(exist_ok=True)
        cp=os.pathsep.join(map(str,[jars[2],classes,*jars[:2],android]))
        run('javac','-encoding','UTF-8','-source','8','-target','8','-classpath',cp,'-d',testclasses,*list((ROOT/'tests').glob('*.java')))
        for test in (ROOT/'tests').glob('*Test.java'):run('java','-classpath',str(testclasses)+os.pathsep+cp,'ru.local.spbumailpilot.'+test.stem)
        run('npm.cmd' if windows else 'npm','ci','--ignore-scripts');run('npm.cmd' if windows else 'npm','test')
    run('jar','cf',build/'app.jar','-C',classes,'.')
    run(tool('d8'),'--min-api','26','--lib',android,'--output',build,build/'app.jar',*jars[:2])
    unsigned=build/'unsigned.apk';run(tool('aapt'),'package','-f','-M','AndroidManifest.xml','-S','res','-A','assets','-I',android,'-F',unsigned)
    with zipfile.ZipFile(unsigned,'a') as apk:apk.write(build/'classes.dex','classes.dex')
    run(tool('zipalign'),'-f','4',unsigned,build/'aligned.apk')
    # CI sets passwords as environment variables. The existing local development key remains compatible.
    os.environ.setdefault('APK_STORE_PASSWORD','android');os.environ.setdefault('APK_KEY_PASSWORD',os.environ['APK_STORE_PASSWORD'])
    output=pathlib.Path(args.output).resolve();output.parent.mkdir(parents=True,exist_ok=True)
    run(tool('apksigner'),'sign','--ks',args.keystore,'--ks-key-alias',os.environ.get('APK_KEY_ALIAS','pilot'),'--ks-pass','env:APK_STORE_PASSWORD','--key-pass','env:APK_KEY_PASSWORD','--out',output,build/'aligned.apk')
    run(tool('apksigner'),'verify','--verbose',output);print(output)
if __name__=='__main__':main()
