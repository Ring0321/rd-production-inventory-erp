"""Renew only DUOXU's certificate, restoring only DUOXU's gateway on failure."""
import fcntl
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess

BASE = Path('/srv/apps/duoxu-erp-demo')
WEB = 'duoxu-erp-web.service'


def main():
    assert os.geteuid() == 0
    assert json.loads((BASE / 'DEPLOYMENT.json').read_text())['owner'] == 'DUOXU-CLOUD-DEMO-V1'
    with (BASE / 'data/cert-renew.lock').open('w') as lock:
        os.chmod(lock.name, 0o600)
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        renew_if_needed()


def renew_if_needed():
    cert = BASE / 'config/tls/server.crt'
    if cert.exists() and subprocess.run(['/usr/bin/openssl','x509','-in',str(cert),'-checkend',str(72*3600),'-noout'], stdout=subprocess.DEVNULL).returncode == 0:
        print('Certificate has more than 72 hours left; gateway was not interrupted')
        return
    active = subprocess.run(['/usr/bin/systemctl','is-active','--quiet',WEB]).returncode == 0
    with socket.socket() as probe:
        if probe.connect_ex(('127.0.0.1',443)) == 0 and not active:
            raise RuntimeError('443 is owned by another service; renewal refused')
    if active:
        subprocess.run(['/usr/bin/systemctl','stop',WEB],check=True)
    try:
        subprocess.run([str(BASE / 'runtime/bin/lego'),'run','--accept-tos','--account-id','duoxu-erp-demo','--domains','101.42.182.117','--path',str(BASE / 'data/acme'),'--server','letsencrypt','--profile','shortlived','--tls','--tls.address',':443','--http-timeout','60','--cert.timeout','90','--renew-days','3','--no-random-sleep'],check=True,timeout=180)
        for extension, dest in [('crt','server.crt'),('key','server.key')]:
            source = BASE / 'data/acme/certificates' / ('101.42.182.117.' + extension)
            target = cert.parent / dest
            temporary = target.with_suffix(target.suffix + '.new')
            shutil.copyfile(source,temporary)
            os.chmod(temporary,0o640)
            os.chown(temporary,0,cert.parent.stat().st_gid)
            os.replace(temporary,target)
        print('ERP-only certificate updated')
    finally:
        if active:
            subprocess.run(['/usr/bin/systemctl','start',WEB],check=True)


if __name__ == '__main__':
    main()
