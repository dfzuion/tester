/**
 * These cover the two pieces where a subtle bug would be invisible in the UI but
 * catastrophic in practice: entry numbers colliding, and the draw favouring
 * some entries over others.
 *
 * Run with: npm test
 */
import { strict as assert } from "node:assert";
import test from "node:test";
import { createHash } from "node:crypto";
import { permute } from "../src/allocation";
import { uniformIndex } from "../src/draw";
import { bestBundlePrice } from "../src/pricing";

test("permute is a bijection over the whole domain", () => {
  for (const domain of [1, 2, 7, 100, 999, 5000]) {
    const seen = new Set<number>();
    for (let i = 0; i < domain; i++) {
      const value = permute(i, domain, 0xC0FFEE);
      assert.ok(value >= 0 && value < domain, `out of range for domain ${domain}`);
      assert.ok(!seen.has(value), `collision at ${i} for domain ${domain}`);
      seen.add(value);
    }
    assert.equal(seen.size, domain);
  }
});

test("permute genuinely shuffles rather than returning the identity", () => {
  const domain = 1000;
  let fixedPoints = 0;
  for (let i = 0; i < domain; i++) {
    if (permute(i, domain, 42) === i) fixedPoints++;
  }
  // A random permutation has ~1 fixed point on average; the identity has 1000.
  assert.ok(fixedPoints < 20, `too many fixed points: ${fixedPoints}`);
});

test("permute is stable for a given key and domain", () => {
  const a = Array.from({ length: 50 }, (_, i) => permute(i, 50, 7));
  const b = Array.from({ length: 50 }, (_, i) => permute(i, 50, 7));
  assert.deepEqual(a, b);
});

test("different keys produce different orderings", () => {
  const a = Array.from({ length: 200 }, (_, i) => permute(i, 200, 1));
  const b = Array.from({ length: 200 }, (_, i) => permute(i, 200, 2));
  assert.notDeepEqual(a, b);
});

test("uniformIndex stays in range and is roughly uniform", () => {
  const count = 7; // deliberately not a power of two, where modulo bias would show
  const buckets = new Array(count).fill(0);
  const draws = 70_000;
  for (let i = 0; i < draws; i++) {
    const seed = createHash("sha256").update(`seed-${i}`).digest();
    const index = uniformIndex(seed, count);
    assert.ok(index >= 0 && index < count);
    buckets[index]++;
  }
  const expected = draws / count;
  for (const observed of buckets) {
    const drift = Math.abs(observed - expected) / expected;
    assert.ok(drift < 0.05, `bucket drifted ${(drift * 100).toFixed(2)}% from uniform`);
  }
});

test("uniformIndex handles a single eligible entry", () => {
  const seed = createHash("sha256").update("only-one").digest();
  assert.equal(uniformIndex(seed, 1), 0);
});

test("bestBundlePrice never charges more than buying singles", () => {
  const bundles = [
    { quantity: 5, pricePence: 900, label: "5 for £9" },
    { quantity: 10, pricePence: 1600, label: "10 for £16" },
  ];
  for (let quantity = 1; quantity <= 40; quantity++) {
    const result = bestBundlePrice(quantity, 200, bundles);
    assert.ok(
      result.total <= quantity * 200,
      `quantity ${quantity} priced above singles`,
    );
    assert.ok(result.total > 0);
  }
});

test("bestBundlePrice uses the larger bundle where it is cheaper", () => {
  const bundles = [
    { quantity: 5, pricePence: 900 },
    { quantity: 10, pricePence: 1600 },
  ];
  assert.equal(bestBundlePrice(10, 200, bundles).total, 1600);
  assert.equal(bestBundlePrice(11, 200, bundles).total, 1800);
});
