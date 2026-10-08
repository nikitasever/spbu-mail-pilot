"""Publish a new version via a write deploy key scoped only to the public APK repository."""
import hashlib,json,os,pathlib,re,shutil,subprocess,tempfile,urllib.request,xml.etree.ElementTree as ET
ROOT=pathlib.Path(__file__).resolve().parent
REPO='nikitasever/spbu-mail-pilot-updates'
def run(*args,**kw):subprocess.run(args,check=True,**kw)
def main():
    manifest=ET.parse(ROOT/'AndroidManifest.xml').getroot();prefix='{http://schemas.android.com/apk/res/android}'
    name=manifest.attrib[prefix+'versionName'];code=int(manifest.attrib[prefix+'versionCode']);tag='v'+name
    if not re.fullmatch(r'[0-9]+(?:\.[0-9]+){1,3}',name):raise ValueError('Invalid version name')
    try:
        urllib.request.urlopen('https://api.github.com/repos/'+REPO+'/releases/tags/'+tag).close();print('Version already published; keeping it immutable.');return
    except urllib.error.HTTPError as error:
        if error.code!=404:raise
    apk=ROOT/'build/spbu-mail-pilot.apk'
    with tempfile.TemporaryDirectory(prefix='apk-publish-') as temporary:
        directory=pathlib.Path(temporary);key=directory/'deploy-key';key.write_text(os.environ['UPDATE_DEPLOY_KEY'].replace('\r','').strip()+'\n');key.chmod(0o600)
        hosts=directory/'known_hosts';meta=json.load(urllib.request.urlopen('https://api.github.com/meta'));hosts.write_text(''.join('github.com '+k+'\n' for k in meta['ssh_keys']))
        env={**os.environ,'GIT_SSH_COMMAND':f'ssh -i "{key}" -o IdentitiesOnly=yes -o StrictHostKeyChecking=yes -o UserKnownHostsFile="{hosts}"'}
        checkout=directory/'release';run('git','clone','git@github.com:'+REPO+'.git',str(checkout),env=env)
        assets=checkout/'dist';assets.mkdir(exist_ok=True);filename='spbu-mail-pilot-'+name+'.apk';shutil.copyfile(apk,assets/filename)
        # Keep only the current APK in the working tree; previous releases remain available.
        for old in assets.glob('*.apk'):
            if old.name!=filename:old.unlink()
        update={'packageId':manifest.attrib['package'],'versionCode':code,'versionName':name,'url':'https://github.com/'+REPO+'/releases/download/'+tag+'/'+filename,'sha256':hashlib.sha256(apk.read_bytes()).hexdigest()}
        (assets/'update.json').write_text(json.dumps(update,indent=2)+'\n')
        (assets/'release-notes.txt').write_text('Версия '+name+' для личного тестирования. Установка поверх предыдущей версии сохраняет аккаунты и задачи.\n')
        run('git','config','user.name','APK Publisher',cwd=checkout);run('git','config','user.email','apk-publisher@users.noreply.github.com',cwd=checkout)
        run('git','add','dist',cwd=checkout);run('git','commit','-m','Release '+tag,cwd=checkout);run('git','push','origin','main',cwd=checkout,env=env)
if __name__=='__main__':main()
