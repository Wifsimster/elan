import { sha256 } from 'js-sha256';

export type S3Config = {
  endpoint: string;
  region: string;
  bucket: string;
  accessKeyId: string;
  secretAccessKey: string;
  objectKey: string;
};

const hmacBytes = (key: string | number[], msg: string): number[] => sha256.hmac.array(key, msg);
const hmacHex = (key: string | number[], msg: string): string => sha256.hmac(key, msg);

function amzDates(d: Date): { amzdate: string; datestamp: string } {
  const amzdate = d.toISOString().replace(/[:-]|\.\d{3}/g, '');
  return { amzdate, datestamp: amzdate.slice(0, 8) };
}

function uriEncode(str: string, encodeSlash = true): string {
  let out = '';
  for (const ch of str) {
    if (/[A-Za-z0-9_.~-]/.test(ch)) out += ch;
    else if (ch === '/' && !encodeSlash) out += ch;
    else {
      const utf8 = unescape(encodeURIComponent(ch));
      for (let i = 0; i < utf8.length; i++) {
        out += '%' + utf8.charCodeAt(i).toString(16).toUpperCase().padStart(2, '0');
      }
    }
  }
  return out;
}

function parseEndpoint(endpoint: string): { origin: string; host: string; basePath: string } {
  const ep = endpoint.trim().replace(/\/+$/, '');
  const m = ep.match(/^https:\/\/([^/]+)(\/[^?#]*)?$/i);
  if (!m) throw new Error('Endpoint S3 invalide : HTTPS requis (attendu https://hôte).');
  const host = m[1];
  const basePath = (m[2] ?? '').replace(/\/+$/, '');
  return { origin: `https://${host}`, host, basePath };
}

const REQUEST_TIMEOUT_MS = 30_000;

type Signed = { url: string; headers: Record<string, string> };

function sign(config: S3Config, method: 'PUT' | 'GET', body: string): Signed {
  const { origin, host, basePath } = parseEndpoint(config.endpoint);
  const canonicalUri = `${uriEncode(basePath, false)}/${uriEncode(config.bucket)}/${uriEncode(config.objectKey, false)}`;
  const payloadHash = sha256(body);
  const { amzdate, datestamp } = amzDates(new Date());

  const canonicalHeaders =
    `host:${host}\n` + `x-amz-content-sha256:${payloadHash}\n` + `x-amz-date:${amzdate}\n`;
  const signedHeaders = 'host;x-amz-content-sha256;x-amz-date';

  const canonicalRequest = [
    method,
    canonicalUri,
    '',
    canonicalHeaders,
    signedHeaders,
    payloadHash,
  ].join('\n');

  const scope = `${datestamp}/${config.region}/s3/aws4_request`;
  const stringToSign = [
    'AWS4-HMAC-SHA256',
    amzdate,
    scope,
    sha256(canonicalRequest),
  ].join('\n');

  let key = hmacBytes('AWS4' + config.secretAccessKey, datestamp);
  key = hmacBytes(key, config.region);
  key = hmacBytes(key, 's3');
  key = hmacBytes(key, 'aws4_request');
  const signature = hmacHex(key, stringToSign);

  const authorization =
    `AWS4-HMAC-SHA256 Credential=${config.accessKeyId}/${scope}, ` +
    `SignedHeaders=${signedHeaders}, Signature=${signature}`;

  return {
    url: origin + canonicalUri,
    headers: {
      'x-amz-date': amzdate,
      'x-amz-content-sha256': payloadHash,
      Authorization: authorization,
    },
  };
}

function fetchWithTimeout(url: string, init: RequestInit): Promise<Response> {
  return fetch(url, { ...init, signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS) });
}

function parseS3Error(xml: string): { code: string; message: string } {
  const pick = (tag: string) =>
    xml.match(new RegExp(`<${tag}>([^<]*)</${tag}>`))?.[1]?.trim() ?? '';
  return { code: pick('Code'), message: pick('Message') };
}

export function describeS3Failure(
  method: 'PUT' | 'GET',
  status: number,
  body: string,
  config: Pick<S3Config, 'bucket' | 'accessKeyId'>,
): string {
  const { code, message } = parseS3Error(body);
  switch (code) {
    case 'SignatureDoesNotMatch':
      return 'Signature refusée : la clé secrète ne correspond pas à l’access key. Vérifie-la caractère par caractère (« Afficher »).';
    case 'InvalidAccessKeyId':
      return `Access key « ${config.accessKeyId} » inconnue du serveur.`;
    case 'NoSuchBucket':
      return `Le bucket « ${config.bucket} » n’existe pas sur ce serveur.`;
    case 'AccessDenied':
      return `Accès refusé : cette clé n’a pas le droit ${method === 'PUT' ? 'd’écrire' : 'de lire'} dans le bucket « ${config.bucket} ».`;
    case 'RequestTimeTooSkewed':
      return 'Horloge du téléphone trop décalée par rapport au serveur (signature expirée).';
  }
  if (status === 401 || status === 403) return 'Identifiants refusés par le serveur (HTTP ' + status + ').';
  if (status === 404) return `Le bucket « ${config.bucket} » ou le chemin de l’endpoint est introuvable (HTTP 404).`;
  if (status >= 500) return `Erreur côté serveur (HTTP ${status}) : réessaie plus tard.`;
  const detail = message || code || body.replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim().slice(0, 120);
  return `Le serveur a refusé la requête (HTTP ${status})${detail ? ` : ${detail}` : '.'}`;
}

export function describeNetworkFailure(e: unknown): string {
  const name = e instanceof Error ? e.name : '';
  const msg = e instanceof Error ? e.message : String(e);
  if (name === 'TimeoutError' || name === 'AbortError') {
    return 'Le serveur ne répond pas (délai dépassé). Vérifie l’endpoint et que le serveur est joignable depuis ce réseau.';
  }
  if (/certificate|ssl|tls|trust anchor/i.test(msg)) {
    return 'Certificat HTTPS refusé par le téléphone. Le serveur doit présenter un certificat valide (Let’s Encrypt par ex.).';
  }
  if (/network request failed|unable to resolve|failed to connect|econnrefused|enotfound/i.test(msg)) {
    return 'Serveur injoignable : vérifie l’endpoint (nom de domaine, port) et la connexion réseau.';
  }
  return msg || 'Échec réseau.';
}

async function request(
  config: S3Config,
  method: 'PUT' | 'GET',
  body: string,
  extraHeaders: Record<string, string> = {},
): Promise<Response> {
  const { url, headers } = sign(config, method, body);
  try {
    return await fetchWithTimeout(url, {
      method,
      headers: { ...headers, ...extraHeaders },
      body: method === 'PUT' ? body : undefined,
    });
  } catch (e) {
    throw new Error(describeNetworkFailure(e));
  }
}

export async function putObject(config: S3Config, body: string): Promise<void> {
  const res = await request(config, 'PUT', body, { 'Content-Type': 'application/json' });
  if (!res.ok) {
    const detail = await res.text().catch(() => '');
    throw new Error(describeS3Failure('PUT', res.status, detail, config));
  }
}

export async function getObject(config: S3Config): Promise<string | null> {
  const res = await request(config, 'GET', '');
  if (res.status === 404) {
    const detail = await res.text().catch(() => '');
    if (parseS3Error(detail).code === 'NoSuchBucket') {
      throw new Error(describeS3Failure('GET', 404, detail, config));
    }
    return null;
  }
  if (!res.ok) {
    const detail = await res.text().catch(() => '');
    throw new Error(describeS3Failure('GET', res.status, detail, config));
  }
  return res.text();
}
