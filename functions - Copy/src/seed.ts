import * as admin from "firebase-admin";
import { onCall, HttpsError, CallableRequest } from "firebase-functions/v2/https";
import { Collections, REGION } from "./config";
import { requireAdmin } from "./guards";

/**
 * DEMO DATA - staging/emulator only.
 * Every document written here carries isDemo: true. The Android app shows a
 * standing "Demo data" banner whenever a demo raffle is on screen, and the
 * function refuses to run against a project id that does not end in -staging
 * or -dev, so demo raffles can never appear as real competitions in production.
 */

const PRIZES = [
  {
    title: "Nash Scope Black Ops 10ft 3lb Set",
    brand: "Nash",
    category: "rods",
    prizeName: "Nash Scope Black Ops rods, 3 rod set",
    description: "Three Nash Scope Black Ops 10ft 3lb carp rods, shrink-wrap handles, matched and unused. The travel-friendly set that stows down to 5ft 6in.",
    retailValuePence: 89999,
    entryPricePence: 200,
    maxEntries: 750,
    heroImageUrl: "https://images.example-cdn.co.uk/rrr/nash-scope-black-ops.jpg",
  },
  {
    title: "Daiwa 23 Emblem 45 SCW QD OT — 3 Reels",
    brand: "Daiwa",
    category: "reels",
    prizeName: "Three Daiwa 23 Emblem 45 SCW QD OT big pit reels",
    description: "A matched trio of Daiwa's 23 Emblem 45 SCW QD OT big pit reels, each loaded with 15lb mono. Quick-drag, one-turn spool release.",
    retailValuePence: 74997,
    entryPricePence: 250,
    maxEntries: 600,
    heroImageUrl: "https://images.example-cdn.co.uk/rrr/daiwa-23-emblem-45.jpg",
  },
  {
    title: "Fox EOS 1 Bedchair & Sleep System",
    brand: "Fox",
    category: "bedchairs",
    prizeName: "Fox EOS 1 bedchair with 5 season sleeping bag",
    description: "Fox EOS 1 wide bedchair with mud feet, paired with the Fox EOS 3 five-season bag. Everything you need for a comfortable 48 hours on the bank.",
    retailValuePence: 42998,
    entryPricePence: 150,
    maxEntries: 500,
    heroImageUrl: "https://images.example-cdn.co.uk/rrr/fox-eos-1-bedchair.jpg",
  },
  {
    title: "Delkim Txi-D Alarm Set & Receiver",
    brand: "Delkim",
    category: "alarms",
    prizeName: "Three Delkim Txi-D alarms with RX-D receiver",
    description: "Three Delkim Txi-D bite alarms in a hard case with the RX-D receiver. Vibration sensing, no moving parts, fully weatherproof.",
    retailValuePence: 79995,
    entryPricePence: 300,
    maxEntries: 400,
    heroImageUrl: "https://images.example-cdn.co.uk/rrr/delkim-txi-d.jpg",
  },
  {
    title: "Deeper Chirp+ 2 Fish Finder",
    brand: "Deeper",
    category: "accessories",
    prizeName: "Deeper Chirp+ 2 castable sonar",
    description: "Castable GPS sonar with onboard bathymetric mapping, 100m casting range and 330ft depth. Charges in 75 minutes.",
    retailValuePence: 47900,
    entryPricePence: 100,
    maxEntries: 900,
    heroImageUrl: "https://images.example-cdn.co.uk/rrr/deeper-chirp-plus-2.jpg",
  },
  {
    title: "Trakker Tempest Brolly Advanced & Groundsheet",
    brand: "Trakker",
    category: "bivvies",
    prizeName: "Trakker Tempest Brolly Advanced 100T with heavy-duty groundsheet",
    description: "The Tempest Brolly Advanced in 100T fabric with the matching heavy-duty groundsheet. Pitches in under two minutes.",
    retailValuePence: 52999,
    entryPricePence: 200,
    maxEntries: 550,
    heroImageUrl: "https://images.example-cdn.co.uk/rrr/trakker-tempest-brolly.jpg",
  },
  {
    title: "Shimano Ultegra 14000 XTD — 3 Reels",
    brand: "Shimano",
    category: "reels",
    prizeName: "Three Shimano Ultegra 14000 XTD reels",
    description: "Three Shimano Ultegra 14000 XTD long-cast reels with spare spools. X-Ship gearing and AR-C spool lip for distance work.",
    retailValuePence: 59997,
    entryPricePence: 200,
    maxEntries: 650,
    heroImageUrl: "https://images.example-cdn.co.uk/rrr/shimano-ultegra-xtd.jpg",
  },
  {
    title: "Complete Carp Setup Bundle",
    brand: "Mixed",
    category: "bundles",
    prizeName: "Rods, reels, alarms, bedchair, brolly and luggage",
    description: "A full bank-ready setup: three rods, three reels, a three-rod alarm set, bedchair, brolly, carryall and rod holdall. Everything in one draw.",
    retailValuePence: 249999,
    entryPricePence: 500,
    maxEntries: 800,
    heroImageUrl: "https://images.example-cdn.co.uk/rrr/complete-carp-bundle.jpg",
  },
];

export const seedDemoData = onCall({ region: REGION }, async (req: CallableRequest) => {
  await requireAdmin(req, "*");
  const projectId = process.env.GCLOUD_PROJECT ?? "";
  const isSafe = projectId.endsWith("-staging") || projectId.endsWith("-dev") || process.env.FUNCTIONS_EMULATOR === "true";
  if (!isSafe) {
    throw new HttpsError("failed-precondition",
      `Demo data is blocked on ${projectId}. Run it against a -staging or -dev project only.`);
  }

  const db = admin.firestore();
  const batch = db.batch();
  const now = Date.now();

  // Rules document, referenced by every raffle. Copy is editable in the admin panel.
  const rulesRef = db.collection(Collections.appContent).doc("rules_default_demo");
  batch.set(rulesRef, {
    isDemo: true,
    title: "Standard raffle rules",
    version: "demo-1",
    sections: [
      { heading: "Who can enter", body: "Open to residents of Great Britain aged 18 or over. Employees of the promoter and their immediate families may not enter." },
      { heading: "Entry limits", body: "Entry limits are shown on each raffle page and are enforced across all of your orders." },
      { heading: "Closing", body: "A raffle closes at the published closing time, or earlier if every entry is sold." },
      { heading: "How the winner is chosen", body: "After closing, one entry is drawn at random from all eligible paid entries using a seeded random draw. The draw record, including the number of eligible entries and the seed hash, is retained." },
      { heading: "Free entry route", body: "A free entry route may apply to this promotion. Where it does, the postal entry address and conditions are published in full in this section before the raffle opens." },
      { heading: "Refunds and cancellation", body: "If a raffle is cancelled before the draw, every paid entry is refunded in full to the original payment method." },
      { heading: "Responsible participation", body: "Only spend what you can comfortably afford. Entry limits and spend reminders are available in Account settings." },
    ],
    legalReviewRequired: true,
    note: "PLACEHOLDER COPY. Replace with rules approved by your own legal advisers before going live.",
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  });

  PRIZES.forEach((p, i) => {
    const ref = db.collection(Collections.competitions).doc(`demo_${i + 1}`);
    const closesAt = admin.firestore.Timestamp.fromMillis(now + (i + 1) * 26 * 60 * 60 * 1000);
    const sold = Math.floor(p.maxEntries * (0.18 + i * 0.09));
    batch.set(ref, {
      isDemo: true,
      title: p.title,
      prizeName: p.prizeName,
      brand: p.brand,
      category: p.category,
      description: p.description,
      heroImageUrl: p.heroImageUrl,
      galleryImageUrls: [p.heroImageUrl],
      retailValuePence: p.retailValuePence,
      entryPricePence: p.entryPricePence,
      bookingFeePence: 50,
      bundles: [
        { quantity: 5, pricePence: Math.round(p.entryPricePence * 4), label: "5 entries" },
        { quantity: 10, pricePence: Math.round(p.entryPricePence * 7.5), label: "10 entries" },
      ],
      maxEntries: p.maxEntries,
      entriesSold: Math.min(sold, p.maxEntries - 1),
      maxEntriesPerCustomer: 50,
      allocationMode: i % 2 === 0 ? "sequential" : "random",
      allocationKey: 1000 + i * 7919,
      status: "live",
      featured: i === 0,
      rulesId: rulesRef.id,
      winnerMechanism: "random_eligible_entry",
      winnerNameDisplay: "first_name_last_initial",
      minimumAge: 18,
      geoRestriction: "GB",
      opensAt: admin.firestore.Timestamp.fromMillis(now - 48 * 60 * 60 * 1000),
      closesAt,
      endingSoonNotified: false,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    });
  });

  // A completed demo raffle so the Results screen has something real to render.
  const doneRef = db.collection(Collections.competitions).doc("demo_completed_1");
  batch.set(doneRef, {
    isDemo: true,
    title: "Nash Scope Carbon Set",
    prizeName: "Nash Scope Carbon 9ft rod set",
    brand: "Nash", category: "rods",
    description: "Completed demonstration raffle.",
    heroImageUrl: "https://images.example-cdn.co.uk/rrr/nash-scope-carbon.jpg",
    retailValuePence: 69999, entryPricePence: 200, bookingFeePence: 50,
    maxEntries: 500, entriesSold: 500, maxEntriesPerCustomer: 50,
    allocationMode: "sequential", allocationKey: 42,
    status: "drawn", resultPublished: true, winningEntryNumber: 187,
    rulesId: rulesRef.id, winnerNameDisplay: "first_name_last_initial",
    closesAt: admin.firestore.Timestamp.fromMillis(now - 72 * 60 * 60 * 1000),
    drawnAt: admin.firestore.Timestamp.fromMillis(now - 70 * 60 * 60 * 1000),
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
  });
  batch.set(db.collection(Collections.winners).doc("demo_winner_1"), {
    isDemo: true, competitionId: "demo_completed_1", competitionTitle: "Nash Scope Carbon Set",
    prizeName: "Nash Scope Carbon 9ft rod set",
    prizeImageUrl: "https://images.example-cdn.co.uk/rrr/nash-scope-carbon.jpg",
    winningEntryNumber: 187, winnerDisplayName: "James T.", winnerUserId: "demo_user",
    published: true, drawnAt: admin.firestore.Timestamp.fromMillis(now - 70 * 60 * 60 * 1000),
  });

  seedEmailTemplates(batch, db);
  seedFaq(batch, db);
  seedAppContent(batch, db);

  await batch.commit();
  return { ok: true, competitions: PRIZES.length + 1, note: "All demo documents carry isDemo: true." };
});

function seedEmailTemplates(batch: FirebaseFirestore.WriteBatch, db: FirebaseFirestore.Firestore) {
  const t = (id: string, subject: string, html: string) =>
    batch.set(db.collection(Collections.emailTemplates).doc(id), { subject, html, enabled: true, isDemo: true });

  t("welcome", "Welcome to Rod Runners Raffles",
    "<p>Hello {{displayName}},</p><p>Your account is ready. Raffle rules and the free entry route, where one applies, are published on every raffle page.</p>");
  t("email_verification", "Confirm your email address",
    "<p>Hello {{displayName}},</p><p>Confirm your address to finish setting up your account: {{verificationLink}}</p>");
  t("password_reset", "Reset your password",
    "<p>Use this link to set a new password: {{resetLink}}. It expires in one hour.</p>");
  t("purchase_confirmation", "Order {{orderNumber}} confirmed",
    "<p>Hello {{displayName}},</p><p>You hold {{quantity}} entries in {{competitionTitle}}.</p><p>Your numbers: {{entryNumbers}}</p><p>Total paid: {{total}}</p>");
  t("entry_confirmation", "Your entry numbers for {{competitionTitle}}",
    "<p>Your numbers: {{entryNumbers}}</p>");
  t("ending_soon", "{{competitionTitle}} closes soon",
    "<p>{{competitionTitle}} closes at {{closesAt}}. {{entriesRemaining}} entries remain.</p>");
  t("winner_notification", "You've won {{prizeName}}",
    "<p>Hello {{displayName}},</p><p>Entry {{entryNumber}} has won {{prizeName}} in {{competitionTitle}}. Reply to this email with your delivery address.</p>");
  t("refund", "Refund issued for {{orderNumber}}",
    "<p>We've refunded {{amount}} to your original payment method. It usually lands within 5 working days.</p>");
  t("account_security", "A security change on your account",
    "<p>{{changeDescription}} at {{timestamp}}. If this wasn't you, contact support immediately.</p>");
  t("support_received", "Support ticket {{ticketId}}",
    "<p>Hello {{displayName}},</p><p>We've logged your ticket about \"{{subject}}\" and will reply shortly.</p>");
  t("announcement", "{{subject}}", "<p>{{body}}</p>");
}

function seedFaq(batch: FirebaseFirestore.WriteBatch, db: FirebaseFirestore.Firestore) {
  const faqs = [
    ["how_it_works", "How do the raffles work?", "Pick a raffle, choose how many entries you want, and pay. Your entry numbers are issued the moment payment clears. When the raffle closes, one entry is drawn at random from all eligible entries.", 1],
    ["entries", "When do I get my numbers?", "As soon as the payment is confirmed by our payment provider. They appear under My Tickets and in your confirmation email.", 2],
    ["entries_limit", "Is there a limit on entries?", "Yes. Each raffle shows its own per-person limit, and the limit is applied across every order you place for that raffle.", 3],
    ["payments", "What can I pay with?", "Any card supported by Stripe, plus Google Pay where your device offers it. We never see or store your card number.", 4],
    ["winners", "How is the winner chosen?", "After closing, the raffle is locked and one entry is drawn at random from all eligible paid entries. The draw record — including the eligible entry count and the seed hash — is kept for every raffle.", 5],
    ["results", "Where are results published?", "Under Results, usually within 24 hours of the raffle closing. Winners are contacted by email and push notification first.", 6],
    ["refunds", "Can I get a refund?", "If a raffle is cancelled before the draw, every entry is refunded in full. Refund rules for other situations are set out in each raffle's rules.", 7],
    ["account", "How do I close my account?", "Account, then Delete account. Any entries you already hold stay in the draw record so results remain accurate.", 8],
    ["legal", "Where are the rules?", "Every raffle has its own Competition Rules section covering eligibility, entry limits, closing, the draw and, where one applies, the free entry route.", 9],
    ["free_entry", "Is there a free entry route?", "Where a raffle operates a free entry route, the postal address and conditions are published in that raffle's rules before it opens.", 10],
  ];
  faqs.forEach(([id, question, answer, order]) =>
    batch.set(db.collection(Collections.faq).doc(String(id)), {
      question, answer, order, category: String(id).split("_")[0], published: true, isDemo: true,
    }));
}

function seedAppContent(batch: FirebaseFirestore.WriteBatch, db: FirebaseFirestore.Firestore) {
  batch.set(db.collection(Collections.appContent).doc("home_banners"), {
    isDemo: true,
    banners: [
      { id: "b1", title: "This week's headline draw", subtitle: "Nash Scope Black Ops, three rod set", imageUrl: "https://images.example-cdn.co.uk/rrr/banner-nash.jpg", deepLink: "rrr://competition/demo_1", active: true },
      { id: "b2", title: "Entries from £1", subtitle: "Deeper Chirp+ 2 fish finder", imageUrl: "https://images.example-cdn.co.uk/rrr/banner-deeper.jpg", deepLink: "rrr://competition/demo_5", active: true },
    ],
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  });
  batch.set(db.collection(Collections.appContent).doc("company"), {
    isDemo: true,
    tradingName: "Rod Runners Raffles",
    registeredName: "REPLACE WITH REGISTERED COMPANY NAME",
    companyNumber: "REPLACE",
    registeredAddress: "REPLACE",
    supportEmail: "support@rodrunnersraffles.co.uk",
    legalNote: "Replace every field here with your own registered details before release.",
  });
  batch.set(db.collection(Collections.appContent).doc("terms"), {
    isDemo: true, title: "Terms and conditions", version: "demo-1",
    body: "PLACEHOLDER. Your own legal advisers must supply these terms before launch.",
  });
  batch.set(db.collection(Collections.appContent).doc("privacy"), {
    isDemo: true, title: "Privacy policy", version: "demo-1",
    body: "PLACEHOLDER. Your own legal advisers must supply this policy before launch.",
  });
}
