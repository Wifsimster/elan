import type { BackupConfig } from '@/lib/backup';

export type BackupQrPatch = Partial<
  Pick<BackupConfig, 'endpoint' | 'bucket' | 'accessKeyId' | 'secretAccessKey' | 'region' | 'objectKey'>
>;

const ALIASES: Record<string, keyof BackupQrPatch> = {
  endpoint: 'endpoint',
  url: 'endpoint',
  endpoint_url: 'endpoint',
  bucket: 'bucket',
  accesskeyid: 'accessKeyId',
  accesskey: 'accessKeyId',
  access_key: 'accessKeyId',
  access_key_id: 'accessKeyId',
  aws_access_key_id: 'accessKeyId',
  secretaccesskey: 'secretAccessKey',
  secretkey: 'secretAccessKey',
  secret_key: 'secretAccessKey',
  secret_access_key: 'secretAccessKey',
  aws_secret_access_key: 'secretAccessKey',
  region: 'region',
  objectkey: 'objectKey',
  object_key: 'objectKey',
  key: 'objectKey',
  object: 'objectKey',
};

export function parseBackupQr(data: string): BackupQrPatch | null {
  const text = data.trim();
  if (!text) return null;
  const patch = text.startsWith('{') ? parseJson(text) : parseUrl(text);
  if (!patch) return null;
  const cleaned: BackupQrPatch = {};
  for (const [k, v] of Object.entries(patch)) {
    if (typeof v === 'string' && v.trim()) cleaned[k as keyof BackupQrPatch] = v.trim();
  }
  return Object.keys(cleaned).length > 0 ? cleaned : null;
}

function parseJson(text: string): BackupQrPatch | null {
  let obj: unknown;
  try {
    obj = JSON.parse(text);
  } catch {
    return null;
  }
  if (!obj || typeof obj !== 'object' || Array.isArray(obj)) return null;
  const patch: BackupQrPatch = {};
  for (const [rawKey, value] of Object.entries(obj as Record<string, unknown>)) {
    const field = ALIASES[rawKey.toLowerCase()];
    if (field && typeof value === 'string') patch[field] = value;
  }
  return patch;
}

function parseUrl(text: string): BackupQrPatch | null {
  const m = text.match(/^s3:\/\/(?:([^:@/]+)(?::([^@/]*))?@)?([^/?#]+)(?:\/([^/?#]+))?(?:\/([^?#]*))?$/i);
  if (!m) return null;
  const [, access, secret, host, bucket, object] = m;
  const dec = (s: string | undefined) => {
    if (s == null) return undefined;
    try {
      return decodeURIComponent(s);
    } catch {
      return s;
    }
  };
  return {
    endpoint: `https://${host}`,
    bucket: dec(bucket),
    accessKeyId: dec(access),
    secretAccessKey: dec(secret),
    objectKey: dec(object),
  };
}

export function describeQrPatch(patch: BackupQrPatch): string {
  const labels: Record<keyof BackupQrPatch, string> = {
    endpoint: 'endpoint',
    bucket: 'bucket',
    accessKeyId: 'access key',
    secretAccessKey: 'secret key',
    region: 'région',
    objectKey: "nom de l'objet",
  };
  return (Object.keys(patch) as (keyof BackupQrPatch)[]).map((k) => labels[k]).join(', ');
}
