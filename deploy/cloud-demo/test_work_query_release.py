import copy
from decimal import Decimal
from io import BytesIO
from pathlib import Path
import tempfile
import unittest
import zipfile

from publish_work_queries import BASE, MARKER, backend_path, replace_backend, validate_work_archive, verify_response


class WorkQueryReleaseTest(unittest.TestCase):
    def test_known_unit_replaces_only_owned_jar(self):
        old = BASE / 'releases/deepseek-abcd/rd-erp-server.jar'
        target = BASE / 'releases/work-query-abcd/rd-erp-server.jar'
        original = '# ' + MARKER + '\nExecStart=/usr/bin/java -Xmx1280m -jar ' + old.as_posix() + ' --spring.profiles.active=cloud\n'
        updated = replace_backend(original, target)
        self.assertEqual(updated, original.replace(old.as_posix(), target.as_posix()))
        self.assertEqual(backend_path(updated), target)

    def test_unknown_duplicate_or_outside_unit_rejected(self):
        for text in (' -jar /tmp/a.jar ', '# ' + MARKER + ' -jar /tmp/a.jar ',
                     '# ' + MARKER + ' -jar ' + (BASE / 'current/backend/rd-erp-server.jar').as_posix() + ' -jar /tmp/b.jar '):
            with self.assertRaises(ValueError):
                backend_path(text)

    def test_target_outside_owned_release_rejected(self):
        original = '# ' + MARKER + ' -jar ' + (BASE / 'current/backend/rd-erp-server.jar').as_posix() + ' '
        for path in (Path('/tmp/a.jar'), BASE / 'current/backend/rd-erp-server.jar'):
            with self.assertRaises(ValueError):
                replace_backend(original, path)

    def test_wire_oracle_checks_scope_time_count_weight_and_total(self):
        value = {'scope': 'TENANT_AUTHORIZED', 'asOf': '2026-10-22T01:00:00Z', 'quality': 'KNOWN',
                 'metrics': {'DISTINCT_PACKAGE_COUNT': {'value': '2', 'quality': 'KNOWN'},
                             'REGISTERED_WEIGHT_KG': {'value': '2000.246912', 'quality': 'KNOWN'}},
                 'total': 2, 'items': []}
        expected = {'count': 2, 'weight': '2000.246912'}
        self.assertTrue(verify_response('Q03', value, expected)['sourceAggregateMatched'])
        for key, invalid in [('scope', 'OWN_TASKS'), ('asOf', 123), ('total', 1), ('quality', 'UNAVAILABLE')]:
            candidate = copy.deepcopy(value)
            candidate[key] = invalid
            with self.assertRaises(ValueError):
                verify_response('Q03', candidate, expected)
        with self.assertRaises(ValueError):
            verify_response('Q03', value, {'count': 2, 'weight': Decimal('2000.246911')})

    def test_partial_coverage_still_requires_known_document_count(self):
        value = {'scope': 'TENANT_AUTHORIZED', 'asOf': '2026-10-22T01:00:00Z', 'quality': 'PARTIAL',
                 'metrics': {'BUSINESS_OBJECT_COUNT': {'value': '3', 'quality': 'KNOWN'}},
                 'total': None, 'items': []}
        self.assertEqual(verify_response('Q04', value, {'count': 3})['quality'], 'PARTIAL')
        with self.assertRaises(ValueError):
            verify_response('Q04', value, {'count': 4})

    def test_archive_requires_guard_and_all_real_query_components(self):
        classes = ['service/ai/RdAiBudgetGuard', 'service/workassistant/RdWorkQueryService',
                   'dal/mysql/workassistant/RdWorkQueryMapper',
                   'controller/admin/workassistant/RdWorkAssistantController']
        with tempfile.TemporaryDirectory() as directory:
            for omit in (None, *classes):
                inner = BytesIO()
                with zipfile.ZipFile(inner, 'w') as module:
                    for name in classes:
                        if name != omit:
                            module.writestr('com/ruiding/erp/' + name + '.class', b'class')
                jar = Path(directory) / 'server.jar'
                with zipfile.ZipFile(jar, 'w') as archive:
                    archive.writestr('BOOT-INF/lib/rd-erp-inventory-test.jar', inner.getvalue())
                    archive.writestr('BOOT-INF/classes/application.yaml', b'${RD_AI_DEEPSEEK_API_KEY:}')
                if omit is None:
                    validate_work_archive(jar)
                else:
                    with self.assertRaises(ValueError):
                        validate_work_archive(jar)


if __name__ == '__main__':
    unittest.main()
