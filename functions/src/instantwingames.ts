import * as admin from "firebase-admin";
import * as crypto from "crypto";
import { onCall, HttpsError, CallableRequest } from "firebase-functions/v2/https";
import { Collections, REGION, ENFORCE_APP_CHECK } from "./config";
import { requireAdmin, requireAuth, assertNotRateLimited } from "./guards";
import { writeAudit } from "./audit";
import { notifyAdmins, createUserNotification, pushToUser, queueEmail } from "./notifications";

/**
 * INSTANT WIN GAMES
 * ------------------------------------------------------------------
 * A separate mechanic from the ticket-number instant wins in instantwins.ts,
 * and it must stay separate: those prizes are sealed to a raffle ticket the
 * moment they're created, which only makes sense because a raffle has a fixed
 * pool of numbers. A game here isn't tied to any raffle - it's its own
 * standing campaign ("Summer Scratch & Win") that a customer opens from the
 * Instant Wins page. Unlike the free daily wheel, these cost site credit to
 * play - each play is charged the game's price up front, and there's no
 * daily limit: a customer can buy and play as many as they like for as long
 * as the game is active and has stock.
 *
 * One game document, one prize table, one play/claim flow - `mechanic` is
 * just which reveal animation the frontend shows (scratch a foil, flip a
 * card). Everything about odds, stock and payout is identical underneath,
 * so a new reveal style is a frontend component, not a new backend. That's
 * the whole point of building it this way rather than one system per game
 * type: the site said "instant win games" - plural styles, one system - and
 * bolting on a second, third, fourth copy of this file per style would mean
 * fixing the same bug in four places the day one turns up.
 *
 * The prize table lives on the game document as a small array (same idea as
 * a competition's own fields) rather than one document per prize - there's
 * no need for per-prize documents here because nothing is sealed to anything
 * external. Stock is tracked per prize line with a plain quantityRemaining
 * counter, moved inside the same transaction that records the play, so two
 * plays racing for the last one of a prize can't both win it.
 *
 * Odds are entirely server-side, the same principle as the daily spin: the
 * reveal the customer watches is just an animation of a result that's
 * already been decided and written before any pixel moves.
 */

export type InstantWinGameMechanic = "scratch" | "swipe";
const MECHANICS: InstantWinGameMechanic[] = ["scratch", "swipe"];

export interface InstantWinPrizeSpec {
  id: string;
  prizeName: string;
  valuePence: number;
  /** "credit" pays straight into the winner's balance; "item" is a physical
   *  prize or cash payout that support arranges by hand, same as instant wins. */
  prizeType: "item" | "credit";
  imageUrl: string | null;
  /** Relative chance while stock lasts - not a percentage, just a weight
   *  against the other prize lines and the noWinWeight below. */
  weight: number;
  quantityTotal: number;
  quantityRemaining: number;
}

interface InstantWinGameDoc {
  title: string;
  description: string;
  imageUrl: string | null;
  status: "draft" | "active" | "ended";
  mechanic: InstantWinGameMechanic;
  /** What one play costs, taken from the customer's site credit balance
   *  before the card is drawn. */
  pricePence: number;
  /** Relative chance of a play being a loser. Set high next to the prize
   *  weights, or nearly every play wins. */
  noWinWeight: number;
  prizes: InstantWinPrizeSpec[];
  totalPlays: number;
  totalWins: number;
  totalValueAwardedPence: number;
  createdAt: FirebaseFirestore.FieldValue;
  createdBy: string;
  updatedAt: FirebaseFirestore.FieldValue;
}

const MAX_PRIZE_LINES = 40;

function parsePrizeLines(raw: unknown): Omit<InstantWinPrizeSpec, "id" | "quantityRemaining">[] {
  if (!Array.isArray(raw) || raw.length === 0) {
    throw new HttpsError("invalid-argument", "Add at least one prize.");
  }
  if (raw.length > MAX_PRIZE_LINES) {
    throw new HttpsError("invalid-argument", `${MAX_PRIZE_LINES} prize lines is the maximum.`);
  }
  return raw.map((p: any, i: number) => {
    const prizeName = typeof p?.prizeName === "string" ? p.prizeName.trim() : "";
    if (!prizeName || prizeName.length > 120) {
      throw new HttpsError("invalid-argument", `Prize ${i + 1}: give it a name.`);
    }
    const valuePence = Number(p?.valuePence);
    if (!Number.isInteger(valuePence) || valuePence < 0 || valuePence > 1_000_000_00) {
      throw new HttpsError("invalid-argument", `Prize ${i + 1}: value looks wrong.`);
    }
    const quantityTotal = Number(p?.quantityTotal ?? p?.quantity ?? 1);
    if (!Number.isInteger(quantityTotal) || quantityTotal < 1 || quantityTotal > 100_000) {
      throw new HttpsError("invalid-argument", `Prize ${i + 1}: quantity must be 1 to 100,000.`);
    }
    const weight = Number(p?.weight ?? 1);
    if (!Number.isFinite(weight) || weight < 1 || weight > 1_000_000) {
      throw new HttpsError("invalid-argument", `Prize ${i + 1}: weight must be 1 or more.`);
    }
    const imageUrl = typeof p?.imageUrl === "string" && p.imageUrl.trim() ? p.imageUrl.trim() : null;
    const prizeType = p?.prizeType === "credit" ? "credit" as const : "item" as const;
    if (prizeType === "credit" && valuePence < 1) {
      throw new HttpsError("invalid-argument", `Prize ${i + 1}: a credit prize needs a value.`);
    }
    return { prizeName, valuePence, prizeType, imageUrl, weight, quantityTotal };
  });
}

function parseMechanic(raw: unknown): InstantWinGameMechanic {
  if (!MECHANICS.includes(raw as InstantWinGameMechanic)) {
    throw new HttpsError("invalid-argument", "Unknown game type.");
  }
  return raw as InstantWinGameMechanic;
}

/** Weighted pick among prizes that still have stock, plus the "no win" bucket. */
function drawResult(game: InstantWinGameDoc): InstantWinPrizeSpec | null {
  const live = game.prizes.filter((p) => p.quantityRemaining > 0);
  const prizeWeight = live.reduce((sum, p) => sum + p.weight, 0);
  const total = prizeWeight + Math.max(0, game.noWinWeight);
  if (total <= 0) return null;

  let ticket = crypto.randomInt(0, total);
  for (const p of live) {
    ticket -= p.weight;
    if (ticket < 0) return p;
  }
  return null; // fell into the no-win bucket
}

// -------------------------------------------------------------- admin: CRUD

export const createInstantWinGame = onCall({ region: REGION, enforceAppCheck: ENFORCE_APP_CHECK }, async (req: CallableRequest) => {
  const ctx = await requireAdmin(req, "competitions.write");
  const d = req.data ?? {};
  const title = typeof d.title === "string" ? d.title.trim() : "";
  if (!title || title.length > 120) throw new HttpsError("invalid-argument", "Give the game a title.");
  const description = typeof d.description === "string" ? d.description.trim().slice(0, 2000) : "";
  const imageUrl = typeof d.imageUrl === "string" && d.imageUrl.trim() ? d.imageUrl.trim() : null;
  const mechanic = parseMechanic(d.mechanic);
  const pricePence = Number(d.pricePence);
  if (!Number.isInteger(pricePence) || pricePence < 1 || pricePence > 1_000_000_00) {
    throw new HttpsError("invalid-argument", "Give the game a price to play.");
  }
  const noWinWeight = Number(d.noWinWeight ?? 85);
  if (!Number.isFinite(noWinWeight) || noWinWeight < 0 || noWinWeight > 1_000_000) {
    throw new HttpsError("invalid-argument", "No-win weight looks wrong.");
  }
  const lines = parsePrizeLines(d.prizes);
  const prizes: InstantWinPrizeSpec[] = lines.map((l) => ({
    ...l,
    id: crypto.randomUUID(),
    quantityRemaining: l.quantityTotal,
  }));

  const db = admin.firestore();
  const ref = db.collection(Collections.instantWinGames).doc();
  const payload: InstantWinGameDoc = {
    title,
    description,
    imageUrl,
    status: "draft",
    mechanic,
    pricePence,
    noWinWeight,
    prizes,
    totalPlays: 0,
    totalWins: 0,
    totalValueAwardedPence: 0,
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
    createdBy: ctx.uid,
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  };
  await ref.set(payload);

  await writeAudit({
    action: "instantwingame.created",
    actorId: ctx.uid, actorRole: "admin",
    objectType: "instantWinGame", objectId: ref.id,
    newValue: { title, mechanic, pricePence, prizes: prizes.map((p) => ({ prizeName: p.prizeName, quantityTotal: p.quantityTotal, valuePence: p.valuePence })) },
  });
  return { id: ref.id };
});

export const updateInstantWinGame = onCall({ region: REGION, enforceAppCheck: ENFORCE_APP_CHECK }, async (req: CallableRequest) => {
  const ctx = await requireAdmin(req, "competitions.write");
  const d = req.data ?? {};
  const gameId = typeof d.gameId === "string" ? d.gameId : "";
  if (!gameId) throw new HttpsError("invalid-argument", "Which game?");

  const db = admin.firestore();
  const ref = db.collection(Collections.instantWinGames).doc(gameId);
  const snap = await ref.get();
  if (!snap.exists) throw new HttpsError("not-found", "Game not found.");
  const current = snap.data() as InstantWinGameDoc;

  const update: Record<string, unknown> = { updatedAt: admin.firestore.FieldValue.serverTimestamp() };
  if (d.title !== undefined) {
    const title = String(d.title).trim();
    if (!title || title.length > 120) throw new HttpsError("invalid-argument", "Give the game a title.");
    update.title = title;
  }
  if (d.description !== undefined) update.description = String(d.description).trim().slice(0, 2000);
  if (d.imageUrl !== undefined) update.imageUrl = String(d.imageUrl).trim() || null;
  if (d.noWinWeight !== undefined) {
    const w = Number(d.noWinWeight);
    if (!Number.isFinite(w) || w < 0 || w > 1_000_000) throw new HttpsError("invalid-argument", "No-win weight looks wrong.");
    update.noWinWeight = w;
  }
  if (d.pricePence !== undefined) {
    const price = Number(d.pricePence);
    if (!Number.isInteger(price) || price < 1 || price > 1_000_000_00) throw new HttpsError("invalid-argument", "Price looks wrong.");
    update.pricePence = price;
  }
  if (d.status !== undefined) {
    if (!["draft", "active", "ended"].includes(d.status)) throw new HttpsError("invalid-argument", "Unknown status.");
    update.status = d.status;
  }

  // New prize lines are appended - existing lines are only ever changed via
  // addInstantWinGameStock (top up) or removeInstantWinGamePrize (only while
  // untouched), never overwritten wholesale, so a game that's already being
  // played can't have its odds or stock silently rewritten under it. The
  // mechanic isn't editable at all once created - switching a live game from
  // a scratch reveal to a swipe reveal mid-run would be confusing at best.
  if (d.newPrizes !== undefined) {
    const lines = parsePrizeLines(d.newPrizes);
    const added: InstantWinPrizeSpec[] = lines.map((l) => ({ ...l, id: crypto.randomUUID(), quantityRemaining: l.quantityTotal }));
    update.prizes = [...(current.prizes ?? []), ...added];
  }

  await ref.update(update);
  await writeAudit({
    action: "instantwingame.updated",
    actorId: ctx.uid, actorRole: "admin",
    objectType: "instantWinGame", objectId: gameId,
    previousValue: { status: current.status }, newValue: update,
  });
  return { ok: true };
});

/** Tops up stock on an existing prize line - can only add, never reduce below
 *  what's already been won, which is what quantityRemaining protects. */
export const addInstantWinGameStock = onCall({ region: REGION, enforceAppCheck: ENFORCE_APP_CHECK }, async (req: CallableRequest) => {
  const ctx = await requireAdmin(req, "competitions.write");
  const { gameId, prizeId, addQuantity } = req.data ?? {};
  const add = Number(addQuantity);
  if (!gameId || !prizeId) throw new HttpsError("invalid-argument", "Which prize?");
  if (!Number.isInteger(add) || add < 1 || add > 100_000) throw new HttpsError("invalid-argument", "Add 1 to 100,000.");

  const db = admin.firestore();
  const ref = db.collection(Collections.instantWinGames).doc(gameId);
  await db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    if (!snap.exists) throw new HttpsError("not-found", "Game not found.");
    const game = snap.data() as InstantWinGameDoc;
    const prizes = game.prizes.map((p) =>
      p.id === prizeId ? { ...p, quantityTotal: p.quantityTotal + add, quantityRemaining: p.quantityRemaining + add } : p
    );
    if (!prizes.some((p) => p.id === prizeId)) throw new HttpsError("not-found", "Prize line not found.");
    tx.update(ref, { prizes, updatedAt: admin.firestore.FieldValue.serverTimestamp() });
  });

  await writeAudit({
    action: "instantwingame.updated", actorId: ctx.uid, actorRole: "admin",
    objectType: "instantWinGame", objectId: gameId, newValue: { prizeId, stockAdded: add },
  });
  return { ok: true };
});

/** Removing a prize line is only allowed while none of its stock has been won
 *  yet - the same rule instant wins uses for removal, adapted to a line
 *  rather than a per-number document. */
export const removeInstantWinGamePrize = onCall({ region: REGION, enforceAppCheck: ENFORCE_APP_CHECK }, async (req: CallableRequest) => {
  const ctx = await requireAdmin(req, "competitions.write");
  const { gameId, prizeId } = req.data ?? {};
  if (!gameId || !prizeId) throw new HttpsError("invalid-argument", "Which prize?");

  const db = admin.firestore();
  const ref = db.collection(Collections.instantWinGames).doc(gameId);
  const snap = await ref.get();
  if (!snap.exists) throw new HttpsError("not-found", "Game not found.");
  const game = snap.data() as InstantWinGameDoc;
  const line = game.prizes.find((p) => p.id === prizeId);
  if (!line) throw new HttpsError("not-found", "Prize line not found.");
  if (line.quantityRemaining !== line.quantityTotal) {
    throw new HttpsError("failed-precondition", "Some of this prize has already been won, so it can't be removed.");
  }

  await ref.update({
    prizes: game.prizes.filter((p) => p.id !== prizeId),
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  });
  await writeAudit({
    action: "instantwingame.updated", actorId: ctx.uid, actorRole: "admin",
    objectType: "instantWinGame", objectId: gameId, newValue: { removedPrizeId: prizeId },
  });
  return { ok: true };
});

/** Admin list. Full prize table including stock, for managing every game. */
export const listInstantWinGames = onCall({ region: REGION, enforceAppCheck: ENFORCE_APP_CHECK }, async (req: CallableRequest) => {
  await requireAdmin(req, "competitions.write");
  const snap = await admin.firestore().collection(Collections.instantWinGames).orderBy("createdAt", "desc").get();
  return {
    games: snap.docs.map((d) => ({ id: d.id, ...d.data(), createdAt: d.data().createdAt?.toMillis?.() ?? null, updatedAt: d.data().updatedAt?.toMillis?.() ?? null })),
  };
});

/** Public list for the Instant Wins page - stock and odds stay out of it,
 *  same reasoning as the ticket-number instant wins keeping numbers off the
 *  wire. `mechanic` is included so the page knows which reveal component to
 *  render for each game. */
export const listActiveInstantWinGames = onCall({ region: REGION, enforceAppCheck: ENFORCE_APP_CHECK }, async () => {
  const snap = await admin.firestore().collection(Collections.instantWinGames).where("status", "==", "active").get();
  return {
    games: snap.docs.map((d) => {
      const g = d.data() as InstantWinGameDoc;
      const topPrize = g.prizes.reduce((max, p) => (p.valuePence > max ? p.valuePence : max), 0);
      return {
        id: d.id,
        title: g.title,
        description: g.description,
        imageUrl: g.imageUrl,
        mechanic: g.mechanic,
        pricePence: g.pricePence,
        topPrizeValuePence: topPrize,
        prizesLeft: g.prizes.some((p) => p.quantityRemaining > 0),
      };
    }),
  };
});

// -------------------------------------------------------------------- play

export const playInstantWinGame = onCall({ region: REGION, enforceAppCheck: ENFORCE_APP_CHECK }, async (req: CallableRequest) => {
  const uid = requireAuth(req);
  // No daily lock any more - a customer can buy and play as many of these as
  // they like - so the rate limit is the only thing stopping a runaway loop
  // of taps, not a once-a-day gate.
  await assertNotRateLimited(`instantwingame:${uid}`, 30, 60_000);
  const { gameId } = req.data ?? {};
  if (!gameId) throw new HttpsError("invalid-argument", "Which game?");

  const db = admin.firestore();
  const gameRef = db.collection(Collections.instantWinGames).doc(gameId);
  const userRef = db.collection(Collections.users).doc(uid);
  // Every play gets its own document now - there's nothing to key a
  // once-a-day lock against any more. The transaction still stops two taps
  // racing each other: the game's stock and the customer's balance are both
  // read and written inside the one transaction that records the play.
  const playRef = db.collection(Collections.instantWinGamePlays).doc();

  const outcome = await db.runTransaction(async (tx) => {
    const [gameSnap, userSnap] = await Promise.all([tx.get(gameRef), tx.get(userRef)]);
    if (!gameSnap.exists) throw new HttpsError("not-found", "That game isn't there any more.");
    if (!userSnap.exists) throw new HttpsError("not-found", "That account no longer exists.");

    const game = gameSnap.data() as InstantWinGameDoc;
    if (game.status !== "active") throw new HttpsError("failed-precondition", "This game isn't running right now.");

    // Belt and braces: every game created through the admin form always has
    // a valid price, but a malformed or hand-edited document must never be
    // allowed to turn into a NaN balance update - fail loudly instead.
    const price = Number(game.pricePence);
    if (!Number.isInteger(price) || price < 1) {
      throw new HttpsError("failed-precondition", "This game isn't set up correctly - no price to play.");
    }
    const before = Number(userSnap.data()!.creditBalancePence ?? 0);
    if (before < price) {
      throw new HttpsError("failed-precondition", "Not enough credit to play - top up your balance first.");
    }

    // Charged the moment the card is drawn, win or not - the same principle
    // as a physical scratchcard: you pay to play, then find out what you got.
    const prize = drawResult(game);
    const winValuePence = prize && prize.prizeType === "credit" ? prize.valuePence : 0;
    const balanceAfterPence = before - price + winValuePence;

    tx.update(userRef, {
      creditBalancePence: balanceAfterPence,
      creditUpdatedAt: admin.firestore.FieldValue.serverTimestamp(),
    });
    tx.set(userRef.collection("creditLedger").doc(), {
      deltaPence: -price,
      balanceAfterPence: before - price,
      reason: "instant_win_game_charge",
      description: `${game.title}: play`,
      orderId: null,
      metadata: {},
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
    });
    if (winValuePence > 0) {
      tx.set(userRef.collection("creditLedger").doc(), {
        deltaPence: winValuePence,
        balanceAfterPence,
        reason: "instant_win_game",
        description: `${game.title}: ${prize!.prizeName}`,
        orderId: null,
        metadata: {},
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
      });
    }

    tx.set(playRef, {
      gameId,
      gameTitle: game.title,
      mechanic: game.mechanic,
      userId: uid,
      pricePence: price,
      won: !!prize,
      prizeId: prize?.id ?? null,
      prizeName: prize?.prizeName ?? null,
      valuePence: prize?.valuePence ?? null,
      prizeType: prize?.prizeType ?? null,
      claimStatus: prize ? (prize.prizeType === "credit" ? "fulfilled" : "pending") : null,
      playedAt: admin.firestore.FieldValue.serverTimestamp(),
    });

    if (prize) {
      const prizes = game.prizes.map((p) => (p.id === prize.id ? { ...p, quantityRemaining: p.quantityRemaining - 1 } : p));
      tx.update(gameRef, {
        prizes,
        totalPlays: admin.firestore.FieldValue.increment(1),
        totalWins: admin.firestore.FieldValue.increment(1),
        totalValueAwardedPence: admin.firestore.FieldValue.increment(prize.valuePence),
      });
    } else {
      tx.update(gameRef, { totalPlays: admin.firestore.FieldValue.increment(1) });
    }

    return {
      won: !!prize,
      prizeName: prize?.prizeName ?? null,
      valuePence: prize?.valuePence ?? null,
      prizeType: prize?.prizeType ?? null,
      pricePence: price,
      balanceAfterPence,
    };
  });

  if (outcome.won) {
    const body = outcome.prizeType === "credit"
      ? `You won ${outcome.prizeName}! £${((outcome.valuePence ?? 0) / 100).toFixed(2)} of site credit has been added to your account.`
      : `You won ${outcome.prizeName}! We'll be in touch shortly to arrange getting it to you.`;
    await createUserNotification(uid, { category: "win", title: "Instant win!", body, deepLink: "rrr://instant-wins" });
    await pushToUser(uid, "win", "Instant win!", body, {});
    await writeAudit({
      action: "instantwingame.played", actorId: uid, actorRole: "customer",
      objectType: "instantWinGame", objectId: gameId,
      newValue: { won: true, prizeName: outcome.prizeName, valuePence: outcome.valuePence },
    });

    if (outcome.prizeType !== "credit") {
      await notifyAdmins(
        "Instant win game prize to fulfil",
        `A customer won ${outcome.prizeName} on the Instant Wins page. Check the Instant Win Games admin tab to arrange it.`,
        {}
      );
      const userSnap = await db.collection(Collections.users).doc(uid).get();
      const user = userSnap.data();
      await queueEmail(user?.email, "instant_win_game_notification", {
        displayName: user?.displayName ?? "Angler",
        prizeName: outcome.prizeName ?? "",
      });
    }
  }

  return outcome;
});

/** Marks a won item/cash prize as contacted or fulfilled - identical claim
 *  workflow to the ticket-number instant wins, applied to instantWinGamePlays. */
export const setInstantWinGameClaimStatus = onCall({ region: REGION, enforceAppCheck: ENFORCE_APP_CHECK }, async (req: CallableRequest) => {
  const ctx = await requireAdmin(req, "competitions.write");
  const { playId, claimStatus, note } = req.data ?? {};
  const allowed = ["pending", "contacted", "dispatched", "fulfilled"];
  if (!allowed.includes(claimStatus)) throw new HttpsError("invalid-argument", "Unknown claim status.");

  const ref = admin.firestore().collection(Collections.instantWinGamePlays).doc(playId);
  const snap = await ref.get();
  if (!snap.exists) throw new HttpsError("not-found", "That win wasn't found.");
  if (!snap.data()!.won) throw new HttpsError("failed-precondition", "That play wasn't a win.");

  await ref.update({
    claimStatus,
    claimNote: typeof note === "string" ? note.slice(0, 500) : null,
    claimUpdatedAt: admin.firestore.FieldValue.serverTimestamp(),
    claimUpdatedBy: ctx.uid,
  });
  await writeAudit({
    action: "instantwingame.claim.updated", actorId: ctx.uid, actorRole: "admin",
    objectType: "instantWinGamePlay", objectId: playId,
    previousValue: { claimStatus: snap.data()!.claimStatus ?? null }, newValue: { claimStatus },
  });
  return { ok: true };
});

/** Admin claims list for a single game - wins needing fulfilment, same shape
 *  as listInstantWins' "won" array. */
export const listInstantWinGameWins = onCall({ region: REGION, enforceAppCheck: ENFORCE_APP_CHECK }, async (req: CallableRequest) => {
  await requireAdmin(req, "competitions.write");
  const { gameId } = req.data ?? {};
  if (!gameId) throw new HttpsError("invalid-argument", "Which game?");

  const snap = await admin.firestore().collection(Collections.instantWinGamePlays)
    .where("gameId", "==", gameId).where("won", "==", true).get();
  const wins = snap.docs.map((d) => {
    const p = d.data();
    return {
      id: d.id,
      userId: p.userId,
      prizeName: p.prizeName,
      valuePence: p.valuePence,
      prizeType: p.prizeType,
      claimStatus: p.claimStatus ?? "pending",
      playedAtMillis: p.playedAt?.toMillis?.() ?? null,
    };
  });
  return { wins };
});
