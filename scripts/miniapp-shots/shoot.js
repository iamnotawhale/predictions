// Screenshots Mini App screens against mocked /api/miniapp/* (no DB / Telegram needed).
// Usage: NODE_PATH=$(npm root -g) node scripts/miniapp-shots/shoot.js <outDir> [chromiumPath]
const http = require('http');
const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

const STATIC = path.resolve(__dirname, '../../src/main/resources/static');
const OUT = path.resolve(process.argv[2] || 'miniapp-shots');
const CHROME = process.argv[3] || process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH;
const THEME = process.env.THEME || 'dark';

const TEAMS = [
  ['MCI', 'Manchester City'], ['ARS', 'Arsenal'], ['LIV', 'Liverpool'], ['CHE', 'Chelsea'],
  ['TOT', 'Tottenham'], ['MUN', 'Manchester United'], ['NEW', 'Newcastle'], ['AST', 'Aston Villa'],
  ['BHA', 'Brighton'], ['WHU', 'West Ham'], ['BOU', 'Bournemouth'], ['CRY', 'Crystal Palace'],
];
const logo = (c) => `/api/miniapp/logos/${c}.png`;
const COLORS = ['#6cabdd', '#ef0107', '#c8102e', '#034694', '#132257', '#da291c', '#241f20', '#670e36', '#0057b8', '#7a263a', '#da291c', '#1b458f'];

function match(i, o = {}) {
  const [h, a] = [TEAMS[(i * 2) % 12], TEAMS[(i * 2 + 1) % 12]];
  return {
    publicId: 100 + i, weekId: 7, homeCode: h[0], homeName: h[1], homeLogo: logo(h[0]),
    awayCode: a[0], awayName: a[1], awayLogo: logo(a[0]), status: 'ns', homeScore: null, awayScore: null,
    kickoff: '18.10 17:30', canPredict: true, hasPrediction: false, predictHome: null, predictAway: null,
    points: null, oddHome: 2.1, oddDraw: 3.5, oddAway: 3.4, predictUntil: '18.10 17:35',
    predictSecondsLeft: 7200, kickoffSecondsLeft: 7500, recommendedHome: 2, recommendedAway: 1,
    weekBonus: false, cup: false, competition: null, bonusMatchId: null, ...o,
  };
}
const EVIL = '<img src=x onerror="window.__xss=1">';
const matches = [
  match(0, { status: 'ft', homeScore: 2, awayScore: 1, canPredict: false, hasPrediction: true, predictHome: 2, predictAway: 1, points: 5 }),
  match(1, { status: '2h', homeScore: 1, awayScore: 1, canPredict: false, hasPrediction: true, predictHome: 1, predictAway: 0, points: 0 }),
  match(2, { hasPrediction: true, predictHome: 1, predictAway: 1, weekBonus: true }),
  match(3, { homeName: EVIL, awayName: EVIL, homeCode: EVIL }), match(4),
  match(5, { cup: true, competition: 'eng.league_cup', bonusMatchId: 9 }),
];
const users = ['nik', 'max', 'alex', 'ivan'];
const API = {
  '/profile': { login: 'nik', currentWeekId: 7, season: 2026, weekLabel: 'Тур 7', dnsHint: null, bettingRecommenderEnabled: true, admin: false },
  '/profile/stats': {
    login: 'nik', weekLabel: 'Тур 7', season: 2026, currentWeekId: 7, seasonPoints: 84, currentWeekPoints: 9,
    bonusMatchPublicId: 102, bonusMatchLabel: 'TOT — ARS', finishedMatches: 60, predictedFinished: 58,
    highlights: [{ label: 'Точных счетов', value: '9', hint: '15% матчей' }, { label: 'Исходов', value: '27', hint: '47%' }, { label: 'Серия', value: '4', hint: 'подряд с очками' }],
    breakdown: [{ label: 'Точный счёт', count: 9, percent: '15%', description: '5 очков' }, { label: 'Исход + разница', count: 12, percent: '21%', description: '3 очка' }, { label: 'Исход', count: 15, percent: '26%', description: '1 очко' }, { label: 'Мимо', count: 22, percent: '38%', description: '0 очков' }],
    teams: TEAMS.slice(0, 6).map((t, i) => ({ teamCode: t[0], teamName: t[1], logo: logo(t[0]), matches: 8 - i % 3, avgPoints: Math.round((3.2 - i * 0.4) * 10) / 10, hint: i % 2 ? 'Не угадываю' : 'Хорошо читаю' })),
    habits: [{ label: 'Любимый счёт', value: '2:1', description: '14 раз' }, { label: 'Средний тотал', value: '2.8', description: 'голов в прогнозах' }],
    bestWeek: { weekId: 3, points: 17, description: '3 точных счёта' }, worstWeek: { weekId: 5, points: 2, description: 'Без попаданий' },
    bonusMatchCount: 6, bonusAvgPoints: 4.2, bonusHint: 'Бонусы идут лучше среднего',
  },
  '/weeks': Array.from({ length: 7 }, (_, i) => ({ id: i + 1, hasPredictions: true })),
  '/cups': [{ competition: 'eng.league_cup', label: 'Carabao Cup', hasPredictions: true, matchCount: 2 }, { competition: 'uefa.europa', label: 'UEL', hasPredictions: false, matchCount: 3 }],
  '/today': { matches: matches.slice(0, 4).concat([matches[5]]), hasLive: true },
  '/leaderboard': { weekId: null, title: 'Общий зачёт', liveActive: true, entries: users.map((u, i) => ({ login: u, points: 84 - i * 9, provisionalPoints: 84 - i * 9 + (i === 1 ? 3 : 0), liveDelta: i === 1 ? 3 : 0 })) },
  '/standings': TEAMS.map((t, i) => ({ place: i + 1, code: t[0], name: t[1], logo: logo(t[0]), played: 7, won: 6 - (i >> 1), drawn: 1, lost: i >> 1, goalsFor: 18 - i, goalsAgainst: 5 + i, points: 19 - i * 1, placeDelta: i === 3 ? 1 : i === 4 ? -1 : 0, liveScore: i === 0 ? '1:0' : null, liveResult: i === 0 ? 'W' : null })),
  '/chart': { weeks: [1, 2, 3, 4, 5, 6, 7], series: users.map((u, i) => ({ login: u, label: u, points: [8, 17, 30, 41, 50, 66, 84].map((p) => Math.round(p * (1 - i * 0.1))) })) },
  '/weeks/7/matches': matches.slice(0, 5),
  '/weeks/7/review': { weekId: 7, totalPoints: 9, items: matches.slice(0, 2).map((m) => ({ publicId: m.publicId, homeCode: m.homeCode, awayCode: m.awayCode, status: m.status, homeScore: m.homeScore, awayScore: m.awayScore, predictHome: m.predictHome, predictAway: m.predictAway, points: m.points, hasPrediction: true })) },
};


const form = (o, n, c, h) => ({ outcome: o, ownScore: n, opponentScore: c, opponentCode: TEAMS[(n + c + 3) % 12][0], kickoff: '10.10', home: h });
const FORM = [form('W', 2, 0, true), form('D', 1, 1, false), form('W', 3, 1, true), form('L', 0, 2, false), form('W', 2, 1, true)];
const LINEUP = (pre) => ['G', 'D', 'D', 'D', 'D', 'M', 'M', 'M', 'F', 'F', 'F'].map((pos, i) => ({ number: i + 1, name: `${pre} Player ${i + 1}`, position: pos }));
const player = (id, n, name, pos, place, starter, extra = {}) => ({ id: String(id), number: n, name, shortName: name.split(' ').pop(), lastName: name.split(' ').pop(), position: pos, positionName: pos, formationPlace: place, starter, subbedOut: false, subbedIn: false, subPartnerId: '', subPartnerName: '', jerseyImage: null, goals: null, assists: null, yellowCards: null, redCards: null, stats: [], ...extra });
const FORMATION = (side, code, color) => ({
  side, teamCode: code, formation: '4-3-3', kitColor: color,
  starters: [['G', 1], ['D', 2], ['D', 3], ['D', 4], ['D', 5], ['M', 6], ['M', 7], ['M', 8], ['F', 9], ['F', 10], ['F', 11]].map(([pos, n], i) => player(side + n, n, `${code} Player ${n}`, pos, i + 1, true, n === 9 ? { goals: 1 } : n === 6 ? { yellowCards: 1 } : {})),
  bench: [player(side + '20', 20, `${code} Sub Twenty`, 'M', 0, false)],
});
const EVENTS = [
  { minute: "67'", text: 'Goal! Chelsea 1, Liverpool 1. Cole Palmer converts the penalty.', type: 'goal', period: 2, fieldX: 0.92, fieldY: 0.5, field2X: null, field2Y: null, goalPositionY: 0.5, teamName: 'Chelsea', shortText: 'Palmer', playerName: 'Cole Palmer' },
  { minute: "52'", text: 'Yellow card for Moises Caicedo.', type: 'yellow-card', period: 2, fieldX: 0.4, fieldY: 0.3, field2X: null, field2Y: null, goalPositionY: null, teamName: 'Chelsea', shortText: 'Caicedo', playerName: 'Moises Caicedo' },
  { minute: "23'", text: 'Goal! Liverpool 1, Chelsea 0. Mohamed Salah scores.', type: 'goal', period: 1, fieldX: 0.1, fieldY: 0.45, field2X: null, field2Y: null, goalPositionY: 0.45, teamName: 'Liverpool', shortText: 'Salah', playerName: 'Mohamed Salah' },
];
const pois = (l, n) => { const r = [Math.exp(-l)]; for (let k = 1; k < n; k++) r.push(r[k - 1] * l / k); return r; };
const MATRIX = (() => { const h = pois(1.8, 6), a = pois(1.1, 6); const m = h.map((x) => a.map((y) => x * y)); const t = m.flat().reduce((x, y) => x + y, 0); return m.map((r) => r.map((v) => v / t)); })();
const pts = (rh, ra, ph, pa) => (rh === ph && ra === pa ? 4 : rh - ra === ph - pa ? 2 : Math.sign(rh - ra) === Math.sign(ph - pa) ? 1 : -1);
const BREAKDOWN = MATRIX.map((_, ph) => MATRIX.map((__, pa) => { const t = [0, 0, 0, 0]; MATRIX.forEach((row, h) => row.forEach((p, a) => { t[{ 4: 0, 2: 1, 1: 2, '-1': 3 }[pts(h, a, ph, pa)]] += p; })); return t; }));
const EV = (t) => 4 * t[0] + 2 * t[1] + t[2] - t[3];
const REC = {
  recommendedHome: 2, recommendedAway: 1, expectedHomeGoals: 1.8, expectedAwayGoals: 1.1, scoreProbability: MATRIX[2][1],
  explanationRows: [{ metric: 'Ожидаемые голы', home: '1.8', away: '1.1' }, { metric: 'Шанс победы', home: '52%', away: '24%' }, { metric: 'Атака', home: 'сильная', away: 'средняя' }, { metric: 'Оборона', home: 'средняя', away: 'дырявая' }, { metric: 'Сухие (форма)', home: '20%', away: '0%' }],
  explanationNotes: ['Основа прогноза — коэффициенты букмекеров'], explanationLines: [], summary: '',
  distribution: { matrix: MATRIX, homeWin: 0.52, draw: 0.24, awayWin: 0.24, btts: 0.55, over05: 0.95, over15: 0.78, over25: 0.55, over35: 0.3, topScores: [] },
  insight: {
    scoring: { exact: 4, diff: 2, outcome: 1, miss: -1 }, breakdown: BREAKDOWN, expectedPoints: EV(BREAKDOWN[2][1]), pointsChance: 1 - BREAKDOWN[2][1][3],
    mostLikely: { home: 1, away: 1, probability: MATRIX[1][1], expectedPoints: EV(BREAKDOWN[1][1]) },
    reasons: ['Букмекеры: победа хозяев 52%, ничья 24%, победа гостей 24%.', 'Ждём около 1.8 : 1.1 гола, тотал больше 2.5 — 55%.',
      `Самый вероятный счёт 1:1 (${Math.round(MATRIX[1][1] * 100)}%), но 2:1 выгоднее: в среднем ${EV(BREAKDOWN[2][1]).toFixed(2)} очка против ${EV(BREAKDOWN[1][1]).toFixed(2)} — чаще попадает хотя бы в исход или разницу голов.`,
      `Очки получим с шансом ${Math.round((1 - BREAKDOWN[2][1][3]) * 100)}%, точный счёт — ${Math.round(BREAKDOWN[2][1][0] * 100)}%.`],
  },
};
const INSIGHTS = {
  homeForm: FORM, awayForm: FORM.slice().reverse(),
  news: [{ title: 'Тренер: «Мы готовы к матчу»', url: 'https://example.com/n1', publishedAt: '10.10 12:00' }, { title: 'Травма основного защитника', url: 'https://example.com/n2', publishedAt: '10.10 09:30' }],
  recommendation: REC,
  injuries: [{ teamCode: 'LIV', playerName: 'Diogo Jota', kind: 'injury' }, { teamCode: 'CHE', playerName: 'Reece James', kind: 'suspension' }],
  homeLineup: LINEUP('LIV'), awayLineup: LINEUP('CHE'),
};
const LIVE = { live: true, homeLineup: LINEUP('LIV'), awayLineup: LINEUP('CHE'), homeFormation: FORMATION('home', 'LIV', '#c8102e'), awayFormation: FORMATION('away', 'CHE', '#034694'), events: EVENTS,
  matchStats: [{ key: 'possessionPct', label: 'Владение', homeValue: '56%', awayValue: '44%' }, { key: 'totalShots', label: 'Удары', homeValue: '12', awayValue: '8' }, { key: 'shotsOnTarget', label: 'В створ', homeValue: '5', awayValue: '3' }],
  homeColor: '#c8102e', awayColor: '#034694', homeScore: 1, awayScore: 1, status: '2h' };
const H2H = Array.from({ length: 5 }, (_, i) => ({ leagueName: 'Premier League', kickoff: `0${i + 1}.0${i + 1}.2025`, homeCode: 'LIV', awayCode: 'CHE', homeScore: i, awayScore: 1 }));
const teamMatch = (i, past) => ({ publicId: 200 + i, weekId: 7 - i, homeCode: 'LIV', homeName: 'Liverpool', homeLogo: logo('LIV'), awayCode: TEAMS[(i + 3) % 12][0], awayName: TEAMS[(i + 3) % 12][1], awayLogo: logo(TEAMS[(i + 3) % 12][0]), status: past ? 'ft' : 'ns', homeScore: past ? 2 : null, awayScore: past ? i % 2 : null, kickoff: '12.10 16:00' });
const TEAM = { teamCode: 'LIV', teamName: 'Liverpool', logo: logo('LIV'), place: 3, played: 7, won: 5, drawn: 1, goalsFor: 16, goalsAgainst: 7, lost: 1, points: 16,
  form: FORM, seasonStats: { xg: 1.9, xga: 0.9, scoredPerMatch: 2.3, concededPerMatch: 1.0, bttsPct: 55, csPct: 30, over25Pct: 60, homePpg: 2.4, awayPpg: 1.9, possessionPct: 58, shotsPerMatch: 15.2, shotsOnTargetPerMatch: 5.8, cleanSheets: 3 },
  leaders: [{ name: 'Mohamed Salah', position: 'F', appearances: 7, goals: 6, assists: 4, saves: null }, { name: 'Alisson', position: 'G', appearances: 7, goals: 0, assists: 0, saves: 21 }],
  lastMatches: [0, 1, 2].map((i) => teamMatch(i, true)), upcomingMatches: [3, 4].map((i) => teamMatch(i, false)) };
Object.assign(API, {
  '/match/LIV/CHE': matches[1], '/match/LIV/CHE/insights': INSIGHTS, '/match/LIV/CHE/live-details': LIVE,
  '/match/BOU/CRY': matches[3], '/match/TOT/MUN': matches[2],
  '/h2h/LIV/CHE': H2H, '/team/LIV': TEAM, '/team/LIV/matches': { teamCode: 'LIV', teamName: 'Liverpool', lastMatches: TEAM.lastMatches, upcomingMatches: TEAM.upcomingMatches },
  '/cups/eng.league_cup/matches': [matches[5]], '/cups/uefa.europa/matches': [],
  '/cups/eng.league_cup/review': { competition: 'eng.league_cup', label: 'Carabao Cup', totalPoints: 4, items: [] },
  '/match/102/crowd': { matchPublicId: 102, totalPredictions: 4, homeWinPct: 50, drawPct: 25, awayWinPct: 25, topScores: [{ score: '2:1', count: 2, percent: 50 }, { score: '1:1', count: 1, percent: 25 }] },
});

const mime = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.png': 'image/png', '.webp': 'image/webp', '.json': 'application/json', '.ttf': 'font/ttf' };
const server = http.createServer((req, res) => {
  const p = decodeURIComponent(req.url.split('?')[0]);
  const f = path.join(STATIC, p === '/' ? 'miniapp/index.html' : p);
  if (!f.startsWith(STATIC) || !fs.existsSync(f) || fs.statSync(f).isDirectory()) { res.writeHead(404); return res.end(); }
  res.writeHead(200, { 'Content-Type': mime[path.extname(f)] || 'application/octet-stream' });
  fs.createReadStream(f).pipe(res);
});

function svgLogo(code) {
  const i = Math.max(0, TEAMS.findIndex((t) => t[0] === code));
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64"><circle cx="32" cy="32" r="30" fill="${COLORS[i]}"/><text x="32" y="38" font-size="18" font-family="sans-serif" font-weight="700" text-anchor="middle" fill="#fff">${code}</text></svg>`;
}

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  await new Promise((r) => server.listen(0, r));
  const base = `http://127.0.0.1:${server.address().port}`;
  const browser = await chromium.launch({ executablePath: CHROME, args: ['--no-sandbox'] });
  const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2, colorScheme: THEME, ...(process.env.VIDEO ? { recordVideo: { dir: path.join(OUT, 'video'), size: { width: 390, height: 844 } } } : {}) });
  const page = await ctx.newPage();
  await page.route('**/*', (route) => {
    const url = new URL(route.request().url());
    if (url.origin !== base) return route.abort();
    if (url.pathname.startsWith('/api/miniapp/logos/')) {
      const code = url.pathname.split('/').pop().replace('.png', '');
      return route.fulfill({ contentType: 'image/svg+xml', body: svgLogo(code) });
    }
    if (url.pathname.startsWith('/api/miniapp/')) {
      const key = url.pathname.replace('/api/miniapp', '');
      if (key.endsWith('/client-log')) return route.fulfill({ status: 204 });
      const body = API[key];
      if (body === undefined) return route.fulfill({ status: 404, contentType: 'application/json', body: '{"message":"mock: no data"}' });
      return route.fulfill({ contentType: 'application/json', body: JSON.stringify(body) });
    }
    return route.continue();
  });
  const errors = [];
  page.on('pageerror', (e) => errors.push(e.message));
  await page.goto(base + '/miniapp/index.html', { waitUntil: 'load' });
  await page.waitForTimeout(1500);
  await page.addStyleTag({ content: '.bottom-nav{position:static!important}' });
  const shot = async (name, full = true) => { await page.waitForTimeout(500); await page.screenshot({ path: path.join(OUT, `${name}-${THEME}.png`), fullPage: full }); console.log('shot', name); };
  await shot('1-stats');
  for (const [screen, name] of [['today', '2-today'], ['predict', '3-predict'], ['my', '4-profile']]) {
    await page.click(`.nav-btn[data-screen="${screen}"]`);
    await page.waitForTimeout(1200);
    await shot(name);
  }
  // --- modals & sub-screens ---
  const close = async (sel) => { await page.click(sel).catch(() => {}); await page.waitForTimeout(300); };
  await page.click('.nav-btn[data-screen="today"]'); await page.waitForTimeout(800);
  const items = page.locator('#screen-today .list-item');
  await items.nth(1).click(); await page.waitForTimeout(1500);
  await page.screenshot({ path: path.join(OUT, `5-match-modal-${THEME}.png`) });
  const modalScroll = page.locator('#score-modal .modal-sheet');
  await modalScroll.evaluate((el) => { el.scrollTop = el.scrollHeight; }); await page.waitForTimeout(400);
  await page.screenshot({ path: path.join(OUT, `5b-match-modal-bottom-${THEME}.png`) });
  await page.locator('#modal-recommendation-open').click({ force: true }).catch(() => {}); await page.waitForTimeout(600);
  await page.screenshot({ path: path.join(OUT, `5c-ai-modal-${THEME}.png`) });
  await page.locator('#ai-modal-heatmap-section').scrollIntoViewIfNeeded().catch(() => {});
  await page.locator('.ai-heat-mode[data-mode="chance"]').click().catch(() => {});
  await page.locator('.ai-heatmap-cell[data-h="1"][data-a="1"]').click().catch(() => {}); await page.waitForTimeout(300);
  await page.screenshot({ path: path.join(OUT, `5d-ai-matrix-${THEME}.png`) });
  await close('#ai-modal-close');
  await close('#modal-close');
  await items.nth(3).click(); await page.waitForTimeout(1200);
  await page.screenshot({ path: path.join(OUT, `7-cup-match-modal-${THEME}.png`) });
  await close('#modal-close');
  await page.click('.nav-btn[data-screen="stats"]'); await page.waitForTimeout(1200);
  await page.locator('#home-live-list li').first().click(); await page.waitForTimeout(2000);
  await page.screenshot({ path: path.join(OUT, `6-live-pitch-${THEME}.png`) });
  await page.locator('#live-modal .modal-sheet').evaluate((el) => { el.scrollTop = el.scrollHeight; }); await page.waitForTimeout(400);
  await page.screenshot({ path: path.join(OUT, `6b-live-bottom-${THEME}.png`) });
  await close('#live-modal-close');
  await page.locator('#standings-body tr').nth(2).click(); await page.waitForTimeout(1500);
  await page.screenshot({ path: path.join(OUT, `8-team-modal-${THEME}.png`) });
  await close('#team-modal-close');
  await page.click('.nav-btn[data-screen="predict"]'); await page.waitForTimeout(600);
  await page.locator('#predict-weeks button', { hasText: /^7$/ }).click(); await page.waitForTimeout(1200);
  await page.screenshot({ path: path.join(OUT, `9-predict-week-${THEME}.png`), fullPage: true });
  await close('#predict-back-weeks');
  await page.locator('#predict-weeks button', { hasText: 'Кубки' }).click(); await page.waitForTimeout(800);
  await page.screenshot({ path: path.join(OUT, `10-predict-cups-${THEME}.png`) });
  await page.click('.nav-btn[data-screen="today"]');
  await page.waitForTimeout(800);
  const xss = await page.evaluate(() => window.__xss === 1);
  console.log('xss executed:', xss);
  if (xss) process.exitCode = 1;
  console.log('pageerrors:', errors.length ? errors : 'none');
  await browser.close();
  server.close();
})();
