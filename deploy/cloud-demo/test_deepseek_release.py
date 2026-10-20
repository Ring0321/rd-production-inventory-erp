from datetime import date, timedelta
from pathlib import Path
import unittest
from io import BytesIO
import zipfile

from configure_deepseek_release import MARKER, OLD_JAR, unit_with_jar, updated_environment, validate_archive


class DeepSeekReleaseTest(unittest.TestCase):
    def test_environment_keeps_business_config_and_separates_provider_key(self):
        before = 'RD_DB_PASSWORD=fixture=preserved\nRD_AI_ENABLED=false\nRD_AI_COST_APPROVED=false\nRD_AI_GLM_API_KEY=glm-protocol-fixture\n'
        value = updated_environment(before, 'sk-' + 'testcredential' * 2, '20', (date.today() + timedelta(days=1)).isoformat())
        self.assertIn('RD_DB_PASSWORD=fixture=preserved\n', value)
        self.assertIn('RD_AI_GLM_API_KEY=glm-protocol-fixture\n', value)
        self.assertIn('RD_AI_DEEPSEEK_API_KEY=', value)
        self.assertIn('RD_AI_PROVIDER=DEEPSEEK\n', value)
        self.assertIn('RD_AI_MONTHLY_BUDGET_CNY=20\n', value)
        self.assertIn('RD_AI_MAX_OUTPUT_TOKENS=512\n', value)

    def test_bad_budgets_dates_and_environment_duplicates_are_rejected(self):
        day = (date.today() + timedelta(days=1)).isoformat()
        key = 'sk-' + 'testcredential' * 2
        for amount in ('0', '-20', '20.001', '1001', 'NaN'):
            with self.assertRaises((ValueError, ArithmeticError)):
                updated_environment('', key, amount, day)
        for until in ('2020-01-01', (date.today() + timedelta(days=32)).isoformat(), 'invalid'):
            with self.assertRaises(ValueError):
                updated_environment('', key, '20', until)
        with self.assertRaises(ValueError):
            updated_environment('A=1\nA=2\n', key, '20', day)
        with self.assertRaises(ValueError):
            updated_environment('', key + '\nOTHER=1', '20', day)

    def test_only_the_known_owned_backend_jar_argument_can_change(self):
        before = '# ' + MARKER + '\nExecStart=/usr/bin/java -jar ' + OLD_JAR + ' --spring.profiles.active=trial\n'
        jar = Path('/srv/apps/duoxu-erp-demo/releases/deepseek-sample/rd-erp-server.jar')
        after = unit_with_jar(before, jar)
        self.assertEqual(after, before.replace(OLD_JAR, str(jar)))
        for invalid in ('ExecStart=other', before.replace(MARKER, 'OTHER'), before + before):
            with self.assertRaises(ValueError):
                unit_with_jar(invalid, jar)

    def test_budget_guard_must_be_in_the_actual_nested_inventory_module(self):
        for present in (True, False):
            module, boot = BytesIO(), BytesIO()
            with zipfile.ZipFile(module, 'w') as archive:
                archive.writestr('com/ruiding/erp/service/ai/' + ('RdAiBudgetGuard.class' if present else 'Other.class'), b'fixture')
            with zipfile.ZipFile(boot, 'w') as archive:
                archive.writestr('BOOT-INF/lib/rd-erp-inventory-1.0.jar', module.getvalue())
                archive.writestr('BOOT-INF/classes/application.yaml', 'RD_AI_DEEPSEEK_API_KEY')
            boot.seek(0)
            if present:
                validate_archive(boot)
            else:
                with self.assertRaises(ValueError):
                    validate_archive(boot)


if __name__ == '__main__':
    unittest.main()
