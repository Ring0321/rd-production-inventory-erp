"""Incremental read-only query release; never bootstrap data, users or model settings."""
import argparse
from datetime import datetime, timezone
from decimal import Decimal
import hashlib
from io import BytesIO
import json
import os
from pathlib import Path
import re
import shutil
import signal
import subprocess
import sys
import zipfile
from zoneinfo import ZoneInfo

from configure_deepseek_release import BASE, MARKER, USER, protected_services, validate_archive, wait_for_backend

UNIT = Path('/etc/systemd/system/duoxu-erp-backend.service')
NGINX = BASE / 'config/nginx.conf'
TABLES = ('rd_package_unit', 'rd_quality_inspection', 'rd_quality_inspection_member',
          'rd_stock_lot', 'rd_stock_transaction', 'rd_location', 'rd_loading_task', 'rd_putaway_task')
PROTECTED = ('/home/ubuntu/cross-studio-backend-0.1.0-SNAPSHOT.jar',
             '/var/www/cross-studio/index.html', '/etc/nginx/nginx.conf',
             '/etc/nginx/sites-enabled/cross-studio')


def sha(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def backend_path(original):
    matches = re.findall(r' -jar (\S+)(?= )', original)
    if MARKER not in original or len(matches) != 1:
        raise ValueError('Unknown ERP unit')
    jar = matches[0]
    if not re.fullmatch(re.escape(BASE.as_posix()) + r'/(?:current/backend|releases/[a-zA-Z0-9-]+)/rd-erp-server\.jar', jar):
        raise ValueError('Backend outside ERP releases')
    return Path(jar)


def replace_backend(original, target):
    old = backend_path(original)
    if target.parent.parent != BASE / 'releases' or target.name != 'rd-erp-server.jar':
        raise ValueError('Release target rejected')
    return original.replace(' -jar ' + old.as_posix() + ' ', ' -jar ' + target.as_posix() + ' ')


def incoming(path, expected):
    if path.is_symlink() or path.resolve().parent != BASE / 'incoming' or not path.is_file():
        raise ValueError('Incoming archive rejected')
    if not re.fullmatch(r'[0-9a-f]{64}', expected) or sha(path) != expected:
        raise ValueError('Archive checksum rejected')


def validate_work_archive(path):
    validate_archive(path)
    with zipfile.ZipFile(path) as archive:
        module = next(name for name in archive.namelist()
                      if name.startswith('BOOT-INF/lib/rd-erp-inventory-') and name.endswith('.jar'))
        with zipfile.ZipFile(BytesIO(archive.read(module))) as inventory:
            for name in ('service/workassistant/RdWorkQueryService',
                         'dal/mysql/workassistant/RdWorkQueryMapper',
                         'controller/admin/workassistant/RdWorkAssistantController'):
                if 'com/ruiding/erp/' + name + '.class' not in inventory.namelist():
                    raise ValueError('Read-only work query module missing')


def snapshot():
    private = [BASE / 'config' / name for name in
               ('backend.env', 'redis.conf', 'secrets.json', 'public-demo-login.json')]
    secret = json.loads((BASE / 'config/secrets.json').read_text())['redis']
    period = datetime.now(ZoneInfo('Asia/Shanghai')).strftime('%Y-%m')
    budget = subprocess.check_output(
        [str(BASE / 'runtime/bin/redis-cli'), '-h', '127.0.0.1', '-p', '16379', '--raw',
         'GET', 'rd:ai-budget:{erp}:DEEPSEEK:' + period],
        env=dict(os.environ, REDISCLI_AUTH=secret), timeout=10, text=True).strip()
    return {'services': protected_services(), 'protected': {name: sha(name) for name in PROTECTED},
            'private': {str(name): sha(name) for name in private}, 'budget': budget,
            'tables': {table: hashlib.sha256(mysql(
                'SELECT * FROM duoxu_erp_demo.' + table + ' ORDER BY id;').encode()).hexdigest()
                       for table in TABLES}}


def oracle():
    result = {}
    for query, where in [('Q03', "status='QC_PENDING'"),
                         ('Q05', "status IN ('QC_PASSED','INBOUND_PENDING')")]:
        count, weight = mysql('SELECT COUNT(*),COALESCE(SUM(package_weight),0) '
                              'FROM duoxu_erp_demo.rd_package_unit WHERE tenant_id=1 AND deleted=0 AND ' + where + ';').split('\t')
        result[query] = {'count': int(count), 'weight': weight}
    result['Q04'] = {'count': int(mysql("SELECT COUNT(*) FROM duoxu_erp_demo.rd_quality_inspection "
                                       "WHERE tenant_id=1 AND deleted=0 AND status='SUBMITTED';"))}
    return result


def verify_response(query, value, expected):
    primary = 'BUSINESS_OBJECT_COUNT' if query == 'Q04' else 'DISTINCT_PACKAGE_COUNT'
    if value.get('quality') not in ('KNOWN', 'PARTIAL'):
        raise ValueError('Source query unavailable')
    if value.get('scope') != 'TENANT_AUTHORIZED' or not isinstance(value.get('asOf'), str):
        raise ValueError('Wire scope or time contract rejected')
    metric = value['metrics'][primary]
    if metric['quality'] != 'KNOWN' or Decimal(metric['value']) != expected['count']:
        raise ValueError('Aggregate differs from actual authorized source')
    if query != 'Q04':
        if Decimal(value['metrics']['REGISTERED_WEIGHT_KG']['value']) != Decimal(expected['weight']):
            raise ValueError('Registered weight differs from actual source')
    if value['quality'] == 'KNOWN' and value['total'] != expected['count']:
        raise ValueError('Paged total differs from aggregate')
    return {'quality': value['quality'], 'count': expected['count'], 'rows': len(value['items']),
            'asOf': value['asOf'], 'sourceAggregateMatched': True}


def verify_queries():
    public = json.loads((BASE / 'config/public-demo-login.json').read_text())
    token = api('/system/auth/login', body={key: public[key] for key in ('username', 'password')})['accessToken']
    expected = oracle()
    queries = {query: verify_response(query, api('/rd/work-assistant/query?queryType=' + query, token), expected[query])
               for query in ('Q03', 'Q04', 'Q05')}
    model = api('/rd/ai/package/context?packageCode=LD20261015A0014', token)
    if model.get('providerState') != 'READY' or Decimal(str(model['provider']['monthlyBudgetCny'])) != 20:
        raise ValueError('Approved model readiness or budget changed')
    return queries


def restore_frontend(original):
    if NGINX.read_text() == original:
        return
    write(NGINX, original)
    command('/usr/sbin/nginx', '-t', '-g', 'user duoxu-erp;',
            '-p', str(BASE / 'data/web') + '/', '-c', str(NGINX))
    pid = int(command('/usr/bin/systemctl', 'show', 'duoxu-erp-web.service', '--property=MainPID', '--value'))
    if pid <= 1:
        raise ValueError('ERP gateway PID invalid')
    os.kill(pid, signal.SIGHUP)


def main():
    global api, command, mysql, write
    import pwd
    from configure_demo_login import deploy_frontend
    from install_release import api, command, mysql, write
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--inspect', action='store_true')
    parser.add_argument('--jar', type=Path)
    parser.add_argument('--sha256')
    parser.add_argument('--expected-old-sha256')
    parser.add_argument('--frontend', type=Path)
    parser.add_argument('--frontend-sha256')
    args = parser.parse_args()
    if os.geteuid() != 0 or BASE.is_symlink() or BASE.resolve() != BASE:
        raise ValueError('ERP namespace rejected')
    if json.loads((BASE / 'DEPLOYMENT.json').read_text()).get('owner') != MARKER:
        raise ValueError('ERP marker rejected')
    for path in (BASE, BASE / 'config', BASE / 'releases', UNIT, NGINX):
        if path.is_symlink() or path.stat().st_uid != 0:
            raise ValueError('Controlled path rejected')
    if mysql('SELECT @@datadir;').rstrip('/') != str(BASE / 'data/mysql'):
        raise ValueError('Independent ERP database required')
    original_unit, original_nginx = UNIT.read_text(), NGINX.read_text()
    old = backend_path(original_unit)
    if not old.resolve().is_relative_to(BASE / 'releases'):
        raise ValueError('Prior backend target rejected')
    old_sha = sha(old)
    if args.inspect:
        print(json.dumps({'backend': str(old), 'backendSha256': old_sha, 'sourceCounts': oracle(),
                          'protectedServicesActive': all('ActiveState=active' in state for state in protected_services().values()),
                          'databaseIsolated': True, 'credentialsPrinted': False}))
        return
    if not all((args.jar, args.sha256, args.expected_old_sha256, args.frontend, args.frontend_sha256)):
        raise ValueError('Explicit old and new artifact checksums required')
    import fcntl
    lock = (BASE / 'config/work-query-release.lock').open('a')
    os.fchmod(lock.fileno(), 0o600)
    fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
    if old_sha != args.expected_old_sha256:
        raise ValueError('Cloud baseline changed; inspect before publishing')
    incoming(args.jar, args.sha256)
    incoming(args.frontend, args.frontend_sha256)
    validate_work_archive(args.jar)
    backup_status = BASE / 'logs/backup-latest.json'
    if not json.loads(backup_status.read_text()).get('verifiedRestore') or datetime.now().timestamp() - backup_status.stat().st_mtime > 3600:
        raise ValueError('Fresh verified ERP-only backup required')
    release = BASE / 'releases' / ('work-query-' + args.sha256[:16])
    target = release / 'rd-erp-server.jar'
    new_unit = replace_backend(original_unit, target)
    if release.exists():
        raise ValueError('Release already exists; inspect instead of overwriting')
    before = snapshot()
    if not all('ActiveState=active' in state for state in before['services'].values()):
        raise ValueError('Original service baseline unhealthy')
    stamp = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')
    rollback = BASE / 'backups' / ('work-query-' + stamp)
    rollback.mkdir(mode=0o700)
    write(rollback / 'backend.service', original_unit, 0o600)
    write(rollback / 'nginx.conf', original_nginx, 0o600)
    release.mkdir(mode=0o750)
    os.chown(release, 0, pwd.getpwnam(USER).pw_gid)
    shutil.copyfile(args.jar, target)
    os.chown(target, 0, pwd.getpwnam(USER).pw_gid)
    os.chmod(target, 0o640)
    if sha(target) != args.sha256:
        raise ValueError('Copied artifact differs')
    changed = False
    try:
        changed = True
        write(UNIT, new_unit, 0o644)
        command('/usr/bin/systemctl', 'daemon-reload')
        command('/usr/bin/systemctl', 'restart', 'duoxu-erp-backend.service')
        wait_for_backend()
        queries = verify_queries()
        frontend = deploy_frontend(args.frontend, args.frontend_sha256)
        if snapshot() != before:
            raise ValueError('Business, private configuration, budget or protected system changed')
    except BaseException:
        if changed:
            restore_frontend(original_nginx)
            write(UNIT, original_unit, 0o644)
            command('/usr/bin/systemctl', 'daemon-reload')
            command('/usr/bin/systemctl', 'restart', 'duoxu-erp-backend.service')
            wait_for_backend()
        raise
    result = {'state': 'RELEASED', 'backendSha256': args.sha256, 'previousBackendSha256': old_sha,
              'backend': str(target), **frontend, 'queries': queries,
              'protectedServicesAndFilesUnchanged': True, 'businessFingerprintsUnchanged': True,
              'privateConfigurationAndBudgetUnchanged': True, 'backupRestoreVerified': True,
              'paidModelCalls': 0, 'credentialsPrinted': False, 'rollback': str(rollback),
              'checkedAt': datetime.now(timezone.utc).isoformat()}
    write(BASE / 'WORK_QUERY_RELEASE.json', json.dumps(result, indent=2), 0o640)
    print(json.dumps(result))


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        import traceback
        frame = traceback.extract_tb(error.__traceback__)[-1]
        print(json.dumps({'state': 'RELEASE_FAILED', 'errorType': type(error).__name__,
                          'source': Path(frame.filename).name, 'line': frame.lineno,
                          'detail': 'Review ERP-only logs; never print private configuration',
                          'credentialsPrinted': False}))
        sys.exit(1)
