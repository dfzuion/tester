const admin = require("firebase-admin");
admin.initializeApp({ projectId: process.env.PROJECT });

const EMAIL = (process.env.ADMIN_EMAIL || "").trim().toLowerCase();
const ROLE = "super_admin";

(async () => {
  if (!EMAIL) {
    console.error("No email supplied.");
    process.exit(1);
  }

  let user;
  try {
    user = await admin.auth().getUserByEmail(EMAIL);
  } catch {
    console.error("");
    console.error("=== FAILED ===");
    console.error("No account exists for " + EMAIL + ".");
    console.error("Sign up in the app with that address first, then run this again.");
    console.error("");
    const list = await admin.auth().listUsers(50);
    console.error("Accounts that DO exist right now:");
    if (!list.users.length) console.error("  (none at all - nobody has signed up yet)");
    list.users.forEach((u) => console.error("  " + (u.email || "(no email)") + "   uid " + u.uid));
    process.exit(1);
  }

  await admin.auth().setCustomUserClaims(user.uid, { role: ROLE, admin: true });
  await admin.firestore().collection("adminUsers").doc(user.uid).set({
    role: ROLE,
    active: true,
    permissions: ["*"],
    email: user.email || EMAIL,
    displayName: user.displayName || null,
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
    updatedBy: user.uid,
  }, { merge: true });

  console.log("");
  console.log("=== DONE ===");
  console.log(EMAIL + " is now Super Admin.");
  console.log("uid: " + user.uid);
  console.log("");
  console.log("Sign out of the app and sign back in - the admin flag only");
  console.log("reaches the phone on a fresh login.");
  console.log("");
  process.exit(0);
})().catch((e) => {
  console.error("");
  console.error("=== FAILED === " + e.message);
  process.exit(1);
});
