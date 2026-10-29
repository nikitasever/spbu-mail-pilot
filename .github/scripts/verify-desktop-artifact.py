import hashlib,json,re,shutil
from pathlib import Path
source=Path('candidate');target=Path('verified');target.mkdir(exist_ok=True)
installers=list(source.rglob('*-setup.exe'));assert len(installers)==1
installer=installers[0]
sums=list(source.rglob('SHA256SUMS-windows.txt'));assert len(sums)==1
records=[line.strip() for line in sums[0].read_text(encoding='utf-8-sig').splitlines() if line.strip()]
expected=next(line[:64].lower() for line in records if line[64:].strip()==installer.name)
assert re.fullmatch('[0-9a-f]{64}',expected) and hashlib.sha256(installer.read_bytes()).hexdigest()==expected
name='spbu-mail-desktop-0.1.0-preview.7-windows-x64-setup.exe'
shutil.copy2(installer,target/name)
locks=list(source.rglob('Cargo.lock'));assert len(locks)==1;shutil.copy2(locks[0],target/'Cargo.lock')
info=dict(version='0.1.0',channel='preview.7',platform='windows-x64',file=name,sha256=expected,sizeBytes=installer.stat().st_size,sourceCommit='0d5c1488c0525c8c2a376a13f5f867335a007b3d',buildRun=40848315905,cargoLockSha256=hashlib.sha256(locks[0].read_bytes()).hexdigest())
(target/'build-info.json').write_text(json.dumps(info,indent=2)+'\n',encoding='utf-8')
print('Verified installer and resolved dependency lock from the successful Windows build.')
