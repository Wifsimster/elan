import { parseCsc, parseHeartRate } from '@/lib/ble';

/** Encode des octets en base64 (sans dépendance Node), comme une valeur de
 *  caractéristique BLE. */
const B64 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
const b64 = (bytes: number[]): string => {
  let out = '';
  for (let i = 0; i < bytes.length; i += 3) {
    const b0 = bytes[i];
    const b1 = bytes[i + 1];
    const b2 = bytes[i + 2];
    out += B64[b0 >> 2];
    out += B64[((b0 & 3) << 4) | ((b1 ?? 0) >> 4)];
    out += b1 === undefined ? '=' : B64[((b1 & 15) << 2) | ((b2 ?? 0) >> 6)];
    out += b2 === undefined ? '=' : B64[b2 & 63];
  }
  return out;
};

describe('parseHeartRate', () => {
  it('décode une FC 8 bits (bit drapeau 0)', () => {
    expect(parseHeartRate(b64([0x00, 72]))).toBe(72);
  });

  it('décode une FC 16 bits (bit drapeau 1, little-endian)', () => {
    // 0x0102 = 258
    expect(parseHeartRate(b64([0x01, 0x02, 0x01]))).toBe(258);
  });

  it('rejette une trame 16 bits tronquée', () => {
    expect(parseHeartRate(b64([0x01, 0x50]))).toBeNull();
  });

  it('traite une FC de 0 (contact perdu) comme absente', () => {
    expect(parseHeartRate(b64([0x00, 0]))).toBeNull();
  });

  it('renvoie null pour une valeur absente ou trop courte', () => {
    expect(parseHeartRate(null)).toBeNull();
    expect(parseHeartRate(b64([0x00]))).toBeNull();
  });
});

describe('parseCsc', () => {
  it('décode les révolutions/temps de roue (bit drapeau 0)', () => {
    // flags=0x01 (roue présente), wheelRevs=1 (uint32 LE), wheelTime=1024 (uint16 LE)
    const raw = parseCsc(b64([0x01, 0x01, 0x00, 0x00, 0x00, 0x00, 0x04]));
    expect(raw).not.toBeNull();
    expect(raw!.wheelRevs).toBe(1);
    expect(raw!.wheelTime).toBe(1024);
    expect(raw!.crankRevs).toBeNull();
  });

  it('renvoie null pour une trame roue tronquée', () => {
    expect(parseCsc(b64([0x01, 0x01, 0x00]))).toBeNull();
  });
});
