const test = require('node:test');
const assert = require('node:assert/strict');
const { load } = require('./load');

const U = load();

test('escapeHtml escapes markup, quotes and keeps 0', () => {
  assert.equal(U.escapeHtml('<img src=x onerror="a()">'), '&lt;img src=x onerror=&quot;a()&quot;&gt;');
  assert.equal(U.escapeHtml("it's & co"), 'it&#39;s &amp; co');
  assert.equal(U.escapeHtml(0), '0');
  assert.equal(U.escapeHtml(null), '');
  assert.equal(U.escapeHtml(undefined), '');
});

test('hexToRgba handles short, long and invalid colours', () => {
  assert.equal(U.hexToRgba('#fff', 0.5), 'rgba(255,255,255,0.5)');
  assert.equal(U.hexToRgba('c8102e', 1), 'rgba(200,16,46,1)');
  assert.equal(U.hexToRgba('nonsense', 0.2), 'rgba(255,255,255,0.2)');
  assert.equal(U.hexToRgba(null, 0.2), 'rgba(255,255,255,0.2)');
});

test('status helpers are case-insensitive', () => {
  assert.ok(U.isFinishedStatus('FT'));
  assert.ok(U.isFinishedStatus('aet'));
  assert.ok(!U.isFinishedStatus('1h'));
  assert.ok(U.isNotStartedStatus('NS'));
  assert.ok(U.isNotStartedStatus('pst'));
  assert.ok(!U.isNotStartedStatus(undefined));
});

test('isLiveNowMatch needs a score and a non-terminal status', () => {
  assert.ok(U.isLiveNowMatch({ status: '2h', homeScore: 1, awayScore: 0 }));
  assert.ok(!U.isLiveNowMatch({ status: '2h', homeScore: null, awayScore: 0 }));
  assert.ok(!U.isLiveNowMatch({ status: 'ft', homeScore: 1, awayScore: 0 }));
  assert.ok(!U.isLiveNowMatch({ status: 'ns', homeScore: 0, awayScore: 0 }));
});

test('kickoff labels', () => {
  assert.equal(U.kickoffDateLabel('18.10 17:30'), '18.10');
  assert.equal(U.kickoffDateLabel(''), '');
  assert.equal(U.kickoffTimeLabel('18.10 17:30'), '17:30');
  assert.equal(U.kickoffTimeLabel(null), '—');
  assert.equal(U.kickoffTimeLabel('TBD'), 'TBD');
});

test('parseKickoffLabel orders by month, day, time and puts garbage last', () => {
  assert.ok(U.parseKickoffLabel('05.11 10:00') > U.parseKickoffLabel('31.10 23:59'));
  assert.ok(U.parseKickoffLabel('18.10 17:30') < U.parseKickoffLabel('18.10 20:00'));
  assert.equal(U.parseKickoffLabel('soon'), Number.MAX_SAFE_INTEGER);
});

test('sortMatchesByKickoff uses seconds-left when both have it', () => {
  const out = U.sortMatchesByKickoff([
    { publicId: 9, kickoff: 'x', kickoffSecondsLeft: 10 },
    { publicId: 8, kickoff: 'y', kickoffSecondsLeft: 5 },
  ]).map((m) => m.publicId);
  assert.deepEqual(out, [8, 9]);
});

test('sortMatchesByKickoff falls back to label, then id, and does not mutate', () => {
  const input = [
    { publicId: 3, kickoff: '18.10 20:00' },
    { publicId: 2, kickoff: '18.10 17:30' },
    { publicId: 1, kickoff: '18.10 17:30' },
  ];
  const copy = input.slice();
  assert.deepEqual(U.sortMatchesByKickoff(input).map((m) => m.publicId), [1, 2, 3]);
  assert.deepEqual(input, copy);
  assert.equal(U.sortMatchesByKickoff(null).length, 0);
});

test('kickoffSecondsLeft only applies to not-started matches', () => {
  assert.equal(U.kickoffSecondsLeft({ status: '1h', kickoffSecondsLeft: 5 }), null);
  assert.equal(U.kickoffSecondsLeft({ status: 'ns', kickoffSecondsLeft: 90 }), 90);
  assert.equal(U.kickoffSecondsLeft({ status: 'ns', predictSecondsLeft: 400 }), 100);
  assert.equal(U.kickoffSecondsLeft({ status: 'ns' }), null);
});

test('probability formatting', () => {
  assert.equal(U.pctLabel(0.125), '13%');
  assert.equal(U.pctLabel(undefined), '0%');
  assert.equal(U.fairOdds(0.5), '2.00');
  assert.equal(U.fairOdds(0), '—');
  assert.equal(U.fairOdds(undefined), '—');
});

test('niceChartStep picks 1/2/5/10 steps and prefers 5 on wide spans', () => {
  assert.equal(U.niceChartStep(10, 5), 2);
  assert.equal(U.niceChartStep(100, 5), 20);
  assert.equal(U.niceChartStep(0, 5), 0.2); // range is floored at 1
  assert.equal(U.niceChartStep(20, 20), 5);
});

test('parsePercentWidth clamps and accepts comma decimals', () => {
  assert.equal(U.parsePercentWidth('45%'), 45);
  assert.equal(U.parsePercentWidth('12,5%'), 12.5);
  assert.equal(U.parsePercentWidth('250%'), 100);
  assert.equal(U.parsePercentWidth('-3%'), 0);
  assert.equal(U.parsePercentWidth('abc'), 0);
  assert.equal(U.parsePercentWidth(null), 0);
});

test('competition keys and labels', () => {
  assert.equal(U.competitionCssKey('eng.league_cup'), 'carabao');
  assert.equal(U.competitionCssKey('whatever'), 'cup');
  assert.equal(U.competitionShort('uefa.europa.conf'), 'UECL');
  assert.equal(U.competitionShort(null), 'Cup');
  assert.equal(U.competitionShort('xyz'), 'xyz');
});

test('competitionBadgeHtml escapes and dims', () => {
  assert.equal(U.competitionBadgeHtml({ cup: false }, false), '');
  const html = U.competitionBadgeHtml({ cup: true, competition: 'eng.fa' }, true);
  assert.match(html, /badge-cup-fa dim/);
  assert.match(html, />FA</);
  const evil = U.competitionBadgeHtml({ cup: true, competition: '<b>' }, false);
  assert.ok(!evil.includes('<b>'));
});

test('today grouping', () => {
  assert.equal(U.todayGroupKey({ cup: true, competition: 'eng.fa' }), 'cup:eng.fa');
  assert.equal(U.todayGroupKey({ weekId: 7 }), 'week:7');
  assert.equal(U.todayGroupLabel({ cup: true, competition: 'uefa.europa' }), 'UEL');
  assert.equal(U.todayGroupLabel({ weekId: 7 }), '7 тур');
});

test('score label / status badge escape server-provided status', () => {
  const m = { hasPrediction: false, canPredict: false, status: '<script>', homeScore: null };
  assert.ok(!U.matchStatusBadge(m).includes('<script>'));
  assert.equal(U.matchScoreLabel({ hasPrediction: true, predictHome: 2, predictAway: 1 }), '2 : 1');
  assert.equal(U.matchScoreLabel({ status: 'ft', homeScore: 3, awayScore: 0 }), '3 : 0');
  assert.equal(U.matchScoreLabel({ kickoff: '18.10 17:30' }), '18.10 17:30');
  assert.equal(U.todayScoreLabel({ status: '1h', homeScore: 1, awayScore: 1 }), '1 : 1');
});

test('kit colour must be a hex value (blocks CSS injection)', () => {
  assert.equal(U.normalizeKitColor('ff0000'), '#ff0000');
  assert.equal(U.normalizeKitColor('#abc'), '#abc');
  assert.equal(U.normalizeKitColor('red;background:url(x)'), '');
  assert.equal(U.normalizeKitColor(''), '');
});

test('form dots and outcomes', () => {
  assert.equal(U.normalizeOutcome('w'), 'W');
  assert.equal(U.normalizeOutcome('?'), 'D');
  assert.equal(U.formDotClassByScores(2, 1), 'form-dot-win');
  assert.equal(U.formDotClassByScores(0, 1), 'form-dot-loss');
  assert.equal(U.formDotClassByScores(1, 1), 'form-dot-draw');
});

test('injury kind metadata falls back to "other"', () => {
  assert.equal(U.injuryKindMeta('INJURY').title, 'травма');
  assert.equal(U.injuryKindMeta('doubt').symbol, '?');
  assert.equal(U.injuryKindMeta('???').cls, 'modal-injury-kind--other');
});

test('player name helpers', () => {
  assert.equal(U.shortPlayerName('Erling Braut Haaland'), 'Haaland');
  assert.equal(U.shortPlayerName(''), '');
  assert.equal(U.surnameFromPlayerName('Mohamed Salah (Liverpool)'), 'Salah');
  assert.equal(U.surnameFromPlayerName('Trent Alexander-Arnold'), 'Alexander-Arnold');
  assert.equal(U.surnameFromPlayerName(''), '');
});

test('jerseySvg: body colour fills the shirt and number is drawn', () => {
  const svg = U.jerseySvg({ body: '#fceec1', trim: '#1b4879', number: 7, isGk: false });
  assert.ok(svg.includes('fill="#fceec1"'));
  assert.ok(svg.includes('stroke="#1b4879"'));
  assert.ok(svg.includes('>7</text>'));
});

test('jerseySvg: trim falls back to a readable colour when contrast is too low', () => {
  const svg = U.jerseySvg({ body: '#fceec1', trim: '#fdf0c8', number: 1, isGk: false });
  assert.ok(svg.includes('stroke="#1a1a1a"'));
});

test('jerseySvg: keeper uses neutral colour even when a kit colour is given', () => {
  const svg = U.jerseySvg({ body: '#fceec1', trim: '#1b4879', number: 1, isGk: true });
  assert.ok(svg.includes('fill="#4a5560"'));
  assert.ok(!svg.includes('#fceec1'));
});

test('jerseySvg: invalid colour falls back to default and number is escaped', () => {
  const svg = U.jerseySvg({ body: 'url(x)"><script>', trim: 'javascript:', number: '<script>1', isGk: false });
  assert.ok(svg.includes('fill="#3a4a58"'));
  assert.ok(!svg.includes('<script>'));
  assert.ok(svg.includes('&lt;script&gt;1'));
});

test('jerseyTextColor: dark text on light kits, white on dark', () => {
  assert.equal(U.jerseyTextColor('#fceec1'), '#1a1a1a');
  assert.equal(U.jerseyTextColor('#cf0221'), '#ffffff');
  assert.equal(U.jerseyTextColor(''), '#ffffff');
});
