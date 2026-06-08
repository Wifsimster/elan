// Client S3 minimal (PUT / GET d'objet) avec signature AWS Signature V4,
// en pur JS — compatible MinIO et tout stockage S3-compatible auto-hébergé.
// Aucune dépendance native : HMAC/SHA-256 via js-sha256.
import { sha256 } from 'js-sha256';

export type S3Config = {
  /** URL de base du service, ex. https://minio.mon-homelab.tld (style « path »). */
  endpoint: string;
  region: string;
  bucket: string;
  accessKeyId: string;
  secretAccessKey: string;
  /** Clé de l'objet, ex. suivi-sport-backup.json. */
  objectKey: string;
};

/** HMAC-SHA256 renvoyant des octets (pour chaîner la dérivation de clé). */
const hmacBytes = (key: string | number[], msg: string): number[] => sha256.hmac.array(key, msg);
/** HMAC-SHA256 renvoyant l'hex (signature finale). */
const hmacHex = (key: string | number[], msg: string): string => sha256.hmac(key, msg);

/** Horodatages AWS : `20260602T143000Z` et `20260602`. */
function amzDates(d: Date): { amzdate: string; datestamp: string } {
  const amzdate = d.toISOString().replace(/[:-]|\.\d{3}/g, '');
  return { amzdate, datestamp: amzdate.slice(0, 8) };
}

/** Encodage URI conforme RFC 3986 attendu par AWS (slashes optionnels). */
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

function parseEndpoint(endpoint: string): { origin: string; host: string } {
  const ep = endpoint.trim().replace(/\/+$/, '');
  // HTTPS obligatoire : une sauvegarde (clé secrète S3 + base entière) ne doit
  // jamais transiter en clair. http:// est refusé volontairement — et de toute
  // façon bloqué par la plateforme en release (cleartext interdit).
  const m = ep.match(/^(https:\/\/([^/]+))/i);
  if (!m) throw new Error('Endpoint S3 invalide : HTTPS requis (attendu https://hôte).');
  return { origin: m[1], host: m[2] };
}

type Signed = { url: string; headers: Record<string, string> };

/** Construit l'URL + en-têtes signés SigV4 pour une requête S3 (style path). */
function sign(config: S3Config, method: 'PUT' | 'GET', body: string): Signed {
  const { origin, host } = parseEndpoint(config.endpoint);
  const canonicalUri = `/${uriEncode(config.bucket)}/${uriEncode(config.objectKey, false)}`;
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

/** Téléverse `body` comme objet dans le bucket. Lève en cas d'échec HTTP. */
export async function putObject(config: S3Config, body: string): Promise<void> {
  const { url, headers } = sign(config, 'PUT', body);
  const res = await fetch(url, {
    method: 'PUT',
    headers: { ...headers, 'Content-Type': 'application/json' },
    body,
  });
  if (!res.ok) {
    const detail = await res.text().catch(() => '');
    throw new Error(`S3 PUT ${res.status} ${res.statusText}${detail ? ` — ${detail.slice(0, 200)}` : ''}`);
  }
}

/** Récupère le contenu de l'objet, ou `null` s'il n'existe pas (404). */
export async function getObject(config: S3Config): Promise<string | null> {
  const { url, headers } = sign(config, 'GET', '');
  const res = await fetch(url, { method: 'GET', headers });
  if (res.status === 404) return null;
  if (!res.ok) {
    const detail = await res.text().catch(() => '');
    throw new Error(`S3 GET ${res.status} ${res.statusText}${detail ? ` — ${detail.slice(0, 200)}` : ''}`);
  }
  return res.text();
}
