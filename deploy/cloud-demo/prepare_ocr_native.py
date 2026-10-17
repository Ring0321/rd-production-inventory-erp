"""Extract signed-distribution GL libraries privately; do not install host packages."""
from email.parser import Parser
import hashlib
import json
import os
from pathlib import Path
import pwd
import subprocess

BASE = Path('/srv/apps/duoxu-erp-demo')


def main():
    assert os.geteuid() == 0
    assert json.loads((BASE / 'DEPLOYMENT.json').read_text())['owner'] == 'DUOXU-CLOUD-DEMO-V1'
    target = BASE / 'runtime/ocr-native'
    target.mkdir(exist_ok=True,mode=0o755)
    user = pwd.getpwnam('duoxu-erp')
    downloads = target / 'packages'
    downloads.mkdir(exist_ok=True,mode=0o750)
    os.chown(downloads,user.pw_uid,user.pw_gid)
    os.chdir(downloads)
    records = []
    # Only runtime libraries; no driver, apt install, ldconfig, host paths or host package database changes.
    for name in ['libgl1','libglx0','libglvnd0','libx11-6','libxcb1','libxau6','libxdmcp6','libxext6','libbsd0','libmd0']:
        metadata = subprocess.check_output(['/usr/bin/apt-cache','show','--no-all-versions',name],text=True)
        info = Parser().parsestr(metadata)
        version, digest = info['Version'], info['SHA256']
        assert version and digest and len(digest) == 64
        filename = Path(info['Filename']).name
        archive = downloads / filename
        if not archive.exists():
            subprocess.run(['/usr/sbin/runuser','-u','duoxu-erp','--','/usr/bin/apt-get','download',name + '=' + version],check=True,stdout=subprocess.DEVNULL)
            candidates = [path for path in downloads.glob('*.deb') if subprocess.check_output(['/usr/bin/dpkg-deb','--field',str(path),'Package'],text=True).strip() == name and subprocess.check_output(['/usr/bin/dpkg-deb','--field',str(path),'Version'],text=True).strip() == version]
            assert len(candidates) == 1
            archive = candidates[0]
        assert hashlib.file_digest(archive.open('rb'),'sha256').hexdigest() == digest
        subprocess.run(['/usr/bin/dpkg-deb','--extract',str(archive),str(target)],check=True)
        records.append({'package':name,'version':version,'sha256':digest,'source':'host signed Ubuntu APT metadata; extracted only'})
    (target / 'provenance.json').write_text(json.dumps(records,indent=2))
    print('Private GL runtime extracted; system package state unchanged')


if __name__ == '__main__':
    main()
