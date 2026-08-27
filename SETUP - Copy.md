# Rod Runners Raffles — setup and operations

An Android app (Kotlin + Jetpack Compose) with a Firebase back end for running
prize raffles. Money handling, entry-number allocation and winner selection all
happen server-side; the app cannot create an entry, change a price or pick a
winner on its own.

---

## 1. What's in the box

```
rrr/
├── app/                 Android app (Kotlin, Compose, Hilt, MVVM)
├── functions/           Cloud Functions (TypeScript, Node 20)
├── firestore.rules      Client write access — deny by default
├── firestore.indexes.json
├── storage.rules
└── firebase.json        Emulator + deploy config
```

## 2. Prerequisites

- Android Studio Ladybug or newer, JDK 17
- Node.js 20 and `npm i -g firebase-tools`
- A Firebase project (Blaze plan — Cloud Functions and outbound Stripe calls need it)
- A Stripe account

## 3. Firebase project setup

Create **two** projects: `rodrunners-staging` and `rodrunners-prod`. The demo-data
seeder refuses to run against anything that isn't a `-staging` / `-dev` project or
the emulator, which is what keeps demo raffles out of live data.

In each project:

1. **Authentication** → enable Email/Password. Turn on email enumeration protection.
2. **Firestore** → create in `europe-west2`.
3. **Storage** → create in `europe-west2`.
4. **App Check** → register the Android app with Play Integrity.
5. Add an Android app with package `uk.co.rodrunners.raffles`, download
   `google-services.json`, and put it at `app/google-services.json`.
   (`app/google-services.json.example` shows the expected shape.)

Then, from the repo root:

```bash
firebase use --add            # alias one project as "staging", one as "production"
firebase deploy --only firestore:rules,firestore:indexes,storage
```

## 4. Secrets

### App (`local.properties`, never committed)

```properties
STRIPE_PUBLISHABLE_KEY=pk_test_xxx
USE_FIREBASE_EMULATORS=false
ALLOW_DEMO_SEED=true
```

CI can supply the same values as environment variables instead.

### Functions (Secret Manager)

```bash
firebase functions:secrets:set STRIPE_SECRET_KEY
firebase functions:secrets:set STRIPE_WEBHOOK_SECRET
firebase functions:secrets:set SENDGRID_API_KEY
```

The publishable key is the only Stripe key that ever reaches the device.

## 5. Stripe

1. Deploy functions (below), then add a webhook endpoint pointing at the
   deployed `stripeWebhook` URL, e.g.
   `https://europe-west2-<project>.cloudfunctions.net/stripeWebhook`
2. Subscribe it to: `payment_intent.succeeded`, `payment_intent.payment_failed`,
   `payment_intent.canceled`, `charge.refunded`.
3. Copy the signing secret into `STRIPE_WEBHOOK_SECRET`.

The webhook is the only thing that marks an order paid. The app treats Stripe's
payment sheet returning "completed" as *not yet paid* and waits for the order
document to flip — so a customer who force-quits mid-payment still gets their
entries, and a spoofed client can't mint them.

## 6. Running locally

```bash
cd functions && npm install && npm run build
cd .. && firebase emulators:start          # UI on http://localhost:4000
```

Set `USE_FIREBASE_EMULATORS=true` in `local.properties`, then run the app on an
emulator. `10.0.2.2` is wired up automatically for the Android emulator's host
loopback.

## 7. Deploying

```bash
cd functions && npm run build && cd ..
firebase deploy --only functions,firestore:rules,firestore:indexes,storage
```

## 8. Building an APK for testing

An APK installs straight onto a phone, with no Play involvement. Useful for
trying the app on a real device, or handing a build to someone to look at.

### Without installing anything (GitHub Actions)

Push this repo to GitHub, then Actions tab -> **Test APK** -> Run workflow. It
builds on Google's machines and attaches the APK as a downloadable artifact.
Nothing needs installing locally.

Add a `GOOGLE_SERVICES_JSON` repository secret (paste the whole file) so the
build points at your Firebase project. Without it the APK still installs and
opens, but every screen that loads data will show a connection error.

### On your own machine

Debug build, test Stripe key, staging Firebase, demo seeding enabled:

```bash
./gradlew installStagingDebug          # builds and installs over adb
./gradlew assembleStagingDebug         # just the file
```

Output: `app/build/outputs/apk/staging/debug/app-staging-debug.apk`

It installs alongside a production build (the id is suffixed `.staging.debug`),
so you can keep both on one phone.

To share it, send that APK; the recipient enables "install unknown apps" for
whatever app they open it from. No signing setup is needed, since debug builds
are signed with the local debug key.

Play will not accept an APK for a new app, so this is a testing route only, not
a shipping one. See the next section for that.

## 9. Building a signed release for Google Play

Play takes an `.aab` (app bundle), not an `.apk`. The build only signs with your
*upload* key; Google re-signs with the app signing key it holds.

### Create the upload key, once

```bash
mkdir -p keystore
keytool -genkeypair -v \
  -keystore keystore/upload.jks \
  -alias rrr-upload \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=Rod Runners Raffles, O=<your registered company>, C=GB"
```

Back the `.jks` and its passwords up somewhere you will still have them in five
years. Losing the upload key is recoverable (Play can reset it); losing it
*without* Play App Signing enabled is not.

`keystore/`, `*.jks` and `local.properties` are all gitignored. Never commit any
of them.

### Point the build at it

In `local.properties`:

```properties
RRR_KEYSTORE_PATH=keystore/upload.jks
RRR_KEYSTORE_PASSWORD=...
RRR_KEY_ALIAS=rrr-upload
RRR_KEY_PASSWORD=...
STRIPE_PUBLISHABLE_KEY_LIVE=pk_live_...
RRR_VERSION_CODE=1
RRR_VERSION_NAME=1.0.0
```

### Build

```bash
./gradlew clean bundleProductionRelease
```

Output: `app/build/outputs/bundle/productionRelease/app-production-release.aab`
Mapping file: `app/build/outputs/mapping/productionRelease/mapping.txt`

Upload the mapping alongside the bundle so Crashlytics stack traces are readable.

The build fails with a plain message rather than producing a bad artifact if the
signing values are missing or if `STRIPE_PUBLISHABLE_KEY_LIVE` is still the
placeholder. `versionCode` must increase on every upload; CI sets it from the
run number.

### Check the bundle before uploading

```bash
# What Play will actually install on a given device
bundletool build-apks --bundle=app-production-release.aab --output=rrr.apks \
  --ks=keystore/upload.jks --ks-key-alias=rrr-upload
bundletool install-apks --apks=rrr.apks
```

Confirm on that install: the live Stripe key is in use, demo seeding is gone
(`ALLOW_DEMO_SEED` is false in release), and the app talks to the production
Firebase project.

### CI

`.github/workflows/release.yml` builds the same bundle on a tag. It needs these
repository secrets:

| Secret | What it holds |
| --- | --- |
| `RRR_KEYSTORE_BASE64` | `base64 -w0 keystore/upload.jks` |
| `RRR_KEYSTORE_PASSWORD` | keystore password |
| `RRR_KEY_ALIAS` | `rrr-upload` |
| `RRR_KEY_PASSWORD` | key password |
| `GOOGLE_SERVICES_JSON` | contents of the production `google-services.json` |
| `STRIPE_PUBLISHABLE_KEY_LIVE` | live publishable key |

### Play Console checklist

Before the store listing will pass review you need:

- Play App Signing enabled (it is, by default, for new apps)
- A privacy policy at a public URL, matching what the app actually collects
- The Data safety form completed: account details, email, purchase history,
  and Crashlytics diagnostics are all collected here
- Content rating questionnaire, declaring that the app involves prize draws
- Target API level current for the Play deadline (this builds against 35)
- A closed or internal testing track run before production

**Read Play's Gambling, Games and Contests policy before you submit.** Google
treats prize competitions as a restricted category, and approval can depend on
your jurisdiction, whether entry is paid, and whether a free entry route exists.
Getting this wrong means a rejected app or a suspended developer account, so
settle it with your solicitor and with Play policy *before* you build a listing
around it. That is a separate question from the UK competition law point in
section 12, and both need answering.

## 10. First admin

Custom claims can only be granted by an existing super admin, so the first one is
set by hand:

```bash
# scripts/bootstrap-admin.js — run once with a service account key
node -e "
const admin = require('firebase-admin');
admin.initializeApp({credential: admin.credential.cert(require('./service-account.json'))});
admin.auth().getUserByEmail('you@example.com').then(async u => {
  await admin.auth().setCustomUserClaims(u.uid, {admin: true, role: 'super_admin'});
  await admin.firestore().doc('adminUsers/' + u.uid).set({
    role: 'super_admin', email: u.email, createdAt: admin.firestore.FieldValue.serverTimestamp(),
  });
  console.log('done');
});
"
```

Every admin callable re-checks the claim *and* the `adminUsers` document, so
revoking one is enough to lock someone out.

## 11. Demo data

With `ALLOW_DEMO_SEED=true` on a staging project, Admin → Seed demo data creates
eight live raffles plus one completed one, using recognisable UK carp tackle
(Nash, Daiwa, Fox, Shimano, Delkim, Deeper, Trakker). Every document is flagged
`isDemo: true` and the app shows a banner wherever demo content appears. The
function hard-refuses to run against a production project ID.

## 12. Before you take real money

The app deliberately contains **no legal copy of its own**. Rules, terms, privacy
policy and company details all live in Firestore and render exactly as published.
Anything seeded is marked `legalReviewRequired: true` and displays a warning
banner until you clear that flag.

You must, with your own legal advice:

- Have a solicitor draft your competition terms, privacy policy and rules.
  Prize competitions in the UK are regulated; whether yours needs a free entry
  route, and what makes a skill question adequate, is a legal question, not a
  technical one. The data model supports a free-entry route
  (`freeEntryRoute` on the rules document) — using it is your decision to make
  with advice.
- Populate `content/company` with your registered name, company number,
  registered address and support email.
- Confirm your age restriction and geographic restriction per raffle
  (`minimumAge`, `geoRestriction`).
- Set up your Stripe account for the right business category.

Nothing in this codebase constitutes legal advice.

## 13. How the sensitive parts work

**Entry numbers.** `allocateEntryNumbers` runs inside a Firestore transaction that
advances `entriesSold`, so two people buying the last entry at the same instant
cannot both get it. Numbers are drawn from a format-preserving Feistel
permutation (`permute`) — random-looking, but provably collision-free across the
whole domain, so no retry loop and no duplicates. Entry documents use
deterministic IDs (`{competitionId}_{n}`), which makes a replayed webhook a
no-op rather than a double issue.

**The draw.** `drawWinner` builds the eligible set (active entries belonging to
non-suspended users), generates a seed with `crypto.randomBytes`, records its
SHA-256 hash, and selects with rejection sampling (`uniformIndex`) so there's no
modulo bias toward low entry numbers. The draw document is immutable and the
result is invisible to customers until an admin publishes it.

**Audit.** `auditLogs` is append-only in the security rules: create, update and
delete are all denied to clients, and functions only ever add.

## 14. Tests

```bash
cd functions && npm install && npm test
```

Covers the permutation being a genuine bijection, the draw's uniformity across a
non-power-of-two entry count, and bundle pricing never exceeding singles.
