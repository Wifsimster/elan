import { describeQrPatch, parseBackupQr } from '@/lib/backup-qr';

describe('backup-qr — parseBackupQr', () => {
  it('lit un JSON complet aux clés canoniques', () => {
    expect(
      parseBackupQr(
        '{"endpoint":"https://s3.x.tld","bucket":"elan","accessKeyId":"AK","secretAccessKey":"SK","region":"eu-west-1","objectKey":"o.json"}',
      ),
    ).toEqual({
      endpoint: 'https://s3.x.tld',
      bucket: 'elan',
      accessKeyId: 'AK',
      secretAccessKey: 'SK',
      region: 'eu-west-1',
      objectKey: 'o.json',
    });
  });

  it('accepte les alias snake_case / AWS, insensibles à la casse', () => {
    expect(
      parseBackupQr('{"URL":"https://s3.x.tld","access_key":"AK","AWS_SECRET_ACCESS_KEY":"SK"}'),
    ).toEqual({ endpoint: 'https://s3.x.tld', accessKeyId: 'AK', secretAccessKey: 'SK' });
  });

  it('ignore les champs vides, non-texte ou inconnus, et nettoie les espaces', () => {
    expect(parseBackupQr('{"accessKeyId":" AK ","secretAccessKey":"","bucket":3,"foo":"bar"}')).toEqual({
      accessKeyId: 'AK',
    });
  });

  it('lit la forme s3://ACCESS:SECRET@hôte/bucket/objet (identifiants encodés)', () => {
    expect(parseBackupQr('s3://AK:s%2Fk%2Bx@s3.x.tld/elan/backup.json')).toEqual({
      endpoint: 'https://s3.x.tld',
      bucket: 'elan',
      accessKeyId: 'AK',
      secretAccessKey: 's/k+x',
      objectKey: 'backup.json',
    });
    expect(parseBackupQr('s3://s3.x.tld/elan')).toEqual({ endpoint: 'https://s3.x.tld', bucket: 'elan' });
  });

  it('renvoie null pour un QR étranger', () => {
    expect(parseBackupQr('https://example.com')).toBeNull();
    expect(parseBackupQr('{"foo":"bar"}')).toBeNull();
    expect(parseBackupQr('[1,2]')).toBeNull();
    expect(parseBackupQr('   ')).toBeNull();
    expect(parseBackupQr('{not json')).toBeNull();
  });

  it('décrit les champs remplis en français', () => {
    expect(describeQrPatch({ accessKeyId: 'a', secretAccessKey: 'b', endpoint: 'c' })).toBe(
      'access key, secret key, endpoint',
    );
  });
});
