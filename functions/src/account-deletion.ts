import { getFirestore } from "firebase-admin/firestore";
import { userHash } from "./privacy";

async function deleteQuery(
  query: FirebaseFirestore.Query<FirebaseFirestore.DocumentData>,
): Promise<void> {
  const db = getFirestore();
  while (true) {
    const snapshot = await query.limit(400).get();
    if (snapshot.empty) return;
    const batch = db.batch();
    snapshot.docs.forEach((document) => batch.delete(document.ref));
    await batch.commit();
  }
}

/** Deletes user-linked backend records. Anonymous daily aggregates intentionally remain. */
export async function deleteAccountData(uid: string): Promise<void> {
  const db = getFirestore();
  const hashedUser = userHash(uid);
  await Promise.all([
    db.recursiveDelete(db.collection("accounts").doc(uid)),
    deleteQuery(db.collection("purchaseTokens").where("uid", "==", uid)),
    deleteQuery(db.collection("usageEvents").where("uid", "==", uid)),
    deleteQuery(db.collection("appUsageEvents").where("userHash", "==", hashedUser)),
    deleteQuery(db.collection("contentReports").where("userHash", "==", hashedUser)),
  ]);
}
