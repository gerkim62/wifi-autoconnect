#!/usr/bin/env node

/**
 * Captive Portal Mock & Inspection Server
 * Zero dependencies — runs on pure Node.js (v18+)
 */

const http = require('http');
const fs = require('fs');
const path = require('path');
const os = require('os');
const querystring = require('querystring');

const PORT = process.env.PORT || 8080;
const HOST = process.env.HOST || '0.0.0.0';
const HTML_FILE = path.join(__dirname, 'index.html');

// State
let isAuthenticated = false;
let requestHistory = [];
const MAX_HISTORY = 50;

// ANSI Colors for Terminal
const colors = {
    reset: '\x1b[0m',
    bold: '\x1b[1m',
    dim: '\x1b[2m',
    red: '\x1b[31m',
    green: '\x1b[32m',
    yellow: '\x1b[33m',
    blue: '\x1b[34m',
    magenta: '\x1b[35m',
    cyan: '\x1b[36m',
    white: '\x1b[37m',
    bgBlue: '\x1b[44m',
    bgGreen: '\x1b[42m',
    bgYellow: '\x1b[43m',
    bgMagenta: '\x1b[45m',
    bgCyan: '\x1b[46m',
};

function getLocalIpAddresses() {
    const interfaces = os.networkInterfaces();
    const addresses = [];
    for (const name of Object.keys(interfaces)) {
        for (const iface of interfaces[name]) {
            if (iface.family === 'IPv4' && !iface.internal) {
                addresses.push({ name, address: iface.address });
            }
        }
    }
    return addresses;
}

function formatTime(date = new Date()) {
    return date.toTimeString().split(' ')[0] + '.' + String(date.getMilliseconds()).padStart(3, '0');
}

function logBox(title, lines, color = colors.cyan) {
    const maxLen = Math.max(title.length + 4, ...lines.map(l => l.replace(/\x1b\[[0-9;]*m/g, '').length), 55);
    const border = '═'.repeat(maxLen);
    console.log(`\n${color}╔═ ${colors.bold}${title} ${color}${border.slice(title.length + 3)}╗${colors.reset}`);
    for (const line of lines) {
        const plainLen = line.replace(/\x1b\[[0-9;]*m/g, '').length;
        const padding = ' '.repeat(Math.max(0, maxLen - plainLen - 2));
        console.log(`${color}║${colors.reset} ${line}${padding} ${color}║${colors.reset}`);
    }
    console.log(`${color}╚${border}╝${colors.reset}\n`);
}

function parseBody(req) {
    return new Promise((resolve) => {
        let body = '';
        req.on('data', chunk => {
            body += chunk.toString();
            // Safeguard against huge payloads
            if (body.length > 1e6) req.destroy();
        });
        req.on('end', () => {
            const contentType = req.headers['content-type'] || '';
            let parsed = null;

            if (contentType.includes('application/x-www-form-urlencoded')) {
                parsed = querystring.parse(body);
            } else if (contentType.includes('application/json')) {
                try {
                    parsed = JSON.parse(body);
                } catch {
                    parsed = { _raw: body };
                }
            } else {
                parsed = querystring.parse(body);
            }
            resolve({ raw: body, parsed, contentType });
        });
    });
}

function getSuccessHtml(username, redirectUrl) {
    return `<!DOCTYPE html>
<html>
<head>
    <title>Authentication Successful</title>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <style>
        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #f0fdf4; color: #166534; display: flex; align-items: center; justify-content: center; height: 100vh; margin: 0; }
        .card { background: white; padding: 2.5rem; border-radius: 16px; box-shadow: 0 10px 25px rgba(0,0,0,0.08); text-align: center; max-width: 420px; width: 90%; }
        .icon { width: 64px; height: 64px; background: #dcfce7; color: #16a34a; border-radius: 50%; display: flex; align-items: center; justify-content: center; margin: 0 auto 1.5rem; font-size: 32px; }
        h1 { margin: 0 0 0.5rem; font-size: 1.5rem; color: #15803d; }
        p { margin: 0.5rem 0; color: #4b5563; font-size: 0.95rem; }
        .badge { display: inline-block; background: #f3f4f6; padding: 4px 12px; border-radius: 6px; font-family: monospace; font-weight: bold; color: #1f2937; margin: 8px 0; }
    </style>
</head>
<body>
    <div class="card">
        <div class="icon">✓</div>
        <h1>Logged In Successfully</h1>
        <p>User <span class="badge">${username || 'Guest'}</span> is now authenticated.</p>
        ${redirectUrl ? `<p style="font-size:0.85rem;color:#6b7280;">Redirect: ${redirectUrl}</p>` : ''}
        <p style="margin-top:1.5rem;font-size:0.85rem;color:#9ca3af;">Captive Portal Mock Server</p>
    </div>
</body>
</html>`;
}

function getInspectDashboardHtml() {
    return `<!DOCTYPE html>
<html>
<head>
    <title>Captive Portal Inspector</title>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta http-equiv="refresh" content="3">
    <style>
        :root { --primary: #057b7b; --bg: #0f172a; --card: #1e293b; --text: #f8fafc; --muted: #94a3b8; }
        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: var(--bg); color: var(--text); margin: 0; padding: 20px; }
        .container { max-width: 900px; margin: 0 auto; }
        .header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; flex-wrap: wrap; gap: 12px; }
        h1 { margin: 0; font-size: 1.5rem; color: #38bdf8; }
        .badge { padding: 6px 12px; border-radius: 20px; font-weight: bold; font-size: 0.85rem; }
        .badge.auth { background: #166534; color: #86efac; }
        .badge.unauth { background: #991b1b; color: #fca5a5; }
        .btn { background: #0284c7; color: white; border: none; padding: 8px 16px; border-radius: 8px; font-weight: 600; cursor: pointer; text-decoration: none; display: inline-block; }
        .btn:hover { background: #0369a1; }
        .btn.danger { background: #dc2626; }
        .btn.danger:hover { background: #b91c1c; }
        .card { background: var(--card); border-radius: 12px; padding: 18px; margin-bottom: 16px; border: 1px solid #334155; }
        .card-header { display: flex; justify-content: space-between; border-bottom: 1px solid #334155; padding-bottom: 10px; margin-bottom: 12px; }
        .method { font-weight: bold; padding: 3px 8px; border-radius: 4px; font-size: 0.8rem; }
        .method.POST { background: #ea580c; color: white; }
        .method.GET { background: #0284c7; color: white; }
        .fields { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 10px; }
        .field { background: #0f172a; padding: 10px; border-radius: 6px; }
        .field-label { font-size: 0.75rem; color: var(--muted); text-transform: uppercase; margin-bottom: 4px; }
        .field-value { font-family: monospace; font-weight: 600; color: #38bdf8; word-break: break-all; }
        pre { background: #0f172a; padding: 12px; border-radius: 6px; overflow-x: auto; font-size: 0.85rem; color: #a5f3fc; }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <div>
                <h1>📡 Captive Portal Live Inspector</h1>
                <p style="color: var(--muted); margin: 4px 0 0;">Auto-refreshes every 3 seconds</p>
            </div>
            <div style="display:flex;gap:10px;align-items:center;">
                <span class="badge ${isAuthenticated ? 'auth' : 'unauth'}">${isAuthenticated ? '✓ Authenticated (204 OK)' : '🔒 Captive Portal Active'}</span>
                <a href="/reset" class="btn danger">Reset State</a>
                <a href="/login.html" class="btn" target="_blank">Open Portal ↗</a>
            </div>
        </div>

        <h2>Received Requests (${requestHistory.length})</h2>
        ${requestHistory.length === 0 ? '<p style="color:var(--muted)">No requests received yet. Submit a login or open the app!</p>' : ''}
        ${requestHistory.map(item => `
            <div class="card">
                <div class="card-header">
                    <div>
                        <span class="method ${item.method}">${item.method}</span>
                        <strong style="margin-left: 8px;">${item.url}</strong>
                    </div>
                    <span style="color: var(--muted); font-size: 0.85rem;">${item.time} from ${item.ip}</span>
                </div>
                ${item.parsedBody ? `
                    <div style="margin-bottom: 10px;">
                        <div style="font-size:0.85rem;font-weight:bold;color:#facc15;margin-bottom:8px;">📥 Form Fields Received:</div>
                        <div class="fields">
                            ${Object.entries(item.parsedBody).map(([k, v]) => `
                                <div class="field">
                                    <div class="field-label">${k}</div>
                                    <div class="field-value">${v}</div>
                                </div>
                            `).join('')}
                        </div>
                    </div>
                ` : ''}
                <details style="margin-top: 8px;">
                    <summary style="cursor: pointer; color: var(--muted); font-size: 0.85rem;">View Headers & Raw Details</summary>
                    <pre>${JSON.stringify({ headers: item.headers, rawBody: item.rawBody }, null, 2)}</pre>
                </details>
            </div>
        `).join('')}
    </div>
</body>
</html>`;
}

const server = http.createServer(async (req, res) => {
    const clientIp = req.socket.remoteAddress || req.headers['x-forwarded-for'] || 'unknown';
    const parsedUrl = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
    const pathname = parsedUrl.pathname;
    const timeStr = formatTime();

    // 1. Reset endpoint
    if (pathname === '/reset' || pathname === '/api/reset') {
        isAuthenticated = false;
        logBox('STATE RESET', [
            `Auth State reset to: ${colors.yellow}UNAUTHENTICATED (Captive Portal Mode)${colors.reset}`,
            `Triggered by: ${clientIp}`
        ], colors.yellow);

        if (req.headers.accept && req.headers.accept.includes('text/html')) {
            res.writeHead(302, { 'Location': '/inspect' });
            return res.end();
        }
        res.writeHead(200, { 'Content-Type': 'application/json' });
        return res.end(JSON.stringify({ status: 'reset', isAuthenticated }));
    }

    // 2. Web Inspect Dashboard
    if (pathname === '/inspect' || pathname === '/logs') {
        res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
        return res.end(getInspectDashboardHtml());
    }

    // 3. Simulated Google generate_204 endpoint
    if (pathname === '/generate_204' || pathname.endsWith('/generate_204')) {
        const logEntry = {
            time: timeStr,
            method: req.method,
            url: req.url,
            ip: clientIp,
            headers: req.headers
        };
        requestHistory.unshift(logEntry);
        if (requestHistory.length > MAX_HISTORY) requestHistory.pop();

        if (isAuthenticated) {
            console.log(`${colors.dim}[${timeStr}]${colors.reset} ${colors.green}GET /generate_204 -> 204 No Content (Internet OK)${colors.reset}`);
            res.writeHead(204, { 'Cache-Control': 'no-cache' });
            return res.end();
        } else {
            console.log(`${colors.dim}[${timeStr}]${colors.reset} ${colors.yellow}GET /generate_204 -> 302 Redirect to Portal (Captive Portal Detected)${colors.reset}`);
            res.writeHead(302, {
                'Location': `http://${req.headers.host || '127.0.0.1:' + PORT}/login.html`,
                'Cache-Control': 'no-cache'
            });
            return res.end();
        }
    }

    // 4. Handle POST (Form Login Submission)
    if (req.method === 'POST') {
        const { raw, parsed, contentType } = await parseBody(req);

        // Record history
        const logEntry = {
            time: timeStr,
            method: req.method,
            url: req.url,
            ip: clientIp,
            headers: req.headers,
            parsedBody: parsed,
            rawBody: raw
        };
        requestHistory.unshift(logEntry);
        if (requestHistory.length > MAX_HISTORY) requestHistory.pop();

        // Mark authenticated
        isAuthenticated = true;

        // Print Rich Box in Terminal
        const bodyLines = [];
        bodyLines.push(`${colors.bold}Endpoint:${colors.reset}    ${req.method} ${pathname}`);
        bodyLines.push(`${colors.bold}Client IP:${colors.reset}   ${clientIp}`);
        bodyLines.push(`${colors.bold}User-Agent:${colors.reset}  ${req.headers['user-agent'] || 'none'}`);
        bodyLines.push(`${colors.bold}Content-Type:${colors.reset} ${contentType}`);
        bodyLines.push('───────────────────────────────────────────────────────');
        bodyLines.push(`${colors.green}${colors.bold}📥 RECEIVED FORM FIELDS / PARAMETERS:${colors.reset}`);

        if (parsed && Object.keys(parsed).length > 0) {
            for (const [key, value] of Object.entries(parsed)) {
                const icon = key === 'username' ? '👤' : key === 'password' ? '🔑' : key === 'au_pxytimetag' ? '🏷️' : '🔹';
                bodyLines.push(`  ${icon} ${colors.bold}${key.padEnd(16)}:${colors.reset} ${colors.cyan}${value}${colors.reset}`);
            }
        } else {
            bodyLines.push(`  ${colors.dim}(No parsed form fields, raw body: ${raw})${colors.reset}`);
        }

        bodyLines.push('───────────────────────────────────────────────────────');
        bodyLines.push(`${colors.bold}Auth Status:${colors.reset}  ${colors.green}✓ AUTHENTICATED (Next 204 check will succeed)${colors.reset}`);

        logBox('CAPTIVE PORTAL LOGIN SUBMISSION RECEIVED', bodyLines, colors.green);

        // Respond with success HTML or redirect
        const username = parsed ? (parsed.username || '') : '';
        const redirectUrl = parsed ? (parsed.redirect_url || '') : '';
        const successHtml = getSuccessHtml(username, redirectUrl);

        res.writeHead(200, {
            'Content-Type': 'text/html; charset=utf-8',
            'Cache-Control': 'no-cache'
        });
        return res.end(successHtml);
    }

    // 5. Handle GET (Serve Portal HTML or static assets)
    if (req.method === 'GET') {
        const isHtmlRequest = pathname === '/' || pathname === '/login.html' || pathname === '/index.html' || pathname.endsWith('.html');

        const logEntry = {
            time: timeStr,
            method: req.method,
            url: req.url,
            ip: clientIp,
            headers: req.headers
        };
        requestHistory.unshift(logEntry);
        if (requestHistory.length > MAX_HISTORY) requestHistory.pop();

        if (isHtmlRequest) {
            fs.readFile(HTML_FILE, 'utf8', (err, htmlContent) => {
                if (err) {
                    res.writeHead(500, { 'Content-Type': 'text/plain' });
                    return res.end(`Error loading captive portal HTML: ${err.message}`);
                }

                // Inject fresh dynamic timestamp into au_pxytimetag
                const freshTimestamp = Math.floor(Date.now() / 1000).toString();
                const updatedHtml = htmlContent.replace(
                    /name="au_pxytimetag"\s+value="[^"]*"/g,
                    `name="au_pxytimetag" value="${freshTimestamp}"`
                );

                console.log(`${colors.dim}[${timeStr}]${colors.reset} ${colors.blue}GET ${pathname}${colors.reset} -> Served Portal HTML (au_pxytimetag: ${freshTimestamp}) to ${clientIp}`);

                res.writeHead(200, {
                    'Content-Type': 'text/html; charset=utf-8',
                    'Cache-Control': 'no-cache'
                });
                res.end(updatedHtml);
            });
            return;
        }

        // Other static requests or 404
        res.writeHead(404, { 'Content-Type': 'text/plain' });
        res.end('Not Found');
    }
});

server.listen(PORT, HOST, () => {
    const ips = getLocalIpAddresses();
    const serverLines = [
        `${colors.bold}Captive Portal Mock Server is RUNNING!${colors.reset}`,
        ``,
        `${colors.bold}Local URL:${colors.reset}         http://localhost:${PORT}/login.html`,
        `${colors.bold}Live Web Inspector:${colors.reset} http://localhost:${PORT}/inspect`,
        `${colors.bold}Reset State:${colors.reset}       http://localhost:${PORT}/reset`,
        ``,
        `${colors.bold}📱 Device & Android URLs:${colors.reset}`,
        `   • Android Emulator:   ${colors.cyan}http://10.0.2.2:${PORT}/login.html${colors.reset}`,
        ...ips.map(ip => `   • Phone (Wi-Fi ${ip.name}): ${colors.cyan}http://${ip.address}:${PORT}/login.html${colors.reset}`),
        ``,
        `${colors.dim}👉 Put one of the URLs above into your app's Advanced Settings -> Portal URL${colors.reset}`,
        `${colors.dim}👉 All submitted form values & headers will be printed here in real-time!${colors.reset}`
    ];

    logBox('CAPTIVE PORTAL SERVER STARTED', serverLines, colors.cyan);
});
