// Import de la configuration S3 par QR code : on ne tape pas une clé secrète
// de 40 caractères sur un clavier de téléphone. Le QR est généré côté serveur
// (cf. docs/SAUVEGARDE.md) et lu avec la caméra ; il ne transite par aucun
// service — le décodage est local.
import type { BackupConfig } from '@/lib/backup';

/** Champs de config qu'un QR peut renseigner (jamais `enabled`). */
export type BackupQrPatch = Partial<
  Pick<BackupConfig, 'endpoint' | 'bucket' | 'accessKeyId' | 'secretAccessKey' | 'region' | 'objectKey'>
>;

/** Alias tolérés dans le JSON (conventions AWS / MinIO / snake_case). */
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

/**
 * Décode le contenu d'un QR code en fragment de configuration. Deux formats :
 *
 * - JSON : `{"endpoint":"https://s3.x.tld","bucket":"elan","accessKeyId":"…","secretAccessKey":"…"}`
 *   (clés insensibles à la casse, alias snake_case/AWS acceptés, champs partiels OK) ;
 * - URL : `s3://ACCESS:SECRET@s3.x.tld/bucket[/objet]` (hôte en HTTPS implicite).
 *
 * Renvoie `null` si rien d'exploitable (QR étranger). Les valeurs sont nettoyées
 * des espaces ; les champs vides sont ignorés pour ne pas écraser une saisie.
 */
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
  // s3://ACCESS:SECRET@host[:port]/bucket[/objet] — identifiants encodés en URL
  // (un secret peut contenir « / » ou « + »).
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

/** Libellés français des champs remplis, pour le message de confirmation. */
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
