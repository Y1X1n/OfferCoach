/**
 * 支付宝小程序本地预览服务。
 *
 * 用途：在没有支付宝开发者工具的机器上，直接运行 miniprogram/ 里的真实
 * axml / acss / js，并把 my.request 代理到本地 oc-app，便于联调与录制演示。
 *
 * 用法：
 *   node scripts/competition/preview/server.js            # 默认代理 127.0.0.1:8081
 *   OC_API_TARGET=http://127.0.0.1:8081 node ...          # 指定后端
 * 打开 http://127.0.0.1:5180/preview/?page=pages/index/index
 */
const http = require('http');
const fs = require('fs');
const path = require('path');

const PREVIEW_DIR = __dirname;
const REPO_ROOT = path.resolve(__dirname, '..', '..', '..');
const PORT = Number(process.env.OC_PREVIEW_PORT || 5180);
const API_TARGET = process.env.OC_API_TARGET || 'http://127.0.0.1:8081';

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.axml': 'text/plain; charset=utf-8',
  '.acss': 'text/css; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.md': 'text/markdown; charset=utf-8'
};

function sendFile(res, filePath) {
  fs.readFile(filePath, (error, content) => {
    if (error) {
      res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
      res.end('Not found: ' + path.basename(filePath));
      return;
    }
    res.writeHead(200, {
      'Content-Type': MIME[path.extname(filePath).toLowerCase()] || 'application/octet-stream',
      'Cache-Control': 'no-store'
    });
    res.end(content);
  });
}

function proxy(req, res) {
  const target = new URL(API_TARGET);
  // 预览页与后端不同源：去掉 Origin/Referer，避免后端的 CORS 白名单把本地预览拦掉。
  const headers = { ...req.headers, host: target.host };
  delete headers.origin;
  delete headers.referer;
  const options = {
    hostname: target.hostname,
    port: target.port,
    path: req.url,
    method: req.method,
    headers: headers
  };
  const upstream = http.request(options, (upstreamRes) => {
    res.writeHead(upstreamRes.statusCode, upstreamRes.headers);
    upstreamRes.pipe(res);
  });
  upstream.on('error', (error) => {
    res.writeHead(502, { 'Content-Type': 'application/json; charset=utf-8' });
    res.end(JSON.stringify({ code: 'UPSTREAM_UNAVAILABLE', message: '后端不可达：' + error.message }));
  });
  req.pipe(upstream);
}

const server = http.createServer((req, res) => {
  const url = new URL(req.url, 'http://localhost');
  const pathname = decodeURIComponent(url.pathname);

  if (pathname.startsWith('/api/')) {
    proxy(req, res);
    return;
  }

  let filePath = null;
  if (pathname === '/' || pathname === '/preview/') {
    filePath = path.join(PREVIEW_DIR, 'index.html');
  } else if (pathname.startsWith('/preview/')) {
    filePath = path.join(PREVIEW_DIR, pathname.slice('/preview/'.length));
  } else {
    filePath = path.join(REPO_ROOT, pathname);
  }

  const resolved = path.resolve(filePath);
  if (!resolved.startsWith(PREVIEW_DIR) && !resolved.startsWith(REPO_ROOT)) {
    res.writeHead(403, { 'Content-Type': 'text/plain; charset=utf-8' });
    res.end('Forbidden');
    return;
  }
  sendFile(res, resolved);
});

server.listen(PORT, '127.0.0.1', () => {
  console.log(`[preview] http://127.0.0.1:${PORT}/preview/  (api -> ${API_TARGET})`);
});
