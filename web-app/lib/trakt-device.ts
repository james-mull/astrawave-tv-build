import { createCipheriv, createHash, createSign, randomBytes } from 'node:crypto';

export type ServiceAccount = {
  client_email: string;
  private_key: string;
  token_uri?: string;
  project_id?: string;
};

export function serviceAccount(): ServiceAccount | null {
  const raw = process.env.ASTRAWAVE_FIREBASE_SERVICE_ACCOUNT_JSON?.trim()
    || process.env.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON?.trim();
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as ServiceAccount;
    if (!parsed.client_email || !parsed.private_key) return null;
    parsed.private_key = parsed.private_key.replace(/\\n/g, '\n');
    return parsed;
  } catch {
    return null;
  }
}

function base64Url(input: string | Buffer): string {
  return Buffer.from(input).toString('base64url');
}

export async function serviceAccessToken(account: ServiceAccount): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  const tokenUri = account.token_uri || 'https://oauth2.googleapis.com/token';
  const header = base64Url(JSON.stringify({ alg: 'RS256', typ: 'JWT' }));
  const payload = base64Url(JSON.stringify({
    iss: account.client_email,
    scope: 'https://www.googleapis.com/auth/datastore',
    aud: tokenUri,
    iat: now,
    exp: now + 3600,
  }));
  const unsigned = `${header}.${payload}`;
  const signer = createSign('RSA-SHA256');
  signer.update(unsigned);
  signer.end();
  const assertion = `${unsigned}.${signer.sign(account.private_key).toString('base64url')}`;
  const response = await fetch(tokenUri, {
    method: 'POST',
    headers: { 'content-type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer', assertion }),
    cache: 'no-store',
  });
  const data = await response.json() as { access_token?: string; error_description?: string; error?: string };
  if (!response.ok || !data.access_token) throw new Error(data.error_description || data.error || `Google OAuth ${response.status}`);
  return data.access_token;
}

export async function verifyFirebaseUser(idToken: string): Promise<string> {
  const apiKey = process.env.ASTRAWAVE_FIREBASE_API_KEY?.trim() || process.env.NEXT_PUBLIC_FIREBASE_API_KEY?.trim();
  if (!apiKey) throw new Error('Firebase verification is not configured');
  const response = await fetch(`https://identitytoolkit.googleapis.com/v1/accounts:lookup?key=${encodeURIComponent(apiKey)}`, {
    method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ idToken }), cache: 'no-store',
  });
  const data = await response.json() as { users?: Array<{ localId?: string }>; error?: { message?: string } };
  const uid = data.users?.[0]?.localId;
  if (!response.ok || !uid) throw new Error(data.error?.message || 'Invalid AstraWave sign-in token');
  return uid;
}

export function encryptSecret(value: string): { cipher: string; iv: string; tag: string } {
  const secret = process.env.ASTRAWAVE_INTEGRATION_TOKEN_KEY?.trim();
  if (!secret) throw new Error('Integration token encryption is not configured');
  const key = createHash('sha256').update(secret).digest();
  const iv = randomBytes(12);
  const cipher = createCipheriv('aes-256-gcm', key, iv);
  const encrypted = Buffer.concat([cipher.update(value, 'utf8'), cipher.final()]);
  return { cipher: encrypted.toString('base64'), iv: iv.toString('base64'), tag: cipher.getAuthTag().toString('base64') };
}

function stringValue(value: string) { return { stringValue: value }; }
function integerValue(value: number) { return { integerValue: String(Math.trunc(value)) }; }
function boolValue(value: boolean) { return { booleanValue: value }; }

export async function saveTraktTokens(uid: string, token: {
  access_token: string;
  refresh_token?: string;
  token_type?: string;
  scope?: string;
  expires_in?: number;
  created_at?: number;
}) {
  const account = serviceAccount();
  if (!account) throw new Error('Firebase server credentials are not configured');
  const projectId = account.project_id?.trim() || process.env.ASTRAWAVE_FIREBASE_PROJECT_ID?.trim() || process.env.NEXT_PUBLIC_FIREBASE_PROJECT_ID?.trim();
  if (!projectId) throw new Error('Firebase project ID is not configured');
  const access = encryptSecret(token.access_token);
  const refresh = token.refresh_token ? encryptSecret(token.refresh_token) : null;
  const accessToken = await serviceAccessToken(account);
  const fields: Record<string, unknown> = {
    provider: stringValue('TRAKT'),
    connected: boolValue(true),
    accessCipher: stringValue(access.cipher),
    accessIv: stringValue(access.iv),
    accessTag: stringValue(access.tag),
    tokenType: stringValue(token.token_type || 'bearer'),
    scope: stringValue(token.scope || 'public'),
    updatedAtEpochMs: integerValue(Date.now()),
  };
  if (refresh) {
    fields.refreshCipher = stringValue(refresh.cipher);
    fields.refreshIv = stringValue(refresh.iv);
    fields.refreshTag = stringValue(refresh.tag);
  }
  if (token.expires_in) fields.expiresInSeconds = integerValue(token.expires_in);
  if (token.created_at) fields.createdAtEpochSeconds = integerValue(token.created_at);
  const url = `https://firestore.googleapis.com/v1/projects/${encodeURIComponent(projectId)}/databases/(default)/documents/users/${encodeURIComponent(uid)}/privateIntegrations/trakt`;
  const response = await fetch(url, {
    method: 'PATCH',
    headers: { authorization: `Bearer ${accessToken}`, 'content-type': 'application/json' },
    body: JSON.stringify({ fields }),
    cache: 'no-store',
  });
  if (!response.ok) throw new Error(`Trakt token storage failed (${response.status}): ${(await response.text()).slice(0, 240)}`);
}
