#!/usr/bin/env node
// Generate and upload release.json to Qiniu Cloud
// Usage: node release-json-upload.cjs --version v4.9.0-alpha.1 [--url https://cdn...] [--channel alpha|beta|rc|stable] [--body "changelog"]
// --channel and --url are optional: auto-detected from version.
//   channel: alpha/beta/rc suffix → that stage, otherwise → stable
//   url: {QINIU_DOMAIN}/COCtools-{stage}.apk
// Reads QINIU_* from .env in project root or environment.

const https = require('https');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

function parseArgs() {
  const args = process.argv.slice(2);
  const opts = {};
  for (let i = 0; i < args.length; i++) {
    if (args[i] === '--channel') opts.channel = args[++i];
    else if (args[i] === '--version') opts.version = args[++i];
    else if (args[i] === '--url') opts.url = args[++i];
    else if (args[i] === '--body') opts.body = args[++i];
  }
  if (!opts.version) {
    console.error('Error: --version is required');
    process.exit(1);
  }
  if (!opts.channel) {
    const match = /-(alpha|beta|rc)\.\d+$/i.exec(opts.version);
    opts.channel = match ? match[1].toLowerCase() : 'stable';
  }
  if (!['alpha', 'beta', 'rc', 'stable'].includes(opts.channel)) {
    console.error('Error: --channel must be "alpha", "beta", "rc", or "stable"');
    process.exit(1);
  }
  return opts;
}

function loadConfig() {
  const config = {
    accessKey: process.env.QINIU_ACCESS_KEY,
    secretKey: process.env.QINIU_SECRET_KEY,
    bucket: process.env.QINIU_BUCKET,
    domain: process.env.QINIU_DOMAIN
  };

  if (Object.values(config).every(v => v)) return config;

  const envPath = path.resolve(__dirname, '../../../../.env');
  if (fs.existsSync(envPath)) {
    const content = fs.readFileSync(envPath, 'utf-8');
    const lines = content.split('\n');
    for (let line of lines) {
      line = line.replace(/\r$/, '').trim();
      if (line.startsWith('#') || !line) continue;
      const match = line.match(/^([^=]+)=(.*)$/);
      if (match) {
        const key = match[1].trim();
        const value = match[2].trim();
        if (key === 'QINIU_ACCESS_KEY') config.accessKey = value;
        else if (key === 'QINIU_SECRET_KEY') config.secretKey = value;
        else if (key === 'QINIU_BUCKET') config.bucket = value;
        else if (key === 'QINIU_DOMAIN') config.domain = value;
      }
    }
  }

  if (!config.accessKey || !config.secretKey || !config.bucket || !config.domain) {
    return null;
  }
  return config;
}

function urlSafeBase64(str) {
  return Buffer.from(str)
    .toString('base64')
    .replace(/\+/g, '-')
    .replace(/\//g, '_');
}

function generateUploadToken(accessKey, secretKey, bucket, key) {
  const deadline = Math.floor(Date.now() / 1000) + 3600;
  const putPolicy = JSON.stringify({
    scope: `${bucket}:${key}`,
    deadline: deadline
  });

  const encodedPolicy = urlSafeBase64(putPolicy);
  const signature = crypto
    .createHmac('sha1', secretKey)
    .update(encodedPolicy)
    .digest('base64')
    .replace(/\+/g, '-')
    .replace(/\//g, '_');

  return `${accessKey}:${signature}:${encodedPolicy}`;
}

function fetchExistingReleaseJson(domain) {
  return new Promise((resolve) => {
    const baseUrl = `${domain.replace(/\/$/, '')}/release.json`;
    const url = `${baseUrl}?_t=${Date.now()}`;
    const parsedUrl = new URL(url);
    const options = {
      hostname: parsedUrl.hostname,
      path: parsedUrl.pathname + parsedUrl.search,
      headers: {
        'User-Agent': 'COCWarTool-ReleaseScript',
        'Cache-Control': 'no-cache'
      },
      rejectUnauthorized: false
    };
    https.get(options, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          try {
            resolve(JSON.parse(data));
          } catch (_) {
            resolve(null);
          }
        } else {
          resolve(null);
        }
      });
    }).on('error', () => resolve(null));
  });
}

function uploadToQiniu(config, content, key) {
  return new Promise((resolve, reject) => {
    const token = generateUploadToken(config.accessKey, config.secretKey, config.bucket, key);
    const fileContent = Buffer.from(content, 'utf-8');

    const boundary = '----QiniuUploadBoundary' + Date.now();
    const parts = [];

    parts.push(`--${boundary}\r\n`);
    parts.push(`Content-Disposition: form-data; name="token"\r\n\r\n`);
    parts.push(`${token}\r\n`);

    parts.push(`--${boundary}\r\n`);
    parts.push(`Content-Disposition: form-data; name="key"\r\n\r\n`);
    parts.push(`${key}\r\n`);

    parts.push(`--${boundary}\r\n`);
    parts.push(`Content-Disposition: form-data; name="file"; filename="${key}"\r\n`);
    parts.push(`Content-Type: application/json\r\n\r\n`);

    const header = Buffer.from(parts.join(''));
    const footer = Buffer.from(`\r\n--${boundary}--\r\n`);
    const body = Buffer.concat([header, fileContent, footer]);

    const req = https.request({
      hostname: 'up-as0.qiniup.com',
      path: '/',
      method: 'POST',
      headers: {
        'Content-Type': `multipart/form-data; boundary=${boundary}`,
        'Content-Length': body.length
      }
    }, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        try {
          const j = JSON.parse(data);
          if (res.statusCode >= 200 && res.statusCode < 300 && j.key) {
            resolve({ key: j.key, url: `${config.domain}/${j.key}` });
          } else {
            reject(new Error(`Qiniu API error (${res.statusCode}): ${j.error || data.substring(0, 200)}`));
          }
        } catch (e) {
          reject(new Error(`Parse error: ${data.substring(0, 300)}`));
        }
      });
    });

    req.on('error', reject);
    req.write(body);
    req.end();
  });
}

async function main() {
  const opts = parseArgs();
  const config = loadConfig();

  if (!config) {
    console.error('Error: Qiniu config not found. Set QINIU_* in .env or environment.');
    process.exit(1);
  }

  try {
    if (!opts.url) {
      const domain = config.domain.replace(/\/$/, '');
      opts.url = `${domain}/COCtools-${opts.channel}.apk`;
    }

    console.log('Fetching existing release.json...');
    const existing = await fetchExistingReleaseJson(config.domain);
    const releaseJson = existing || {};

    releaseJson[opts.channel] = {
      version: opts.version,
      url: opts.url,
      body: opts.body || ''
    };

    const content = JSON.stringify(releaseJson, null, 2);
    console.log(`Uploading release.json (${opts.channel} = ${opts.version})...`);
    const result = await uploadToQiniu(config, content, 'release.json');
    console.log('Upload successful!');
    console.log(`URL: ${result.url}`);
    console.log(`Content:\n${content}`);
  } catch (err) {
    console.error(`Failed: ${err.message}`);
    process.exit(1);
  }
}

main();
