/**
 * The spin pays real money, so the parts worth testing are the ones that would
 * be invisible if they were wrong: a wheel that can pay more than advertised,
 * a draw that never reaches its last segment, and a day boundary that drifts
 * when the clocks change.
 *
 * Run with: npm test
 */
import { strict as assert } from "node:assert";
import test from "node:test";
import {
  SPIN_WHEEL,
  SPIN_MAX_PENCE,
  pickSegment,
  londonDayKey,
  nextLondonMidnight,
} from "../src/dailyspin";

test("no segment pays more than the advertised maximum", () => {
  for (const segment of SPIN_WHEEL) {
    assert.ok(
      segment.pence > 0 && segment.pence <= SPIN_MAX_PENCE,
      `${segment.label} pays ${segment.pence}p`
    );
  }
});

test("weights are whole positive numbers", () => {
  for (const segment of SPIN_WHEEL) {
    assert.ok(Number.isInteger(segment.weight) && segment.weight > 0);
  }
});

test("every segment is reachable and nothing else is", () => {
  const seen = new Map<number, number>();
  for (let i = 0; i < 40_000; i++) {
    const segment = pickSegment();
    assert.ok(SPIN_WHEEL.includes(segment), "returned a segment not on the wheel");
    seen.set(segment.pence, (seen.get(segment.pence) ?? 0) + 1);
  }
  for (const segment of SPIN_WHEEL) {
    assert.ok((seen.get(segment.pence) ?? 0) > 0, `${segment.label} never came up`);
  }
});

test("the draw roughly follows the weights", () => {
  const total = SPIN_WHEEL.reduce((sum, s) => sum + s.weight, 0);
  const runs = 60_000;
  const counts = new Map<number, number>();
  for (let i = 0; i < runs; i++) {
    const s = pickSegment();
    counts.set(s.pence, (counts.get(s.pence) ?? 0) + 1);
  }
  for (const segment of SPIN_WHEEL) {
    const expected = segment.weight / total;
    const actual = (counts.get(segment.pence) ?? 0) / runs;
    assert.ok(
      Math.abs(actual - expected) < 0.02,
      `${segment.label}: expected about ${expected}, saw ${actual}`
    );
  }
});

test("the day key is the London date, not UTC", () => {
  // Just after midnight British Summer Time is already the next day in London
  // while UTC is still on the previous date.
  const summer = new Date("2026-06-14T23:30:00Z");
  assert.equal(londonDayKey(summer), "2026-06-15");

  // In winter London is UTC, so the same clock time is still the 14th.
  const winter = new Date("2026-12-14T23:30:00Z");
  assert.equal(londonDayKey(winter), "2026-12-14");
});

test("the next rollover is the start of the next London day, across a clock change", () => {
  // The night the clocks go back: that London day is 25 hours long.
  const beforeAutumnChange = new Date("2026-10-24T20:00:00Z");
  const rollover = nextLondonMidnight(beforeAutumnChange);

  assert.ok(rollover.getTime() > beforeAutumnChange.getTime());
  assert.notEqual(
    londonDayKey(rollover),
    londonDayKey(beforeAutumnChange),
    "rollover should land on the following London day"
  );
  assert.equal(
    londonDayKey(new Date(rollover.getTime() - 60_000)),
    londonDayKey(beforeAutumnChange),
    "a minute earlier should still be the same London day"
  );
});
