# Rod Runners Raffles

Android app and Firebase back end for a UK carp-tackle raffle business.

- **App** — Kotlin, Jetpack Compose, MVVM + Hilt, Navigation Compose, Coil,
  Stripe PaymentSheet. Dark-and-gold theme matching the supplied design.
- **Back end** — Cloud Functions (TypeScript, `europe-west2`), Firestore,
  Storage, FCM, Stripe with server-side verification.

Prices, entry-number allocation, order state and winner selection are all
computed on the server. The client cannot write to any collection that affects
money, entries or results — see `firestore.rules`.

**Start here: [SETUP.md](SETUP.md)** — project setup, secrets, Stripe webhook,
emulators, deployment, seeding demo data, and the pre-launch legal checklist.

Rules, terms, privacy policy and company details are content, not code: they're
stored in Firestore and rendered as published. Seeded copy is flagged
`legalReviewRequired` and shows a warning until a solicitor has replaced it.
