"""ERP-only backup, restoration rehearsal, health and inventory fingerprints."""
import argparse
from datetime import datetime, timezone
import gzip
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import urllib.request

sys.path.insert(0,str(Path(__file__).resolve().parent))
from install_release import BASE, MARKER, command, mysql, write, unit

TABLES = ['rd_package_unit','rd_stock_lot','rd_stock_transaction','rd_location','rd_loading_task','rd_putaway_task']


def fingerprint(database='duoxu_erp_demo'):
    assert database == 'duoxu_erp_demo' or database.startswith('duoxu_erp_rehearsal_')
    return {table:hashlib.sha256(mysql(f'SELECT * FROM {database}.{table} ORDER BY id;').encode()).hexdigest() for table in TABLES}


def health():
    names = ['mysql','redis','backend','ocr','web']
    units = {name:subprocess.run(['/usr/bin/systemctl','is-active','--quiet','duoxu-erp-' + name + '.service']).returncode == 0 for name in names}
    request = urllib.request.Request('http://127.0.0.1:14880/admin-api/system/auth/capabilities',headers={'tenant-id':'1'})
    with urllib.request.urlopen(request,timeout=15) as response:
        backend = json.load(response)['code'] == 0 and mysql('SELECT 1;') == '1'
    secret = json.loads((BASE / 'config/secrets.json').read_text())
    redis = subprocess.run([str(BASE / 'runtime/bin/redis-cli'),'-h','127.0.0.1','-p','16379','PING'],env=dict(os.environ,REDISCLI_AUTH=secret['redis']),capture_output=True,timeout=10)
    backend = backend and redis.returncode == 0 and redis.stdout.strip() == b'PONG'
    with urllib.request.urlopen('http://127.0.0.1:48110/status',timeout=10) as response:
        ocr = json.load(response)['available']
    certificate = subprocess.run(['/usr/bin/openssl','x509','-in',str(BASE / 'config/tls/server.crt'),'-checkend',str(48*3600),'-noout'],stdout=subprocess.DEVNULL).returncode == 0
    result = {'checkedAt':datetime.now(timezone.utc).isoformat(),'units':units,'backendHealthy':backend,'ocrRuntimeAvailable':ocr,'certificateHas48Hours':certificate,'allHealthy':all(units.values()) and backend and ocr and certificate}
    write(BASE / 'logs/health-latest.json',json.dumps(result,indent=2),0o644)
    print(json.dumps(result),flush=True)
    assert result['allHealthy']


def backup(verify=False):
    stamp = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')
    target = BASE / 'backups' / ('duoxu-demo-' + stamp + '.sql.gz')
    assert target.resolve().parent == BASE / 'backups' and not target.exists()
    dump = command('/usr/bin/mysqldump','--defaults-file=' + str(BASE / 'config/mysql-root.cnf'),'--single-transaction','--routines','--events','--triggers','--default-character-set=utf8mb4','--no-tablespaces','--set-gtid-purged=OFF','duoxu_erp_demo')
    with target.open('xb') as file:
        os.chmod(target,0o600)
        with gzip.GzipFile(fileobj=file,mode='wb') as compressed:
            compressed.write(dump)
    result = {'backup':target.name,'sha256':hashlib.file_digest(target.open('rb'),'sha256').hexdigest(),'bytes':target.stat().st_size,'verifiedRestore':False,'originalDatabasesAccessed':False}
    if verify:
        database = 'duoxu_erp_rehearsal_' + stamp.lower()
        assert mysql(f"SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='{database}';") == '0'
        before = fingerprint()
        mysql(f'CREATE DATABASE {database} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;')
        mysql(f'USE {database};\n' + dump.decode('utf-8'))
        assert fingerprint(database) == before
        assert fingerprint() == before
        # This name was generated here, checked absent and created in this independently owned instance only.
        mysql('DROP DATABASE ' + database + ';')
        result['verifiedRestore'] = True
    write(target.with_suffix(target.suffix + '.json'),json.dumps(result,indent=2),0o600)
    write(BASE / 'logs/backup-latest.json',json.dumps(result,indent=2),0o644)
    print(json.dumps(result),flush=True)


def install():
    source = BASE / 'ops/operations.py'
    for name in ['operations.py','install_release.py']:
        write(BASE / 'ops' / name,(BASE / 'incoming' / name).read_text(),0o700)
    unit('duoxu-erp-backup.service',f'''[Unit]
Description=Backup only DUOXU demo database
After=duoxu-erp-mysql.service
[Service]
Type=oneshot
ExecStart=/usr/bin/python3 {source} --backup
UMask=0077
StandardOutput=append:{BASE}/logs/backup-service.log
StandardError=append:{BASE}/logs/backup-service.log
''')
    unit('duoxu-erp-backup.timer','''[Unit]
Description=Daily DUOXU demo backup
[Timer]
OnCalendar=*-*-* 03:20:00
RandomizedDelaySec=15min
Persistent=true
[Install]
WantedBy=timers.target
''')
    unit('duoxu-erp-health.service',f'''[Unit]
Description=Check only DUOXU demo services
[Service]
Type=oneshot
ExecStart=/usr/bin/python3 {source} --health
StandardOutput=append:{BASE}/logs/health-service.log
StandardError=append:{BASE}/logs/health-service.log
''')
    unit('duoxu-erp-health.timer','''[Unit]
Description=DUOXU demo health observation
[Timer]
OnBootSec=5min
OnUnitActiveSec=15min
[Install]
WantedBy=timers.target
''')
    command('/usr/bin/systemctl','daemon-reload')
    command('/usr/bin/systemctl','enable','--now','duoxu-erp-backup.timer','duoxu-erp-health.timer')
    print('ERP-only backup and observation timers installed')


if __name__ == '__main__':
    assert os.geteuid() == 0
    assert json.loads((BASE / 'DEPLOYMENT.json').read_text())['owner'] == MARKER
    assert mysql('SELECT @@datadir;').rstrip('/') == str(BASE / 'data/mysql')
    parser = argparse.ArgumentParser()
    parser.add_argument('--install',action='store_true')
    parser.add_argument('--backup',action='store_true')
    parser.add_argument('--verify',action='store_true')
    parser.add_argument('--health',action='store_true')
    parser.add_argument('--fingerprint',action='store_true')
    args = parser.parse_args()
    if args.install: install()
    if args.backup: backup(args.verify)
    if args.health: health()
    if args.fingerprint: print(json.dumps(fingerprint()),flush=True)
