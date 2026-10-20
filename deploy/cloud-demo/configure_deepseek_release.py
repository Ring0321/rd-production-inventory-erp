"""Publish only the ERP backend and its approved, private DeepSeek configuration."""
import argparse
from datetime import date, datetime, timedelta, timezone
from decimal import Decimal
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import stat
import subprocess
import sys
import time
import urllib.request
import zipfile
from io import BytesIO

BASE = Path('/srv/apps/duoxu-erp-demo')
MARKER = 'DUOXU-CLOUD-DEMO-V1'
USER = 'duoxu-erp'

UNIT = Path('/etc/systemd/system/duoxu-erp-backend.service')
ENV = BASE / 'config/backend.env'
OLD_JAR = str(BASE / 'current/backend/rd-erp-server.jar')


def updated_environment(original, key, budget, price_until):
    amount = Decimal(budget)
    if not amount.is_finite() or amount < Decimal('0.10') or amount > 1000 or amount * 100 != (amount * 100).to_integral_value():
        raise ValueError('Budget rejected')
    until = date.fromisoformat(price_until)
    if not date.today() <= until <= date.today() + timedelta(days=31):
        raise ValueError('Price review date rejected')
    if not re.fullmatch(r'sk-[A-Za-z0-9._-]{16,500}', key):
        raise ValueError('Credential rejected')
    values = {}
    for line in original.splitlines():
        if not line.strip() or line.startswith('#'):
            continue
        name, value = line.split('=', 1)
        if not re.fullmatch(r'[A-Z][A-Z0-9_]*', name) or name in values:
            raise ValueError('Environment needs review')
        values[name] = value
    values.update({
        'RD_AI_PROVIDER': 'DEEPSEEK', 'RD_AI_DEEPSEEK_API_KEY': key,
        'RD_AI_MODEL': 'deepseek-flash', 'RD_AI_ENABLED': 'true', 'RD_AI_COST_APPROVED': 'true',
        'RD_AI_MONTHLY_BUDGET_CNY': str(amount), 'RD_AI_PRICE_VALID_UNTIL': price_until,
        'RD_AI_DAILY_CALL_LIMIT': '30', 'RD_AI_USER_MINUTE_LIMIT': '2', 'RD_AI_MAX_OUTPUT_TOKENS': '512',
    })
    return '\n'.join(name + '=' + value for name, value in values.items()) + '\n'


def unit_with_jar(original, jar):
    if MARKER not in original or original.count(' -jar ' + OLD_JAR + ' ') != 1:
        raise ValueError('Unknown backend unit; no replacement performed')
    return original.replace(' -jar ' + OLD_JAR + ' ', ' -jar ' + str(jar) + ' ')


def validate_archive(source):
    with zipfile.ZipFile(source) as archive:
        modules = [name for name in archive.namelist()
                   if name.startswith('BOOT-INF/lib/rd-erp-inventory-') and name.endswith('.jar')]
        if len(modules) != 1 or archive.getinfo(modules[0]).file_size > 32 * 1024 * 1024:
            raise ValueError('Inventory module missing or ambiguous')
        with zipfile.ZipFile(BytesIO(archive.read(modules[0]))) as inventory:
            if 'com/ruiding/erp/service/ai/RdAiBudgetGuard.class' not in inventory.namelist():
                raise ValueError('Budget guard missing from archive')
        if b'RD_AI_DEEPSEEK_API_KEY' not in archive.read('BOOT-INF/classes/application.yaml'):
            raise ValueError('Provider configuration missing from archive')


def protected_services():
    return {name: subprocess.check_output(['/usr/bin/systemctl', 'show', name, '--property=ActiveState,MainPID,ExecMainStartTimestamp'], text=True)
            for name in ('cross-studio.service', 'mysql.service', 'nginx.service')}


def healthy():
    request = urllib.request.Request('http://127.0.0.1:14880/admin-api/system/auth/capabilities', headers={'tenant-id': '1'})
    try:
        with urllib.request.urlopen(request, timeout=4) as response:
            return json.load(response).get('code') == 0
    except Exception:
        return False


def wait_for_backend():
    for attempt in range(100):
        if healthy():
            return
        time.sleep(2)
    raise RuntimeError('ERP backend health check failed')


def main():
    sys.path.insert(0, str(BASE / 'ops'))
    from install_release import api, command, write
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', required=True)
    parser.add_argument('--sha256', required=True)
    parser.add_argument('--monthly-budget-cny', required=True)
    parser.add_argument('--price-valid-until', required=True)
    args = parser.parse_args()
    if os.geteuid() != 0 or BASE.is_symlink() or BASE.resolve() != BASE:
        raise ValueError('Namespace rejected')
    if json.loads((BASE / 'DEPLOYMENT.json').read_text()).get('owner') != MARKER:
        raise ValueError('Namespace rejected')
    source = Path(args.jar)
    if source.is_symlink() or source.resolve().parent != BASE / 'incoming' or not source.is_file():
        raise ValueError('Archive path rejected')
    with source.open('rb') as stream:
        digest = hashlib.file_digest(stream, 'sha256').hexdigest()
    if not re.fullmatch(r'[0-9a-f]{64}', args.sha256) or digest != args.sha256:
        raise ValueError('Archive digest rejected')
    validate_archive(source)
    private = BASE / 'config/deepseek-private.pending.json'
    if private.is_symlink() or private.stat().st_uid != 0 or stat.S_IMODE(private.stat().st_mode) != 0o600:
        raise ValueError('Private credential permissions rejected')
    secret = json.loads(private.read_text())
    if secret.get('provider') != 'DEEPSEEK':
        raise ValueError('Credential provider rejected')
    for path in (UNIT, ENV, BASE / 'config/redis.conf'):
        if path.is_symlink() or path.stat().st_uid != 0:
            raise ValueError('Controlled configuration rejected')
    original_unit, original_env = UNIT.read_text(), ENV.read_text()
    new_env = updated_environment(original_env, secret['apiKey'], args.monthly_budget_cny, args.price_valid_until)
    release = BASE / 'releases' / ('deepseek-' + digest[:16])
    jar = release / 'rd-erp-server.jar'
    new_unit = unit_with_jar(original_unit, jar)
    if release.exists():
        raise ValueError('Release directory already exists; preserve it and review first')
    before = protected_services()
    stamp = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')
    backup = BASE / 'backups' / ('model-config-' + stamp)
    backup.mkdir(mode=0o700)
    write(backup / 'backend.env', original_env, 0o600)
    write(backup / 'backend.service', original_unit, 0o600)
    redis_secret = json.loads((BASE / 'config/secrets.json').read_text())['redis']
    def redis(*arguments):
        result = subprocess.run([str(BASE / 'runtime/bin/redis-cli'), '-h', '127.0.0.1', '-p', '16379', '--raw', *arguments],
                                env=dict(os.environ, REDISCLI_AUTH=redis_secret), capture_output=True, timeout=10, check=True)
        return result.stdout.decode().strip()
    if redis('CONFIG', 'GET', 'appendonly').splitlines() != ['appendonly', 'yes']:
        raise ValueError('Persistent ERP Redis required')
    if redis('CONFIG', 'GET', 'maxmemory-policy').splitlines() != ['maxmemory-policy', 'noeviction']:
        raise ValueError('Non-evicting ERP Redis required')
    redis_config = BASE / 'config/redis.conf'
    original_redis = redis_config.read_text()
    if len(re.findall(r'^appendfsync\s+.*$', original_redis, re.M)) > 1:
        raise ValueError('Redis persistence configuration needs review')
    always = re.sub(r'^appendfsync\s+.*$', 'appendfsync always', original_redis, flags=re.M)
    if not re.search(r'^appendfsync always$', always, re.M):
        always = always.rstrip() + '\nappendfsync always\n'
    write(backup / 'redis.conf', original_redis, 0o600)
    if redis('CONFIG', 'SET', 'appendfsync', 'always') != 'OK':
        raise ValueError('Durable ERP budget reservation unavailable')
    write(redis_config, always)
    release.mkdir(mode=0o750)
    shutil.copyfile(source, jar)
    import pwd
    os.chown(release, 0, pwd.getpwnam(USER).pw_gid)
    os.chown(jar, 0, pwd.getpwnam(USER).pw_gid)
    os.chmod(jar, 0o640)
    with jar.open('rb') as stream:
        if hashlib.file_digest(stream, 'sha256').hexdigest() != digest:
            raise ValueError('Copied archive digest rejected')
    changed = False
    try:
        changed = True
        write(ENV, new_env, 0o600)
        write(UNIT, new_unit, 0o644)
        command('/usr/bin/systemctl', 'daemon-reload')
        command('/usr/bin/systemctl', 'restart', 'duoxu-erp-backend.service')
        wait_for_backend()
        public = json.loads((BASE / 'config/public-demo-login.json').read_text())
        token = api('/system/auth/login', body={'username': public['username'], 'password': public['password']})['accessToken']
        configured = api('/rd/ai/package/context?packageCode=LD20261015A0014', token)
        if (configured.get('providerState') != 'READY'
                or configured.get('provider', {}).get('name') != 'DeepSeek'
                or Decimal(str(configured.get('provider', {}).get('monthlyBudgetCny'))) != Decimal(args.monthly_budget_cny)):
            raise ValueError('ERP model configuration not ready')
        if before != protected_services():
            raise RuntimeError('Protected service changed')
    except Exception:
        if changed:
            write(ENV, original_env, 0o600)
            write(UNIT, original_unit, 0o644)
            command('/usr/bin/systemctl', 'daemon-reload')
            command('/usr/bin/systemctl', 'restart', 'duoxu-erp-backend.service')
            wait_for_backend()
        raise
    result = {'provider': 'DEEPSEEK', 'model': 'deepseek-flash', 'thinking': 'disabled',
              'monthlyBudgetCny': str(Decimal(args.monthly_budget_cny)), 'reservationPerCallCny': '0.10',
              'monthlyReservationLimit': int(Decimal(args.monthly_budget_cny) * 10),
              'dailyCallLimit': 30, 'userMinuteLimit': 2, 'maxOutputTokens': 512,
              'priceValidUntil': args.price_valid_until, 'backendSha256': digest,
              'release': str(jar), 'rollbackBackup': str(backup), 'backendHealthy': True,
              'protectedServicesUnchanged': True, 'erpRedisAppendFsync': 'always',
              'providerReadyVerified': True, 'paidModelCalls': 0, 'credentialsPrinted': False}
    write(BASE / 'DEEPSEEK_RELEASE.json', json.dumps(result, indent=2), 0o640)
    print(json.dumps(result))


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        import traceback
        frame = traceback.extract_tb(error.__traceback__)[-1]
        print(json.dumps({'state': 'RELEASE_FAILED', 'credentialsPrinted': False,
                          'errorType': type(error).__name__, 'source': Path(frame.filename).name, 'line': frame.lineno,
                          'detail': 'Inspect ERP-only health and private rollback backup; do not print environment files'}))
        sys.exit(1)
