import { createHash } from "node:crypto";
import { google } from "googleapis";
import { planForProduct } from "./catalog";
import { AccountState, EntitlementError, grantPlan } from "./store";

const PACKAGE_NAME = "com.aiagents.app";

export const tokenHash = (token: string): string =>
  createHash("sha256").update(token).digest("hex");

export async function verifyAndGrantPurchase(
  uid: string,
  productId: string,
  purchaseToken: string,
  packageName: string,
): Promise<AccountState> {
  if (packageName !== PACKAGE_NAME) throw new EntitlementError(400, "Paquete de compra inválido");
  const expectedPlan = planForProduct(productId);
  if (!expectedPlan) throw new EntitlementError(400, "Producto de suscripción inválido");
  if (!purchaseToken || purchaseToken.length < 20) throw new EntitlementError(400, "Token de compra inválido");

  const auth = new google.auth.GoogleAuth({
    scopes: ["https://www.googleapis.com/auth/androidpublisher"],
  });
  const publisher = google.androidpublisher({ version: "v3", auth });
  const result = await publisher.purchases.subscriptionsv2.get({
    packageName: PACKAGE_NAME,
    token: purchaseToken,
  });
  const state = result.data.subscriptionState;
  const entitledStates = new Set([
    "SUBSCRIPTION_STATE_ACTIVE",
    "SUBSCRIPTION_STATE_IN_GRACE_PERIOD",
    // User cancellation disables renewal but keeps access until expiryTime.
    "SUBSCRIPTION_STATE_CANCELED",
  ]);
  if (!state || !entitledStates.has(state)) {
    throw new EntitlementError(403, "La suscripción no está activa");
  }
  const matchingItem = result.data.lineItems?.find((item) => item.productId === productId);
  const expiry = matchingItem?.expiryTime ? Date.parse(matchingItem.expiryTime) : NaN;
  if (!matchingItem || !Number.isFinite(expiry) || expiry <= Date.now()) {
    throw new EntitlementError(403, "La suscripción ya no está vigente");
  }
  return grantPlan(uid, expectedPlan, expiry, tokenHash(purchaseToken), productId);
}
