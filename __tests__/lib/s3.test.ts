import { describeNetworkFailure, describeS3Failure, putObject } from '@/lib/s3';

// Référence indépendante : `crypto` de Node (pas de @types/node dans le projet,
// d'où le require typé à la main, comme better-sqlite3 dans db.test.ts).
type Hasher = { update: (s: string, enc: 'utf8') => Hasher; digest: (enc?: 'hex') => string & Uint8Array };
const { createHash, createHmac } = require('node:crypto') as {
  createHash: (alg: 'sha256') => Hasher;
  createHmac: (alg: 'sha256', key: string | Uint8Array) => Hasher;
};
const g = globalThis as { fetch: typeof fetch };

/**
 * Vérifie la signature SigV4 produite par le client (js-sha256) contre un
 * calcul indépendant en Node `crypto` : garantit que la dérivation de clé
 * (HMAC chaîné sur octets) et la requête canonique sont conformes au format
 * AWS — un écart ici se traduit par un `SignatureDoesNotMatch` côté serveur.
 */
describe('s3 — signature SigV4', () => {
  const config = {
    endpoint: 'https://s3.example.tld',
    region: 'us-east-1',
    bucket: 'suivi-sport',
    accessKeyId: 'AKIDEXAMPLE',
    secretAccessKey: 'wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY',
    objectKey: 'suivi-sport.json',
  };
  const body = '{"format":1}';

  let captured: { url: string; init: RequestInit } | null = null;
  const originalFetch = g.fetch;

  beforeAll(() => {
    jest.useFakeTimers({ now: new Date('2026-09-11T10:06:00Z') });
    g.fetch = jest.fn(async (url: string, init: RequestInit) => {
      captured = { url, init };
      return { ok: true, status: 200, statusText: 'OK', text: async () => '' } as Response;
    }) as unknown as typeof fetch;
  });
  afterAll(() => {
    jest.useRealTimers();
    g.fetch = originalFetch;
  });

  it('signe PUT comme la référence Node crypto', async () => {
    await putObject(config, body);
    expect(captured).not.toBeNull();
    const headers = captured!.init.headers as Record<string, string>;
    expect(captured!.url).toBe('https://s3.example.tld/suivi-sport/suivi-sport.json');

    const amzdate = '20260911T100600Z';
    const datestamp = '20260911';
    expect(headers['x-amz-date']).toBe(amzdate);

    const payloadHash = createHash('sha256').update(body, 'utf8').digest('hex');
    expect(headers['x-amz-content-sha256']).toBe(payloadHash);

    const canonicalRequest = [
      'PUT',
      '/suivi-sport/suivi-sport.json',
      '',
      `host:s3.example.tld\nx-amz-content-sha256:${payloadHash}\nx-amz-date:${amzdate}\n`,
      'host;x-amz-content-sha256;x-amz-date',
      payloadHash,
    ].join('\n');
    const scope = `${datestamp}/us-east-1/s3/aws4_request`;
    const stringToSign = [
      'AWS4-HMAC-SHA256',
      amzdate,
      scope,
      createHash('sha256').update(canonicalRequest, 'utf8').digest('hex'),
    ].join('\n');
    const hmac = (key: Uint8Array | string, msg: string) =>
      createHmac('sha256', key).update(msg, 'utf8').digest();
    const kSigning = hmac(hmac(hmac(hmac('AWS4' + config.secretAccessKey, datestamp), 'us-east-1'), 's3'), 'aws4_request');
    const signature = createHmac('sha256', kSigning).update(stringToSign, 'utf8').digest('hex');

    expect(headers.Authorization).toBe(
      `AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/${scope}, SignedHeaders=host;x-amz-content-sha256;x-amz-date, Signature=${signature}`,
    );
  });
});

describe('s3 — messages d’erreur', () => {
  const cfg = { bucket: 'elan', accessKeyId: 'AK' };
  const xml = (code: string, msg = 'x') => `<?xml version="1.0"?><Error><Code>${code}</Code><Message>${msg}</Message></Error>`;

  it('oriente vers la clé secrète sur SignatureDoesNotMatch', () => {
    expect(describeS3Failure('PUT', 403, xml('SignatureDoesNotMatch'), cfg)).toMatch(/clé secrète/);
  });
  it('nomme le bucket manquant et la clé inconnue', () => {
    expect(describeS3Failure('PUT', 404, xml('NoSuchBucket'), cfg)).toMatch(/« elan »/);
    expect(describeS3Failure('GET', 403, xml('InvalidAccessKeyId'), cfg)).toMatch(/« AK »/);
  });
  it('distingue lecture et écriture sur AccessDenied', () => {
    expect(describeS3Failure('PUT', 403, xml('AccessDenied'), cfg)).toMatch(/d’écrire/);
    expect(describeS3Failure('GET', 403, xml('AccessDenied'), cfg)).toMatch(/de lire/);
  });
  it('retombe sur un message générique lisible sans XML brut', () => {
    const m = describeS3Failure('PUT', 400, xml('MalformedXML', 'bad body'), cfg);
    expect(m).toMatch(/HTTP 400/);
    expect(m).toMatch(/bad body/);
    expect(m).not.toMatch(/</);
    expect(describeS3Failure('PUT', 503, '', cfg)).toMatch(/serveur/);
  });
  it('traduit les échecs réseau', () => {
    const timeout = Object.assign(new Error('timeout'), { name: 'TimeoutError' });
    expect(describeNetworkFailure(timeout)).toMatch(/délai/);
    expect(describeNetworkFailure(new TypeError('Network request failed'))).toMatch(/injoignable/);
    expect(describeNetworkFailure(new Error('Trust anchor for certification path not found'))).toMatch(/Certificat/);
  });
});
