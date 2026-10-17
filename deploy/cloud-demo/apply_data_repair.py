"""Fill only wholly absent, whitelisted synthetic tables; never replace existing rows."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import sys
import tarfile

sys.path.insert(0,str(Path(__file__).resolve().parent))
from install_release import BASE, MARKER, mysql, write


def main(expected):
    assert os.geteuid() == 0
    assert json.loads((BASE / 'DEPLOYMENT.json').read_text())['owner'] == MARKER
    assert mysql('SELECT @@datadir;').rstrip('/') == str(BASE / 'data/mysql')
    archive = BASE / 'incoming/data-repair.tar.gz'
    assert hashlib.file_digest(archive.open('rb'),'sha256').hexdigest() == expected
    target = BASE / 'ops/data-repair'
    target.mkdir(exist_ok=True,mode=0o700)
    allowed = json.loads((BASE / 'current/provenance.json').read_text())['counts']
    with tarfile.open(archive) as stream:
        assert set(stream.getnames()) == {'manifest.json',*(table + '.sql' for table in allowed)}
        stream.extractall(target,filter='data')
    manifest = json.loads((target / 'manifest.json').read_text())
    assert set(manifest) == set(allowed)
    restored, counts = [], {}
    for table, record in manifest.items():
        assert table.startswith('rd_') and table.replace('_','').isalnum()
        assert record['rows'] == allowed[table]
        source = target / (table + '.sql')
        assert hashlib.file_digest(source.open('rb'),'sha256').hexdigest() == record['sha256']
        current = int(mysql(f'SELECT COUNT(*) FROM duoxu_erp_demo.`{table}`;'))
        if current == 0 and record['rows'] > 0:
            mysql('USE duoxu_erp_demo;\n' + source.read_text())
            restored.append(table)
        else:
            assert current == record['rows'], 'Unexpected existing table count; no overwrite: ' + table
        counts[table] = int(mysql(f'SELECT COUNT(*) FROM duoxu_erp_demo.`{table}`;'))
        assert counts[table] == record['rows']
    assert mysql('SELECT COUNT(*),SUM(package_weight) FROM duoxu_erp_demo.rd_package_unit;') == '30\t29800.000000'
    result = {'restoredOnlyEmptyTables':restored,'counts':counts,'allExpectedRowsPresent':True,'archiveSha256':expected}
    write(BASE / 'data/DEMO_RECONCILED.json',json.dumps(result,indent=2),0o644)
    print(json.dumps(result),flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--sha256',required=True)
    main(parser.parse_args().sha256)
