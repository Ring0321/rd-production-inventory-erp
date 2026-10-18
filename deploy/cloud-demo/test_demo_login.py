import hashlib
import importlib.util
import io
import json
from pathlib import Path
import subprocess
import sys
import tarfile
from tempfile import TemporaryDirectory
from types import SimpleNamespace
import unittest
from unittest.mock import MagicMock, patch


class DemoLoginBoundaryTest(unittest.TestCase):
    def setUp(self):
        self.temp = TemporaryDirectory()
        self.base = Path(self.temp.name)
        self.writes = []
        def write(path, text, mode=0o640):
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text, encoding='utf-8')
            self.writes.append(path)
        stub = SimpleNamespace(BASE=self.base, MARKER='DUOXU-CLOUD-DEMO-V1',
                               api=MagicMock(), mysql=MagicMock(), write=write)
        path = Path(__file__).with_name('configure_demo_login.py')
        spec = importlib.util.spec_from_file_location('demo_login_test_subject', path)
        self.module = importlib.util.module_from_spec(spec)
        with patch.dict(sys.modules, {'install_release': stub}):
            spec.loader.exec_module(self.module)

    def tearDown(self):
        self.temp.cleanup()

    def test_business_menu_filter_never_grants_wildcard_or_system_permissions(self):
        rows = [{'id': 600000, 'permission': ''}, {'id': 600100, 'permission': 'rd:production-output:create'},
                {'id': 100, 'permission': 'system:user:create'}, {'id': 900000, 'permission': '*:*:*'},
                {'id': 900001, 'permission': 'infra:config:update'}]
        self.assertEqual([600000, 600100], self.module.business_menu_ids(rows))

    def bootstrap_model(self, forbidden_permission=False):
        secret = self.base / 'config/secrets.json'
        secret.parent.mkdir()
        secret.write_text(json.dumps({'admin': 'private-fixture-password', 'roles': {}}))
        users = {1: {'id': 1, 'username': 'admin', 'nickname': 'maintenance', 'postIds': []}}
        calls = []
        def mysql(sql):
            if sql.startswith('SELECT username'): return users[1]['username']
            if "remark='Synthetic public" in sql:
                return '235' if 235 in users else ''
            if "code='rd_public_demo'" in sql: return ''
            return '0'
        def api(path, token=None, body=None, method=None):
            calls.append((path, body, method))
            if path == '/system/auth/login':
                return {'accessToken': 'private-token' if body['username'] == users[1]['username'] else 'public-token'}
            if path == '/system/auth/get-permission-info':
                if token == 'private-token': return {'roles': ['super_admin']}
                return {'user': {'id': 235}, 'roles': ['rd_public_demo'],
                        'permissions': ['*:*:*'] if forbidden_permission else ['rd:production-output:create']}
            if path == '/system/user/create':
                users[235] = {'id': 235, **body}
                return 235
            if path == '/system/role/create': return 700001
            if path == '/system/menu/list':
                return [{'id': 600000 + i, 'permission': 'rd:fixture:query'} for i in range(110)]
            if path.startswith('/system/user/get?id='): return users[int(path.split('=')[1])].copy()
            if path == '/system/user/update': users[body['id']].update(body)
            return True
        self.module.mysql = mysql
        self.module.api = api
        return users, calls

    def test_public_account_is_business_only_and_private_admin_is_preserved(self):
        users, calls = self.bootstrap_model()
        result = self.module.bootstrap_public_account()
        self.assertEqual('erpmaintadmin', users[1]['username'])
        self.assertEqual('admin', users[235]['username'])
        self.assertTrue(result['maintenanceLoginPreserved'])
        grants = next(body for path, body, _ in calls if path.endswith('/assign-user-role'))
        self.assertNotIn(1, grants['roleIds'])
        self.assertNotIn(600008, grants['roleIds'])
        self.assertTrue({600001, 600002, 600003, 600006}.issubset(grants['roleIds']))
        self.assertFalse(any('/rd/' in path for path, _, _ in calls))
        values = json.loads((self.base / 'config/secrets.json').read_text())
        self.assertEqual('private-fixture-password', values['admin'])
        public = json.loads((self.base / 'config/public-demo-login.json').read_text())
        self.assertNotEqual(values['admin'], public['password'])
        before = len([call for call in calls if call[0].endswith('/create')])
        self.module.bootstrap_public_account()
        self.assertEqual(before, len([call for call in calls if call[0].endswith('/create')]))

    def test_unexpected_privileged_permission_prevents_credential_publication(self):
        self.bootstrap_model(forbidden_permission=True)
        with self.assertRaises(AssertionError): self.module.bootstrap_public_account()
        self.assertFalse((self.base / 'config/public-demo-login.json').exists())

    def archive(self, entry='index.html', link=False):
        incoming = self.base / 'incoming'
        incoming.mkdir(exist_ok=True)
        path = incoming / 'frontend.tar.gz'
        with tarfile.open(path, 'w:gz') as bundle:
            item = tarfile.TarInfo(entry)
            if link:
                item.type, item.linkname = tarfile.SYMTYPE, '/etc/passwd'
                bundle.addfile(item)
            else:
                data = b'<html>fixture</html>'
                item.size = len(data)
                bundle.addfile(item, io.BytesIO(data))
        return path, hashlib.sha256(path.read_bytes()).hexdigest()

    def test_frontend_digest_is_checked_before_unpacking(self):
        path, _ = self.archive()
        with self.assertRaises(AssertionError): self.module.deploy_frontend(path, '0' * 64)
        self.assertFalse((self.base / 'releases').exists())

    def test_traversal_and_links_are_rejected(self):
        for entry, link in [('../escape', False), ('linked.html', True)]:
            path, digest = self.archive(entry, link)
            (self.base / 'releases').mkdir(exist_ok=True)
            with self.assertRaises(AssertionError): self.module.deploy_frontend(path, digest)
        self.assertFalse((self.base / 'escape').exists())

    def test_invalid_nginx_configuration_is_rolled_back_without_signalling_services(self):
        path, digest = self.archive()
        (self.base / 'releases').mkdir()
        config = self.base / 'config/nginx.conf'
        config.parent.mkdir()
        original = '# DUOXU-CLOUD-DEMO-V1\nserver_name 101.42.182.117;\nroot ' + str(self.base / 'current/frontend') + ';\n    location /admin-api/ {\n}\n'
        config.write_text(original)
        with patch.object(self.module.subprocess, 'run', side_effect=subprocess.CalledProcessError(1, 'nginx')), \
                patch.object(self.module.os, 'kill', create=True) as kill:
            with self.assertRaises(subprocess.CalledProcessError): self.module.deploy_frontend(path, digest)
        self.assertEqual(original, config.read_text())
        kill.assert_not_called()


if __name__ == '__main__':
    unittest.main()
