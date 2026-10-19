"""Publish a business-only admin demo entry inside the isolated cloud demo."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import secrets
import signal
import subprocess
import sys
import tarfile
import time

# Reuse the root-controlled helper, not an older upload in incoming/.
sys.path.insert(0, '/srv/apps/duoxu-erp-demo/ops')
from install_release import BASE, MARKER, api, mysql, write

PUBLIC_MARKER = 'Synthetic public business demo account'
PRIVATE_USERNAME = 'erpmaintadmin'
ROLE_CODE = 'rd_public_demo'
PROTECTED = ['cross-studio.service', 'mysql.service', 'nginx.service']


def protected_pids():
    return {name: subprocess.check_output(
        ['systemctl', 'show', name, '--property=MainPID', '--value'], text=True
    ).strip() for name in PROTECTED}


def business_menu_ids(rows):
    return [int(row['id']) for row in rows
            if int(row['id']) >= 600000
            and (not row.get('permission') or row['permission'].startswith('rd:'))]


def business_permissions(rows):
    assert isinstance(rows, list)
    assert all(isinstance(permission, str) for permission in rows)
    # Directory nodes have no permission code; every nonempty code must stay business-only.
    permissions = [permission for permission in rows if permission != '']
    assert permissions and all(permission.startswith('rd:') and '*' not in permission for permission in permissions)
    return permissions


def bootstrap_public_account():
    secret_path = BASE / 'config/secrets.json'
    values = json.loads(secret_path.read_text())
    # SQL is read-only here; identities and grants are changed through existing RBAC APIs.
    private_name = mysql('SELECT username FROM duoxu_erp_demo.system_users WHERE id=1 AND deleted=0;')
    assert private_name in ['admin', PRIVATE_USERNAME]
    token = api('/system/auth/login', body={
        'username': private_name, 'password': values['admin']
    })['accessToken']
    assert 'super_admin' in api('/system/auth/get-permission-info', token)['roles']
    public = values.get('publicDemo')
    if public is None:
        assert mysql("SELECT COUNT(*) FROM duoxu_erp_demo.system_users WHERE username='publicdemostage' AND deleted=0;") == '0'
        public = {'password': secrets.token_urlsafe(12)}
        values['publicDemo'] = public
        write(secret_path, json.dumps(values), 0o600)
    if not public.get('userId'):
        existing = mysql("SELECT id FROM duoxu_erp_demo.system_users WHERE remark='" + PUBLIC_MARKER + "' AND deleted=0;")
        public['userId'] = int(existing) if existing else int(api('/system/user/create', token, {
            'username': 'publicdemostage', 'password': public['password'],
            'nickname': 'admin 演示管理员', 'remark': PUBLIC_MARKER, 'deptId': 1, 'postIds': []
        }))
        write(secret_path, json.dumps(values), 0o600)
    user_id = int(public['userId'])
    assert user_id > 1
    if not public.get('roleId'):
        existing = mysql("SELECT id FROM duoxu_erp_demo.system_role WHERE code='" + ROLE_CODE + "' AND deleted=0;")
        public['roleId'] = int(existing) if existing else int(api('/system/role/create', token, {
            'name': '公开演示业务管理', 'code': ROLE_CODE, 'sort': 60, 'status': 0,
            'remark': PUBLIC_MARKER
        }))
        write(secret_path, json.dumps(values), 0o600)
    menus = api('/system/menu/list', token)
    menu_ids = business_menu_ids(menus)
    assert len(menu_ids) > 100
    api('/system/permission/assign-role-menu', token, {'roleId': public['roleId'], 'menuIds': menu_ids})
    # Existing business roles preserve domain-specific review checks, without the driver role.
    api('/system/permission/assign-user-role', token, {
        'userId': user_id, 'roleIds': [public['roleId'], 600001, 600002, 600003, 600006]
    })
    if private_name == 'admin':
        assert mysql("SELECT COUNT(*) FROM duoxu_erp_demo.system_users WHERE username='" + PRIVATE_USERNAME + "' AND deleted=0;") == '0'
        private_user = api('/system/user/get?id=1', token)
        api('/system/user/update', token, {
            **{key: private_user.get(key) for key in ['id', 'nickname', 'remark', 'deptId', 'postIds', 'email', 'mobile', 'sex', 'avatar']},
            'username': PRIVATE_USERNAME
        }, 'PUT')
    values['adminUsername'] = PRIVATE_USERNAME
    write(secret_path, json.dumps(values), 0o600)
    public_user = api('/system/user/get?id=' + str(user_id), token)
    assert public_user['remark'] == PUBLIC_MARKER
    if public_user['username'] != 'admin':
        api('/system/user/update', token, {
            'id': user_id, 'username': 'admin', 'nickname': 'admin 演示管理员',
            'remark': PUBLIC_MARKER, 'deptId': 1, 'postIds': []
        }, 'PUT')
    demo_token = api('/system/auth/login', body={
        'username': 'admin', 'password': public['password']
    })['accessToken']
    info = api('/system/auth/get-permission-info', demo_token)
    assert int(info['user']['id']) == user_id
    assert 'super_admin' not in info['roles'] and 'rd_forklift_driver' not in info['roles']
    permissions = business_permissions(info['permissions'])
    assert any(p.endswith(':create') for p in permissions)
    assert api('/system/auth/login', body={
        'username': PRIVATE_USERNAME, 'password': values['admin']
    })['accessToken']
    write(BASE / 'config/public-demo-login.json', json.dumps({
        'enabled': True, 'syntheticDemoOnly': True, 'username': 'admin', 'password': public['password']
    }), 0o640)
    return {'publicUserId': user_id, 'publicRoleId': public['roleId'],
            'permissionCount': len(permissions), 'maintenanceLoginPreserved': True}


def deploy_frontend(archive, expected_sha):
    assert archive.resolve().parent == BASE / 'incoming'
    with archive.open('rb') as stream:
        assert hashlib.file_digest(stream, 'sha256').hexdigest() == expected_sha
    release = BASE / 'releases' / ('login-' + expected_sha[:12])
    target = release / 'frontend'
    if not release.exists():
        release.mkdir(mode=0o755)
        target.mkdir(mode=0o755)
        with tarfile.open(archive) as bundle:
            for entry in bundle.getmembers():
                path = Path(entry.name)
                assert not path.is_absolute() and '..' not in path.parts
                assert entry.isfile() or entry.isdir(), 'Links are not frontend assets'
            bundle.extractall(target, filter='data')
        for directory, _, files in os.walk(target):
            Path(directory).chmod(0o755)
            for name in files:
                (Path(directory) / name).chmod(0o644)
        write(release / 'manifest.json', json.dumps({'frontendSha256': expected_sha, 'owner': MARKER}), 0o644)
    assert json.loads((release / 'manifest.json').read_text())['frontendSha256'] == expected_sha
    assert (target / 'index.html').is_file()
    config_path = BASE / 'config/nginx.conf'
    original = config_path.read_text()
    assert MARKER in original
    assert 'server_name 101.42.182.117;' in original
    old_roots = [line.strip() for line in original.splitlines() if line.strip().startswith('root ')]
    assert len(old_roots) == 1
    assert old_roots[0].startswith('root ' + str(BASE / 'releases') + '/') or (
        old_roots[0] == 'root ' + str(BASE / 'current/frontend') + ';')
    updated = original.replace(old_roots[0], 'root ' + str(target) + ';')
    if 'location = /demo-login.json {' not in updated:
        updated = updated.replace('    location /admin-api/ {', f'''    location = /demo-login.json {{
        alias {BASE}/config/public-demo-login.json;
        default_type application/json;
        add_header Cache-Control "no-store" always;
        add_header X-Content-Type-Options "nosniff" always;
    }}
    # Shared demo credentials cannot be changed by a visitor through self-service.
    # Private maintenance administrators retain the RBAC-protected reset endpoint.
    location ~ "^/admin-api/.*;" {{ return 400; }}
    location ~ ^/admin-api/system/user/profile/update-password([/.]|$) {{
        default_type application/json;
        return 403 '{{"code":403,"msg":"Shared demo password changes are disabled"}}';
    }}
    location /admin-api/ {{''', 1)
    assert 'location = /demo-login.json {' in updated
    backup = BASE / 'backups' / ('nginx-before-demo-login-' + expected_sha[:12] + '.conf')
    if not backup.exists():
        write(backup, original, 0o600)
    write(config_path, updated)
    try:
        # Match the service worker identity without losing access to privileged port 443.
        subprocess.run(['/usr/sbin/nginx', '-t', '-g', 'user duoxu-erp;',
                        '-p', str(BASE / 'data/web') + '/', '-c', str(config_path)], check=True)
    except BaseException:
        write(config_path, original)
        raise
    pid = int(subprocess.check_output(['systemctl', 'show', 'duoxu-erp-web.service', '--property=MainPID', '--value'], text=True))
    assert pid > 1
    os.kill(pid, signal.SIGHUP)
    time.sleep(2)
    assert subprocess.run(['systemctl', 'is-active', '--quiet', 'duoxu-erp-web.service']).returncode == 0
    return {'frontendSha256': expected_sha, 'frontendRoot': str(target), 'gatewayReloaded': True}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--frontend', type=Path, required=True)
    parser.add_argument('--sha256', required=True)
    args = parser.parse_args()
    assert os.geteuid() == 0
    assert json.loads((BASE / 'DEPLOYMENT.json').read_text())['owner'] == MARKER
    assert mysql('SELECT @@datadir;').rstrip('/') == str(BASE / 'data/mysql')
    assert args.frontend.resolve().parent == BASE / 'incoming'
    with args.frontend.open('rb') as stream:
        assert hashlib.file_digest(stream, 'sha256').hexdigest() == args.sha256
    before = protected_pids()
    accounts = bootstrap_public_account()
    frontend = deploy_frontend(args.frontend, args.sha256)
    assert before == protected_pids(), 'Protected services must remain untouched'
    result = {**accounts, **frontend, 'protectedServicesUnchanged': True,
              'syntheticDemoOnly': True, 'selfServicePasswordChangesDisabled': True}
    write(BASE / 'data/PUBLIC_DEMO_LOGIN.json', json.dumps(result), 0o644)
    print(json.dumps(result))


if __name__ == '__main__':
    main()
