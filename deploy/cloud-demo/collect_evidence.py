"""Export deployment facts only, never environment, credentials, database rows or raw logs."""
import hashlib
import json
from pathlib import Path
import subprocess

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
result['paidGlmDisabled'] = 'RD_AI_ENABLED=false\n' in (BASE / 'config/backend.env').read_text() and 'RD_AI_COST_APPROVED=false\n' in (BASE / 'config/backend.env').read_text()
result['protectedFileSha256'] = {name:hashlib.file_digest(Path(name).open('rb'),'sha256').hexdigest() for name in ['/home/ubuntu/cross-studio-backend-0.1.0-SNAPSHOT.jar','/var/www/cross-studio/index.html','/etc/nginx/nginx.conf','/etc/nginx/sites-enabled/cross-studio']}
result['originalServices'] = {name:subprocess.check_output(['/usr/bin/systemctl','show',name,'--property=ActiveState,MainPID,ExecMainStartTimestamp'],text=True).strip() for name in ['cross-studio.service','mysql.service','nginx.service']}
result['listeningPorts'] = subprocess.check_output(['/usr/bin/ss','-lnt'],text=True).strip()
print(json.dumps(result,ensure_ascii=True,indent=2))
