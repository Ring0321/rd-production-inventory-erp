"""Generate local-only login handoff with Windows ACLs; never echo credentials."""
import argparse
import json
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'temp/cloud-demo-20261018/cloud-access.json'


def protect(path):
    assert os.name == 'nt', 'This handoff uses Windows NTFS ACLs'
    username = subprocess.check_output(['whoami'],text=True).strip()
    result = subprocess.run(['icacls',str(path),'/inheritance:r','/grant:r',username + ':(R,W)','/remove:g','*S-1-1-0','*S-1-5-11','*S-1-5-32-545'],capture_output=True)
    assert result.returncode == 0, 'Credential file ACL could not be restricted'


def main(target_dir):
    protect(SOURCE)
    access = json.loads(SOURCE.read_text(encoding='utf-8-sig'))
    assert access['origin'] == 'https://101.42.182.117' and access['syntheticDemoOnly']
    labels = {'admin':'系统管理员','production':'生产班长','quality':'质检员','warehouse':'仓管员','driver':'叉车司机','sales':'商务员','viewer':'只读访客'}
    assert len(access['accounts']) == 7
    target_dir.mkdir(parents=True,exist_ok=True)
    target = target_dir / '云端演示登录资料-请勿转发.txt'
    if target.exists():
        assert target.read_text(encoding='utf-8-sig').startswith('垛序 ERP 云端演示登录资料')
    else:
        target.touch()
    protect(target)
    lines = ['垛序 ERP 云端演示登录资料','仅供本机保管，不要上传到 Git 或转发全部账号。','','系统入口：https://101.42.182.117','手机/PDA：https://101.42.182.117/pda','','投屏讲解建议使用只读访客；需要演示办理流程时使用对应岗位。','']
    for account in access['accounts']:
        assert len(account['password']) >= 16
        lines.extend([labels[account['role']], '账号：' + account['username'], '密码：' + account['password'],''])
    lines.extend(['当前为合成演示数据，业务日期为 2026-10-15。','实体打印机、PDA 摄像头连续实扫和工厂现场验收尚未执行。','GLM 付费模型调用仍关闭。'])
    target.write_text('\n'.join(lines),encoding='utf-8-sig')
    print('Local account handoff generated with current-user-only NTFS permissions; no credential printed')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--output-dir',type=Path,required=True)
    main(parser.parse_args().output_dir)
