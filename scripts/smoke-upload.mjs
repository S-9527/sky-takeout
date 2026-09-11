// 图片上传(本地存储)的端到端冒烟验证。
//
// 依赖:后端已在跑,sky.storage.type=local(默认)。用法:node scripts/smoke-upload.mjs
//
// 会真的往 sky.storage.local-dir(默认 ./data/upload,已在 .gitignore 里)写文件。

const BASE = process.env.SKY_BASE_URL ?? 'http://localhost:8080';

async function call(method, path, { token, body } = {}) {
  const res = await fetch(BASE + path, {
    method,
    headers: {
      ...(body ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  let json = null;
  if (text) {
    try { json = JSON.parse(text); } catch { json = text; }
  }
  return { status: res.status, json, traceId: res.headers.get('x-trace-id') };
}

async function upload(token, { filename, contentType, bytes }) {
  const form = new FormData();
  if (filename !== undefined) {
    form.append('file', new Blob([bytes ?? new Uint8Array([1, 2, 3])], { type: contentType ?? 'image/png' }), filename);
  }
  const res = await fetch(`${BASE}/api/v1/admin/uploads`, {
    method: 'POST',
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body: form,
  });
  const text = await res.text();
  let json = null;
  if (text) {
    try { json = JSON.parse(text); } catch { json = text; }
  }
  return { status: res.status, json };
}

const results = [];
function check(name, ok, detail) {
  results.push({ name, ok });
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? `   [${detail}]` : ''}`);
}

const adminLogin = await call('POST', '/api/v1/admin/auth/login', {
  body: { username: 'admin', password: '123456' },
});
const adminToken = adminLogin.json?.accessToken;
const workerLogin = await call('POST', '/api/v1/admin/auth/login', {
  body: { username: 'zhangsan', password: '123456' },
});
const staffToken = workerLogin.json?.accessToken;
const buyer = await call('POST', '/api/v1/customer/auth/wechat-login', { body: { code: 'smoke-upload-1' } });
const customerToken = buyer.json?.accessToken;
check('拿到 admin / staff / 顾客令牌', !!adminToken && !!staffToken && !!customerToken,
  `admin=${adminLogin.status} staff=${workerLogin.status} buyer=${buyer.status}`);

// 一个最小的合法 PNG(8 字节签名 + 极少数据即可,服务端只按扩展名/内容类型/大小校验)
const PNG = new Uint8Array([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x00, 0x00, 0x00, 0x0d]);

// ---- 1. 上传成功 + 可公开访问 ----
const uploaded = await upload(staffToken, { filename: 'smoke-dish.png', contentType: 'image/png', bytes: PNG });
check('STAFF 上传 png → 201,返回 /files/** 的 URL 与大小/类型',
  uploaded.status === 201 && /^http:\/\/localhost:8080\/files\/\d{8}\/[0-9a-f]{32}\.png$/.test(uploaded.json?.url ?? '')
  && uploaded.json?.size === PNG.length && uploaded.json?.contentType === 'image/png',
  `${uploaded.status} ${JSON.stringify(uploaded.json)}`);

const fetched = await fetch(uploaded.json.url);
const fetchedBytes = new Uint8Array(await fetched.arrayBuffer());
check('上传结果可被匿名 GET(/files/** 放行,内容一致)',
  fetched.status === 200 && fetchedBytes.length === PNG.length && fetchedBytes[0] === 0x89,
  `status=${fetched.status} bytes=${fetchedBytes.length}`);

// ---- 2. 校验 ----
const gif = await upload(adminToken, { filename: 'x.gif', contentType: 'image/gif' });
check('扩展名不在白名单 → 400 UPLOAD_TYPE_NOT_ALLOWED',
  gif.status === 400 && gif.json?.code === 'UPLOAD_TYPE_NOT_ALLOWED',
  `${gif.status} ${gif.json?.code}`);

const fakeType = await upload(adminToken, { filename: 'x.png', contentType: 'application/octet-stream' });
check('内容类型不在白名单 → 400 UPLOAD_TYPE_NOT_ALLOWED(扩展名与类型都要过)',
  fakeType.status === 400 && fakeType.json?.code === 'UPLOAD_TYPE_NOT_ALLOWED',
  `${fakeType.status} ${fakeType.json?.code}`);

const tooLarge = await upload(adminToken, {
  filename: 'big.png', contentType: 'image/png', bytes: new Uint8Array(6 * 1024 * 1024),
});
check('超过 5MB → 400 UPLOAD_FILE_TOO_LARGE',
  tooLarge.status === 400 && tooLarge.json?.code === 'UPLOAD_FILE_TOO_LARGE',
  `${tooLarge.status} ${tooLarge.json?.code}`);

const empty = await upload(adminToken, { filename: 'empty.png', contentType: 'image/png', bytes: new Uint8Array(0) });
check('空文件 → 400 UPLOAD_EMPTY_FILE',
  empty.status === 400 && empty.json?.code === 'UPLOAD_EMPTY_FILE',
  `${empty.status} ${empty.json?.code}`);

const missingField = await upload(adminToken, {});
check('缺 file 字段 → 400 UPLOAD_EMPTY_FILE(而不是参数缺失的统一错误码)',
  missingField.status === 400 && missingField.json?.code === 'UPLOAD_EMPTY_FILE',
  `${missingField.status} ${missingField.json?.code}`);

const illegalFlagJson = await fetch(`${BASE}/api/v1/admin/uploads`, {
  method: 'POST',
  headers: { Authorization: `Bearer ${adminToken}`, 'Content-Type': 'application/json' },
  body: JSON.stringify({ file: 'not-a-file' }),
});
check('非 multipart 请求 → 400(统一错误体,不是 500)',
  illegalFlagJson.status === 400, `${illegalFlagJson.status}`);

// ---- 3. 鉴权 ----
const customerUpload = await upload(customerToken, { filename: 'x.png', contentType: 'image/png' });
check('顾客令牌上传 → 403 AUTH_AUDIENCE_MISMATCH',
  customerUpload.status === 403 && customerUpload.json?.code === 'AUTH_AUDIENCE_MISMATCH',
  `${customerUpload.status} ${customerUpload.json?.code}`);

const anonymousUpload = await upload(null, { filename: 'x.png', contentType: 'image/png' });
check('无令牌上传 → 401 AUTH_TOKEN_INVALID',
  anonymousUpload.status === 401 && anonymousUpload.json?.code === 'AUTH_TOKEN_INVALID',
  `${anonymousUpload.status} ${anonymousUpload.json?.code}`);

const failed = results.filter((r) => !r.ok);
console.log(`\n${results.length - failed.length}/${results.length} passed`);
if (failed.length) {
  console.log('failed:');
  failed.forEach((f) => console.log(`  - ${f.name}`));
}
process.exit(failed.length ? 1 : 0);
