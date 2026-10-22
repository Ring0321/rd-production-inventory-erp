"""HTTPS role and contract checks; no business mutations or model requests."""
import json
from pathlib import Path

from verify_cloud import OUT, ROOT, ORIGIN, api, request


def main():
    access = json.loads((OUT / 'cloud-access.json').read_text(encoding='utf-8-sig'))
    assert access['origin'] == ORIGIN and access['syntheticDemoOnly']
    accounts = access['accounts']
    status, _, raw = request('/demo-login.json')
    assert status == 200
    public = json.loads(raw)
    accounts = accounts + [{'role': 'public', **{key: public[key] for key in ('username', 'password')}}]
    roles = {}
    for account in accounts:
        token = api('/system/auth/login', body={key: account[key] for key in ('username', 'password')})['accessToken']
        permissions = api('/system/auth/get-permission-info', token)['permissions']
        result = {}
        for query in ('Q03', 'Q04', 'Q05'):
            permission = 'rd:quality-inspection:query' if query == 'Q04' else 'rd:package-unit:query'
            allowed = account['role'] != 'driver' and (permission in permissions or '*:*:*' in permissions)
            if not allowed:
                assert api('/rd/work-assistant/query?queryType=' + query, token, allowed=False) == 403
                result[query] = 'FORBIDDEN'
                continue
            data = api('/rd/work-assistant/query?queryType=' + query, token)
            assert data['scope'] == 'TENANT_AUTHORIZED' and isinstance(data['asOf'], str)
            assert data['quality'] in ('KNOWN', 'PARTIAL')
            primary = 'BUSINESS_OBJECT_COUNT' if query == 'Q04' else 'DISTINCT_PACKAGE_COUNT'
            metric = data['metrics'][primary]
            assert metric['quality'] == 'KNOWN' and isinstance(metric['value'], str)
            assert len(data['items']) <= 20
            if data['quality'] == 'KNOWN':
                assert data['total'] == int(metric['value'])
            page = api('/rd/work-assistant/query?queryType=' + query + '&pageNo=2&pageSize=1', token)
            assert page['metrics'] == data['metrics'] and page['total'] == data['total'] and len(page['items']) <= 1
            result[query] = {'quality': data['quality'], 'primaryCount': metric['value'], 'pageTotal': data['total']}
        roles[account['role']] = result
        if account['role'] == 'public':
            for suffix in ('queryType=Q03&tenantId=2', 'queryType=Q03&queryType=Q05',
                           'queryType=Q03&pageSize=101', 'queryType=Q03&warehouseCode=DEMO-SPACE-V2'):
                assert api('/rd/work-assistant/query?' + suffix, token, allowed=False) == 400
            api('/rd/work-assistant/query?queryType=Q03', token, allowed=False, headers={'tenant-id': '2'})
            for reserved in ('Q09', 'Q10'):
                suffix = '&alertId=1' if reserved == 'Q10' else ''
                assert api('/rd/work-assistant/query?queryType=' + reserved + suffix, token, allowed=False) == 503
    assert api('/rd/work-assistant/query?queryType=Q03', allowed=False) == 401
    result = {'status': 'PASS', 'origin': ORIGIN, 'roles': roles, 'rolesChecked': len(roles),
              'paginationAndFullScopeTotals': True, 'unsupportedFiltersRejected': True,
              'crossTenantDenied': True, 'reservedQueriesDisabled': True,
              'businessWrites': 0, 'paidModelCalls': 0, 'credentialsPrinted': False}
    output = ROOT / 'temp/s1-02-cloud-verification'
    output.mkdir(parents=True, exist_ok=True)
    (output / 'api.json').write_text(json.dumps(result, indent=2), encoding='utf-8')
    print(json.dumps(result))


if __name__ == '__main__':
    main()
