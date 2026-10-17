"""Export only the labelled synthetic showcase; never export local users or secrets."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tarfile

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'temp/cloud-demo-20261018'
sys.path.insert(0, str(OUT / 'py'))
import sqlparse
from sqlparse import tokens as T

CUSTOMER = '演示客户·宁川新材'
MARKER = 'DUOXU-DEMO-FLOW-V1'


def run(args, **kwargs):
    result = subprocess.run(args, stdout=subprocess.PIPE, stderr=subprocess.PIPE, **kwargs)
    if result.returncode:
        raise RuntimeError(f'{Path(args[0]).name} failed ({result.returncode}); output withheld')
    return result.stdout


def insert_table(statement):
    if statement.get_type() != 'INSERT':
        return None
    flat = [t for t in statement.flatten() if not t.is_whitespace and t.ttype not in T.Comment]
    index = next(i for i, token in enumerate(flat) if token.normalized == 'INTO')
    return flat[index + 1].value.strip('`')


def sanitized_schema(source, destination):
    text = source.read_text(encoding='utf-8-sig')
    tokens = list(sqlparse.lexer.tokenize(text))
    assert ''.join(value for _, value in tokens) == text
    kept, removed, index = [], 0, 0
    while index < len(tokens):
        token_type, value = tokens[index]
        if token_type is T.Keyword.DML and value.upper() == 'INSERT':
            end = index
            while end < len(tokens) and tokens[end] != (T.Punctuation, ';'):
                end += 1
            part = ''.join(value for _, value in tokens[index:end+1])
            if insert_table(sqlparse.parse(part)[0]) == 'rd_customer_product':
                removed += 1
                index = end + 1
                continue
        kept.append(value)
        index += 1
    assert removed, 'Expected customer master seed was not located'
    # Preserve MySQL client DELIMITER directives and every other source byte.
    destination.write_text(''.join(kept), encoding='utf-8')
    return removed


def build(repair_only=False):
    OUT.mkdir(exist_ok=True)
    settings = dict(line.split('=', 1) for line in (ROOT / 'temp/local-runtime-patent/.env.local').read_text(encoding='utf-8-sig').splitlines() if line and not line.startswith('#') and '=' in line)
    assert settings['RD_DB_PORT'] == '3310'
    mysql = shutil.which('mysql')
    dump = shutil.which('mysqldump')
    assert mysql and dump
    env = dict(os.environ, MYSQL_PWD=settings['RD_DB_PASSWORD'])
    args = ['--host=127.0.0.1', '--port=3310', '--user=root', '--default-character-set=utf8mb4']

    def query(sql):
        return run([mysql, *args, '--batch', '--raw', '--skip-column-names', 'rd_erp', '-e', sql], env=env).decode('utf-8').strip()

    def ids(table, where):
        rows = query(f'SELECT id FROM `{table}` WHERE {where} ORDER BY id')
        return ','.join(str(int(row)) for row in rows.splitlines()) if rows else '0'

    summary = query('SELECT COUNT(*),SUM(package_weight),COUNT(DISTINCT customer_name),MIN(customer_name) FROM rd_package_unit WHERE production_task_id=90 AND deleted=0 AND tenant_id=1').split('\t')
    assert summary == ['30', '29800.000000', '1', CUSTOMER], 'Curated showcase drift; refusing export'
    assert query('SELECT remark FROM rd_production_task WHERE id=90').find(MARKER) >= 0
    tables = {}
    def add(table, where):
        tables[table] = where
        return ids(table, where)

    outputs = add('rd_production_output', 'production_task_id=90 AND tenant_id=1')
    bags = add('rd_package_unit', 'production_task_id=90 AND tenant_id=1')
    locations = add('rd_location', "warehouse_code='DEMO-FLOW-V1' AND tenant_id=1")
    add('rd_production_task', 'id=90 AND tenant_id=1')
    qc = add('rd_quality_inspection', f'production_output_id IN ({outputs}) AND tenant_id=1')
    for suffix in ['member', 'item', 'event']:
        add('rd_quality_inspection_' + suffix, f'inspection_id IN ({qc}) AND tenant_id=1')
    lots = add('rd_stock_lot', f"customer_name='{CUSTOMER}' AND location_id IN ({locations}) AND tenant_id=1")
    add('rd_stock_transaction', f'package_id IN ({bags}) AND tenant_id=1')
    add('rd_daily_stock_summary', f'stock_lot_id IN ({lots}) AND tenant_id=1')
    for kind in ['putaway', 'loading']:
        lines = add(f'rd_{kind}_task_line', f'package_id IN ({bags}) AND tenant_id=1')
        task_ids = query(f'SELECT DISTINCT task_id FROM rd_{kind}_task_line WHERE id IN ({lines}) ORDER BY task_id').replace('\n', ',') or '0'
        add(f'rd_{kind}_task', f'id IN ({task_ids}) AND tenant_id=1')
        related = ['scan_record', 'batch_scan_record', 'exception'] if kind == 'putaway' else ['scan_record', 'package_claim', 'exception', 'reduction']
        for suffix in related:
            add(f'rd_{kind}_{suffix}', f'task_id IN ({task_ids}) AND tenant_id=1')
    for kind in ['inbound', 'outbound']:
        lines = add(f'rd_{kind}_order_line', f'package_id IN ({bags}) AND tenant_id=1')
        orders = query(f'SELECT DISTINCT order_id FROM rd_{kind}_order_line WHERE id IN ({lines}) ORDER BY order_id').replace('\n', ',') or '0'
        add(f'rd_{kind}_order', f'id IN ({orders}) AND tenant_id=1')
    sales = add('rd_sale_order_extend', f"customer_name='{CUSTOMER}' AND remark LIKE '%{MARKER}%' AND tenant_id=1")
    add('rd_order_fulfillment', f'order_id IN ({sales}) AND tenant_id=1')
    slips = add('rd_pickup_slip', f"customer_name='{CUSTOMER}' AND remark LIKE '%{MARKER}%' AND tenant_id=1")
    add('rd_pickup_slip_line', f'slip_id IN ({slips}) AND tenant_id=1')
    for name in ['rd_label_print_log', 'rd_package_task_event', 'rd_scan_event']:
        add(name, f'package_id IN ({bags}) AND tenant_id=1')
    docs = add('rd_visual_document', "((kind='LAYOUT' AND scope_id='DEMO-FLOW-V1') OR (kind='LABEL' AND scope_id='GLOBAL')) AND tenant_id=1")
    add('rd_visual_document_event', f'document_id IN ({docs}) AND tenant_id=1')
    for name in ['rd_customer_product', 'rd_product_master']:
        add(name, f"customer_name='{CUSTOMER}' AND tenant_id=1")

    sql_dir = OUT / 'bundle/sql'
    sql_dir.mkdir(parents=True, exist_ok=True)
    sanitized = {}
    for source in ['rd_erp_clean.sql', 'rd_erp_upgrade_slim.sql']:
        sanitized[source] = sanitized_schema(ROOT / 'backend/sql/mysql' / source, sql_dir / source)
    shutil.copyfile(ROOT / 'backend/sql/mysql/rd_visual_documents.sql', sql_dir / 'rd_visual_documents.sql')
    counts, repair_manifest = {}, {}
    repair_dir = OUT / 'data-repair'
    if repair_only:
        repair_dir.mkdir(exist_ok=True)
    destination = repair_dir / 'all.sql' if repair_only else sql_dir / 'synthetic-showcase.sql'
    with destination.open('wb') as output:
        output.write(b'SET NAMES utf8mb4;\nSET FOREIGN_KEY_CHECKS=0;\n')
        for table, where in tables.items():
            counts[table] = int(query(f'SELECT COUNT(*) FROM `{table}` WHERE {where}'))
            # mysqldump.exe may interpret non-ASCII --where arguments using an ANSI code page.
            # Select IDs with mysql first, then give mysqldump an ASCII-only predicate.
            selected_ids = ids(table,where)
            data = run([dump, *args, '--no-create-info', '--complete-insert', '--skip-extended-insert', '--single-transaction', '--skip-lock-tables', '--skip-add-locks', '--skip-comments', '--skip-triggers', '--no-tablespaces', '--set-gtid-purged=OFF', f'--where=id IN ({selected_ids})', 'rd_erp', table], env=env)
            inserts = [statement for statement in sqlparse.parse(data.decode('utf-8')) if statement.get_type() == 'INSERT']
            assert len(inserts) == counts[table] and all(insert_table(statement) == table for statement in inserts), 'Exported row count mismatch: ' + table
            if repair_only:
                (repair_dir / (table + '.sql')).write_bytes(data)
                repair_manifest[table] = {'rows':counts[table],'sha256':hashlib.sha256(data).hexdigest()}
            output.write(data)
        if repair_only:
            (repair_dir / 'manifest.json').write_text(json.dumps(repair_manifest,indent=2),encoding='utf-8')
            with tarfile.open(OUT / 'data-repair.tar.gz','w:gz') as archive:
                archive.add(repair_dir / 'manifest.json',arcname='manifest.json')
                for table in tables:
                    archive.add(repair_dir / (table + '.sql'),arcname=table + '.sql')
            repair = OUT / 'data-repair.tar.gz'
            print(json.dumps({'repairSha256':hashlib.file_digest(repair.open('rb'),'sha256').hexdigest(),'counts':counts}))
            return
        actors = [(229, 'productiondemo', '演示生产班长', 600001), (230, 'qualitydemo', '演示质检员', 600006), (231, 'warehousedemo', '演示仓管员', 600002), (232, 'driverdemo', '演示叉车司机', 600008), (233, 'salesdemo', '演示商务员', 600003), (234, 'viewerdemo', '演示只读访客', 600005)]
        sql = []
        for uid, username, name, role in actors:
            sql.append(f"INSERT INTO system_users (id,tenant_id,username,password,nickname,dept_id,status,remark) VALUES ({uid},1,'{username}','!CLOUD_ACTIVATION_REQUIRED!','{name}',1,0,'Synthetic cloud demonstration account');")
            sql.append(f'INSERT INTO system_user_role (user_id,role_id,tenant_id) VALUES ({uid},{role},1);')
        sql += [f"UPDATE rd_package_unit SET qr_code=CONCAT('https://101.42.182.117/pkg/',package_code) WHERE id IN ({bags});", "INSERT INTO rd_package_code_sequence (tenant_id,biz_date,current_value) VALUES (1,'2026-10-15',30);", "UPDATE system_tenant SET name='垛序演示中心' WHERE id=1;", "UPDATE system_users SET nickname='演示系统管理员' WHERE id=1;", 'SET FOREIGN_KEY_CHECKS=1;']
        output.write(('\n'.join(sql) + '\n').encode('utf-8'))
    assert query('SELECT COUNT(*),SUM(package_weight),COUNT(DISTINCT customer_name),MIN(customer_name) FROM rd_package_unit WHERE production_task_id=90 AND deleted=0 AND tenant_id=1').split('\t') == summary
    provenance = {'sourceCommit': run(['git', 'rev-parse', 'HEAD']).decode().strip(), 'marker': MARKER, 'taskId':90,'packageCount':30,'weightKg':29800, 'counts': counts, 'removedCustomerSeeds':sanitized, 'noLocalUsersOrCredentialsExported':True}
    (OUT / 'bundle/provenance.json').write_text(json.dumps(provenance, ensure_ascii=False, indent=2), encoding='utf-8')
    jar = ROOT / 'backend/rd-erp-server/target/rd-erp-server.jar'
    assert jar.stat().st_size > 200_000_000
    with tarfile.open(OUT / 'duoxu-cloud-demo.tar.gz', 'w:gz', compresslevel=3) as archive:
        archive.add(OUT / 'bundle', arcname='.')
        archive.add(jar, arcname='backend/rd-erp-server.jar')
        archive.add(ROOT / 'frontend/dist-prod', arcname='frontend')
        ocr = ROOT / 'tools/document-ocr'
        for name in ['document_ocr', 'requirements.txt', 'models.lock.json', 'README.md', 'tests']:
            archive.add(ocr / name, arcname='ocr/' + name, filter=lambda info: None if '__pycache__' in info.name else info)
        for model in ['PP-OCRv5_mobile_det', 'PP-OCRv5_mobile_rec']:
            archive.add(ocr / '.runtime/models' / model, arcname='ocr/.runtime/models/' + model)
        for path in (ROOT / 'docs').rglob('*'):
            if path.is_file() and ('license' in path.name.lower() or 'notice' in path.name.lower() or '开源' in path.name):
                archive.add(path, arcname='notices/' + str(path.relative_to(ROOT / 'docs')).replace('\\','/'))
    bundle = OUT / 'duoxu-cloud-demo.tar.gz'
    digest = hashlib.file_digest(bundle.open('rb'), 'sha256').hexdigest()
    (OUT / 'bundle.sha256').write_text(digest + '  duoxu-cloud-demo.tar.gz\n', encoding='ascii')
    print(json.dumps({'bundleBytes':bundle.stat().st_size,'sha256':digest,'counts':counts}, ensure_ascii=True))


if __name__ == '__main__':
    build(repair_only='--repair-only' in sys.argv)
