"""Finalize newly created demo accounts and rotate the bootstrap credential."""
import json
import os
from pathlib import Path
import secrets
import sys

sys.path.insert(0,str(Path(__file__).resolve().parent))
from install_release import BASE, MARKER, api, mysql, write


def main():
    assert os.geteuid() == 0
    assert json.loads((BASE / 'DEPLOYMENT.json').read_text())['owner'] == MARKER
    assert mysql('SELECT @@datadir;').rstrip('/') == str(BASE / 'data/mysql')
    assert mysql("SELECT COUNT(*) FROM duoxu_erp_demo.system_users WHERE id BETWEEN 229 AND 234 AND remark='Synthetic cloud demonstration account';") == '6'
    mysql("UPDATE duoxu_erp_demo.system_users SET dept_id=1 WHERE id BETWEEN 229 AND 234 AND remark='Synthetic cloud demonstration account' AND dept_id=100;")
    marker = BASE / 'data/CREDENTIALS_FINALIZED.json'
    if marker.exists():
        print('Cloud-only account finalization already recorded')
        return
    values = json.loads((BASE / 'config/secrets.json').read_text())
    assert mysql("SELECT COUNT(*) FROM duoxu_erp_demo.system_users WHERE id BETWEEN 229 AND 234 AND remark='Synthetic cloud demonstration account';") == '6'
    for offset, role in enumerate(values['roles']):
        mysql(f"UPDATE duoxu_erp_demo.system_users SET username='{role}demo' WHERE id={229+offset} AND remark='Synthetic cloud demonstration account';")
    old = values['admin']
    token = api('/system/auth/login',body={'username':'admin','password':old})['accessToken']
    values['admin'] = secrets.token_urlsafe(12)
    api('/system/user/update-password',token,{'id':1,'password':values['admin']},'PUT')
    assert api('/system/auth/login',body={'username':'admin','password':values['admin']})['accessToken']
    write(BASE / 'config/secrets.json',json.dumps(values),0o600)
    env = BASE / 'config/backend.env'
    write(env,env.read_text().replace('RD_ADMIN_INITIAL_PASSWORD=' + old,'RD_ADMIN_INITIAL_PASSWORD=' + values['admin']))
    write(marker,json.dumps({'cloudDemoOnly':True,'rotatedBootstrap':True,'validAlphanumericUsernames':True}),0o644)
    print('Cloud-only accounts finalized; bootstrap credential rotated; no credential printed')


if __name__ == '__main__':
    main()
