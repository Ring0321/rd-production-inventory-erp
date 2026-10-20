import contextlib
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import urllib.error

from configure_private_model_key import NoRedirect, TARGET, stage_key, verify_models


class PrivateModelKeyTest(unittest.TestCase):
    def test_stages_only_disabled_private_pending_config(self):
        with tempfile.TemporaryDirectory() as directory:
            config = Path(directory)
            key = 'sk-' + 'samplecredential' * 2
            output = io.StringIO()
            with contextlib.redirect_stdout(output):
                target = stage_key(config, key)
                self.assertEqual(stage_key(config, key), target)
            saved = json.loads(target.read_text())
            self.assertEqual(saved['apiKey'], key)
            self.assertFalse(saved['enabled'])
            self.assertFalse(saved['costApproved'])
            self.assertIsNone(saved['monthlyBudgetCny'])
            self.assertEqual(output.getvalue(), '')
            self.assertEqual([path.name for path in config.iterdir()], [TARGET])

    def test_rejects_key_injection_and_preserves_existing_key(self):
        with tempfile.TemporaryDirectory() as directory:
            config = Path(directory)
            for key in ('', 'sk-short', 'sk-' + 'x' * 20 + '\nOTHER=1', None):
                with self.assertRaises(ValueError):
                    stage_key(config, key)
            target = stage_key(config, 'sk-' + 'first' * 8)
            original = target.read_bytes()
            with self.assertRaises(ValueError):
                stage_key(config, 'sk-' + 'second' * 8)
            self.assertEqual(target.read_bytes(), original)

    def test_no_redirect_or_paid_endpoint(self):
        self.assertIsNone(NoRedirect().redirect_request(None, None, 302, '', {}, 'https://other.test'))
        response = unittest.mock.MagicMock()
        response.__enter__.return_value = response
        response.status = 200
        response.headers = {'Content-Type': 'application/json'}
        response.read.return_value = json.dumps({'object': 'list', 'data': [
            {'id': 'deepseek-flash'}, {'id': 'https://untrusted.test'}, {'id': 'deepseek-flash'}
        ]}).encode()
        opener = unittest.mock.MagicMock()
        opener.open.return_value = response
        with patch('urllib.request.build_opener', return_value=opener):
            result = verify_models('not-a-real-credential')
        request = opener.open.call_args.args[0]
        self.assertEqual(request.full_url, 'https://api.deepseek.com/models')
        self.assertEqual(request.get_method(), 'GET')
        self.assertIsNone(request.data)
        self.assertEqual(result, {'apiKeyAccepted': True, 'availableModels': ['deepseek-flash'],
                                  'paidChatCalls': 0})

    def test_auth_failure_never_prints_error_body_or_credential(self):
        opener = unittest.mock.MagicMock()
        opener.open.side_effect = urllib.error.HTTPError('https://api.deepseek.com/models',
                                                        401, 'private message', {}, None)
        with patch('urllib.request.build_opener', return_value=opener):
            result = verify_models('not-a-real-credential')
        self.assertEqual(result, {'apiKeyAccepted': False, 'httpStatus': 401, 'paidChatCalls': 0})


if __name__ == '__main__':
    unittest.main()
