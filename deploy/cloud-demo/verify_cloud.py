"""External HTTPS integration checks against the synthetic cloud demo only."""
from collections import Counter
import json
from pathlib import Path
import secrets
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'temp/cloud-demo-20261018'
ORIGIN = 'https://101.42.182.117'


def request(path, token=None, body=None, method=None, headers=None):
    extra = {'tenant-id':'1', **(headers or {})}
    if token:
        extra['Authorization'] = 'Bearer ' + token
    if body is not None and not isinstance(body, bytes):
        body = json.dumps(body).encode()
        extra['Content-Type'] = 'application/json'
    req = urllib.request.Request(ORIGIN + path, data=body, headers=extra, method=method)
    try:
        response = urllib.request.urlopen(req,timeout=80)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        return response.status, response.headers, response.read()


def api(path, token=None, body=None, method=None, allowed=True, headers=None):
    status, _, raw = request('/admin-api' + path,token,body,method,headers)
    value = json.loads(raw)
    if allowed:
        assert status == 200 and value.get('code') == 0, f'{path}: {status}/{value.get("code")}'
        return value.get('data')
    assert value.get('code') != 0, 'Expected access denial: ' + path
    return value.get('code')


def main():
    access = json.loads((OUT / 'cloud-access.json').read_text(encoding='utf-8-sig'))
    assert access['origin'] == ORIGIN and access['syntheticDemoOnly']
    tokens, permissions = {}, {}
    for account in access['accounts']:
        role = account['role']
        login = api('/system/auth/login',body={'username':account['username'],'password':account['password']})
        tokens[role] = login['accessToken']
        permissions[role] = api('/system/auth/get-permission-info',tokens[role])
    admin = tokens['admin']
    bags = api('/rd/package-unit/task-packages?productionTaskId=90',admin)
    assert len(bags) == 30
    assert sum(bag['packageWeight'] for bag in bags) == 29800
    assert all(bag['customerName'] == '演示客户·宁川新材' and bag['productionTaskId'] == 90 for bag in bags)
    assert all(bag['qrCode'] == ORIGIN + '/pkg/' + bag['packageCode'] for bag in bags)
    customers = api('/rd/customer-product/customer-options',admin)
    assert '演示客户·宁川新材' in customers and all(customer.startswith('演示客户·') for customer in customers)
    replay = api('/rd/warehouse-replay/task?productionTaskId=90',admin)
    assert len(replay['members']) == 30 and len(replay['events']) == 22 and replay['complete']
    for role in [None,'driver','production']:
        api('/rd/warehouse-replay/task?productionTaskId=90',tokens.get(role),allowed=False)
    layout = api('/rd/visual-document/get?kind=LAYOUT&scopeId=DEMO-FLOW-V1',admin)
    label = api('/rd/visual-document/get?kind=LABEL&scopeId=GLOBAL',admin)
    assert layout['publishedVersion'] > 0 and label['publishedVersion'] > 0
    visible_map = api('/rd/putaway-task/location-map',tokens['viewer'])
    assert len([location for location in visible_map if location['warehouseCode'] == 'DEMO-FLOW-V1']) == 7
    assert api('/rd/visual-document/get?kind=LAYOUT&scopeId=DEMO-FLOW-V1',tokens['viewer'])['publishedVersion'] == 12
    api('/rd/visual-document/get?kind=LAYOUT&scopeId=DEMO-FLOW-V1',tokens['driver'],allowed=False)
    for forbidden in ['rd:putaway-task:scan','rd:putaway-task:confirm','rd:warehouse-location:update','rd:loading-task:batch-scan','rd:loading-task:confirm-outbound']:
        assert forbidden not in permissions['driver']['permissions']
    weight = api('/rd/weight-assistant/suggest',tokens['warehouse'],{'pickupSlipId':162,'targetWeightKg':2000,'toleranceKg':0})
    assert weight['solverStatus'] == 'OPTIMAL' and weight['totalWeightKg'] == 2000 and len(weight['bags']) == 2
    impossible = api('/rd/weight-assistant/suggest',tokens['warehouse'],{'pickupSlipId':162,'targetWeightKg':2500,'toleranceKg':0})
    assert impossible['solverStatus'] == 'INFEASIBLE'
    api('/rd/weight-assistant/suggest',tokens['driver'],{'pickupSlipId':162,'targetWeightKg':2000,'toleranceKg':0},allowed=False)
    code = 'LD20261015A0014'
    relation = api('/rd/package-unit/relations?packageCode=' + code,admin)
    assert relation['rootPackageCode'] == code and relation['nodes'] and relation['edges']
    trace = api('/rd/package-unit/trace-detail/' + code,admin)
    assert trace['packageCode'] == code and trace['internalView']
    status, headers, raw = request('/pkg/' + code + '?resource=passport')
    assert status == 200 and 'application/json' in headers['Content-Type']
    assert b'localhost' not in raw and '演示客户'.encode() not in raw
    public = json.loads(raw)
    status2, _, raw2 = request('/pkg/' + code + '?resource=passport',headers={'tenant-id':'999','Authorization':'Bearer deliberately-invalid'})
    second = json.loads(raw2)
    # Generation time legitimately advances; all source facts and digest must remain identical.
    assert status2 == 200 and {k:v for k,v in second.items() if k != 'generatedAt'} == {k:v for k,v in public.items() if k != 'generatedAt'}
    assert request('/pkg/' + code + '?tenant-id=999')[0] == 400
    assert request('/admin-api/actuator/health')[0] == 404
    ocr_status = api('/rd/document-ocr/status',tokens['warehouse'])
    assert ocr_status['available']
    boundary = 'duoxu-' + secrets.token_hex(12)
    image = (OUT / 'synthetic-pickup.png').read_bytes()
    body = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="synthetic.png"\r\nContent-Type: image/png\r\n\r\n'.encode() + image + f'\r\n--{boundary}--\r\n'.encode())
    ocr = api('/rd/document-ocr/recognize',tokens['warehouse'],body,headers={'Content-Type':'multipart/form-data; boundary=' + boundary})
    assert ocr['engine'] == 'PaddleOCR' and len(ocr['rawLines']) >= 6
    assert '000042' in [candidate['value'] for candidate in ocr['candidates']['order']['candidates']]
    result = {'result':'PASS','origin':ORIGIN,'rolesAuthenticated':list(tokens),'taskId':90,'packageCount':30,'weightKg':29800,'statusCounts':dict(Counter(bag['status'] for bag in bags)),'ledgerEvents':22,'layoutPublishedVersion':layout['publishedVersion'],'labelPublishedVersion':label['publishedVersion'],'solver':{'status':weight['solverStatus'],'weightKg':2000,'bags':2,'impossibleStatus':impossible['solverStatus']},'relationNodes':len(relation['nodes']),'relationEdges':len(relation['edges']),'publicPassportTenantHeaderIsolation':True,'publicQueryTenantInjectionRejected':True,'ocrThroughHttps':{'engine':ocr['engine'],'lines':len(ocr['rawLines'])},'driverCannotMaintainLocationOrConfirmStock':True,'syntheticCustomersOnly':True}
    (OUT / 'https-integration-result.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(result,ensure_ascii=True))


if __name__ == '__main__':
    main()
