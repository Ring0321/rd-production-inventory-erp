"""Write account handoff through SSH only; callers must not echo the response."""
import json
from pathlib import Path

base = Path('/srv/apps/duoxu-erp-demo')
assert json.loads((base / 'DEPLOYMENT.json').read_text())['owner'] == 'DUOXU-CLOUD-DEMO-V1'
values = json.loads((base / 'config/secrets.json').read_text())
accounts = [{'role':'admin','username':'admin','password':values['admin']}]
accounts += [{'role':role,'username':role + 'demo','password':password} for role,password in values['roles'].items()]
print(json.dumps({'origin':'https://101.42.182.117','syntheticDemoOnly':True,'accounts':accounts}))
