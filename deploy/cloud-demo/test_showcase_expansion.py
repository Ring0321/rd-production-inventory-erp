import importlib.util
import json
from pathlib import Path
import sys
from tempfile import TemporaryDirectory
from types import SimpleNamespace
import unittest
from unittest.mock import patch


class ShowcaseExpansionTest(unittest.TestCase):
    def setUp(self):
        self.temp = TemporaryDirectory()
        self.base = Path(self.temp.name)
        def write(path, text, mode=0o640):
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text, encoding='utf-8')
        stub = SimpleNamespace(BASE=self.base, MARKER='DUOXU-CLOUD-DEMO-V1', api=lambda *a: None,
                               mysql=lambda *a: '', write=write)
        spec = importlib.util.spec_from_file_location('expansion_subject', Path(__file__).with_name('seed_showcase_expansion.py'))
        self.module = importlib.util.module_from_spec(spec)
        with patch.dict(sys.modules, {'install_release': stub}):
            spec.loader.exec_module(self.module)

    def tearDown(self):
        self.temp.cleanup()

    def test_plan_has_real_distinct_cells_products_and_complete_package_counts(self):
        products, locations, rows = self.module.plan()
        self.assertEqual((20, 96, 60), (len(products), len(locations), len(rows)))
        self.assertEqual(480, sum(row['count'] for row in rows))
        self.assertEqual(487680, sum(row['count'] * row['product']['defaultPackageWeight'] for row in rows))
        self.assertEqual({'01', '02'}, {location['levelCode'] for location in locations})
        self.assertEqual(96, len({(l['areaCode'], l['rowCode'], l['columnCode'], l['levelCode']) for l in locations}))
        self.assertEqual(20, len({p['productCode'] for p in products}))
        self.assertTrue(all('演示' in p['customerName'] and '演示' in p['productName'] for p in products))
        self.assertTrue(all(row.get('locationIndex', -1) < 92 for row in rows))
        self.assertTrue(all(row['furnaceLayer'] != row.get('levelCode') for row in rows))

    def test_successful_checkpoint_is_not_repeated_and_changed_input_is_rejected(self):
        journal = self.module.Journal(self.base / 'journal.json')
        calls = []
        self.assertEqual(42, journal.run('product.1', {'x': 1}, lambda: calls.append(1) or 42))
        self.assertEqual(42, journal.run('product.1', {'x': 1}, lambda: calls.append(2) or 99))
        self.assertEqual([1], calls)
        with self.assertRaises(AssertionError):
            journal.run('product.1', {'x': 2}, lambda: None)

    def test_uncertain_request_is_durable_and_never_automatically_replayed(self):
        filename = self.base / 'journal.json'
        journal = self.module.Journal(filename)
        with self.assertRaises(TimeoutError):
            journal.run('confirm.1', {'id': 8}, lambda: (_ for _ in ()).throw(TimeoutError()))
        self.assertEqual('PENDING', json.loads(filename.read_text())['steps']['confirm.1']['state'])
        restarted = self.module.Journal(filename)
        with self.assertRaisesRegex(RuntimeError, 'uncertain'):
            restarted.run('confirm.1', {'id': 8}, lambda: self.fail('must not replay'))

    def test_write_boundary_keeps_stock_confirmation_with_keeper_and_rejects_admin_maintenance(self):
        self.module.check_write('warehouse', '/rd/putaway-task/confirm', 'PUT')
        for role, path, method in [('driver', '/rd/putaway-task/confirm', 'PUT'),
                                   ('admin', '/system/user/create', 'POST'),
                                   ('warehouse', '/rd/stock-lot/update', 'PUT')]:
            with self.assertRaises(AssertionError):
                self.module.check_write(role, path, method)


if __name__ == '__main__':
    unittest.main()
