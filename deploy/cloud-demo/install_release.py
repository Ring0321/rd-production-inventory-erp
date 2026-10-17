"""Install a verified demo bundle exclusively into the marked ERP namespace."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import pwd
import secrets
import shutil
import subprocess
import tarfile
import tempfile
import time
import urllib.request

BASE = Path('/srv/apps/duoxu-erp-demo')
MARKER = 'DUOXU-CLOUD-DEMO-V1'
USER = 'duoxu-erp'


def command(*args, input=None, log=None):
    result = subprocess.run(args, input=input, stdout=log or subprocess.PIPE, stderr=log or subprocess.PIPE)
    if result.returncode:
        raise RuntimeError(f'{Path(args[0]).name} failed ({result.returncode}); inspect ERP-only logs')
    return result.stdout


def write(path, content, mode=0o640):
    path.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary = tempfile.mkstemp(prefix='.' + path.name + '-',dir=path.parent)
    try:
        os.fchmod(descriptor,mode)
        os.fchown(descriptor,0,pwd.getpwnam(USER).pw_gid)
        with os.fdopen(descriptor,'w',encoding='utf-8') as stream:
            stream.write(content)
        # Replacing a link does not follow its target, unlike write_text/chown on a pathname.
        os.replace(temporary,path)
    finally:
        if os.path.lexists(temporary):
            os.unlink(temporary)


def unit(name, text):
    path = Path('/etc/systemd/system') / name
    if path.exists() and MARKER not in path.read_text():
        raise RuntimeError('Unidentified systemd name collision: ' + name)
    write(path, '# ' + MARKER + '\n' + text, 0o644)


def service(name, executable, memory, after='', extra=''):
    unit(name, f'''[Unit]
Description=DUOXU ERP demo {name}
After=network.target {after}
[Service]
User={USER}
Group={USER}
Slice=duoxu-erp.slice
WorkingDirectory={BASE}
ExecStart={executable}
Restart=on-failure
RestartSec=8
TimeoutStartSec=180
TimeoutStopSec=45
MemoryMax={memory}
CPUQuota=200%
UMask=0027
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths={BASE}/data {BASE}/logs
{extra}
[Install]
WantedBy=multi-user.target
''')


def mysql(sql, root=True):
    config = BASE / 'config/mysql-root.cnf'
    args = ['/usr/bin/mysql', '--defaults-file=' + str(config)] if config.exists() else ['/usr/bin/mysql', '--no-defaults', '--socket=' + str(BASE / 'data/mysql/mysql.sock'), '--user=root']
    args += ['--default-character-set=utf8mb4', '--batch', '--raw', '--skip-column-names']
    return command(*args, input=sql.encode()).decode().strip()


def api(path, token=None, body=None, method=None):
    headers = {'tenant-id': '1', 'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    request = urllib.request.Request('http://127.0.0.1:14880/admin-api' + path, data=None if body is None else json.dumps(body).encode(), headers=headers, method=method)
    with urllib.request.urlopen(request, timeout=30) as response:
        value = json.load(response)
    if value.get('code') != 0:
        raise RuntimeError(f'ERP API rejected {path}; response withheld')
    return value.get('data')


def install(bundle, digest, restart_empty_import=False, schema_fix=False):
    assert os.geteuid() == 0
    assert json.loads((BASE / 'DEPLOYMENT.json').read_text())['owner'] == MARKER
    assert hashlib.file_digest(bundle.open('rb'), 'sha256').hexdigest() == digest
    release = BASE / 'releases' / digest[:12]
    if release.exists():
        assert (release / 'provenance.json').exists(), 'Incomplete release; inspect rather than overwrite'
    else:
        release.mkdir(mode=0o755)
        with tarfile.open(bundle) as archive:
            archive.extractall(release, filter='data')
        for directory, _, files in os.walk(release):
            Path(directory).chmod(0o755)
            for file in files:
                (Path(directory) / file).chmod(0o644)
    assert json.loads((release / 'provenance.json').read_text())['packageCount'] == 30
    if schema_fix:
        assert not (BASE / 'data/DEMO_IMPORTED.json').exists()
        fixes = {'rd_erp_clean.sql':'1dc588a5ad8eb7a800fcb6f565a01b6c3c3d9aed74b9de1d16ae0bf9ed477f77','rd_erp_upgrade_slim.sql':'93513da3491e05084fb626d5d7e734d68df6a9d94d136d1686268bb41242cdb0'}
        for name, expected in fixes.items():
            source = BASE / 'incoming' / name
            assert hashlib.file_digest(source.open('rb'),'sha256').hexdigest() == expected
            shutil.copyfile(source,release / 'sql' / name)
        write(release / 'schema-fix.json',json.dumps({'reason':'Preserve MySQL client DELIMITER directives during customer-seed sanitization','sha256':fixes}),0o644)
    current = BASE / 'current'
    if current.exists():
        assert current.is_symlink() and current.resolve() == release, 'Use documented release switch, not an overwrite'
    else:
        current.symlink_to(release)
    # The application cannot edit service definitions, environment files or release sources.
    for name in ['config', 'ops', 'releases', 'runtime']:
        os.chown(BASE / name, 0, pwd.getpwnam(USER).pw_gid)
    secret_path = BASE / 'config/secrets.json'
    if not secret_path.exists():
        values = {key: secrets.token_urlsafe(27) for key in ['db', 'dbRoot', 'redis', 'admin']}
        values['roles'] = {role: secrets.token_urlsafe(12) for role in ['production','quality','warehouse','driver','sales','viewer']}
        write(secret_path, json.dumps(values), 0o600)
    values = json.loads(secret_path.read_text())
    if not (BASE / 'data/ACCOUNTS_ACTIVATED.json').exists() and any(len(value) > 16 for value in values['roles'].values()):
        values['roles'] = {role:secrets.token_urlsafe(12) for role in values['roles']}
        write(secret_path,json.dumps(values),0o600)
    write(BASE / 'config/mysql.cnf', f'''[mysqld]
basedir=/usr
datadir={BASE}/data/mysql
socket={BASE}/data/mysql/mysql.sock
pid-file={BASE}/data/mysql/mysql.pid
port=13316
bind-address=127.0.0.1
mysqlx=OFF
skip-name-resolve
max_connections=32
innodb_buffer_pool_size=256M
innodb_redo_log_capacity=128M
performance_schema=OFF
local_infile=OFF
secure_file_priv=NULL
log_error={BASE}/logs/mysql-error.log
''')
    write(BASE / 'config/redis.conf', f'''bind 127.0.0.1
port 16379
protected-mode yes
requirepass {values['redis']}
dir {BASE}/data/redis
appendonly yes
maxmemory 64mb
maxmemory-policy noeviction
logfile {BASE}/logs/redis.log
''')
    url = 'jdbc:mysql://127.0.0.1:13316/duoxu_erp_demo?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8&rewriteBatchedStatements=true'
    env = {'RD_DB_USERNAME':'duoxu_demo','RD_DB_PASSWORD':values['db'],'RD_MASTER_DATASOURCE_URL':url,'RD_SLAVE_DATASOURCE_URL':url,'RD_REDIS_HOST':'127.0.0.1','RD_REDIS_PORT':'16379','RD_REDIS_PASSWORD':values['redis'],'RD_ADMIN_INITIAL_PASSWORD':values['admin'],'RD_AI_ENABLED':'false','RD_AI_COST_APPROVED':'false','RD_CAPTCHA_ENABLED':'false','RD_DOCUMENT_OCR_ENABLED':'true','RD_DIGITAL_LINK_BASE_URL':'https://101.42.182.117/pkg','RD_SMS_ENABLED':'false','RD_PASSWORD_RESET_ENABLED':'false','RD_SOCIAL_ENABLED':'false','TZ':'Asia/Shanghai'}
    write(BASE / 'config/backend.env', '\n'.join(key + '=' + value for key, value in env.items()) + '\n')
    trial = (BASE / 'incoming/application-trial.yaml').read_text()
    write(BASE / 'config/application-trial.yaml', trial)
    write(BASE / 'config/application-cloud.yaml', f'''server:
  address: 127.0.0.1
  port: 14880
spring:
  datasource:
    dynamic:
      druid:
        initial-size: 1
        min-idle: 1
        max-active: 12
logging:
  file:
    path: {BASE}/logs/backend
''')
    unit('duoxu-erp.slice', '''[Unit]
Description=Independent DUOXU ERP demo resource budget
[Slice]
MemoryMax=5G
CPUQuota=250%
TasksMax=768
''')
    service('duoxu-erp-mysql.service', f'{BASE}/runtime/bin/mysqld --defaults-file={BASE}/config/mysql.cnf', '900M')
    service('duoxu-erp-redis.service', f'{BASE}/runtime/bin/redis-server {BASE}/config/redis.conf', '160M')
    service('duoxu-erp-backend.service', f'/usr/bin/java -Xms256m -Xmx1280m -XX:MaxMetaspaceSize=384m -Dfile.encoding=UTF-8 -Duser.home={BASE}/data -jar {BASE}/current/backend/rd-erp-server.jar --spring.profiles.active=trial --spring.config.additional-location=file:{BASE}/config/ --logging.file.path={BASE}/logs/backend', '2300M', 'duoxu-erp-mysql.service duoxu-erp-redis.service', f'EnvironmentFile={BASE}/config/backend.env\nStandardOutput=append:{BASE}/logs/backend-console.log\nStandardError=append:{BASE}/logs/backend-console.log\n')
    # Separate cloud config overlays trial config without changing the built JAR.
    path = Path('/etc/systemd/system/duoxu-erp-backend.service')
    write(path, path.read_text().replace('--spring.profiles.active=trial ', '--spring.profiles.active=trial,cloud '), 0o644)
    command('/usr/bin/systemctl', 'daemon-reload')
    datadir = BASE / 'data/mysql'
    if not (datadir / 'auto.cnf').exists():
        assert not list(datadir.iterdir()), 'Refuse initialization of a nonempty data directory'
        with (BASE / 'logs/mysql-initialize.log').open('wb') as log:
            command('/usr/sbin/runuser', '-u', USER, '--', str(BASE / 'runtime/bin/mysqld'), '--defaults-file=' + str(BASE / 'config/mysql.cnf'), '--initialize-insecure', log=log)
    command('/usr/bin/systemctl', 'start', 'duoxu-erp-mysql.service', 'duoxu-erp-redis.service')
    for _ in range(60):
        if (datadir / 'mysql.sock').exists():
            try:
                actual = mysql('SELECT @@datadir;')
                assert actual.rstrip('/') == str(datadir), 'Wrong database instance; all imports refused'
                break
            except RuntimeError:
                pass
        time.sleep(2)
    else:
        raise RuntimeError('Independent MySQL startup failed; inspect ERP-only log')
    imported = BASE / 'data/DEMO_IMPORTED.json'
    if not imported.exists():
        if restart_empty_import:
            assert actual.rstrip('/') == str(datadir)
            assert mysql('SELECT COUNT(*) FROM duoxu_erp_demo.rd_package_unit;') == '0'
            assert mysql('SELECT COUNT(*) FROM duoxu_erp_demo.rd_production_task;') == '0'
            mysql('DROP DATABASE duoxu_erp_demo;')
        assert mysql("SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='duoxu_erp_demo';") == '0', 'Refuse reimport over an existing schema'
        mysql("CREATE DATABASE duoxu_erp_demo CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;")
        for name in ['rd_erp_clean.sql','rd_erp_upgrade_slim.sql','rd_visual_documents.sql','synthetic-showcase.sql']:
            with (BASE / ('logs/import-' + name + '.log')).open('wb') as log:
                command('/usr/bin/mysql', '--no-defaults', '--socket=' + str(datadir / 'mysql.sock'), '--user=root', '--default-character-set=utf8mb4', 'duoxu_erp_demo', input=(release / 'sql' / name).read_bytes(), log=log)
        assert mysql('SELECT COUNT(*),SUM(package_weight) FROM duoxu_erp_demo.rd_package_unit;') == '30\t29800.000000'
        mysql(f"CREATE USER 'duoxu_demo'@'127.0.0.1' IDENTIFIED BY '{values['db']}'; GRANT ALL PRIVILEGES ON duoxu_erp_demo.* TO 'duoxu_demo'@'127.0.0.1'; ALTER USER 'root'@'localhost' IDENTIFIED BY '{values['dbRoot']}';")
        write(BASE / 'config/mysql-root.cnf', f"[client]\nuser=root\npassword={values['dbRoot']}\nsocket={datadir}/mysql.sock\n", 0o600)
        write(imported, json.dumps({'marker':MARKER,'bundleSha256':digest,'packageCount':30,'weightKg':29800}), 0o644)
    command('/usr/bin/systemctl', 'enable', 'duoxu-erp-mysql.service', 'duoxu-erp-redis.service', 'duoxu-erp-backend.service')
    command('/usr/bin/systemctl', 'start', 'duoxu-erp-backend.service')
    for _ in range(120):
        try:
            login = api('/system/auth/login', body={'username':'admin','password':values['admin']})
            token = login['accessToken']
            break
        except Exception:
            time.sleep(3)
    else:
        raise RuntimeError('Backend did not become ready; inspect ERP console log')
    activation = BASE / 'data/ACCOUNTS_ACTIVATED.json'
    if not activation.exists():
        for offset, role in enumerate(['production','quality','warehouse','driver','sales','viewer']):
            api('/system/user/update-password', token, {'id':229 + offset,'password':values['roles'][role]}, 'PUT')
        write(activation, json.dumps({'activated':True,'roles':list(values['roles'])}), 0o644)
    print('CORE_OK: separate database, Redis, backend and new role accounts are ready', flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--bundle', type=Path, required=True)
    parser.add_argument('--sha256', required=True)
    parser.add_argument('--restart-empty-import', action='store_true')
    parser.add_argument('--schema-fix', action='store_true')
    args = parser.parse_args()
    install(args.bundle, args.sha256, args.restart_empty_import, args.schema_fix)
