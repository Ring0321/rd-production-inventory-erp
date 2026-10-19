"""Read-only acceptance for the public business demo login and deployed assets."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import tarfile

from verify_cloud import ORIGIN, OUT, api, request


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--frontend', type=Path, default=OUT / 'frontend-security-admin-entry-20261018.tar.gz')
    args = parser.parse_args()
    status, headers, raw = request('/demo-login.json')
    assert status == 200 and 'application/json' in headers['Content-Type']
    assert 'no-store' in headers['Cache-Control']
    public = json.loads(raw)
    assert set(public) == {'enabled', 'syntheticDemoOnly', 'username', 'password'}
    assert public['enabled'] is True and public['syntheticDemoOnly'] is True
    assert public['username'] == 'admin' and len(public['password']) >= 12
    access = json.loads((OUT / 'cloud-access.json').read_text(encoding='utf-8-sig'))
    maintenance = next(row for row in access['accounts'] if row['role'] == 'admin')
    assert maintenance['username'] == 'erpmaintadmin'
    assert all(public['password'] != row['password'] for row in access['accounts'])
    login = api('/system/auth/login', body={
        'username': public['username'], 'password': public['password']
    })
    token = login['accessToken']
    info = api('/system/auth/get-permission-info', token)
    assert info['user']['username'] == 'admin' and info['user']['id'] != 1
    assert 'super_admin' not in info['roles'] and 'rd_forklift_driver' not in info['roles']
    assert isinstance(info['permissions'], list)
    assert all(isinstance(permission, str) for permission in info['permissions'])
    permissions = [permission for permission in info['permissions'] if permission != '']
    assert permissions and all(permission.startswith('rd:') and '*' not in permission for permission in permissions)
    assert any(permission.endswith(':create') for permission in permissions)
    forbidden = [
        '/system/user/page?pageNo=1&pageSize=1',
        '/system/role/page?pageNo=1&pageSize=1',
        '/infra/config/page?pageNo=1&pageSize=1'
    ]
    maintenance_endpoints = {}
    for path in forbidden:
        status, _, body = request('/admin-api' + path, token)
        value = json.loads(body)
        assert value.get('code') == 403 or (status == 404 and path.startswith('/infra/')), path
        maintenance_endpoints[path] = 'forbidden' if value.get('code') == 403 else 'not-exposed'
    protected_paths = [
        '/admin-api/system/user/profile/update-password',
        '/admin-api/system/user/profile/update-password/',
        '/admin-api/system/user/profile/update-password.json',
        '/admin-api/system/user/profile/update-password/extra',
        '/admin-api/system/user/profile/update-password;ignored'
    ]
    for path in protected_paths:
        status, _, _ = request(path, token, {}, 'PUT')
        assert status == (400 if ';' in path else 403), path
    private_login = api('/system/auth/login', body={
        'username': maintenance['username'], 'password': maintenance['password']
    })
    assert 'super_admin' in api('/system/auth/get-permission-info', private_login['accessToken'])['roles']
    bags = api('/rd/package-unit/page?pageNo=1&pageSize=100', token)
    assert bags['total'] == 30
    assert api('/rd/warehouse-replay/task?productionTaskId=90', token)['complete']
    archive = args.frontend
    with tarfile.open(archive) as bundle:
        member = next(item for item in bundle.getmembers() if item.name in ['index.html', './index.html'])
        with bundle.extractfile(member) as stream:
            expected_index = hashlib.sha256(stream.read()).hexdigest()
    for path in ['/login', '/warehouse-space?warehouseCode=DEMO-FLOW-V1&layout=published', '/business-flow?taskId=90']:
        status, _, body = request(path)
        assert status == 200 and hashlib.sha256(body).hexdigest() == expected_index, path
    result = {
        'result': 'PASS', 'checkedAt': datetime.now(timezone.utc).isoformat(), 'origin': ORIGIN,
        'publicUsername': 'admin', 'businessPermissionCount': len(permissions),
        'publicConfigurationNoStore': True, 'privateCredentialsNotPublished': True,
        'maintenanceEndpoints': maintenance_endpoints, 'passwordPathVariantsBlocked': len(protected_paths),
        'privateMaintenanceLoginPreserved': True, 'packageCount': bags['total'],
        'publishedIndexMatchesBundle': True, 'frontendIndexSha256': expected_index,
        'businessWritePermissionsPresent': True, 'businessWritesPerformed': False,
        'browserInteractionVerified': False
    }
    (OUT / 'public-demo-integration-result.json').write_text(json.dumps(result, indent=2), encoding='utf-8')
    print(json.dumps(result))


if __name__ == '__main__':
    main()
