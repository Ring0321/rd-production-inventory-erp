import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';

const frontendRoot = path.resolve(process.env.FRONTEND_ROOT || '');
const apiTarget = new URL(process.env.API_TARGET || 'http://127.0.0.1:48080');
const webPort = Number(process.env.WEB_PORT || 9000);
const tenantId = process.env.TENANT_ID || '1';

if (!process.env.FRONTEND_ROOT || !fs.existsSync(frontendRoot)) {
  throw new Error('FRONTEND_ROOT must point to an existing production build');
}

const mimeTypes = new Map([
  ['.css', 'text/css; charset=utf-8'],
  ['.gif', 'image/gif'],
  ['.html', 'text/html; charset=utf-8'],
  ['.ico', 'image/x-icon'],
  ['.jpeg', 'image/jpeg'],
  ['.jpg', 'image/jpeg'],
  ['.js', 'text/javascript; charset=utf-8'],
  ['.json', 'application/json; charset=utf-8'],
  ['.png', 'image/png'],
  ['.svg', 'image/svg+xml'],
  ['.woff', 'font/woff'],
  ['.woff2', 'font/woff2'],
]);

function sendFile(req, res, filePath) {
  fs.stat(filePath, (error, stats) => {
    if (error || !stats.isFile()) {
      res.writeHead(404);
      res.end('Not found');
      return;
    }

    res.writeHead(200, {
      'Content-Type': mimeTypes.get(path.extname(filePath).toLowerCase()) || 'application/octet-stream',
      'Cache-Control': filePath.endsWith('index.html') ? 'no-store' : 'public, max-age=3600',
    });
    if (req.method === 'HEAD') {
      res.end();
      return;
    }
    fs.createReadStream(filePath).pipe(res);
  });
}

function proxyApi(req, res) {
  const target = new URL(req.url || '/', apiTarget);
  const headers = { ...req.headers, host: target.host };
  if (!headers['tenant-id']) {
    headers['tenant-id'] = tenantId;
  }

  const proxyRequest = http.request(
    {
      hostname: target.hostname,
      port: target.port || 80,
      path: target.pathname + target.search,
      method: req.method,
      headers,
    },
    (proxyResponse) => {
      res.writeHead(proxyResponse.statusCode || 502, proxyResponse.headers);
      proxyResponse.pipe(res);
    },
  );
  proxyRequest.on('error', (error) => {
    res.writeHead(502, { 'Content-Type': 'application/json; charset=utf-8' });
    res.end(JSON.stringify({ code: 502, msg: `Backend unavailable: ${error.message}` }));
  });
  req.pipe(proxyRequest);
}

const server = http.createServer((req, res) => {
  const requestUrl = new URL(req.url || '/', 'http://127.0.0.1');
  if (requestUrl.pathname.startsWith('/admin-api/')) {
    proxyApi(req, res);
    return;
  }

  const relativePath = decodeURIComponent(requestUrl.pathname).replace(/^[/\\]+/, '');
  const requestedFile = path.resolve(frontendRoot, relativePath);
  const rootPrefix = `${frontendRoot}${path.sep}`;
  if (requestedFile !== frontendRoot && !requestedFile.startsWith(rootPrefix)) {
    res.writeHead(403);
    res.end('Forbidden');
    return;
  }

  fs.stat(requestedFile, (error, stats) => {
    if (!error && stats.isFile()) {
      sendFile(req, res, requestedFile);
      return;
    }
    sendFile(req, res, path.join(frontendRoot, 'index.html'));
  });
});

server.listen(webPort, '127.0.0.1', () => {
  console.log(`RD ERP frontend listening on http://127.0.0.1:${webPort}`);
});

for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => server.close(() => process.exit(0)));
}
