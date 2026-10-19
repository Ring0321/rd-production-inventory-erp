"""Append synthetic showcase records through existing ERP business APIs only."""
import argparse
from collections import Counter
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import sys
import time
from urllib.parse import urlsplit

sys.path.insert(0, '/srv/apps/duoxu-erp-demo/ops')
from install_release import BASE, MARKER, api, mysql, write

EXPANSION = 'DUOXU-DEMO-SPACE-V2'
WAREHOUSE = 'DEMO-SPACE-V2'
NOTE = EXPANSION + '；全合成演示，非实际生产或交易；检验值为模拟值；货位及上下层为自建示意，非实测或承载设计。'
TABLES = ['rd_package_unit', 'rd_stock_lot', 'rd_stock_transaction', 'rd_location', 'rd_loading_task', 'rd_putaway_task']
WRITES = {
    ('POST', '/rd/product-master/create'): 'admin',
    ('POST', '/rd/warehouse-location/create'): 'admin',
    ('POST', '/rd/production-task/create'): 'production',
    ('POST', '/rd/production-output/create'): 'production',
    ('POST', '/rd/production-output/confirm'): 'production',
    ('POST', '/rd/package-unit/generate'): 'production',
    ('POST', '/rd/package-unit/print-batch'): 'production',
    ('POST', '/rd/quality-inspection/create-batch'): 'quality',
    ('POST', '/rd/quality-inspection/submit'): 'quality',
    ('POST', '/rd/quality-inspection/approve'): 'admin',
    ('POST', '/rd/quality-inspection/reject'): 'admin',
    ('POST', '/rd/putaway-task/create'): 'warehouse',
    ('POST', '/rd/putaway-task/batch-scan/confirm'): 'warehouse',
    ('PUT', '/rd/putaway-task/confirm'): 'warehouse',
    ('POST', '/rd/sale-order/create'): 'sales',
    ('POST', '/rd/order-fulfillment/allocate'): 'sales',
    ('POST', '/rd/loading-task/pickup-slip/create'): 'sales',
    ('POST', '/rd/loading-task/create'): 'warehouse',
    ('POST', '/rd/loading-task/batch-scan/confirm'): 'warehouse',
    ('POST', '/rd/loading-task/confirm-outbound'): 'warehouse'
}


def digest(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, ensure_ascii=False).encode()).hexdigest()


def plan():
    customers = ['宁川新材', '云川材料', '星禾工业', '锦源新能', '川岳精材', '青岚科技', '海禾材料', '北辰工贸']
    models = ['SIC-90', 'SIC-95', 'SIC-97', 'SIC-98', 'G-SIC-98', 'G-SIC-99', 'GC-F120', 'GC-F240',
              'GC-F400', 'GC-F600', 'AG-300', 'AG-320', 'AG-350', 'AG-400', 'AN-500', 'AN-550',
              'AN-600', 'RC-95', 'RC-98', 'TAIL-80']
    products = []
    for index, model in enumerate(models):
        category = ('碳化硅成品' if index < 6 else '碳化硅微粉' if index < 10 else
                    '人造石墨' if index < 14 else '负极材料' if index < 17 else
                    '增碳材料' if index < 19 else '尾料周转')
        products.append({'productCode': f'DEMO2-P{index + 1:03}', 'productName': category + '（演示）',
                         'customerName': '演示客户·' + customers[index % len(customers)],
                         'productModel': model + '（演示）', 'category': category, 'unit': 'kg',
                         'defaultPackageWeight': [1000, 980, 1050, 950, 1100][index % 5],
                         'qualityStandard': '按演示检验模板校验；模拟数值不作为实际产品质量标准',
                         'enabled': True, 'remark': NOTE})
    locations = []
    for area in ['A', 'B']:
        for row in range(1, 5):
            for column in range(1, 7):
                for level in range(1, 3):
                    index = len(locations)
                    locations.append({
                        'warehouseCode': WAREHOUSE, 'warehouseName': '双层成品演示仓（合成示意）',
                        'areaCode': area, 'areaName': ('碳化硅演示区' if area == 'A' else '石墨材料演示区'),
                        'rowCode': f'{row:02}', 'columnCode': f'{column:02}', 'levelCode': f'{level:02}',
                        'locationCode': f'V2-{area}{row:02}-{column:02}-{level:02}',
                        'locationName': f'{area}区{row}排{column}位{level}层（演示）',
                        'capacityPackages': 8 if index % 5 == 0 else 12, 'capacityWeight': 16000,
                        'enabled': True, 'frozen': index >= 92, 'locationStatus': 'AVAILABLE', 'remark': NOTE})
    rows, location_index = [], 0
    for index in range(60):
        phase = index % 16
        scenario = 'stock' if phase < 10 else ['assigned', 'loading', 'shipped', 'passed', 'waiting', 'rejected'][phase - 10]
        row = {'key': f'{index:02}', 'productIndex': index // 3, 'product': products[index // 3],
               'count': 8, 'scenario': scenario, 'furnaceNo': f'DEMO2-{index // 3 + 1:02}-{index % 3 + 1:02}',
               'furnaceLayer': ['上层（演示）', '中层（演示）', '下层（演示）'][index % 3]}
        if scenario in ['stock', 'assigned', 'loading', 'shipped']:
            row['locationIndex'] = location_index
            location_index += 1
        rows.append(row)
    return products, locations, rows


class Journal:
    def __init__(self, path):
        self.path = path
        self.value = json.loads(path.read_text(encoding='utf-8')) if path.exists() else {
            'marker': EXPANSION, 'planHash': digest(plan()), 'steps': {}}
        assert self.value['marker'] == EXPANSION and self.value['planHash'] == digest(plan())

    def save(self):
        write(self.path, json.dumps(self.value, ensure_ascii=False, indent=2), 0o600)

    def has(self, key):
        return self.value['steps'].get(key, {}).get('state') == 'DONE'

    def result(self, key):
        assert self.has(key)
        return self.value['steps'][key]['result']

    def run(self, key, request, callback):
        previous = self.value['steps'].get(key)
        if previous:
            assert previous['requestHash'] == digest(request), 'Checkpoint input changed'
            if previous['state'] != 'DONE':
                raise RuntimeError('uncertain request ' + key + '; inspect ERP records before recovery; no automatic replay')
            return previous['result']
        self.value['steps'][key] = {'state': 'PENDING', 'requestHash': digest(request)}
        self.save()
        result = callback()
        self.value['steps'][key].update(state='DONE', result=result)
        self.save()
        return result


def check_write(role, path, method):
    assert not urlsplit(path).scheme and not urlsplit(path).netloc
    assert WRITES.get((method, urlsplit(path).path)) == role, 'Write outside showcase business boundary'


def original_snapshot():
    result = {}
    for table in TABLES:
        ids = [int(row) for row in mysql(f'SELECT id FROM duoxu_erp_demo.{table} ORDER BY id;').splitlines() if row]
        assert all(value > 0 for value in ids)
        clause = ','.join(map(str, ids)) or '0'
        raw = mysql(f'SELECT * FROM duoxu_erp_demo.{table} WHERE id IN ({clause}) ORDER BY id;')
        result[table] = {'ids': ids, 'sha256': hashlib.sha256(raw.encode()).hexdigest()}
    return result


def verify_original(snapshot):
    for table, before in snapshot.items():
        assert table in TABLES
        assert all(isinstance(value, int) and value > 0 for value in before['ids'])
        clause = ','.join(map(str, before['ids'])) or '0'
        raw = mysql(f'SELECT * FROM duoxu_erp_demo.{table} WHERE id IN ({clause}) ORDER BY id;')
        assert hashlib.sha256(raw.encode()).hexdigest() == before['sha256'], 'Existing business records changed: ' + table


def sessions():
    values = json.loads((BASE / 'config/secrets.json').read_text())
    tokens = {'admin': api('/system/auth/login', body={
        'username': values.get('adminUsername', 'admin'), 'password': values['admin']})['accessToken']}
    for role in ['production', 'quality', 'warehouse', 'driver', 'sales']:
        tokens[role] = api('/system/auth/login', body={
            'username': role + 'demo', 'password': values['roles'][role]})['accessToken']
    info = {role: api('/system/auth/get-permission-info', token) for role, token in tokens.items()}
    assert 'super_admin' in info['admin']['roles'] and 'rd_forklift_driver' in info['driver']['roles']
    assert info['quality']['user']['id'] != info['admin']['user']['id']
    return tokens, info['driver']['user']['id']


def seed(journal, tokens, driver):
    products, locations, rows = plan()
    def read(path):
        return api(path, tokens['admin'])
    def perform(key, role, path, body=None, method='POST'):
        check_write(role, path, method)
        return journal.run(key, {'role': role, 'path': path, 'body': body, 'method': method},
                           lambda: api(path, tokens[role], body, method))
    for index, product in enumerate(products):
        perform(f'product.{index}', 'admin', '/rd/product-master/create', product)
    for index, location in enumerate(locations):
        perform(f'location.{index}', 'admin', '/rd/warehouse-location/create', location)
    for index, product in enumerate(products):
        perform(f'task.{index}', 'production', '/rd/production-task/create', {
            'customerName': product['customerName'], 'productionDate': f'2026-10-{16 + index % 3:02}',
            'shiftCode': ['演示白班', '演示夜班'][index % 2], 'remark': NOTE + f' 产品 {product["productCode"]}'})
    for index, row in enumerate(rows):
        prefix, product = 'batch.' + row['key'], row['product']
        def action(suffix, *args, **kwargs):
            return perform(prefix + '.' + suffix, *args, **kwargs)
        def done(suffix):
            return journal.has(prefix + '.' + suffix)
        date = f'2026-10-{16 + row["productIndex"] % 3:02}'
        output = action('create', 'production', '/rd/production-output/create', {
            'productionTaskId': journal.result(f'task.{row["productIndex"]}'),
            'clientRequestId': EXPANSION + ':output:' + row['key'], 'productionDate': date,
            'furnaceOutDate': date, 'shiftCode': ['演示白班', '演示夜班'][row['productIndex'] % 2],
            'customerName': product['customerName'], 'productModel': product['productModel'],
            'furnaceNo': row['furnaceNo'], 'furnaceLayer': row['furnaceLayer'], 'outputQty': row['count'],
            'packageWeight': product['defaultPackageWeight'], 'sourceSheet': NOTE, 'remark': NOTE + ' ' + row['scenario']})
        if not done('confirm'):
            record = read(f'/rd/production-output/get?id={output}')
            assert record['status'] == 'DRAFT'
            action('confirm', 'production', '/rd/production-output/confirm', {'id': output, 'version': record['version']})
        generated = action('generate', 'production', '/rd/package-unit/generate', {'productionOutputId': output, 'packageCount': row['count']})
        bags = sorted(read(f'/rd/package-unit/trace?productionOutputId={output}'), key=lambda bag: bag['id'])
        assert len(bags) == row['count'] and {bag['id'] for bag in bags} == {bag['id'] for bag in generated}
        action('print', 'production', '/rd/package-unit/print-batch', {'packageIds': [bag['id'] for bag in bags]})
        inspection = action('qc', 'quality', '/rd/quality-inspection/create-batch', {
            'packageId': bags[0]['id'], 'packageIds': [bag['id'] for bag in bags], 'samplePackageIds': [bags[0]['id']],
            'clientRequestId': EXPANSION + ':qc:' + row['key'], 'remark': NOTE,
            'items': [{'samplePackageId': bags[0]['id'], 'itemCode': 'DEMO-CHECK',
                       'itemName': '演示检验指标（模拟值，非实测）', 'standardMin': 90, 'standardMax': 100,
                       'measuredValue': 85 if row['scenario'] == 'rejected' else 98.5, 'unit': '%', 'remark': NOTE}]})
        if not done('qc.submit'):
            action('qc.submit', 'quality', f'/rd/quality-inspection/submit?id={inspection}')
        if row['scenario'] != 'waiting' and not done('qc.review'):
            if row['scenario'] == 'rejected':
                action('qc.review', 'admin', '/rd/quality-inspection/reject', {'id': inspection, 'reason': NOTE + ' 模拟值低于演示下限。'})
            else:
                action('qc.review', 'admin', f'/rd/quality-inspection/approve?id={inspection}')
        if 'locationIndex' in row:
            location_id = journal.result(f'location.{row["locationIndex"]}')
            putaway = action('putaway', 'warehouse', '/rd/putaway-task/create', {
                'plannedLocationId': location_id, 'forkliftDriverId': driver,
                'packageIds': [bag['id'] for bag in bags], 'remark': NOTE})
            if row['scenario'] != 'assigned':
                if not done('putaway.scan'):
                    detail = read(f'/rd/putaway-task/get?id={putaway}')
                    action('putaway.scan', 'warehouse', '/rd/putaway-task/batch-scan/confirm', {
                        'id': putaway, 'version': detail['task']['version'], 'packageCode': bags[0]['packageCode'],
                        'taskLineIds': [line['id'] for line in detail['lines']],
                        'idempotencyKey': EXPANSION + ':in:' + row['key'], 'deviceCode': 'DEMO2-KEEPER'})
                if not done('putaway.post'):
                    detail = read(f'/rd/putaway-task/get?id={putaway}')
                    action('putaway.post', 'warehouse', '/rd/putaway-task/confirm', {
                        'id': putaway, 'version': detail['task']['version'], 'finalLocationId': location_id}, method='PUT')
        if row['scenario'] in ['loading', 'shipped']:
            bags = sorted(read(f'/rd/package-unit/trace?productionOutputId={output}'), key=lambda bag: bag['id'])
            sale = action('sale', 'sales', '/rd/sale-order/create', {
                'orderNo': 'DEMO2-ORDER-' + row['key'], 'orderDate': date, 'customerName': product['customerName'],
                'productModel': product['productModel'], 'requiredQty': row['count'], 'actualOutputQty': 0, 'remark': NOTE})
            action('allocation', 'sales', '/rd/order-fulfillment/allocate', {
                'orderId': sale, 'stockLotId': bags[0]['stockLotId'], 'quantity': row['count'], 'remark': NOTE})
            pickup = action('pickup', 'sales', '/rd/loading-task/pickup-slip/create', {
                'slipNo': 'DEMO2-PICKUP-' + row['key'], 'customerName': product['customerName'], 'pickupDate': date,
                'requestedPackageCount': row['count'], 'requestedWeight': row['count'] * product['defaultPackageWeight'],
                'remark': NOTE, 'lines': [{'lineNo': 1, 'saleOrderId': sale, 'productName': product['productName'],
                    'productModel': product['productModel'], 'furnaceNo': row['furnaceNo'], 'furnaceLayer': row['furnaceLayer'],
                    'requestedPackageCount': row['count'], 'requestedWeight': row['count'] * product['defaultPackageWeight'], 'remark': NOTE}]})
            slip = read(f'/rd/loading-task/pickup-slip/get?id={pickup}')
            assert len(slip['lines']) == 1
            loading = action('loading', 'warehouse', '/rd/loading-task/create', {
                'pickupSlipId': pickup, 'forkliftDriverId': driver, 'remark': NOTE,
                'selections': [{'pickupSlipLineId': slip['lines'][0]['id'], 'packageId': bag['id']} for bag in bags]})
            if not done('loading.scan'):
                detail = read(f'/rd/loading-task/get?id={loading}')
                selected = detail['lines'] if row['scenario'] == 'shipped' else detail['lines'][:4]
                action('loading.scan', 'warehouse', '/rd/loading-task/batch-scan/confirm', {
                    'taskId': loading, 'version': detail['task']['version'], 'representativePackageCode': selected[0]['packageCode'],
                    'taskLineIds': [line['id'] for line in selected], 'idempotencyKey': EXPANSION + ':out:' + row['key'],
                    'deviceCode': 'DEMO2-KEEPER'})
            if row['scenario'] == 'shipped' and not done('loading.post'):
                detail = read(f'/rd/loading-task/get?id={loading}')
                action('loading.post', 'warehouse', '/rd/loading-task/confirm-outbound', {
                    'taskId': loading, 'version': detail['task']['version'], 'remark': NOTE})
        if index % 6 == 5:
            print(json.dumps({'completedBatches': index + 1, 'totalBatches': len(rows)}), flush=True)


def verify(journal, tokens):
    products, locations, rows = plan()
    units = []
    for index in range(len(products)):
        units.extend(api(f'/rd/package-unit/task-packages?productionTaskId={journal.result(f"task.{index}")}', tokens['admin']))
    assert len(units) == 480 and len({bag['id'] for bag in units}) == 480
    assert sum(bag['packageWeight'] for bag in units) == 487680
    assert all(bag['qrCode'] == 'https://101.42.182.117/pkg/' + bag['packageCode'] for bag in units)
    expected = Counter()
    states = {'stock': 'IN_STOCK', 'loading': 'IN_STOCK', 'shipped': 'OUTBOUNDED', 'assigned': 'INBOUND_PENDING',
              'passed': 'QC_PASSED', 'waiting': 'QC_PENDING', 'rejected': 'QC_REJECTED'}
    for row in rows:
        expected[states[row['scenario']]] += row['count']
    assert Counter(bag['status'] for bag in units) == expected
    mapped = api('/rd/putaway-task/location-map', tokens['warehouse'])
    new_map = [location for location in mapped if location['warehouseCode'] == WAREHOUSE]
    assert len(new_map) == 96 and Counter(l['levelCode'] for l in new_map) == {'01': 48, '02': 48}
    in_stock = [bag for bag in units if bag['status'] == 'IN_STOCK']
    assert sum(location['occupiedPackages'] for location in new_map) == len(in_stock)
    assert sum(location['occupiedWeight'] for location in new_map) == sum(bag['packageWeight'] for bag in in_stock)
    listed = api('/rd/product-master/page?pageNo=1&pageSize=100', tokens['admin'])
    assert {journal.result(f'product.{index}') for index in range(20)}.issubset({p['id'] for p in listed['list']})
    verify_original(journal.value['original'])
    original = api('/rd/package-unit/task-packages?productionTaskId=90', tokens['admin'])
    assert len(original) == 30
    return {'result': 'PASS', 'marker': EXPANSION, 'checkedAt': datetime.now(timezone.utc).isoformat(),
            'newProducts': 20, 'newTasks': 20, 'newProductionBatches': 60, 'newPackages': len(units),
            'newWeightKg': 487680, 'newLocations': len(new_map), 'newLevelCounts': dict(Counter(l['levelCode'] for l in new_map)),
            'newStatusCounts': dict(expected), 'newInStockWeightKg': sum(bag['packageWeight'] for bag in in_stock),
            'originalTask90Packages': len(original), 'originalBusinessRowsUnchanged': True, 'syntheticOnly': True}


def main():
    parser = argparse.ArgumentParser()
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument('--apply', action='store_true')
    mode.add_argument('--verify', action='store_true')
    args = parser.parse_args()
    products, locations, rows = plan()
    if not args.apply and not args.verify:
        print(json.dumps({'marker': EXPANSION, 'products': len(products), 'locations': len(locations),
                          'tasks': len(products), 'batches': len(rows), 'packages': sum(row['count'] for row in rows),
                          'weightKg': sum(row['count'] * row['product']['defaultPackageWeight'] for row in rows), 'planHash': digest(plan())}))
        return
    assert os.geteuid() == 0
    assert json.loads((BASE / 'DEPLOYMENT.json').read_text())['owner'] == MARKER
    assert mysql('SELECT @@datadir;').rstrip('/') == str(BASE / 'data/mysql')
    journal = Journal(BASE / 'data/SHOWCASE_EXPANSION_V2.json')
    import fcntl
    lock = os.open(BASE / 'data/SHOWCASE_EXPANSION_V2.lock', os.O_CREAT | os.O_RDWR | os.O_NOFOLLOW, 0o600)
    try:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        tokens, driver = sessions()
        if args.apply:
            if 'original' not in journal.value:
                backup_file = BASE / 'logs/backup-latest.json'
                backup = json.loads(backup_file.read_text())
                assert backup['verifiedRestore'] and not backup['originalDatabasesAccessed']
                assert time.time() - backup_file.stat().st_mtime < 7200, 'Take and verify a fresh ERP-only backup first'
                assert not any(l['warehouseCode'] == WAREHOUSE for l in api('/rd/putaway-task/location-map', tokens['admin']))
                journal.value['original'] = original_snapshot()
                journal.save()
            seed(journal, tokens, driver)
        result = verify(journal, tokens)
        if args.apply:
            journal.value['verification'] = result
            journal.save()
            write(BASE / 'data/SHOWCASE_EXPANSION_V2_RESULT.json', json.dumps(result, indent=2), 0o644)
        print(json.dumps(result), flush=True)
    finally:
        os.close(lock)


if __name__ == '__main__':
    main()
