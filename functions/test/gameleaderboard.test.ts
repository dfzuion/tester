/**
 * The weekly board pays real credit, so what is worth testing here is the pair
 * of things that would be invisible if they were wrong.
 *
 * The first is the week key. A board that rolls over on the wrong day pays the
 * wrong person, and it only shows up twice a year when the clocks move.
 *
 * The second is the one that has already gone wrong once. The clients decide
 * what a fish weighs and the server decides whether to believe them, and those
 * two tables live in three different files - GameScreen.kt, Game.jsx and here.
 * When the game's ranges were widened and this table was not, every genuinely
 * big fish was refused on its way to the board. The client ranges are written
 * out below and checked against the server's, so the next person to change one
 * finds out from a failing test rather than from a customer who landed a fifty
 * and never appeared on the leaderboard.
 *
 * Run with: npm test
 */
import { strict as assert } from "node:assert";
import test from "node:test";
import { SPECIES_RANGE, londonWeekKey, previousLondonWeekKey } from "../src/gameleaderboard";

/** Copied from SPECIES in GameScreen.kt and Game.jsx. Keep all three in step. */
const CLIENT_RANGE: Record<string, { min: number; max: number }> = {
  "Roach": { min: 0.25, max: 3.5 },
  "Tench": { min: 1.2, max: 12 },
  "Bream": { min: 1.5, max: 18 },
  "Leather carp": { min: 5, max: 42 },
  "Common carp": { min: 5, max: 50 },
  "Mirror carp": { min: 6, max: 60 },
};

test("the server knows every species the clients can catch", () => {
  for (const name of Object.keys(CLIENT_RANGE)) {
    assert.ok(SPECIES_RANGE[name], `${name} is missing from SPECIES_RANGE`);
  }
  assert.equal(Object.keys(SPECIES_RANGE).length, Object.keys(CLIENT_RANGE).length);
});

test("no fish a client can produce is refused by the server", () => {
  for (const [name, client] of Object.entries(CLIENT_RANGE)) {
    const server = SPECIES_RANGE[name];

    assert.ok(
      server.min <= client.min,
      `${name}: the server's floor of ${server.min} rejects the smallest ${client.min} the game can draw`
    );
    assert.ok(
      server.max >= client.max,
      `${name}: the server's ceiling of ${server.max} rejects the largest ${client.max} the game can draw`
    );
  }
});

test("the server leaves room for the client's rounding", () => {
  // The clients round to two decimals before sending. A fish drawn exactly at
  // the top of its range must not be refused by a hundredth of a pound.
  for (const [name, client] of Object.entries(CLIENT_RANGE)) {
    assert.ok(
      SPECIES_RANGE[name].max >= client.max + 0.01,
      `${name}: no headroom above the client maximum`
    );
  }
});

test("a mirror can still reach sixty", () => {
  // The number Scott asked for. If someone quietly trims the ceiling, the
  // fish of a lifetime stops being possible and nothing else complains.
  assert.ok(CLIENT_RANGE["Mirror carp"].max >= 60);
  assert.ok(SPECIES_RANGE["Mirror carp"].max >= 60);
});

test("the week key is the London week, and it rolls at Monday midnight", () => {
  // Sunday night into Monday morning, London time, in British Summer Time.
  const sundayLate = new Date("2026-08-30T22:30:00Z");
  const mondayEarly = new Date("2026-08-31T00:30:00Z");

  assert.notEqual(londonWeekKey(sundayLate), londonWeekKey(mondayEarly));
  assert.equal(previousLondonWeekKey(mondayEarly), londonWeekKey(sundayLate));
});

test("the week key survives the clock change", () => {
  // The last Sunday in October, when the clocks go back an hour.
  const before = new Date("2026-10-24T12:00:00Z");
  const after = new Date("2026-10-26T12:00:00Z");

  assert.notEqual(londonWeekKey(before), londonWeekKey(after));
  assert.equal(previousLondonWeekKey(after), londonWeekKey(before));
});
