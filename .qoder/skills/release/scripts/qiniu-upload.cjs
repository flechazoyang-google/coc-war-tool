#!/usr/bin/env node
// Upload APK to Qiniu Cloud
// Usage: node qiniu-upload.cjs --file path/to/apk --key filename.apk
// Reads QINIU_* from .env in project root or environment.

const https = require('https');
const http = require('http');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

function parseArgs() {
  const args = process.argv.slice(2);
  const opts = {};
  for (let i = 0; i < args.length; i++) {
    if (args[i] === '--file') opts.file = args[++i];
    else if (args[i] === '--key') opts.key = args[++i];
  }
  if (!opts.file || !opts.key) {
    console.error('Error: --file and --key are required');
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

function uploadToQiniu(config, filePath, key) {
  return new Promise((resolve, reject) => {
    const token = generateUploadToken(config.accessKey, config.secretKey, config.bucket, key);
    const fileContent = fs.readFileSync(filePath);
    const fileName = path.basename(filePath);

    const boundary = '----QiniuUploadBoundary' + Date.now();
    const parts = [];

    parts.push(`--${boundary}\r\n`);
    parts.push(`Content-Disposition: form-data; name="token"\r\n\r\n`);
    parts.push(`${token}\r\n`);

    parts.push(`--${boundary}\r\n`);
    parts.push(`Content-Disposition: form-data; name="key"\r\n\r\n`);
    parts.push(`${key}\r\n`);

    parts.push(`--${boundary}\r\n`);
    parts.push(`Content-Disposition: form-data; name="file"; filename="${fileName}"\r\n`);
    parts.push(`Content-Type: application/vnd.android.package-archive\r\n\r\n`);

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
            const downloadUrl = `${config.domain}/${j.key}`;
            resolve({
              key: j.key,
              hash: j.hash,
              url: downloadUrl
            });
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

  if (!fs.existsSync(opts.file)) {
    console.error(`Error: File not found: ${opts.file}`);
    process.exit(1);
  }

  try {
    console.log(`Uploading ${opts.file} to Qiniu...`);
    const result = await uploadToQiniu(config, opts.file, opts.key);
    console.log(`Upload successful!`);
    console.log(`Key: ${result.key}`);
    console.log(`URL: ${result.url}`);
  } catch (err) {
    console.error(`Failed: ${err.message}`);
    process.exit(1);
  }
}

main();
