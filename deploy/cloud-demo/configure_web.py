"""Add an independent TLS gateway; never reload or edit the original nginx."""
import os
from pathlib import Path
import pwd
import shutil
import socket
import subprocess
import sys

sys.path.insert(0,str(Path(__file__).resolve().parent))
from install_release import BASE, USER, MARKER, write, unit


def configure():
    assert os.geteuid() == 0
    import json
    assert json.loads((BASE / 'DEPLOYMENT.json').read_text())['owner'] == MARKER
    group = pwd.getpwnam(USER).pw_gid
    tls = BASE / 'config/tls'
    tls.mkdir(exist_ok=True,mode=0o750)
    os.chown(tls,0,group)
    for ext in ['crt','key']:
        shutil.copyfile(BASE / 'data/acme/certificates' / ('101.42.182.117.' + ext),tls / ('server.' + ext))
        os.chmod(tls / ('server.' + ext),0o640)
        os.chown(tls / ('server.' + ext),0,group)
    for directory, _, files in os.walk(BASE / 'data/acme'):
        os.chown(directory,0,0)
        os.chmod(directory,0o700)
        for file in files:
            os.chown(Path(directory) / file,0,0)
            os.chmod(Path(directory) / file,0o600)
    runtime = BASE / 'data/web'
    runtime.mkdir(exist_ok=True,mode=0o750)
    os.chown(runtime,pwd.getpwnam(USER).pw_uid,group)
    source = (BASE / 'incoming/nginx.conf').read_text()
    source = source.replace('listen 80;', 'listen 443 ssl;\n    ssl_certificate ' + str(tls / 'server.crt') + ';\n    ssl_certificate_key ' + str(tls / 'server.key') + ';\n    ssl_protocols TLSv1.2 TLSv1.3;\n    ssl_session_cache shared:DUOXUTLS:5m;')
    source = source.replace('server_name _;', 'server_name 101.42.182.117;')
    source = source.replace('/usr/share/nginx/html',str(BASE / 'current/frontend'))
    source = source.replace('backend:48080','127.0.0.1:14880').replace('${RD_PUBLIC_TENANT_ID}','1')
    source = source.replace('    root ', '    add_header Permissions-Policy "camera=(self), microphone=(), geolocation=()" always;\n    root ',1)
    source = source.replace('    location /admin-api/ {', '''    location = /admin-api/system/auth/login {
        limit_req zone=duoxu_login burst=8 nodelay;
        limit_req_status 429;
        proxy_pass http://127.0.0.1:14880;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $remote_addr;
        proxy_set_header X-Forwarded-Proto https;
        proxy_read_timeout 30s;
    }
    location /admin-api/actuator { return 404; }
    location ~ /\\. { return 404; }
    location /admin-api/ {''')
    full = f'''# {MARKER}
pid {runtime}/nginx.pid;
error_log {BASE}/logs/web-error.log warn;
events {{ worker_connections 512; }}
http {{
  include /etc/nginx/mime.types;
  default_type application/octet-stream;
  server_tokens off;
  access_log {BASE}/logs/web-access.log;
  client_body_temp_path {runtime}/client_temp;
  proxy_temp_path {runtime}/proxy_temp;
  fastcgi_temp_path {runtime}/fastcgi_temp;
  uwsgi_temp_path {runtime}/uwsgi_temp;
  scgi_temp_path {runtime}/scgi_temp;
  limit_req_zone $binary_remote_addr zone=duoxu_login:1m rate=10r/m;
  sendfile on;
  gzip on;
  gzip_types text/plain text/css application/javascript application/json;
{source}
}}
'''
    write(BASE / 'config/nginx.conf',full)
    unit('duoxu-erp-web.service',f'''[Unit]
Description=DUOXU ERP independent HTTPS gateway
After=network.target duoxu-erp-backend.service
[Service]
User={USER}
Group={USER}
Slice=duoxu-erp.slice
ExecStartPre=/usr/sbin/nginx -t -p {runtime}/ -c {BASE}/config/nginx.conf
ExecStart=/usr/sbin/nginx -p {runtime}/ -c {BASE}/config/nginx.conf -g "daemon off;"
Restart=on-failure
RestartSec=5
UMask=0027
AmbientCapabilities=CAP_NET_BIND_SERVICE
CapabilityBoundingSet=CAP_NET_BIND_SERVICE
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths={BASE}/data/web {BASE}/logs
MemoryMax=160M
[Install]
WantedBy=multi-user.target
''')
    renewal = BASE / 'ops/renew_certificate.py'
    write(renewal,(BASE / 'incoming/renew_certificate.py').read_text(),0o700)
    unit('duoxu-erp-certificate.service',f'''[Unit]
Description=Renew only DUOXU demo short-lived IP certificate
[Service]
Type=oneshot
ExecStart=/usr/bin/python3 {renewal}
TimeoutStartSec=240
UMask=0077
StandardOutput=append:{BASE}/logs/certificate-renew.log
StandardError=append:{BASE}/logs/certificate-renew.log
''')
    unit('duoxu-erp-certificate.timer','''[Unit]
Description=DUOXU ERP certificate check every eight hours
[Timer]
OnBootSec=10min
OnUnitActiveSec=8h
RandomizedDelaySec=20min
Persistent=true
[Install]
WantedBy=timers.target
''')
    subprocess.run(['/usr/bin/systemctl','daemon-reload'],check=True)
    with socket.socket() as probe:
        occupied = probe.connect_ex(('127.0.0.1',443)) == 0
    own_active = subprocess.run(['/usr/bin/systemctl','is-active','--quiet','duoxu-erp-web.service']).returncode == 0
    assert not occupied or own_active, '443 collision; original owner untouched'
    subprocess.run(['/usr/bin/systemctl','enable','--now','duoxu-erp-web.service','duoxu-erp-certificate.timer'],check=True)
    subprocess.run(['/usr/bin/systemctl','start','duoxu-erp-certificate.service'],check=True)
    print('HTTPS_OK: independent 443 gateway and ERP-only renewal timer configured')


if __name__ == '__main__':
    configure()
