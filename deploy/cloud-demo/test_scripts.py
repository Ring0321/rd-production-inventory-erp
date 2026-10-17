import importlib
from pathlib import Path
import sys
from tempfile import TemporaryDirectory
from types import SimpleNamespace
import unittest
from unittest.mock import patch, MagicMock

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT / 'temp/cloud-demo-20261018/py'))
sys.path.insert(0,str(Path(__file__).resolve().parent))
import build_bundle


class SchemaSanitizationTest(unittest.TestCase):
    def test_preserves_mysql_client_delimiters(self):
        sql = "-- marker\nDELIMITER ;;\nCREATE PROCEDURE p() BEGIN SELECT 'kept;'; END;;\nDELIMITER ;\nINSERT INTO `rd_customer_product` (`customer_name`) VALUES ('excluded');\nSELECT 'kept';\n"
        with TemporaryDirectory() as directory:
            source, target = Path(directory)/'source.sql', Path(directory)/'target.sql'
            source.write_text(sql,encoding='utf-8')
            self.assertEqual(1,build_bundle.sanitized_schema(source,target))
            expected = sql.replace("INSERT INTO `rd_customer_product` (`customer_name`) VALUES ('excluded');",'')
            self.assertEqual(expected,target.read_text(encoding='utf-8'))

    def test_ignores_insert_text_inside_quoted_values(self):
        sql = "INSERT IGNORE INTO rd_customer_product (customer_name) VALUES ('remove');\nINSERT INTO other_table VALUES ('INSERT INTO rd_customer_product; keep');\n"
        with TemporaryDirectory() as directory:
            source, target = Path(directory)/'source.sql', Path(directory)/'target.sql'
            source.write_text(sql,encoding='utf-8')
            self.assertEqual(1,build_bundle.sanitized_schema(source,target))
            self.assertIn("VALUES ('INSERT INTO rd_customer_product; keep')",target.read_text())


class CertificateBoundaryTest(unittest.TestCase):
    def setUp(self):
        if sys.platform == 'win32':
            sys.modules.setdefault('fcntl',SimpleNamespace(flock=lambda *args:None,LOCK_EX=2,LOCK_NB=4))
        self.renewal = importlib.import_module('renew_certificate')
        self.temp = TemporaryDirectory()
        self.base = Path(self.temp.name)
        (self.base / 'data').mkdir()
        (self.base / 'DEPLOYMENT.json').write_text('{"owner":"DUOXU-CLOUD-DEMO-V1"}')
        self.base_patch = patch.object(self.renewal,'BASE',self.base)
        self.uid_patch = patch.object(self.renewal.os,'geteuid',return_value=0,create=True)
        self.base_patch.start()
        self.uid_patch.start()

    def tearDown(self):
        self.base_patch.stop()
        self.uid_patch.stop()
        self.temp.cleanup()

    def test_healthy_certificate_does_not_interrupt_gateway(self):
        cert = self.base / 'config/tls/server.crt'
        cert.parent.mkdir(parents=True)
        cert.write_text('synthetic')
        with patch.object(self.renewal.subprocess,'run',return_value=SimpleNamespace(returncode=0)) as run:
            self.renewal.main()
        self.assertEqual(1,run.call_count)
        self.assertEqual('/usr/bin/openssl',run.call_args.args[0][0])

    def test_acme_failure_restores_only_erp_gateway(self):
        calls = []
        def run(args,**kwargs):
            calls.append(args)
            if args[0].endswith('lego'):
                raise RuntimeError('synthetic CA failure')
            return SimpleNamespace(returncode=0)
        with patch.object(self.renewal.subprocess,'run',side_effect=run),patch.object(self.renewal.socket,'socket') as socket:
            socket.return_value.__enter__.return_value.connect_ex.return_value = 0
            with self.assertRaisesRegex(RuntimeError,'synthetic CA failure'):
                self.renewal.main()
        self.assertIn(['/usr/bin/systemctl','stop','duoxu-erp-web.service'],calls)
        self.assertEqual(['/usr/bin/systemctl','start','duoxu-erp-web.service'],calls[-1])
        self.assertFalse(any('nginx.service' in call for call in calls))

    def test_other_443_owner_is_not_stopped(self):
        with patch.object(self.renewal.subprocess,'run',return_value=SimpleNamespace(returncode=1)) as run,patch.object(self.renewal.socket,'socket') as socket:
            socket.return_value.__enter__.return_value.connect_ex.return_value = 0
            with self.assertRaisesRegex(RuntimeError,'another service'):
                self.renewal.main()
        self.assertEqual(1,run.call_count)


if __name__ == '__main__':
    unittest.main()
