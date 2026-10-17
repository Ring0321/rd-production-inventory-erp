"""Operator-only fixed synthetic inference diagnosis; never expose worker metadata via HTTP."""
import json
import multiprocessing
import os
from pathlib import Path
import sys

BASE = Path('/srv/apps/duoxu-erp-demo')
sys.path.insert(0,str(BASE / 'current/ocr'))
from document_ocr.worker import run_job


if __name__ == '__main__':
    os.chdir(BASE / 'data/ocr')
    os.environ['LD_LIBRARY_PATH'] = str(BASE / 'runtime/ocr-native/usr/lib/x86_64-linux-gnu')
    context = multiprocessing.get_context('spawn')
    parent, child = context.Pipe(duplex=False)
    data = (BASE / 'data/ocr/fixtures/synthetic-pickup.png').read_bytes()
    process = context.Process(target=run_job,args=(child,data,str(BASE / 'data/ocr/models')))
    process.start()
    child.close()
    if parent.poll(90):
        result = json.loads(parent.recv_bytes(1024*1024))
        print(json.dumps(result if 'error' in result else {'success':True,'lines':len(result['rawLines'])}),flush=True)
    else:
        print('Inference timed out',flush=True)
    if process.is_alive():
        process.kill()
    process.join()
