"""Create only the independently owned ERP namespace; never manage existing apps."""
import hashlib
import json
import os
from pathlib import Path
import pwd
import shutil
import socket
import subprocess
import tarfile

BASE = Path('/srv/apps/duoxu-erp-demo')
USER = 'duoxu-erp'
MARKER = 'DUOXU-CLOUD-DEMO-V1'
ASSETS = {
    'redis': ('https://download.redis.io/releases/redis-7.2.16.tar.gz', '960a8ec15e34ff40e57ff16837b26b33bd81f2da6d24497bb63de532a323a18e'),
    'lego': ('https://github.com/go-acme/lego/releases/download/v5.5.2/lego_v5.5.2_linux_amd64.tar.gz', '2a35505089e7772c92e1e9ac144df91151ef2eca8568630db0ff91fca06d9bef'),
    'uv': ('https://github.com/astral-sh/uv/releases/download/0.13.1/uv-x86_64-unknown-linux-gnu.tar.gz', 'eadd9d27c134ab5ac1ffd4473b97a741407f3ea238288f03121fc4deddf90220'),
}


def run(*args, log=None):
    result = subprocess.run(args, stdout=log or subprocess.PIPE, stderr=log or subprocess.PIPE)
    if result.returncode:
        raise RuntimeError(f'Command failed: {args[0]} ({result.returncode}); inspect only ERP logs')
    return result.stdout


def own(path, uid, gid, mode=0o750):
    path.mkdir(parents=True, exist_ok=True)
    os.chown(path, uid, gid)
    path.chmod(mode)


def extract(archive, target):
    target.mkdir(parents=True, exist_ok=True)
    with tarfile.open(archive) as stream:
        stream.extractall(target, filter='data')


def prepare():
    assert os.geteuid() == 0
    assert str(BASE.resolve()) == str(BASE) and not BASE.is_symlink()
    marker = BASE / 'DEPLOYMENT.json'
    if BASE.exists() and not marker.exists():
        raise RuntimeError('Unidentified pre-existing application directory; refusing to adopt it')
    if marker.exists():
        assert json.loads(marker.read_text())['owner'] == MARKER
    for port in [13316, 16379, 14880, 48110]:
        with socket.socket() as probe:
            if probe.connect_ex(('127.0.0.1', port)) == 0:
                raise RuntimeError(f'Port {port} is already occupied; no process was stopped')
    BASE.mkdir(parents=True, exist_ok=True)
    BASE.chmod(0o755)
    try:
        user = pwd.getpwnam(USER)
        assert user.pw_dir == str(BASE), 'Existing account belongs to another application'
    except KeyError:
        run('/usr/sbin/useradd', '--system', '--home-dir', str(BASE), '--shell', '/usr/sbin/nologin', USER)
        user = pwd.getpwnam(USER)
    marker.write_text(json.dumps({'owner': MARKER, 'user': USER, 'ports': {'mysql':13316,'redis':16379,'backend':14880,'ocr':48110,'https':443}, 'protected': ['cross-studio.service','mysql.service','nginx.service','/srv/cross-studio','/opt/cross-studio','/var/www/cross-studio']}, indent=2))
    for name in ['config', 'data', 'logs', 'backups', 'runtime', 'ops', 'releases', 'incoming']:
        own(BASE / name, 0, user.pw_gid, 0o750)
    own(BASE / 'logs/backend', user.pw_uid, user.pw_gid)
    for name in ['mysql-error.log','redis.log','web-error.log','web-access.log']:
        path = BASE / 'logs' / name
        assert not path.is_symlink()
        path.touch(exist_ok=True)
        os.chown(path,user.pw_uid,user.pw_gid)
        path.chmod(0o640)
    incoming = BASE / 'incoming'
    ubuntu = pwd.getpwnam('ubuntu')
    os.chown(incoming, ubuntu.pw_uid, ubuntu.pw_gid)
    for name in ['mysql', 'redis', 'ocr', 'uploads', 'acme']:
        own(BASE / 'data' / name, user.pw_uid, user.pw_gid)
    own(BASE / 'runtime' / 'bin', 0, user.pw_gid)
    mysql = BASE / 'runtime/bin/mysqld'
    if not mysql.exists():
        shutil.copyfile('/usr/sbin/mysqld', mysql)
        mysql.chmod(0o755)
    profile = f'''# {MARKER}: this executable only; original mysqld profile is untouched.
#include <tunables/global>
{mysql} {{
  #include <abstractions/base>
  #include <abstractions/nameservice>
  #include <abstractions/user-tmp>
  network inet stream,
  network inet6 stream,
  network unix stream,
  /proc/*/status r,
  /proc/*/stat r,
  /sys/devices/system/cpu/** r,
  /sys/devices/system/node/** r,
  /usr/lib/mysql/plugin/ r,
  /usr/lib/mysql/plugin/** mr,
  /usr/share/mysql/ r,
  /usr/share/mysql/** r,
  /etc/ssl/openssl.cnf r,
  {mysql} mr,
  {BASE}/config/ r,
  {BASE}/config/** r,
  {BASE}/data/mysql/ r,
  {BASE}/data/mysql/** rwk,
  {BASE}/logs/ r,
  {BASE}/logs/mysql* rw,
  /run/systemd/notify w,
}}
'''
    profile_file = Path('/etc/apparmor.d/duoxu-erp-mysql')
    if profile_file.exists() and MARKER not in profile_file.read_text():
        raise RuntimeError('AppArmor name collision')
    profile_file.write_text(profile)
    run('/usr/sbin/apparmor_parser', '-r', str(profile_file))
    print('ERP namespace and dedicated enforced MySQL profile prepared', flush=True)
    for name, (url, expected) in ASSETS.items():
        archive = BASE / 'runtime' / (name + '.tar.gz')
        if not archive.exists():
            run('/usr/bin/curl', '--fail', '--location', '--silent', '--show-error', '--connect-timeout', '15', '--max-time', '180', '--output', str(archive), url)
        actual = hashlib.file_digest(archive.open('rb'), 'sha256').hexdigest()
        if actual != expected:
            raise RuntimeError(f'{name} digest mismatch; no executable started')
        target = BASE / 'runtime' / name
        if not target.exists():
            extract(archive, target)
        if name in ['uv', 'lego']:
            executable = next(p for p in target.rglob(name) if p.is_file())
            dest = BASE / 'runtime/bin' / name
            shutil.copyfile(executable, dest)
            dest.chmod(0o755)
        else:
            source = target / 'redis-7.2.16'
            for current, folders, files in os.walk(source):
                os.chown(current, user.pw_uid, user.pw_gid)
                for filename in files:
                    os.chown(Path(current) / filename, user.pw_uid, user.pw_gid)
            with (BASE / 'logs/redis-build.log').open('wb') as log:
                run('/usr/bin/systemd-run', '--unit=duoxu-erp-build', '--collect', '--wait', '--pipe', '--uid=' + USER, '-p', 'MemoryMax=768M', '-p', 'CPUQuota=150%', '/usr/bin/make', '-C', str(source), '-j2', 'MALLOC=libc', log=log)
            for executable in ['redis-server','redis-cli']:
                dest = BASE / 'runtime/bin' / executable
                shutil.copyfile(source / 'src' / executable, dest)
                dest.chmod(0o755)
        print(f'{name}: pinned archive verified and isolated runtime prepared', flush=True)
    (BASE / 'runtime/provenance.json').write_text(json.dumps(ASSETS, indent=2))
    print('PREPARE_OK: no original application unit, configuration or database was changed', flush=True)


if __name__ == '__main__':
    prepare()
