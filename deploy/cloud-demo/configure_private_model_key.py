"""Stage a DeepSeek credential privately; never enable or call paid chat APIs."""
import argparse
import json
import os
from pathlib import Path
import re
import stat
import sys
import tempfile
import urllib.error
import urllib.request


BASE = Path('/srv/apps/duoxu-erp-demo')
MARKER = 'DUOXU-CLOUD-DEMO-V1'
TARGET = 'deepseek-private.pending.json'
MAX_BYTES = 65536


def stage_key(config_dir, key):
    if not isinstance(key, str) or not re.fullmatch(r'sk-[A-Za-z0-9._-]{16,500}', key):
        raise ValueError('Credential format rejected')
    target = config_dir / TARGET
    if config_dir.is_symlink() or target.is_symlink():
        raise ValueError('Symbolic configuration paths rejected')
    if target.exists():
        prior = json.loads(target.read_text(encoding='utf-8'))
        if prior.get('apiKey') != key:
            raise ValueError('Existing credential preserved; explicit rotation required')
        if stat.S_IMODE(target.stat().st_mode) != 0o600 and os.name != 'nt':
            raise ValueError('Private credential permissions require review')
        return target
    value = {
        'provider': 'DEEPSEEK',
        'modelPreference': 'ordinary-chat-non-thinking',
        'apiKey': key,
        'enabled': False,
        'costApproved': False,
        'monthlyBudgetCny': None,
        'status': 'PENDING_BUDGET_AND_ADAPTER',
    }
    descriptor, temporary = tempfile.mkstemp(prefix='.deepseek-pending-', dir=config_dir)
    try:
        os.chmod(temporary, 0o600)
        with os.fdopen(descriptor, 'w', encoding='utf-8') as stream:
            json.dump(value, stream, ensure_ascii=True)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, target)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)
    return target


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def verify_models(key):
    request = urllib.request.Request(
        'https://api.deepseek.com/models',
        headers={'Authorization': 'Bearer ' + key, 'Accept': 'application/json'},
        method='GET',
    )
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())
    try:
        with opener.open(request, timeout=12) as response:
            body = response.read(MAX_BYTES + 1)
            if response.status != 200 or len(body) > MAX_BYTES:
                raise ValueError('Model discovery response rejected')
            if 'application/json' not in response.headers.get('Content-Type', '').lower():
                raise ValueError('Model discovery response rejected')
            data = json.loads(body)
        models = data.get('data')
        if data.get('object') != 'list' or not isinstance(models, list):
            raise ValueError('Model discovery response rejected')
        names = sorted({item['id'] for item in models if isinstance(item, dict)
                        and isinstance(item.get('id'), str)
                        and re.fullmatch(r'deepseek-[a-z0-9.-]{1,80}', item['id'])})
        return {'apiKeyAccepted': True, 'availableModels': names, 'paidChatCalls': 0}
    except urllib.error.HTTPError as error:
        return {'apiKeyAccepted': False, 'httpStatus': error.code, 'paidChatCalls': 0}
    except Exception:
        return {'apiKeyAccepted': None, 'state': 'VERIFICATION_UNAVAILABLE', 'paidChatCalls': 0}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--install-from-stdin', action='store_true')
    parser.add_argument('--verify-models', action='store_true')
    args = parser.parse_args()
    if not (args.install_from_stdin or args.verify_models):
        parser.error('Select a staging or read-only verification action')
    if os.geteuid() != 0 or BASE.is_symlink() or BASE.resolve() != BASE:
        raise ValueError('ERP namespace ownership rejected')
    deployment = json.loads((BASE / 'DEPLOYMENT.json').read_text())
    config = BASE / 'config'
    if deployment.get('owner') != MARKER or config.is_symlink() or config.stat().st_uid != 0:
        raise ValueError('ERP namespace ownership rejected')
    target = config / TARGET
    if args.install_from_stdin:
        payload = sys.stdin.buffer.read(4097)
        if len(payload) > 4096:
            raise ValueError('Credential input rejected')
        value = json.loads(payload)
        if not isinstance(value, dict) or set(value) != {'apiKey'}:
            raise ValueError('Credential input rejected')
        stage_key(config, value['apiKey'])
    if not target.is_file() or target.is_symlink() or target.stat().st_uid != 0:
        raise ValueError('Private credential unavailable')
    if stat.S_IMODE(target.stat().st_mode) != 0o600:
        raise ValueError('Private credential permissions require review')
    saved = json.loads(target.read_text(encoding='utf-8'))
    result = {'provider': 'DEEPSEEK', 'credentialStaged': True, 'fileMode': '0600',
              'enabled': False, 'costApproved': False,
              'state': 'PENDING_BUDGET_AND_ADAPTER', 'backendRestarted': False,
              'businessDataSent': False, 'paidChatCalls': 0}
    if args.verify_models:
        result['verification'] = verify_models(saved['apiKey'])
    print(json.dumps(result))


if __name__ == '__main__':
    try:
        main()
    except Exception:
        print(json.dumps({'state': 'STAGING_FAILED', 'credentialPrinted': False}))
        sys.exit(1)
