"""Protect root-operated control directories without changing any original service."""
import json
import os
from pathlib import Path
import pwd
import subprocess
import sys
import time
import urllib.request

sys.path.insert(0,str(Path(__file__).resolve().parent))
from install_release import BASE, MARKER, write


def main():
    assert os.geteuid() == 0
    assert json.loads((BASE / 'DEPLOYMENT.json').read_text())['owner'] == MARKER
    user = pwd.getpwnam('duoxu-erp')
    for name in ['config','data','logs','backups','runtime','ops','releases']:
        path = BASE / name
        assert path.resolve() == path and not path.is_symlink()
        os.chown(path,0,user.pw_gid)
        path.chmod(0o700 if name == 'backups' else 0o750)
    application_logs = BASE / 'logs/backend'
    application_logs.mkdir(exist_ok=True,mode=0o750)
    os.chown(application_logs,user.pw_uid,user.pw_gid)
    for name in ['mysql-error.log','redis.log','web-error.log','web-access.log']:
        path = BASE / 'logs' / name
        assert path.is_file() and not path.is_symlink()
        os.chown(path,user.pw_uid,user.pw_gid)
        path.chmod(0o640)
    # Console/maintenance files cannot be replaced by the application before a root service opens them.
    for path in (BASE / 'logs').glob('*'):
        if path.is_file() and path.name not in ['mysql-error.log','redis.log','web-error.log','web-access.log','spring.log']:
            assert not path.is_symlink()
            os.chown(path,0,user.pw_gid)
            path.chmod(0o640)
    unit = Path('/etc/systemd/system/duoxu-erp-backend.service')
    original = unit.read_text()
    assert MARKER in original
    updated = original.replace(f'--logging.file.path={BASE}/logs\n',f'--logging.file.path={BASE}/logs/backend\n')
    write(unit,updated,0o644)
    write(BASE / 'ops/install_release.py',(BASE / 'incoming/install_release.py').read_text(),0o700)
    if updated != original:
        subprocess.run(['/usr/bin/systemctl','daemon-reload'],check=True)
        subprocess.run(['/usr/bin/systemctl','restart','duoxu-erp-backend.service'],check=True)
        for _ in range(80):
            try:
                with urllib.request.urlopen('http://127.0.0.1:14880/admin-api/system/auth/capabilities',timeout=3) as response:
                    assert json.load(response)['code'] == 0
                break
            except Exception:
                time.sleep(2)
        else:
            raise RuntimeError('ERP restart not healthy; original services were not managed')
    print('ERP control directories protected; root writes are atomic; original services were not managed')


if __name__ == '__main__':
    main()
