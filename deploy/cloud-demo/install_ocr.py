"""Prepare a pinned, CPU-only Linux OCR runtime without changing system Python."""
import json
import os
from pathlib import Path
import pwd
import shutil
import subprocess
import sys

sys.path.insert(0,str(Path(__file__).resolve().parent))
from install_release import BASE, USER, MARKER, command, service, write


def install():
    assert os.geteuid() == 0
    assert json.loads((BASE / 'DEPLOYMENT.json').read_text())['owner'] == MARKER
    user = pwd.getpwnam(USER)
    for path in [BASE / 'runtime/python', BASE / 'runtime/ocr-venv', BASE / 'data/ocr/uv-cache']:
        path.mkdir(parents=True,exist_ok=True)
        os.chown(path,user.pw_uid,user.pw_gid)
    os.chdir(BASE)
    args = ['/usr/sbin/runuser','-u',USER,'--','/usr/bin/env','UV_PYTHON_INSTALL_DIR=' + str(BASE / 'runtime/python'),'UV_CACHE_DIR=' + str(BASE / 'data/ocr/uv-cache'),'HOME=' + str(BASE / 'data/ocr'),str(BASE / 'runtime/bin/uv'),'--no-config']
    with (BASE / 'logs/ocr-setup.log').open('ab') as log:
        command(*args,'python','install','3.12.12',log=log)
        venv = BASE / 'runtime/ocr-venv'
        if not (venv / 'bin/python').exists():
            command(*args,'venv','--python','3.12.12',str(venv),'--allow-existing',log=log)
        command(*args,'pip','install','--python',str(venv / 'bin/python'),'-r',str(BASE / 'current/ocr/requirements.txt'),log=log)
        command(*args,'pip','check','--python',str(venv / 'bin/python'),log=log)
    freeze = command(*args,'pip','freeze','--python',str(venv / 'bin/python')).decode()
    write(BASE / 'runtime/ocr-linux-freeze.txt',freeze,0o644)
    runtime = BASE / 'current/ocr/.runtime'
    models = BASE / 'data/ocr/models'
    if not models.exists():
        shutil.move(str(runtime / 'models'),str(models))
    if not runtime.is_symlink():
        assert not list(runtime.iterdir()), 'Unexpected OCR runtime data; refusing replacement'
        runtime.rmdir()
        runtime.symlink_to(BASE / 'data/ocr')
    # Models are immutable to the service; only its own home/cache/temp areas are writable.
    for directory, _, files in os.walk(models):
        os.chown(directory,0,user.pw_gid)
        os.chmod(directory,0o750)
        for filename in files:
            os.chown(Path(directory) / filename,0,user.pw_gid)
            os.chmod(Path(directory) / filename,0o640)
    fixtures = BASE / 'data/ocr/fixtures'
    fixtures.mkdir(exist_ok=True,mode=0o750)
    os.chown(fixtures,0,user.pw_gid)
    for extension in ['png','jpeg','pdf']:
        source = BASE / 'incoming' / ('synthetic-pickup.' + extension)
        target = fixtures / source.name
        shutil.copyfile(source,target)
        os.chown(target,0,user.pw_gid)
        os.chmod(target,0o640)
    if (BASE / 'incoming/diagnose_ocr.py').exists():
        write(BASE / 'ops/diagnose_ocr.py',(BASE / 'incoming/diagnose_ocr.py').read_text(),0o750)
    service('duoxu-erp-ocr.service', f'{venv}/bin/python -m document_ocr.server --port 48110', '1700M', '', f'''WorkingDirectory={BASE}/current/ocr
Environment=PYTHONDONTWRITEBYTECODE=1
Environment=HOME={BASE}/data/ocr
Environment=OMP_NUM_THREADS=2
Environment=OPENBLAS_NUM_THREADS=2
Environment=LD_LIBRARY_PATH={BASE}/runtime/ocr-native/usr/lib/x86_64-linux-gnu
IPAddressDeny=any
IPAddressAllow=localhost
StandardOutput=append:{BASE}/logs/ocr-service.log
StandardError=append:{BASE}/logs/ocr-service.log
''')
    command('/usr/bin/systemctl','daemon-reload')
    command('/usr/bin/systemctl','enable','--now','duoxu-erp-ocr.service')
    print('OCR_RUNTIME_OK: isolated Python 3.12, pinned packages, local models; real inference still requires verification',flush=True)


if __name__ == '__main__':
    install()
