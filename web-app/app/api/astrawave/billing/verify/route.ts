import { createHash, createSign } from 'node:crypto';
import { NextRequest, NextResponse } from 'next/server';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

type ServiceAccount = {
  client_email: string;
  private_key: string;
  token_uri?: string;
  project_id?: string;
};

type SubscriptionLineItem = {
  productId?: string;
  expiryTime?: string;
  autoRenewingPlan?: { autoRenewEnabled?: boolean };
};

type SubscriptionPurchaseV2 = {
  subscriptionState?: string;
  acknowledgementState?: string;
  lineItems?: SubscriptionLineItem[];
};

const PACKAGE_NAME = 'com.astrawave.app';
const ALLOWED_STATES = new Set([
  'SUBSCRIPTION_STATE_ACTIVE',
  'SUBSCRIPTION_STATE_IN_GRACE_PERIOD',
  // A canceled subscription remains entitled until its paid-through expiry time.
  'SUBSCRIPTION_STATE_CANCELED',
]);

function jsonError(status: number, error: string) {
  return NextResponse.json({ verified: false, error }, { status });
}

function base64Url(input: string | Buffer): string {
  return Buffer.from(input).toString('base64url');
}

function serviceAccount(): ServiceAccount | null {
  const raw = process.env.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON?.trim();
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

async function serviceAccessToken(account: ServiceAccount): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  const tokenUri = account.token_uri || 'https://oauth2.googleapis.com/token';
  const header = base64Url(JSON.stringify({ alg: 'RS256', typ: 'JWT' }));
  const payload = base64Url(JSON.stringify({
    iss: account.client_email,
    scope: [
      'https://www.googleapis.com/auth/androidpublisher',
      'https://www.googleapis.com/auth/datastore',
    ].join(' '),
    aud: tokenUri,
    iat: now,
    exp: now + 3600,
  }));
  const unsigned = `${header}.${payload}`;
  const signer = createSign('RSA-SHA256');
  signer.update(unsigned);
  signer.end();
  const signature = signer.sign(account.private_key).toString('base64url');
  const assertion = `${unsigned}.${signature}`;

  const response = await fetch(tokenUri, {
    method: 'POST',
    headers: { 'content-type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
      assertion,
    }),
    cache: 'no-store',
  });
  const data = await response.json() as { access_token?: string; error_description?: string; error?: string };
  if (!response.ok || !data.access_token) {
    throw new Error(data.error_description || data.error || `Google OAuth ${response.status}`);
  }
  return data.access_token;
}

async function verifyFirebaseUser(idToken: string): Promise<string> {
  const apiKey = process.env.ASTRAWAVE_FIREBASE_API_KEY?.trim()
    || process.env.NEXT_PUBLIC_FIREBASE_API_KEY?.trim();
  if (!apiKey) throw new Error('Firebase verification is not configured');

  const response = await fetch(`https://identitytoolkit.googleapis.com/v1/accounts:lookup?key=${encodeURIComponent(apiKey)}`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ idToken }),
    cache: 'no-store',
  });
  const data = await response.json() as { users?: Array<{ localId?: string }>; error?: { message?: string } };
  const uid = data.users?.[0]?.localId;
  if (!response.ok || !uid) throw new Error(data.error?.message || 'Invalid AstraWave sign-in token');
  return uid;
}

async function googleSubscription(accessToken: string, packageName: string, purchaseToken: string): Promise<SubscriptionPurchaseV2> {
  const url = `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${encodeURIComponent(packageName)}/purchases/subscriptionsv2/tokens/${encodeURIComponent(purchaseToken)}`;
  const response = await fetch(url, {
    headers: { authorization: `Bearer ${accessToken}` },
    cache: 'no-store',
  });
  const data = await response.json() as SubscriptionPurchaseV2 & { error?: { message?: string } };
  if (!response.ok) throw new Error(data.error?.message || `Google Play verification ${response.status}`);
  return data;
}

function firestoreValue(value: string | number | boolean | string[]) {
  if (typeof value === 'string') return { stringValue: value };
  if (typeof value === 'number') return { integerValue: String(Math.trunc(value)) };
  if (typeof value === 'boolean') return { booleanValue: value };
  return { arrayValue: { values: value.map(item => ({ stringValue: item })) } };
}

async function writeEntitlement(
  accessToken: string,
  projectId: string,
  uid: string,
  state: string,
  productId: string,
  purchaseToken: string,
  renewsAtEpochMs: number | null,
) {
  const url = `https://firestore.googleapis.com/v1/projects/${encodeURIComponent(projectId)}/databases/(default)/documents/entitlements/${encodeURIComponent(uid)}`;
  const fields: Record<string, ReturnType<typeof firestoreValue>> = {
    plan: firestoreValue('PREMIUM'),
    billingProvider: firestoreValue('GOOGLE_PLAY'),
    purchaseProductId: firestoreValue(productId),
    purchaseTokenHash: firestoreValue(createHash('sha256').update(purchaseToken).digest('hex')),
    subscriptionState: firestoreValue(state),
    updatedAtEpochMs: firestoreValue(Date.now()),
  };
  if (renewsAtEpochMs != null) fields.renewsAtEpochMs = firestoreValue(renewsAtEpochMs);

  const response = await fetch(url, {
    method: 'PATCH',
    headers: {
      authorization: `Bearer ${accessToken}`,
      'content-type': 'application/json',
    },
    body: JSON.stringify({ fields }),
    cache: 'no-store',
  });
  if (!response.ok) {
    const body = await response.text();
    throw new Error(`Entitlement write failed (${response.status}): ${body.slice(0, 300)}`);
  }
}

export async function POST(request: NextRequest) {
  const expectedProductId = process.env.ASTRAWAVE_PREMIUM_PRODUCT_ID?.trim() || 'astrawave_premium_monthly';
  const account = serviceAccount();
  if (!account) return jsonError(503, 'Google Play server verification is not configured');

  const authHeader = request.headers.get('authorization') || '';
  const idToken = authHeader.startsWith('Bearer ') ? authHeader.slice(7).trim() : '';
  if (!idToken) return jsonError(401, 'AstraWave sign-in is required');

  let body: { packageName?: string; productId?: string; purchaseToken?: string };
  try {
    body = await request.json();
  } catch {
    return jsonError(400, 'Invalid purchase verification payload');
  }

  const packageName = body.packageName?.trim() || '';
  const productId = body.productId?.trim() || '';
  const purchaseToken = body.purchaseToken?.trim() || '';
  if (packageName !== PACKAGE_NAME) return jsonError(400, 'Package mismatch');
  if (productId !== expectedProductId) return jsonError(400, 'Premium product mismatch');
  if (purchaseToken.length < 16 || purchaseToken.length > 4096) return jsonError(400, 'Invalid purchase token');

  try {
    const uid = await verifyFirebaseUser(idToken);
    const accessToken = await serviceAccessToken(account);
    const subscription = await googleSubscription(accessToken, packageName, purchaseToken);
    const state = subscription.subscriptionState || 'SUBSCRIPTION_STATE_UNSPECIFIED';
    if (!ALLOWED_STATES.has(state)) {
      return jsonError(403, `Subscription is not entitled (${state})`);
    }

    const matching = (subscription.lineItems || []).filter(item => item.productId === expectedProductId);
    if (!matching.length) return jsonError(403, 'Verified subscription does not contain AstraWave Premium');

    const expiries = matching
      .map(item => item.expiryTime ? Date.parse(item.expiryTime) : NaN)
      .filter(Number.isFinite) as number[];
    const latestExpiry = expiries.length ? Math.max(...expiries) : null;
    // Active/grace access can be extended by Play; canceled access must still be paid-through.
    if (state === 'SUBSCRIPTION_STATE_CANCELED' && (latestExpiry == null || latestExpiry <= Date.now())) {
      return jsonError(403, 'Canceled subscription has expired');
    }
    if (state === 'SUBSCRIPTION_STATE_ACTIVE' && latestExpiry != null && latestExpiry <= Date.now()) {
      return jsonError(403, 'Subscription expiry is not current');
    }

    const projectId = account.project_id?.trim()
      || process.env.ASTRAWAVE_FIREBASE_PROJECT_ID?.trim()
      || process.env.NEXT_PUBLIC_FIREBASE_PROJECT_ID?.trim();
    if (!projectId) throw new Error('Firebase project ID is not configured');

    await writeEntitlement(accessToken, projectId, uid, state, expectedProductId, purchaseToken, latestExpiry);
    return NextResponse.json({
      verified: true,
      plan: 'PREMIUM',
      subscriptionState: state,
      renewsAtEpochMs: latestExpiry,
      acknowledgementState: subscription.acknowledgementState || null,
    });
  } catch (error) {
    const message = error instanceof Error ? error.message : 'Purchase verification failed';
    console.error('[AstraWave billing verify]', message);
    return jsonError(502, message);
  }
}
