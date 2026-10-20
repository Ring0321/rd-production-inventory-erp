"""Export deployment facts only, never environment, credentials, database rows or raw logs."""
import hashlib
import json
from pathlib import Path
import subprocess
import os
from datetime import datetime
from zoneinfo import ZoneInfo

BASE = Path('/srv/apps/duoxu-erp-demo')
assert json.loads((BASE / 'DEPLOYMENT.json').read_text())['owner'] == 'DUOXU-CLOUD-DEMO-V1'
result = {}
for name, relative in {'release':'current/provenance.json','dataReconciliation':'data/DEMO_RECONCILED.json','health':'logs/health-latest.json','backup':'logs/backup-latest.json','ocrRealInference':'logs/ocr-cloud-verification.json','runtimeSources':'runtime/provenance.json','privateGlSources':'runtime/ocr-native/provenance.json'}.items():
    result[name] = json.loads((BASE / relative).read_text())
result['services'] = {}
for name in ['mysql','redis','backend','ocr','web']:
    result['services'][name] = subprocess.check_output(['/usr/bin/systemctl','show','duoxu-erp-' + name + '.service','--property=ActiveState,MainPID,MemoryCurrent,MemoryMax'],text=True).strip()
result['timers'] = subprocess.check_output(['/usr/bin/systemctl','list-timers','--all','duoxu-erp-*'],text=True).strip()
result['certificate'] = subprocess.check_output(['/usr/bin/openssl','x509','-in',str(BASE / 'config/tls/server.crt'),'-noout','-issuer','-dates','-ext','subjectAltName'],text=True).strip()
result['linuxOcrPackages'] = (BASE / 'runtime/ocr-linux-freeze.txt').read_text().splitlines()
environment = dict(line.split('=', 1) for line in (BASE / 'config/backend.env').read_text().splitlines()
                   if line and not line.startswith('#') and '=' in line)
provider = environment.get('RD_AI_PROVIDER', 'GLM')
result['paidGlmDisabled'] = provider != 'GLM' or environment.get('RD_AI_ENABLED') != 'true' or environment.get('RD_AI_COST_APPROVED') != 'true'
result['modelRuntime'] = {name: environment.get(key, fallback) for name, key, fallback in [
    ('provider', 'RD_AI_PROVIDER', 'GLM'), ('model', 'RD_AI_MODEL', ''),
    ('enabled', 'RD_AI_ENABLED', 'false'), ('costApproved', 'RD_AI_COST_APPROVED', 'false'),
    ('monthlyBudgetCny', 'RD_AI_MONTHLY_BUDGET_CNY', '0'), ('priceValidUntil', 'RD_AI_PRICE_VALID_UNTIL', '')]}
if (BASE / 'DEEPSEEK_RELEASE.json').is_file():
    result['deepSeekRelease'] = json.loads((BASE / 'DEEPSEEK_RELEASE.json').read_text())
    credential = json.loads((BASE / 'config/secrets.json').read_text())['redis']
    def budget_read(*arguments):
        value = subprocess.check_output([str(BASE / 'runtime/bin/redis-cli'), '-h', '127.0.0.1', '-p', '16379', '--raw', *arguments],
                                        env=dict(os.environ, REDISCLI_AUTH=credential), timeout=10, text=True)
        return value.strip()
    period = datetime.now(ZoneInfo('Asia/Shanghai')).strftime('%Y-%m')
    used = budget_read('GET', 'rd:ai-budget:{erp}:DEEPSEEK:' + period)
    result['deepSeekBudget'] = {'period': period, 'reservedCalls': int(used or '0'),
                               'reservedCny': round(int(used or '0') / 10, 2), 'notActualPlatformBill': True,
                               'appendFsync': budget_read('CONFIG', 'GET', 'appendfsync').splitlines()[-1]}
result['protectedFileSha256'] = {name:hashlib.file_digest(Path(name).open('rb'),'sha256').hexdigest() for name in ['/home/ubuntu/cross-studio-backend-0.1.0-SNAPSHOT.jar','/var/www/cross-studio/index.html','/etc/nginx/nginx.conf','/etc/nginx/sites-enabled/cross-studio']}
result['originalServices'] = {name:subprocess.check_output(['/usr/bin/systemctl','show',name,'--property=ActiveState,MainPID,ExecMainStartTimestamp'],text=True).strip() for name in ['cross-studio.service','mysql.service','nginx.service']}
result['listeningPorts'] = subprocess.check_output(['/usr/bin/ss','-lnt'],text=True).strip()
print(json.dumps(result,ensure_ascii=True,indent=2))
