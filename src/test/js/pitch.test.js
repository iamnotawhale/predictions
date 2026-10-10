const test = require('node:test');
const assert = require('node:assert/strict');
const { load } = require('./load');

const P = load();

const starters = (n) => Array.from({ length: n }, (_, i) => ({
  id: String(i + 1), number: i + 1, name: `Player ${i + 1}`, lastName: `P${i + 1}`,
  position: 'M', formationPlace: i + 1, starter: true,
}));

test('inferFormationKey falls back to 4-2-3-1 for unknown or missing formations', () => {
  assert.equal(P.inferFormationKey({ formation: '4-3-3' }), '4-3-3');
  assert.equal(P.inferFormationKey({ formation: '9-9-9' }), '4-2-3-1');
  assert.equal(P.inferFormationKey(null), '4-2-3-1');
});

test('buildFormationRows lays eleven starters out in rows and handles empty input', () => {
  assert.equal(P.buildFormationRows(null).length, 0);
  assert.equal(P.buildFormationRows({ formation: '4-3-3', starters: [] }).length, 0);
  const rows = P.buildFormationRows({ formation: '4-3-3', starters: starters(11) });
  assert.equal(P.countFormationSlots(rows), 11);
});

test('player labels and jersey numbers', () => {
  assert.equal(P.formationPlayerLabel({ lastName: 'Salah', name: 'Mohamed Salah' }), 'Salah');
  assert.equal(P.formationPlayerLabel({ name: ' Alisson ' }), 'Alisson');
  assert.equal(P.formationJerseyNumber({ number: 11, lastName: 'Salah' }), '11');
  assert.equal(P.formationJerseyNumber({ number: 0, lastName: 'salah' }), 'S');
  assert.equal(P.formationJerseyNumber(null), '?');
  assert.equal(P.formatBenchLabel({ number: 20, lastName: 'Jota', position: 'F' }), '20 · Jota · F');
  assert.equal(P.formatBenchLabel({ number: 20, lastName: 'Jota', position: 'SUB' }), '20 · Jota');
  assert.equal(P.formatBenchLabel({}), null);
});

test('event type classification', () => {
  assert.ok(P.isShotEventType('goal'));
  assert.ok(P.isShotEventType('Shot-Off-Target'));
  assert.ok(!P.isShotEventType('yellow-card'));
  assert.ok(P.isOnTargetShotType('shot-on-target'));
  assert.ok(P.isOnTargetShotType('save'));
  assert.ok(P.isBlockedShotType('shot-blocked'));
  assert.ok(P.isOffsideEventType('Offside'));
  assert.ok(!P.isOffsideEventType(undefined));
});

test('live event icons', () => {
  assert.equal(P.liveEventIcon('goal'), '⚽');
  assert.equal(P.liveEventIcon('yellow-card'), '🟨');
  assert.equal(P.liveEventIcon('substitution'), '🔁');
  assert.equal(P.liveEventIcon('unknown'), '•');
  assert.equal(P.liveEventIcon(null), '•');
});

test('event minute and period resolution', () => {
  assert.equal(P.parseLiveEventMinute("67'"), 67);
  assert.equal(P.parseLiveEventMinute('45\'+2\''), 45);
  assert.equal(P.parseLiveEventMinute(''), 0);
  assert.equal(P.resolveEventPeriod({ period: 2 }), 2);
  assert.equal(P.resolveEventPeriod({ minute: "30'" }), 1);
  assert.equal(P.resolveEventPeriod({ minute: "52'" }), 2);
  assert.equal(P.resolveEventPeriod({}), 1);
});

test('pitch percent flip and event keys', () => {
  assert.equal(P.flipPitchPercent(30), 70);
  assert.ok(Number.isNaN(P.flipPitchPercent(NaN)));
  const key = P.livePitchEventKey({ type: 'goal', shortText: 'P', text: 'T', playerName: 'N', fieldX: 0.94, fieldY: 0.5 });
  assert.equal(key, 'goal|P|T|N|0.9|0.5');
  assert.equal(P.livePitchEventKey({}), '|||||');
});
