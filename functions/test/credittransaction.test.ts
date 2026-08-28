/**
 * Firestore aborts any transaction that reads after it has written. Paying for
 * entries with site credit did exactly that - it moved the credit (read then
 * write) and then allocated entry numbers (read) - so every credit checkout
 * failed with a bare INTERNAL.
 *
 * These use a stand-in transaction that refuses a read once a write has been
 * queued, which is the rule the real one enforces.
 *
 * Run with: npm test
 */
import { strict as assert } from "node:assert";
import test from "node:test";

class ReadAfterWriteError extends Error {
  constructor() {
    super("Firestore transactions require all reads to be executed before all writes.");
  }
}

/** Minimal stand-in for a Firestore transaction with the real ordering rule. */
class FakeTransaction {
  written = false;
  reads = 0;

  get(_ref: unknown): Promise<{ exists: boolean; data: () => Record<string, unknown> }> {
    if (this.written) throw new ReadAfterWriteError();
    this.reads++;
    return Promise.resolve({ exists: true, data: () => ({ creditBalancePence: 5000 }) });
  }
  set(_ref: unknown, _value: unknown): void { this.written = true; }
  update(_ref: unknown, _value: unknown): void { this.written = true; }
}

/**
 * The shape the checkout uses: prepare every read, then apply every write.
 * Mirrors createOrderAndPaymentIntent without needing Firestore itself.
 */
async function checkoutOrder(tx: FakeTransaction) {
  const prepared = await preparePhase(tx);   // reads the user
  const alloc = await allocatePhase(tx);     // reads the raffle, then writes
  prepared.apply(tx);                        // writes the credit
  tx.set({}, { entryNumbers: alloc });       // writes the order
  return alloc;
}

async function preparePhase(tx: FakeTransaction) {
  const snap = await tx.get({});
  const before = Number(snap.data().creditBalancePence ?? 0);
  return { balanceAfterPence: before - 500, apply: (t: FakeTransaction) => { t.update({}, {}); t.set({}, {}); } };
}

async function allocatePhase(tx: FakeTransaction) {
  await tx.get({});
  tx.update({}, {});
  return [1, 2, 3];
}

/** The old shape: move the credit fully, then try to read the raffle. */
async function checkoutOrderOldWay(tx: FakeTransaction) {
  const snap = await tx.get({});
  void snap;
  tx.update({}, {});          // credit written here
  tx.set({}, {});
  return allocatePhase(tx);   // and this read is what blew up
}

test("the fake transaction actually enforces reads-before-writes", () => {
  const tx = new FakeTransaction();
  tx.update({}, {});
  assert.throws(() => tx.get({}), /all reads to be executed before all writes/);
});

test("paying with credit reads everything before it writes anything", async () => {
  const tx = new FakeTransaction();
  const numbers = await checkoutOrder(tx);
  assert.deepEqual(numbers, [1, 2, 3]);
  assert.equal(tx.reads, 2, "should read the user and the raffle");
});

test("the old interleaved order is what Firestore rejects", async () => {
  const tx = new FakeTransaction();
  await assert.rejects(
    () => checkoutOrderOldWay(tx),
    /all reads to be executed before all writes/
  );
});

test("two credit movements in one transaction both read before either writes", async () => {
  const tx = new FakeTransaction();
  const toReferrer = await preparePhase(tx);
  const toReferee = await preparePhase(tx);
  toReferrer.apply(tx);
  toReferee.apply(tx);
  assert.equal(tx.reads, 2, "referral pays both sides, so both are read up front");
});
