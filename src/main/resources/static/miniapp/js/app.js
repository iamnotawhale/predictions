(function () {
    const hasTelegramWebApp = !!(window.Telegram && window.Telegram.WebApp);
    const tg = hasTelegramWebApp ? window.Telegram.WebApp : {
        initData: '',
        initDataUnsafe: {},
        themeParams: {},
        ready() {},
        expand() {},
        BackButton: {
            show() {},
            hide() {},
            onClick() {}
        },
        HapticFeedback: {
            impactOccurred() {},
            notificationOccurred() {}
        }
    };
    const isLocalDev = location.hostname === 'localhost' || location.hostname === '127.0.0.1';
    if (tg.initData) {
        try {
            tg.ready();
            tg.expand();
        } catch (_) {
            // no-op
        }
    }

    const CHART_COLORS = ['#2ea6ff', '#5cd97a', '#e85c4a', '#f5c542', '#b07aff', '#ff8ec4'];
    const TODAY_POLL_LIVE_MS = 10000;
    const TODAY_POLL_IDLE_MS = 60000;
    const LIVE_MODAL_POLL_MS = 10000;
    const LIVE_PRESTART_WINDOW_SECONDS = 10 * 60;
    const CACHE_TTL_MS = 45000;
    const FINISHED_STATUSES = new Set(['ft', 'aet', 'pen', 'canc', 'abd', 'awrd', 'wo']);
    const NOT_STARTED_STATUSES = new Set(['ns', 'pst', 'tbd']);

    const state = {
        profile: null,
        currentWeekId: null,
        selectedMatch: null,
        selectedTeamCode: null,
        teamModalOpened: false,
        h2hModalOpened: false,
        predictWeekOpened: false,
        myWeekOpened: false,
        leaderboardMode: '',
        chartLoaded: false,
        chartData: null,
        chartActiveLogin: null,
        chartNodes: [],
        todayLoaded: false,
        todayHasLive: false,
        todaySnapshotInitialized: false,
        todayScoresByMatchId: {},
        scoreNotificationsQueue: [],
        activeScoreNotificationId: null,
        todayPollingTimerId: null,
        liveModalPollingTimerId: null,
        livePitchStatsOpened: false,
        lastLiveEvents: [],
        livePitchHomeColor: '#ffffff',
        livePitchAwayColor: '#ffffff',
        selectedPitchEventKey: null,
        liveEventRuCompiled: null,
        apiCache: {}
    };

    const $ = (sel) => document.querySelector(sel);
    const $$ = (sel) => document.querySelectorAll(sel);

    const MUTATION_RETRY_DELAYS_MS = [0, 600, 1500];

    function reportClientLog(level, event, details) {
        try {
            const payload = JSON.stringify({
                level: level || 'INFO',
                event: event || 'event',
                details: details || '-',
                href: location.href,
                userAgent: navigator.userAgent || '-'
            });
            if (navigator.sendBeacon) {
                const blob = new Blob([payload], { type: 'application/json' });
                navigator.sendBeacon('/api/miniapp/client-log', blob);
                return;
            }
            fetch('/api/miniapp/client-log', {
                method: 'POST',
                keepalive: true,
                headers: {
                    'Content-Type': 'application/json',
                    'X-Telegram-Init-Data': tg.initData || ''
                },
                body: payload
            }).catch(() => {});
        } catch (_) {
            // no-op
        }
    }

    window.addEventListener('error', (e) => {
        const message = e && e.message ? e.message : 'unknown';
        const file = e && e.filename ? e.filename : '-';
        const line = e && Number.isFinite(e.lineno) ? e.lineno : '-';
        const col = e && Number.isFinite(e.colno) ? e.colno : '-';
        const stack = e && e.error && e.error.stack ? e.error.stack : '-';
        reportClientLog('ERROR', 'window.error',
            message + ' @' + file + ':' + line + ':' + col + ' stack=' + stack);
    });
    window.addEventListener('unhandledrejection', (e) => {
        const reason = e && e.reason ? (e.reason.message || String(e.reason)) : 'unknown';
        const stack = e && e.reason && e.reason.stack ? e.reason.stack : '-';
        reportClientLog('ERROR', 'window.unhandledrejection', reason + ' stack=' + stack);
    });

    function headers() {
        return {
            'Content-Type': 'application/json',
            'Accept': 'application/json',
            'X-Telegram-Init-Data': tg.initData || ''
        };
    }

    function sleep(ms) {
        return new Promise((resolve) => setTimeout(resolve, ms));
    }

    function hexToRgba(hex, alpha) {
        if (!hex || typeof hex !== 'string') return 'rgba(255,255,255,' + alpha + ')';
        const normalized = hex.replace('#', '');
        const full = normalized.length === 3
            ? normalized.split('').map(c => c + c).join('')
            : normalized;
        const intVal = Number.parseInt(full, 16);
        if (!Number.isFinite(intVal)) return 'rgba(255,255,255,' + alpha + ')';
        const r = (intVal >> 16) & 255;
        const g = (intVal >> 8) & 255;
        const b = intVal & 255;
        return 'rgba(' + r + ',' + g + ',' + b + ',' + alpha + ')';
    }

    function isRetryableStatus(status) {
        return status === 408 || status === 429 || status === 502 || status === 503 || status === 504;
    }

    class ApiError extends Error {
        constructor(message, status, data) {
            super(message);
            this.name = 'ApiError';
            this.status = status;
            this.data = data;
        }
    }

    async function api(path, options = {}) {
        try {
            const res = await fetch('/api/miniapp' + path, {
                ...options,
                headers: { ...headers(), ...(options.headers || {}) }
            });
            const data = await res.json().catch(() => ({}));
            if (!res.ok) {
                setOfflineBanner(true, data.message || ('Ошибка ' + res.status));
                reportClientLog('ERROR', 'api.error',
                    path + ' status=' + res.status + ' message=' + (data.message || 'n/a'));
                throw new ApiError(data.message || 'Ошибка запроса', res.status, data);
            }
            setOfflineBanner(false);
            return data;
        } catch (e) {
            if (!(e instanceof ApiError)) {
                setOfflineBanner(true, 'Нет связи с сервером');
                reportClientLog('ERROR', 'api.network',
                    path + ' message=' + (e && e.message ? e.message : 'network')
                    + ' online=' + navigator.onLine);
            }
            throw e;
        }
    }

    function setOfflineBanner(show, text) {
        const el = $('#offline-banner');
        if (!el) return;
        if (show) {
            el.textContent = text || 'Нет связи';
            el.classList.remove('hidden');
        } else {
            el.classList.add('hidden');
        }
    }

    function cacheGet(key) {
        const hit = state.apiCache[key];
        if (!hit) return null;
        if (Date.now() - hit.at > CACHE_TTL_MS) {
            delete state.apiCache[key];
            return null;
        }
        return hit.data;
    }

    function cacheSet(key, data) {
        state.apiCache[key] = { at: Date.now(), data };
    }

    function cacheDrop(key) {
        delete state.apiCache[key];
    }

    async function apiCached(path) {
        const cached = cacheGet(path);
        if (cached) return cached;
        const data = await api(path);
        cacheSet(path, data);
        return data;
    }

    async function apiWithRetry(path, options = {}, config = {}) {
        const delays = config.delays || MUTATION_RETRY_DELAYS_MS;
        const maxAttempts = config.retries ?? delays.length;
        let lastError = null;

        for (let attempt = 0; attempt < maxAttempts; attempt++) {
            if (attempt > 0) {
                await sleep(delays[attempt] ?? delays[delays.length - 1]);
            }
            try {
                const res = await fetch('/api/miniapp' + path, {
                    ...options,
                    headers: { ...headers(), ...(options.headers || {}) }
                });
                const data = await res.json().catch(() => ({}));
                if (res.ok) {
                    return data;
                }
                const err = new ApiError(data.message || 'Ошибка запроса', res.status, data);
                if (!isRetryableStatus(res.status) || attempt === maxAttempts - 1) {
                    throw err;
                }
                lastError = err;
            } catch (e) {
                if (e instanceof ApiError) {
                    reportClientLog('ERROR', 'apiWithRetry.apiError', path + ' status=' + e.status + ' message=' + (e.message || 'n/a'));
                    throw e;
                }
                lastError = e;
                if (attempt === maxAttempts - 1) {
                    reportClientLog('ERROR', 'apiWithRetry.network', path + ' message=' + (e && e.message ? e.message : 'network'));
                    throw new ApiError(
                        'Нет связи с сервером. Проверьте интернет и попробуйте снова.',
                        0,
                        null
                    );
                }
            }
        }
        throw lastError || new ApiError('Ошибка запроса', 0, null);
    }

    async function verifyPredictionSaved(match, homeScore, awayScore) {
        try {
            const item = await api('/match/' + encodeURIComponent(match.homeCode) + '/' + encodeURIComponent(match.awayCode));
            return item.hasPrediction
                && item.predictHome === homeScore
                && item.predictAway === awayScore;
        } catch {
            return false;
        }
    }

    function setScoreButtonsDisabled(disabled) {
        $$('#score-modal .score-btn').forEach((btn) => {
            btn.disabled = disabled;
        });
    }

    async function refreshAfterPredictionChange() {
        state.todayLoaded = false;
        const predictBlock = $('#predict-matches');
        if (!predictBlock.classList.contains('hidden') && predictBlock.dataset.weekId) {
            await loadPredictMatches(parseInt(predictBlock.dataset.weekId, 10));
        }
        const myBlock = $('#my-predictions');
        if (!myBlock.classList.contains('hidden') && myBlock.dataset.weekId) {
            await loadMyPredictions(parseInt(myBlock.dataset.weekId, 10));
        }
        if ($('#screen-today').classList.contains('active')) {
            await loadTodayMatches();
        }
        await refreshLeaderboardByMode();
        await loadWeeksGrid('#predict-weeks', showPredictWeek);
        await loadWeeksGrid('#my-weeks', showMyWeek);
    }

    function selectedLeaderboardWeekId() {
        return state.leaderboardMode === 'current' && state.currentWeekId ? state.currentWeekId : null;
    }

    async function refreshLeaderboardByMode() {
        await loadLeaderboard(selectedLeaderboardWeekId());
    }

    function showToast(msg, type) {
        const el = $('#toast');
        el.textContent = msg;
        el.className = 'toast ' + (type || '');
        el.classList.remove('hidden');
        setTimeout(() => el.classList.add('hidden'), 2800);
    }

    function normalizeStatus(status) {
        return (status || '').toLowerCase();
    }

    function isNotStartedStatus(status) {
        return NOT_STARTED_STATUSES.has(normalizeStatus(status));
    }

    function isFinishedStatus(status) {
        return FINISHED_STATUSES.has(normalizeStatus(status));
    }

    function matchScoreLabel(m) {
        if (m.hasPrediction && m.predictHome != null) {
            return m.predictHome + ' : ' + m.predictAway;
        }
        if (m.status === 'ft' && m.homeScore != null) {
            return m.homeScore + ' : ' + m.awayScore;
        }
        return m.kickoff || '—';
    }

    function matchStatusBadge(m) {
        if (m.hasPrediction) return '<span class="badge predicted">прогноз</span>';
        if (m.canPredict) return '<span class="badge">можно</span>';
        if (m.status === 'ft') return '<span class="badge">завершён</span>';
        return '<span class="badge live">' + (m.status || '') + '</span>';
    }

    function todayScoreLabel(m) {
        const hasLiveScore = m.homeScore != null && m.awayScore != null && !isNotStartedStatus(m.status);
        if (hasLiveScore) {
            return m.homeScore + ' : ' + m.awayScore;
        }
        return m.kickoff || '—';
    }

    function todayStatusBadge(m) {
        if (isFinishedStatus(m.status)) return '<span class="badge">завершён</span>';
        if (m.homeScore != null && m.awayScore != null && !isNotStartedStatus(m.status)) {
            return '<span class="badge live">' + (m.status || 'live') + '</span>';
        }
        if (m.canPredict) return '<span class="badge">можно</span>';
        if (m.hasPrediction) return '<span class="badge predicted">прогноз</span>';
        return '<span class="badge">' + (m.status || 'ожидание') + '</span>';
    }

    function renderMatchItem(m, onClick) {
        const li = document.createElement('li');
        li.className = 'list-item';
        li.innerHTML =
            '<div class="list-item-main">' +
            '<div class="list-item-title">' + m.homeCode + ' — ' + m.awayCode + '</div>' +
            '<div class="list-item-sub">' + (m.homeName || '') + ' vs ' + (m.awayName || '') + '</div>' +
            '</div>' +
            '<div class="list-item-meta">' +
            '<div class="score-pill">' + matchScoreLabel(m) + '</div>' +
            matchStatusBadge(m) +
            '</div>';
        if (onClick) li.addEventListener('click', () => onClick(m));
        return li;
    }

    function renderTeamMatchItem(m, onClick) {
        const formClass = teamFormDotClass(m);
        const li = document.createElement('li');
        li.className = 'list-item';
        li.innerHTML =
            '<div class="list-item-main">' +
            '<div class="list-item-title">' + m.homeCode + ' — ' + m.awayCode + '</div>' +
            '<div class="list-item-sub">' + (m.kickoff || '') + ' · ' + (m.weekId || '') + ' тур</div>' +
            '</div>' +
            '<div class="list-item-meta">' +
            '<span class="form-dot ' + formClass + '" aria-hidden="true"></span>' +
            '<div class="score-pill">' + (m.homeScore != null ? (m.homeScore + ' : ' + m.awayScore) : '—') + '</div>' +
            '<span class="badge">' + (m.status || '') + '</span>' +
            '</div>';
        if (onClick) li.addEventListener('click', () => onClick(m));
        return li;
    }

    function teamFormDotClass(match) {
        if (!state.selectedTeamCode) return 'form-dot-neutral';
        if (match.homeScore == null || match.awayScore == null) return 'form-dot-neutral';
        const teamIsHome = match.homeCode === state.selectedTeamCode;
        const teamScore = teamIsHome ? match.homeScore : match.awayScore;
        const oppScore = teamIsHome ? match.awayScore : match.homeScore;
        return formDotClassByScores(teamScore, oppScore);
    }

    function renderTodayMatchItem(m, onClick) {
        const li = document.createElement('li');
        li.className = 'list-item' + (m.hasPrediction ? '' : ' needs-predict');
        li.innerHTML =
            '<div class="list-item-main">' +
            '<div class="list-item-title">' + m.homeCode + ' — ' + m.awayCode + '</div>' +
            '<div class="list-item-sub">' + (m.homeName || '') + ' vs ' + (m.awayName || '') + '</div>' +
            '</div>' +
            '<div class="list-item-meta">' +
            '<div class="score-pill">' + todayScoreLabel(m) + '</div>' +
            todayStatusBadge(m) +
            (!m.hasPrediction && m.canPredict ? '<span class="badge badge-warn">нет прогноза</span>' : '') +
            '</div>';
        if (onClick) li.addEventListener('click', () => onClick(m));
        return li;
    }

    function renderTodayMatchesList(matches) {
        const list = $('#today-match-list');
        list.innerHTML = '';
        if (!matches.length) {
            list.innerHTML = '<li class="empty-state">Сегодня матчей нет</li>';
            return;
        }
        let lastWeek = null;
        matches.forEach(m => {
            if (m.weekId !== lastWeek) {
                lastWeek = m.weekId;
                const label = document.createElement('li');
                label.className = 'week-label';
                label.textContent = m.weekId + ' тур';
                list.appendChild(label);
            }
            list.appendChild(renderTodayMatchItem(m, openScoreModal));
        });
    }

    function isLiveNowMatch(m) {
        return !isFinishedStatus(m.status)
            && !isNotStartedStatus(m.status)
            && m.homeScore != null
            && m.awayScore != null;
    }

    function kickoffSecondsLeftApprox(m) {
        if (!isNotStartedStatus(m.status)) return null;
        if (typeof m.predictSecondsLeft !== 'number') return null;
        // predictSecondsLeft is measured to kickoff + 5m
        return m.predictSecondsLeft - 300;
    }

    function isPreLiveSoonMatch(m) {
        const sec = kickoffSecondsLeftApprox(m);
        return sec != null && sec >= 0 && sec <= LIVE_PRESTART_WINDOW_SECONDS;
    }

    function isLiveModuleMatch(m) {
        return isLiveNowMatch(m) || isPreLiveSoonMatch(m);
    }

    function openLiveModuleMatch(match) {
        if (isLiveNowMatch(match)) {
            openLiveMatchModal(match);
            return;
        }
        openScoreModal(match);
    }

    function renderHomeLiveModule(matches) {
        const liveMatches = (matches || []).filter(isLiveModuleMatch);
        const card = $('#home-live-card');
        const list = $('#home-live-list');
        if (!liveMatches.length) {
            card.classList.add('hidden');
            list.innerHTML = '';
            return;
        }

        card.classList.remove('hidden');
        list.innerHTML = '';
        liveMatches.forEach((m) => list.appendChild(renderTodayMatchItem(m, openLiveModuleMatch)));
    }

    function hasLiveOrSoonMatches(matches) {
        return (matches || []).some(isLiveModuleMatch);
    }

    function enqueueScoreNotification(match, previousScore, highlightTeam) {
        const previousParts = previousScore ? previousScore.split(':') : null;
        state.scoreNotificationsQueue.push({
            id: Date.now() + '-' + Math.random().toString(16).slice(2),
            homeCode: match.homeCode,
            awayCode: match.awayCode,
            homeScore: match.homeScore,
            awayScore: match.awayScore,
            homeLogo: match.homeLogo,
            awayLogo: match.awayLogo,
            prevHomeScore: previousParts ? Number(previousParts[0]) : null,
            prevAwayScore: previousParts ? Number(previousParts[1]) : null,
            highlightTeam
        });
        showNextScoreNotification();
    }

    function showNextScoreNotification() {
        if (state.activeScoreNotificationId || !state.scoreNotificationsQueue.length) {
            return;
        }
        const item = state.scoreNotificationsQueue.shift();
        state.activeScoreNotificationId = item.id;
        const container = $('#score-notifications');
        const alert = document.createElement('div');
        alert.className = 'score-alert';
        alert.dataset.notificationId = item.id;
        const hasPrevScore = Number.isInteger(item.prevHomeScore) && Number.isInteger(item.prevAwayScore);
        const initialHome = hasPrevScore ? item.prevHomeScore : item.homeScore;
        const initialAway = hasPrevScore ? item.prevAwayScore : item.awayScore;
        alert.innerHTML =
            '<div class="score-alert-body">' +
            '<img class="score-alert-logo" src="' + (item.homeLogo || '') + '" alt="' + item.homeCode + '" onerror="this.style.visibility=\'hidden\'">' +
            '<div class="score-alert-center">' +
            '<span class="score-alert-code score-alert-home-code">' + item.homeCode + '</span>' +
            '<span class="score-alert-score">' + initialHome + '-' + initialAway + '</span>' +
            '<span class="score-alert-code score-alert-away-code">' + item.awayCode + '</span>' +
            '</div>' +
            '<img class="score-alert-logo" src="' + (item.awayLogo || '') + '" alt="' + item.awayCode + '" onerror="this.style.visibility=\'hidden\'">' +
            '</div>' +
            '<button class="score-alert-close" aria-label="Закрыть">&#10005;</button>';

        const close = () => {
            if (alert.dataset.closing === '1') return;
            alert.dataset.closing = '1';
            alert.classList.add('closing');
            setTimeout(() => {
                alert.remove();
                if (state.activeScoreNotificationId === item.id) {
                    state.activeScoreNotificationId = null;
                }
                showNextScoreNotification();
            }, 320);
        };

        alert.querySelector('.score-alert-close').addEventListener('click', close);
        container.appendChild(alert);
        tg.HapticFeedback?.impactOccurred('medium');

        if (hasPrevScore) {
            setTimeout(() => {
                const scoreEl = alert.querySelector('.score-alert-score');
                const homeCodeEl = alert.querySelector('.score-alert-home-code');
                const awayCodeEl = alert.querySelector('.score-alert-away-code');
                if (!scoreEl || !homeCodeEl || !awayCodeEl) return;
                scoreEl.textContent = item.homeScore + '-' + item.awayScore;
                if (item.highlightTeam === 'home' || item.highlightTeam === 'both') {
                    homeCodeEl.classList.add('goal-flash');
                }
                if (item.highlightTeam === 'away' || item.highlightTeam === 'both') {
                    awayCodeEl.classList.add('goal-flash');
                }
            }, 420);
        }

        setTimeout(close, 6000);
    }

    function detectGoalHighlightTeam(previousScore, nextScore) {
        if (!previousScore || !nextScore) return null;
        const [prevHome, prevAway] = previousScore.split(':').map(Number);
        const [nextHome, nextAway] = nextScore.split(':').map(Number);
        if (Number.isNaN(prevHome) || Number.isNaN(prevAway) || Number.isNaN(nextHome) || Number.isNaN(nextAway)) {
            return null;
        }
        const homeScored = nextHome > prevHome;
        const awayScored = nextAway > prevAway;
        if (homeScored && awayScored) return 'both';
        if (homeScored) return 'home';
        if (awayScored) return 'away';
        return null;
    }

    function processTodayScoreUpdates(matches, previousScoresByMatchId) {
        const nextScores = {};
        matches.forEach((m) => {
            const hasScore = m.homeScore != null && m.awayScore != null && !isNotStartedStatus(m.status);
            if (!hasScore) return;

            const score = m.homeScore + ':' + m.awayScore;
            const prev = previousScoresByMatchId[m.publicId];
            nextScores[m.publicId] = score;

            if (!state.todaySnapshotInitialized) {
                return;
            }

            if (prev == null) {
                if (score !== '0:0') {
                    enqueueScoreNotification(m, '0:0', 'both');
                }
                return;
            }

            if (prev !== score) {
                enqueueScoreNotification(m, prev, detectGoalHighlightTeam(prev, score) || 'both');
            }
        });
        state.todayScoresByMatchId = nextScores;
        state.todaySnapshotInitialized = true;
    }

    async function loadProfile() {
        const profile = await api('/profile');
        state.profile = profile;
        state.currentWeekId = profile.currentWeekId;
        const user = tg.initDataUnsafe?.user;
        const name = user ? (user.first_name + (user.last_name ? ' ' + user.last_name : '')) : profile.login;
        $('#user-greeting').textContent = name + ' · ' + (profile.weekLabel || ('тур ' + profile.currentWeekId));
    }

    async function loadLeaderboard(weekId) {
        const path = weekId != null ? '/leaderboard?weekId=' + weekId : '/leaderboard';
        const data = await api(path);
        $('#leaderboard-title').textContent = data.title;
        const list = $('#leaderboard-list');
        list.innerHTML = '';
        if (!data.entries.length) {
            list.innerHTML = '<li class="empty-state">Нет данных</li>';
            return;
        }
        data.entries.forEach((e, i) => {
            const li = document.createElement('li');
            li.className = 'list-item';
            li.style.cursor = 'default';
            const shownPoints = e.provisionalPoints != null ? e.provisionalPoints : e.points;
            const sub = e.provisionalPoints != null && data.liveActive
                ? '<div class="list-item-sub">база ' + e.points + ' · live ' + (e.liveDelta >= 0 ? '+' : '') + e.liveDelta + '</div>'
                : '';
            li.innerHTML =
                '<span class="rank">' + (i + 1) + '</span>' +
                '<div class="list-item-main"><div class="list-item-title">' + e.login.toUpperCase() + '</div>' + sub + '</div>' +
                '<span class="pts">' + shownPoints + '</span>';
            list.appendChild(li);
        });
    }

    async function loadTodayMatches() {
        const data = await api('/today');
        state.todayHasLive = hasLiveOrSoonMatches(data.matches) || !!data.hasLive;
        const previousScores = { ...state.todayScoresByMatchId };
        processTodayScoreUpdates(data.matches, previousScores);
        renderHomeLiveModule(data.matches);
        renderTodayMatchesList(data.matches);
        await refreshLeaderboardByMode();
        state.todayLoaded = true;
        scheduleTodayPolling();
    }

    async function pollTodayMatchesForUpdates() {
        const data = await api('/today');
        state.todayHasLive = hasLiveOrSoonMatches(data.matches) || !!data.hasLive;
        const previousScores = { ...state.todayScoresByMatchId };
        processTodayScoreUpdates(data.matches, previousScores);
        renderHomeLiveModule(data.matches);
        if ($('#screen-today').classList.contains('active')) {
            renderTodayMatchesList(data.matches);
            state.todayLoaded = true;
        }
        await refreshLeaderboardByMode();
        if ($('#screen-stats').classList.contains('active')) {
            await Promise.all([
                loadStandings(true),
                loadPointsChart(true)
            ]);
        }
        const predictBlock = $('#predict-matches');
        if (!predictBlock.classList.contains('hidden') && predictBlock.dataset.weekId) {
            await loadPredictMatches(parseInt(predictBlock.dataset.weekId, 10));
        }
        const myBlock = $('#my-predictions');
        if (!myBlock.classList.contains('hidden') && myBlock.dataset.weekId) {
            await loadMyPredictions(parseInt(myBlock.dataset.weekId, 10));
        }
        scheduleTodayPolling();
    }

    async function loadHomeLiveModule() {
        const data = await api('/today');
        state.todayHasLive = hasLiveOrSoonMatches(data.matches) || !!data.hasLive;
        renderHomeLiveModule(data.matches);
    }

    function scheduleTodayPolling() {
        if (state.todayPollingTimerId) {
            clearTimeout(state.todayPollingTimerId);
        }
        const delay = state.todayHasLive ? TODAY_POLL_LIVE_MS : TODAY_POLL_IDLE_MS;
        state.todayPollingTimerId = setTimeout(() => {
            pollTodayMatchesForUpdates().catch(() => scheduleTodayPolling());
        }, delay);
    }

    function startTodayPolling() {
        pollTodayMatchesForUpdates().catch(() => {});
    }

    function drawPointsChart(data) {
        const canvas = $('#points-chart');
        const ctx = canvas.getContext('2d');
        state.chartData = data;
        const dpr = window.devicePixelRatio || 1;
        const width = canvas.parentElement.clientWidth || 320;
        const height = 200;
        canvas.width = width * dpr;
        canvas.height = height * dpr;
        canvas.style.width = width + 'px';
        canvas.style.height = height + 'px';
        ctx.scale(dpr, dpr);

        const pad = { top: 12, right: 12, bottom: 28, left: 36 };
        const plotW = width - pad.left - pad.right;
        const plotH = height - pad.top - pad.bottom;

        ctx.clearRect(0, 0, width, height);

        if (!data.series.length || !data.weeks.length) {
            ctx.fillStyle = '#8b98a5';
            ctx.font = '14px sans-serif';
            ctx.fillText('Нет данных', pad.left, height / 2);
            $('#chart-legend').innerHTML = '';
            return;
        }

        if (state.chartActiveLogin && !data.series.some(s => s.login === state.chartActiveLogin)) {
            state.chartActiveLogin = null;
        }

        let maxY = Number.NEGATIVE_INFINITY;
        let minY = Number.POSITIVE_INFINITY;
        data.series.forEach(s => {
            s.points.forEach(p => {
                if (p == null) return;
                maxY = Math.max(maxY, p);
                minY = Math.min(minY, p);
            });
        });
        if (!Number.isFinite(maxY) || !Number.isFinite(minY)) {
            ctx.fillStyle = '#8b98a5';
            ctx.font = '14px sans-serif';
            ctx.fillText('Нет данных', pad.left, height / 2);
            return;
        }
        minY = Math.min(minY, 0);
        maxY = Math.max(maxY, 4);
        if (maxY <= minY) {
            maxY = minY + 1;
        }
        const yRange = maxY - minY;

        const weekCount = data.weeks.length;
        const xAt = (i) => pad.left + (weekCount <= 1 ? plotW / 2 : (i / (weekCount - 1)) * plotW);
        const yAt = (v) => pad.top + plotH - ((v - minY) / yRange) * plotH;

        const tickMin = Math.floor(minY);
        const tickMax = Math.ceil(maxY);
        ctx.strokeStyle = 'rgba(255,255,255,0.1)';
        ctx.lineWidth = 1;
        for (let tick = tickMin; tick <= tickMax; tick++) {
            const y = yAt(tick);
            ctx.beginPath();
            ctx.moveTo(pad.left, y);
            ctx.lineTo(pad.left + plotW, y);
            ctx.stroke();
            ctx.fillStyle = '#8b98a5';
            ctx.font = '10px sans-serif';
            ctx.textAlign = 'right';
            ctx.fillText(String(tick), pad.left - 6, y + 3);
        }

        ctx.fillStyle = '#8b98a5';
        ctx.font = '10px sans-serif';
        ctx.textAlign = 'center';
        data.weeks.forEach((w, i) => {
            if (weekCount > 12 && i % 2 !== 0) return;
            ctx.fillText(String(w), xAt(i), height - 8);
        });

        const hasActiveSeries = !!state.chartActiveLogin;
        const nodes = [];
        data.series.forEach((s, si) => {
            const color = CHART_COLORS[si % CHART_COLORS.length];
            const isActive = !hasActiveSeries || s.login === state.chartActiveLogin;
            ctx.strokeStyle = isActive ? color : hexToRgba(color, 0.22);
            ctx.fillStyle = isActive ? color : hexToRgba(color, 0.28);
            ctx.lineWidth = isActive ? 2.4 : 1.6;
            let started = false;
            ctx.beginPath();
            s.points.forEach((p, i) => {
                if (p == null) {
                    started = false;
                    return;
                }
                const x = xAt(i);
                const y = yAt(p);
                if (!started) {
                    ctx.moveTo(x, y);
                    started = true;
                } else {
                    ctx.lineTo(x, y);
                }
            });
            ctx.stroke();
            s.points.forEach((p, i) => {
                if (p == null) return;
                const x = xAt(i);
                const y = yAt(p);
                ctx.beginPath();
                ctx.arc(x, y, isActive ? 3.4 : 2.6, 0, Math.PI * 2);
                ctx.fill();
                nodes.push({
                    x: x,
                    y: y,
                    login: s.login,
                    week: data.weeks[i],
                    points: p,
                    color: color
                });
            });
        });
        state.chartNodes = nodes;

        const legend = $('#chart-legend');
        legend.innerHTML = '';
        data.series.forEach((s, si) => {
            const li = document.createElement('li');
            const isActive = !hasActiveSeries || s.login === state.chartActiveLogin;
            li.className = isActive ? 'active' : 'muted';
            li.innerHTML =
                '<span class="dot" style="background:' + CHART_COLORS[si % CHART_COLORS.length] + '"></span>' +
                s.login.toUpperCase();
            li.addEventListener('click', () => {
                state.chartActiveLogin = (state.chartActiveLogin === s.login) ? null : s.login;
                hideChartTooltip();
                drawPointsChart(state.chartData);
            });
            legend.appendChild(li);
        });

        canvas.onclick = (e) => {
            const rect = canvas.getBoundingClientRect();
            const x = e.clientX - rect.left;
            const y = e.clientY - rect.top;
            const nearest = findNearestChartNode(x, y);
            if (!nearest) {
                hideChartTooltip();
                return;
            }
            showChartTooltip(nearest, width);
        };
        state.chartLoaded = true;
    }

    function findNearestChartNode(x, y) {
        let nearest = null;
        let bestDistSq = Number.POSITIVE_INFINITY;
        state.chartNodes.forEach(n => {
            if (state.chartActiveLogin && n.login !== state.chartActiveLogin) return;
            const dx = n.x - x;
            const dy = n.y - y;
            const distSq = dx * dx + dy * dy;
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                nearest = n;
            }
        });
        return bestDistSq <= 196 ? nearest : null;
    }

    function hideChartTooltip() {
        const tooltip = $('#chart-tooltip');
        if (tooltip) {
            tooltip.classList.remove('visible');
        }
    }

    function showChartTooltip(node, chartWidth) {
        const wrap = $('.chart-wrap');
        if (!wrap) return;
        let tooltip = $('#chart-tooltip');
        if (!tooltip) {
            tooltip = document.createElement('div');
            tooltip.id = 'chart-tooltip';
            tooltip.className = 'chart-tooltip';
            wrap.appendChild(tooltip);
        }
        tooltip.textContent = node.login.toUpperCase() + ' · тур ' + node.week + ': ' + node.points;
        tooltip.style.borderColor = node.color;
        tooltip.classList.add('visible');
        const tooltipWidth = Math.min(180, chartWidth - 12);
        tooltip.style.maxWidth = tooltipWidth + 'px';
        const actualWidth = tooltip.offsetWidth || 140;
        const top = Math.max(6, node.y - 34);
        const left = Math.min(Math.max(6, node.x - Math.round(actualWidth / 2)), chartWidth - actualWidth - 6);
        tooltip.style.left = left + 'px';
        tooltip.style.top = top + 'px';
    }

    async function loadPointsChart(forceFresh = false) {
        if (forceFresh) {
            cacheDrop('/chart');
        }
        const data = forceFresh ? await api('/chart') : await apiCached('/chart');
        drawPointsChart(data);
    }

    async function loadStandings(forceFresh = false) {
        if (forceFresh) {
            cacheDrop('/standings');
        }
        const rows = forceFresh ? await api('/standings') : await apiCached('/standings');
        const tbody = $('#standings-body');
        tbody.innerHTML = '';
        rows.forEach(r => {
            const tr = document.createElement('tr');
            tr.style.cursor = 'pointer';
            tr.innerHTML =
                '<td>' + r.place + '</td>' +
                '<td class="club-cell">' +
                '<img class="club-logo" src="' + (r.logo || '') + '" alt="' + (r.code || '') + '" onerror="this.style.visibility=\'hidden\'">' +
                '<strong>' + r.code + '</strong>' +
                '</td>' +
                '<td>' + r.played + '</td>' +
                '<td>' + r.won + '</td>' +
                '<td>' + r.drawn + '</td>' +
                '<td>' + r.goalsFor + '</td>' +
                '<td>' + r.goalsAgainst + '</td>' +
                '<td>' + r.points + '</td>';
            tr.addEventListener('click', () => openTeamModal(r.code));
            tbody.appendChild(tr);
        });
    }

    async function loadWeeksGrid(containerId, onSelect) {
        const weeks = await api('/weeks');
        const container = $(containerId);
        container.innerHTML = '';
        weeks.forEach(w => {
            const btn = document.createElement('button');
            btn.className = 'week-btn' + (w.hasPredictions ? ' has-predictions' : '');
            btn.textContent = w.id;
            btn.addEventListener('click', () => onSelect(w.id));
            container.appendChild(btn);
        });
    }

    async function loadPredictMatches(weekId) {
        const matches = await api('/weeks/' + weekId + '/matches');
        $('#predict-week-title').textContent = weekId + ' тур';
        const list = $('#predict-match-list');
        list.innerHTML = '';
        if (!matches.length) {
            list.innerHTML = '<li class="empty-state">Матчей нет</li>';
            return;
        }
        matches.forEach(m => list.appendChild(renderMatchItem(m, openScoreModal)));
    }

    async function loadMyPredictions(weekId) {
        const matches = await api('/weeks/' + weekId + '/my-predictions');
        $('#my-week-title').textContent = 'Прогнозы · тур ' + weekId;
        const list = $('#my-prediction-list');
        list.innerHTML = '';
        if (!matches.length) {
            list.innerHTML = '<li class="empty-state">Прогнозов нет</li>';
        } else {
            matches.forEach(m => list.appendChild(renderMatchItem(m, openScoreModal)));
        }
        await loadWeekReview(weekId);
    }

    async function loadWeekReview(weekId) {
        const card = $('#my-review-card');
        const list = $('#my-review-list');
        const title = $('#my-review-title');
        if (!card || !list) return;
        try {
            const data = await api('/weeks/' + weekId + '/review');
            card.classList.remove('hidden');
            title.textContent = 'Разбор тура · ' + data.totalPoints + ' очк.';
            list.innerHTML = '';
            if (!data.items.length) {
                list.innerHTML = '<li class="empty-state">Нет матчей</li>';
                return;
            }
            data.items.forEach((item) => {
                const li = document.createElement('li');
                li.className = 'list-item';
                li.style.cursor = 'default';
                const actual = (item.homeScore ?? '-') + ':' + (item.awayScore ?? '-');
                const pred = item.hasPrediction
                    ? ((item.predictHome ?? '-') + ':' + (item.predictAway ?? '-'))
                    : '—';
                const pts = item.points == null ? '—' : String(item.points);
                li.innerHTML =
                    '<div class="list-item-main">' +
                    '<div class="list-item-title">' + item.homeCode + ' — ' + item.awayCode + '</div>' +
                    '<div class="list-item-sub">факт ' + actual + ' · прогноз ' + pred + '</div>' +
                    '</div>' +
                    '<span class="pts">' + pts + '</span>';
                list.appendChild(li);
            });
        } catch (_) {
            card.classList.add('hidden');
        }
    }

    function openScoreModal(match) {
        closeLiveMatchModal(false);
        stopLiveMatchModalPolling();
        state.selectedMatch = match;
        renderH2hList('#modal-h2h-content', []);
        renderTeamFormDots('#modal-home-form', match.homeCode, []);
        renderTeamFormDots('#modal-away-form', match.awayCode, []);
        renderModalNews([]);
        $('#score-grid').classList.remove('hidden');
        $('#modal-h2h-section').classList.remove('hidden');
        $('#modal-news-section').classList.remove('hidden');
        setModalCenterRegular(match);
        $('#modal-home-code').textContent = match.homeCode || 'HOME';
        $('#modal-away-code').textContent = match.awayCode || 'AWAY';
        const homeLogo = $('#modal-home-logo');
        const awayLogo = $('#modal-away-logo');
        homeLogo.src = match.homeLogo || '';
        awayLogo.src = match.awayLogo || '';
        homeLogo.onerror = () => { homeLogo.style.visibility = 'hidden'; };
        awayLogo.onerror = () => { awayLogo.style.visibility = 'hidden'; };
        homeLogo.style.visibility = 'visible';
        awayLogo.style.visibility = 'visible';
        const oddsBlock = $('#modal-odds');
        if (match.oddHome != null && match.oddDraw != null && match.oddAway != null) {
            $('#odd-home').textContent = Number(match.oddHome).toFixed(2);
            $('#odd-draw').textContent = Number(match.oddDraw).toFixed(2);
            $('#odd-away').textContent = Number(match.oddAway).toFixed(2);
            oddsBlock.classList.remove('hidden');
        } else {
            oddsBlock.classList.add('hidden');
        }
        const grid = $('#score-grid');
        grid.innerHTML = '';
        const deleteBtn = $('#modal-delete');

        if (!match.canPredict) {
            deleteBtn.classList.add('hidden');
            grid.innerHTML = '<p class="empty-state">Прогноз недоступен</p>';
            $('#score-modal').classList.remove('hidden');
            loadScoreModalH2h(match).catch(() => {});
            loadScoreModalInsights(match).catch(() => {});
            return;
        }

        deleteBtn.classList.toggle('hidden', !match.hasPrediction);

        for (let h = 0; h <= 5; h++) {
            for (let a = 0; a <= 5; a++) {
                const btn = document.createElement('button');
                btn.className = 'score-btn';
                btn.textContent = h + ':' + a;
                if (match.predictHome === h && match.predictAway === a) {
                    btn.classList.add('selected');
                }
                btn.addEventListener('click', () => savePrediction(match, h, a));
                grid.appendChild(btn);
            }
        }
        $('#score-modal').classList.remove('hidden');
        tg.BackButton.show();
        loadScoreModalH2h(match).catch(() => {});
        loadScoreModalInsights(match).catch(() => {});
    }

    function isLiveModalOpen() {
        const modal = $('#live-modal');
        return !!(modal && !modal.classList.contains('hidden'));
    }

    function openLiveMatchModal(match) {
        closeScoreModal(false);
        stopLiveMatchModalPolling();
        hideLivePitchStatsOverlay(false);
        state.lastLiveEvents = [];
        state.selectedPitchEventKey = null;
        resetLivePitchMarker();
        state.selectedMatch = match;
        setLiveModalHeader(match);
        renderLiveMatchDetailsPlaceholder(match);
        $('#live-modal').classList.remove('hidden');
        tg.BackButton.show();
        loadLiveMatchDetails(match).catch(() => {});
        scheduleLiveMatchModalPolling();
    }

    function setModalCenterRegular(match) {
        const center = $('#modal-center-main');
        if (center) {
            center.textContent = 'VS';
        }
        $('#modal-kickoff').textContent = match.kickoff || '';
    }

    function setLiveModalHeader(match) {
        $('#live-modal-home-code').textContent = match.homeCode || 'HOME';
        $('#live-modal-away-code').textContent = match.awayCode || 'AWAY';
        const homeLogo = $('#live-modal-home-logo');
        const awayLogo = $('#live-modal-away-logo');
        if (homeLogo) {
            homeLogo.src = match.homeLogo || '';
            homeLogo.onerror = () => { homeLogo.style.visibility = 'hidden'; };
            homeLogo.style.visibility = 'visible';
        }
        if (awayLogo) {
            awayLogo.src = match.awayLogo || '';
            awayLogo.onerror = () => { awayLogo.style.visibility = 'hidden'; };
            awayLogo.style.visibility = 'visible';
        }
        const center = $('#live-modal-center-main');
        const hasScore = match.homeScore != null && match.awayScore != null;
        if (center) {
            center.textContent = hasScore ? (match.homeScore + ':' + match.awayScore) : 'LIVE';
        }
        const clock = $('#live-modal-clock');
        if (clock) {
            clock.textContent = ((match.status || '').trim()) || (match.kickoff || '');
        }
    }

    function renderLiveMatchDetailsPlaceholder(match) {
        $('#modal-live-home-title').textContent = (match.homeCode || 'HOME') + ' · состав';
        $('#modal-live-away-title').textContent = (match.awayCode || 'AWAY') + ' · состав';
        $('#modal-live-home-lineup').innerHTML = '<p class="empty-state">Загрузка…</p>';
        $('#modal-live-away-lineup').innerHTML = '<p class="empty-state">Загрузка…</p>';
        $('#modal-live-events').innerHTML = '<p class="empty-state">Загрузка…</p>';
        state.lastLiveEvents = [];
        state.selectedPitchEventKey = null;
        renderLivePitchStats([], match);
        resetLivePitchMarker();
    }

    async function loadLiveMatchDetails(match) {
        const modalMatchId = match.publicId;
        const data = await api('/match/' + encodeURIComponent(match.homeCode) + '/' + encodeURIComponent(match.awayCode) + '/live-details');
        if (!state.selectedMatch || state.selectedMatch.publicId !== modalMatchId) {
            return;
        }
        if (!data.live) {
            $('#modal-live-home-lineup').innerHTML = '<p class="empty-state">Матч уже не в live</p>';
            $('#modal-live-away-lineup').innerHTML = '<p class="empty-state">—</p>';
            $('#modal-live-events').innerHTML = '<p class="empty-state">—</p>';
            resetLivePitchMarker();
            stopLiveMatchModalPolling();
            return;
        }
        if (data.homeScore != null || data.awayScore != null || data.status) {
            state.selectedMatch = Object.assign({}, state.selectedMatch, {
                homeScore: data.homeScore != null ? data.homeScore : state.selectedMatch.homeScore,
                awayScore: data.awayScore != null ? data.awayScore : state.selectedMatch.awayScore,
                status: data.status || state.selectedMatch.status
            });
            setLiveModalHeader(state.selectedMatch);
            syncTodayMatchScores(state.selectedMatch);
        }
        renderLiveLineup('#modal-live-home-lineup', data.homeLineup || []);
        renderLiveLineup('#modal-live-away-lineup', data.awayLineup || []);
        state.livePitchHomeColor = data.homeColor || '#ffffff';
        state.livePitchAwayColor = data.awayColor || '#ffffff';
        renderLiveEvents(data.events || [], state.selectedMatch);
        renderLivePitchStats(data.matchStats || [], state.selectedMatch);
    }

    function syncTodayMatchScores(match) {
        if (!match || match.publicId == null) return;
        if (match.homeScore == null || match.awayScore == null) return;
        if (isNotStartedStatus(match.status)) return;
        state.todayScoresByMatchId[match.publicId] = match.homeScore + ':' + match.awayScore;
    }

    function renderLivePitchStats(stats, match) {
        const overlay = $('#live-pitch-stats-overlay');
        const body = $('#live-pitch-stats-body');
        if (!overlay || !body) return;
        const homeCodeEl = $('#live-pitch-stats-home-code');
        const awayCodeEl = $('#live-pitch-stats-away-code');
        const homeLogoEl = $('#live-pitch-stats-home-logo');
        const awayLogoEl = $('#live-pitch-stats-away-logo');
        if (homeCodeEl) homeCodeEl.textContent = match?.homeCode || 'HOME';
        if (awayCodeEl) awayCodeEl.textContent = match?.awayCode || 'AWAY';
        if (homeLogoEl) {
            homeLogoEl.src = match?.homeLogo || '';
            homeLogoEl.onerror = () => { homeLogoEl.style.visibility = 'hidden'; };
            homeLogoEl.style.visibility = 'visible';
        }
        if (awayLogoEl) {
            awayLogoEl.src = match?.awayLogo || '';
            awayLogoEl.onerror = () => { awayLogoEl.style.visibility = 'hidden'; };
            awayLogoEl.style.visibility = 'visible';
        }
        if (!stats.length) {
            body.innerHTML = '<div class="live-pitch-stats-row"><span class="home">—</span><span class="label">нет данных</span><span class="away">—</span></div>';
        } else {
            body.innerHTML = '';
            stats.forEach((item) => {
                const row = document.createElement('div');
                row.className = 'live-pitch-stats-row';
                row.innerHTML =
                    '<span class="home">' + (item.homeValue ?? '—') + '</span>' +
                    '<span class="label">' + (item.label || '') + '</span>' +
                    '<span class="away">' + (item.awayValue ?? '—') + '</span>';
                body.appendChild(row);
            });
        }
        if (state.livePitchStatsOpened) {
            overlay.classList.remove('hidden');
        } else {
            overlay.classList.add('hidden');
        }
    }

    function showLivePitchStatsOverlay() {
        const overlay = $('#live-pitch-stats-overlay');
        if (!overlay) return;
        state.livePitchStatsOpened = true;
        overlay.classList.remove('hidden');
        const hint = $('#live-pitch-stats-hint');
        if (hint) hint.classList.add('hidden');
        resetLivePitchMarker();
    }

    function hideLivePitchStatsOverlay(restoreMarker) {
        state.livePitchStatsOpened = false;
        const overlay = $('#live-pitch-stats-overlay');
        if (overlay) {
            overlay.classList.add('hidden');
        }
        const hint = $('#live-pitch-stats-hint');
        if (hint && isLiveModalOpen()) {
            hint.classList.remove('hidden');
        }
        if (restoreMarker !== false && isLiveModalOpen()) {
            applyLivePitchMarkerFromEvents(state.lastLiveEvents, state.selectedMatch);
        }
    }

    function scheduleLiveMatchModalPolling() {
        stopLiveMatchModalPolling();
        state.liveModalPollingTimerId = setTimeout(async () => {
            const active = state.selectedMatch && isLiveModalOpen();
            if (!active) {
                stopLiveMatchModalPolling();
                return;
            }
            try {
                await loadLiveMatchDetails(state.selectedMatch);
            } catch (_) {
                reportClientLog('WARN', 'live.poll.failed', 'live-details request failed');
            }
            scheduleLiveMatchModalPolling();
        }, LIVE_MODAL_POLL_MS);
    }

    function stopLiveMatchModalPolling() {
        if (!state.liveModalPollingTimerId) return;
        clearTimeout(state.liveModalPollingTimerId);
        state.liveModalPollingTimerId = null;
    }

    function renderLiveLineup(selector, lineup) {
        const container = $(selector);
        if (!container) return;
        if (!lineup.length) {
            container.innerHTML = '<p class="empty-state">Состав пока недоступен</p>';
            return;
        }
        container.innerHTML = '';
        lineup.forEach((p) => {
            const row = document.createElement('div');
            row.className = 'h2h-item';
            row.innerHTML =
                '<div class="h2h-item-head">' +
                '<span>#' + (p.number || '-') + '</span>' +
                '<span>' + (p.position || '') + '</span>' +
                '</div>' +
                '<div class="h2h-item-score">' + (p.name || '—') + '</div>';
            container.appendChild(row);
        });
    }

    function parseLiveEventMinute(minute) {
        const raw = (minute || '').trim();
        if (!raw) return 0;
        const match = raw.match(/^(\d+)/);
        if (!match) return 0;
        return parseInt(match[1], 10);
    }

    function hasSecondHalfActivity(events, match) {
        if (parseLiveEventMinute(match?.status) >= 46) return true;
        if (!events || !events.length) return false;
        return events.some((e) => {
            const text = (e.text || '').trim();
            if (text.includes('Second Half begins') || text.startsWith('Начался второй тайм')) {
                return true;
            }
            return parseLiveEventMinute(e.minute) >= 46;
        });
    }

    function isMatchHalftimeBreak(match, events) {
        const status = normalizeStatus(match?.status);
        if (status === 'ht') return true;
        const rawStatus = (match?.status || '').trim();
        if (/^ht$/i.test(rawStatus)) return true;

        if (hasSecondHalfActivity(events, match)) return false;

        if (!events || !events.length) return false;
        let firstHalfEnded = false;
        let secondHalfStarted = false;
        events.forEach((e) => {
            const text = (e.text || '').trim();
            if (text.includes('First Half ends') || text.startsWith('Перерыв,')) {
                firstHalfEnded = true;
            }
            if (text.includes('Second Half begins') || text.startsWith('Начался второй тайм')) {
                secondHalfStarted = true;
            }
        });
        return firstHalfEnded && !secondHalfStarted;
    }

    function isShotEventType(type) {
        const normalized = (type || '').toLowerCase();
        return normalized.includes('goal') || normalized.includes('shot');
    }

    function isBlockedShotType(type) {
        return (type || '').toLowerCase().includes('blocked');
    }

    function isOnTargetShotType(type) {
        const normalized = (type || '').toLowerCase();
        return normalized.includes('shot-on-target') || normalized.includes('save');
    }

    function resolveGoalLineX(event) {
        if (Number.isFinite(event.field2X)) {
            return event.field2X >= 50 ? 100 : 0;
        }
        if (Number.isFinite(event.fieldX)) {
            return event.fieldX >= 50 ? 100 : 0;
        }
        return 100;
    }

    function flipPitchPercent(value) {
        if (!Number.isFinite(value)) return value;
        return 100 - value;
    }

    /**
     * ESPN coords: attack toward x≈100; ends swap at half-time.
     * Flip X when (away XOR second half); flip Y in the complementary cases
     * (home XOR 1st half) so touchlines match our fixed pitch (y=0 = top).
     */
    function resolveEventPeriod(event) {
        const period = Number(event?.period);
        if (Number.isFinite(period) && period > 0) return period;
        const minuteText = String(event?.minute || '');
        const match = minuteText.match(/(\d+)/);
        if (!match) return 1;
        const minute = Number(match[1]);
        return minute > 45 ? 2 : 1;
    }

    function shouldMirrorPitchX(event, match) {
        const away = resolveEventTeamSide(match, event) === 'away';
        const secondHalf = resolveEventPeriod(event) >= 2;
        return away !== secondHalf;
    }

    function mapEventToPitchCoords(event, match) {
        if (!event) return event;
        if (shouldMirrorPitchX(event, match)) {
            return Object.assign({}, event, {
                fieldX: flipPitchPercent(event.fieldX),
                field2X: flipPitchPercent(event.field2X)
            });
        }
        return Object.assign({}, event, {
            fieldY: flipPitchPercent(event.fieldY),
            field2Y: flipPitchPercent(event.field2Y),
            goalPositionY: flipPitchPercent(event.goalPositionY)
        });
    }

    /**
     * Shot points on pitch (after home/away mapping):
     * Blocked: origin → fieldEnd (+ mid marker = blocker).
     * On-target: origin → fieldEnd (= keeper) when present; else → goalmouth.
     * Other: origin → goalmouth / fieldEnd (no mid).
     */
    function resolveShotPoints(event) {
        const hasOrigin = Number.isFinite(event.fieldX) && Number.isFinite(event.fieldY);
        if (!hasOrigin) return null;
        const hasField2 = Number.isFinite(event.field2X) && Number.isFinite(event.field2Y);
        const hasGoalY = Number.isFinite(event.goalPositionY);
        const origin = { x: event.fieldX, y: event.fieldY };
        let mid = null;
        let end = null;

        if (isBlockedShotType(event.type)) {
            if (hasField2) {
                mid = { x: event.field2X, y: event.field2Y };
                end = mid;
            }
            return { origin, mid, end };
        }

        if (isOnTargetShotType(event.type)) {
            // Save / on-target with fieldEnd: trail stops at keeper (do not continue past to goalmouth).
            if (hasField2) {
                mid = { x: event.field2X, y: event.field2Y };
                end = mid;
            } else if (hasGoalY) {
                end = { x: resolveGoalLineX(event), y: event.goalPositionY };
            }
            return { origin, mid, end };
        }

        if (hasGoalY) {
            end = { x: resolveGoalLineX(event), y: event.goalPositionY };
        } else if (hasField2) {
            end = { x: event.field2X, y: event.field2Y };
        }

        return { origin, mid: null, end };
    }

    function resolveEventTeamSide(match, event) {
        const team = (event.teamName || '').trim().toLowerCase();
        const home = (match?.homeName || '').trim().toLowerCase();
        const away = (match?.awayName || '').trim().toLowerCase();
        if (team && away && (team === away || away.includes(team) || team.includes(away))) {
            return 'away';
        }
        if (team && home && (team === home || home.includes(team) || team.includes(home))) {
            return 'home';
        }
        return 'home';
    }

    function resolveEventTeamColor(match, event) {
        return resolveEventTeamSide(match, event) === 'away'
            ? (state.livePitchAwayColor || '#ffffff')
            : (state.livePitchHomeColor || '#ffffff');
    }

    function resolveOppositeTeamColor(match, event) {
        return resolveEventTeamSide(match, event) === 'away'
            ? (state.livePitchHomeColor || '#ffffff')
            : (state.livePitchAwayColor || '#ffffff');
    }

    const PITCH_NON_PLAYER_TOKEN = /^(foul|goal|shot|blocked|save|woodwork|miss|missed|header|penalty|free|kick|card|yellow|red|corner|offside|var|substitution|sub|target|on|off|by|won|shown|awarded)$/i;
    const PITCH_NAME_PARTICLE = /^(van|von|de|da|dos|del|della|di|der|den|la|le)$/i;

    function surnameFromPlayerName(name) {
        const cleaned = String(name || '')
            .replace(/\s*\([^)]*\)\s*/g, ' ')
            .replace(/[.,;:]+$/g, '')
            .trim();
        const parts = cleaned.split(/\s+/).filter(Boolean);
        if (!parts.length) return '';
        if (parts.length >= 2 && PITCH_NAME_PARTICLE.test(parts[parts.length - 2])) {
            const compound = parts.slice(-2).join(' ');
            if (!PITCH_NON_PLAYER_TOKEN.test(parts[parts.length - 1])) {
                return compound;
            }
        }
        const last = parts[parts.length - 1];
        if (PITCH_NON_PLAYER_TOKEN.test(last)) return '';
        return last;
    }

    function pitchPlayerLabel(event) {
        // Prefer ESPN play.participants[0].athlete.displayName from backend.
        const fromParticipant = surnameFromPlayerName(event.playerName || '');
        if (fromParticipant) return fromParticipant;

        // Fallback: shortText like "Dango Ouattara Foul" / full commentary text.
        for (const blob of [event.shortText || '', event.text || '']) {
            let text = String(blob).trim();
            if (!text) continue;
            text = text.replace(/\s*\([^)]*\)\s*/g, ' ').trim();
            const byMatch = text.match(/\bby\s+(.+?)(?:\s*[.!]|$)/i);
            if (byMatch) {
                const name = surnameFromPlayerName(byMatch[1]);
                if (name) return name;
            }
            text = text
                .replace(/\s+(foul|shot(\s+(on|off)\s+target)?|blocked(\s+shot)?|goal|save|woodwork|miss(ed)?|header|penalty|offside).*$/i, '')
                .replace(/^(foul|free\s*kick|offside|corner|goal|penalty|save)\b[\s.:-]*/i, '')
                .trim();
            const name = surnameFromPlayerName(text);
            if (name) return name;
        }
        return '';
    }

    function pitchEventLabel(event) {
        const minute = event.minute || 'live';
        const typeLabel = prettyLiveEventType(event.type);
        const player = pitchPlayerLabel(event);
        if (player) {
            return (minute + ' ' + player + ' · ' + typeLabel).trim();
        }
        return (minute + ' ' + typeLabel).trim();
    }

    function livePitchEventKey(event) {
        return [event.minute || '', event.type || '', event.text || '', event.shortText || ''].join('|');
    }

    function shotTrajectoryStyle(type) {
        const normalized = (type || '').toLowerCase();
        // Goal: solid. All other shot trails (on/off target, block, woodwork): dashed.
        if (normalized.includes('goal') && !normalized.includes('own')) return 'solid';
        if (normalized.includes('blocked')) return 'dashed';
        if (normalized.includes('shot-on-target')) return 'dashed';
        if (normalized.includes('off-target') || normalized.includes('woodwork')) return 'dashed';
        if (normalized.includes('shot') || normalized.includes('save')) return 'dashed';
        return 'solid';
    }

    function shouldShowLivePitchMarker(match, events) {
        // Stats overlay still blocks the marker entirely.
        if (state.livePitchStatsOpened) return false;
        return true;
    }

    function applyLivePitchMarkerFromEvents(events, match) {
        state.lastLiveEvents = events || [];
        if (!shouldShowLivePitchMarker(match, state.lastLiveEvents)) {
            resetLivePitchMarker();
            return;
        }
        let target = null;
        if (state.selectedPitchEventKey) {
            target = state.lastLiveEvents.find((e) => livePitchEventKey(e) === state.selectedPitchEventKey);
            if (!target) {
                state.selectedPitchEventKey = null;
            }
        }
        // At HT: hide auto marker, but keep an explicitly selected event from the list.
        if (!target) {
            if (isMatchHalftimeBreak(match, state.lastLiveEvents)) {
                resetLivePitchMarker();
                return;
            }
            target = state.lastLiveEvents.find((e) => Number.isFinite(e.fieldX) && Number.isFinite(e.fieldY));
        }
        if (target && Number.isFinite(target.fieldX) && Number.isFinite(target.fieldY)) {
            applyLivePitchMarker(target, match);
        } else {
            resetLivePitchMarker();
        }
    }

    function applyLivePitchMarker(event, match) {
        if (!event || !Number.isFinite(event.fieldX) || !Number.isFinite(event.fieldY)) {
            resetLivePitchMarker();
            return;
        }
        const mapped = mapEventToPitchCoords(event, match);
        const teamColor = resolveEventTeamColor(match, event);
        updateLivePitchMarker(mapped, match, teamColor, pitchEventLabel(event));
    }

    function updateLivePitchMarker(event, match, teamColor, label) {
        const marker = $('#live-pitch-marker');
        const midMarker = $('#live-pitch-mid-marker');
        const shotLine = $('#live-pitch-shot-line');
        const shotLine2 = $('#live-pitch-shot-line-2');
        if (!marker) return;

        const pitch = $('#modal-live-pitch');
        const originX = clamp(event.fieldX, 0, 100);
        const originY = clamp(event.fieldY, 0, 100);
        marker.style.left = originX + '%';
        marker.style.top = originY + '%';

        const dot = marker.querySelector('.live-pitch-dot');
        if (dot) {
            dot.style.background = teamColor || '#ffffff';
            dot.style.boxShadow = '0 0 0 2px rgba(0, 0, 0, 0.35), 0 0 8px ' + (teamColor || '#ffffff') + '88';
        }

        const labelEl = $('#live-pitch-marker-label');
        if (labelEl) {
            labelEl.textContent = label || 'live';
        }
        positionLivePitchBadge(marker, pitch, originX, originY);
        marker.classList.remove('hidden');

        const showShotTrail = isShotEventType(event.type);
        const points = showShotTrail ? resolveShotPoints(event) : null;
        const trajectoryStyle = showShotTrail ? shotTrajectoryStyle(event.type) : null;
        const midColor = resolveOppositeTeamColor(match, event);

        const mid = points && points.mid
            ? { x: clamp(points.mid.x, 0, 100), y: clamp(points.mid.y, 0, 100) }
            : null;
        const end = points && points.end
            ? { x: clamp(points.end.x, 0, 100), y: clamp(points.end.y, 0, 100) }
            : null;

        if (shotLine) {
            if (end && trajectoryStyle) {
                shotLine.setAttribute('x1', String(originX));
                shotLine.setAttribute('y1', String(originY));
                shotLine.setAttribute('x2', String(end.x));
                shotLine.setAttribute('y2', String(end.y));
                shotLine.style.stroke = 'rgba(18, 20, 24, 0.88)';
                shotLine.classList.remove('hidden', 'is-solid', 'is-dashed');
                shotLine.classList.add(trajectoryStyle === 'dashed' ? 'is-dashed' : 'is-solid');
            } else {
                shotLine.classList.add('hidden');
            }
        }

        if (shotLine2) shotLine2.classList.add('hidden');

        if (midMarker) {
            const midDot = midMarker.querySelector('.live-pitch-mid-dot');
            if (mid) {
                midMarker.style.left = mid.x + '%';
                midMarker.style.top = mid.y + '%';
                if (midDot) {
                    midDot.style.background = midColor || 'rgba(255, 255, 255, 0.92)';
                    midDot.style.boxShadow = '0 0 0 2px rgba(0, 0, 0, 0.35)';
                }
                midMarker.classList.remove('hidden');
            } else {
                midMarker.classList.add('hidden');
                if (midDot) {
                    midDot.style.background = '';
                    midDot.style.boxShadow = '';
                }
            }
        }
    }

    function resetLivePitchMarker() {
        const marker = $('#live-pitch-marker');
        const midMarker = $('#live-pitch-mid-marker');
        const shotLine = $('#live-pitch-shot-line');
        const shotLine2 = $('#live-pitch-shot-line-2');
        if (marker) {
            marker.classList.add('hidden');
            const meta = marker.querySelector('.live-pitch-marker-meta');
            if (meta) {
                meta.style.left = '';
                meta.style.right = '';
                meta.style.top = '';
                meta.style.bottom = '';
                meta.style.transform = '';
            }
            const dot = marker.querySelector('.live-pitch-dot');
            if (dot) {
                dot.style.background = '';
                dot.style.boxShadow = '';
            }
        }
        if (midMarker) {
            midMarker.classList.add('hidden');
            const midDot = midMarker.querySelector('.live-pitch-mid-dot');
            if (midDot) {
                midDot.style.background = '';
                midDot.style.boxShadow = '';
            }
        }
        if (shotLine) shotLine.classList.add('hidden');
        if (shotLine2) shotLine2.classList.add('hidden');
    }

    function renderLiveEvents(events, match) {
        const container = $('#modal-live-events');
        if (!container) return;
        if (!events.length) {
            container.innerHTML = '<p class="empty-state">Событий пока нет</p>';
            state.lastLiveEvents = [];
            state.selectedPitchEventKey = null;
            resetLivePitchMarker();
            return;
        }
        container.innerHTML = '';
        events.forEach((e) => {
            const row = document.createElement('div');
            row.className = 'h2h-item';
            if (state.selectedPitchEventKey && livePitchEventKey(e) === state.selectedPitchEventKey) {
                row.classList.add('pitch-selected');
            }
            const icon = liveEventIcon(e.type);
            const minute = e.minute ? '<span>' + e.minute + '</span>' : '<span>live</span>';
            const translatedText = translateLiveEventText(e.text || '', e.type);
            row.innerHTML =
                '<div class="h2h-item-head"><span>' + icon + '</span>' + minute + '</div>' +
                '<div class="h2h-item-score">' + translatedText + '</div>';
            if (Number.isFinite(e.fieldX) && Number.isFinite(e.fieldY)) {
                row.classList.add('pitch-selectable');
                row.addEventListener('click', () => {
                    state.selectedPitchEventKey = livePitchEventKey(e);
                    renderLiveEvents(events, match || state.selectedMatch);
                });
            }
            container.appendChild(row);
        });
        applyLivePitchMarkerFromEvents(events, match || state.selectedMatch);
    }

    function prettyLiveEventType(type) {
        return LiveEventRu.prettyLiveEventType(type, state.liveEventRuCompiled);
    }

    async function loadLiveEventTranslations() {
        if (state.liveEventRuCompiled) return;
        state.liveEventRuCompiled = await LiveEventRu.loadLiveEventTranslations('/miniapp/live_event_ru_translation.json');
    }

    function translateLiveEventText(text, type) {
        return LiveEventRu.translateLiveEventText(text, type, state.liveEventRuCompiled);
    }

    function positionLivePitchBadge(marker, pitch, xPercent, yPercent) {
        if (!marker) return;
        const meta = marker.querySelector('.live-pitch-marker-meta');
        if (!meta) return;
        const gap = 12;
        const inUpperHalf = yPercent <= 50;
        const inLeftHalf = xPercent <= 50;
        meta.style.top = inUpperHalf ? (gap + 'px') : 'auto';
        meta.style.bottom = inUpperHalf ? 'auto' : (gap + 'px');
        if (inLeftHalf) {
            meta.style.left = gap + 'px';
            meta.style.right = 'auto';
            meta.style.transform = 'none';
        } else {
            meta.style.left = 'auto';
            meta.style.right = gap + 'px';
            meta.style.transform = 'none';
        }
        if (pitch) {
            const pitchRect = pitch.getBoundingClientRect();
            const maxWidth = Math.max(120, Math.floor(pitchRect.width * 0.42));
            const labelEl = marker.querySelector('.live-pitch-marker-label');
            if (labelEl) {
                labelEl.style.maxWidth = maxWidth + 'px';
            }
        }
    }

    function clamp(value, min, max) {
        const num = Number(value);
        if (!Number.isFinite(num)) return min;
        if (num < min) return min;
        if (num > max) return max;
        return num;
    }

    function liveEventIcon(type) {
        const t = (type || '').toLowerCase();
        if (t.includes('goal')) return '⚽';
        if (t.includes('penalty')) return '🅿️';
        if (t.includes('yellow')) return '🟨';
        if (t.includes('red')) return '🟥';
        if (t.includes('sub')) return '🔁';
        if (t.includes('offside')) return '🏳️';
        if (t.includes('foul')) return '✋';
        if (t.includes('shot-on-target')) return '🎯';
        if (t.includes('shot-blocked')) return '🧱';
        if (t.includes('shot-off-target')) return '↗️';
        if (t.includes('shot-hit-woodwork') || t.includes('woodwork')) return '🪵';
        if (t.includes('corner-awarded')) return '🚩';
        if (t.includes('save')) return '🥅';
        if (t.includes('var')) return '📺';
        if (t.includes('kickoff') || t.includes('period')) return '🕒';
        return '•';
    }

    function closeScoreModal(clearSelection) {
        const modal = $('#score-modal');
        if (modal) modal.classList.add('hidden');
        if (clearSelection !== false) {
            state.selectedMatch = null;
        }
        if (shouldHideBackButton()) {
            tg.BackButton.hide();
        }
    }

    function closeLiveMatchModal(clearSelection) {
        stopLiveMatchModalPolling();
        hideLivePitchStatsOverlay(false);
        resetLivePitchMarker();
        const modal = $('#live-modal');
        if (modal) modal.classList.add('hidden');
        if (clearSelection !== false) {
            state.selectedMatch = null;
        }
        if (shouldHideBackButton()) {
            tg.BackButton.hide();
        }
    }

    function shouldHideBackButton() {
        const scoreHidden = !$('#score-modal') || $('#score-modal').classList.contains('hidden');
        return scoreHidden
            && !isLiveModalOpen()
            && !state.teamModalOpened
            && !state.h2hModalOpened
            && !state.predictWeekOpened
            && !state.myWeekOpened;
    }

    async function loadScoreModalH2h(match) {
        try {
            const items = await api('/h2h/' + encodeURIComponent(match.homeCode) + '/' + encodeURIComponent(match.awayCode));
            renderH2hList('#modal-h2h-content', items);
        } catch (e) {
            renderH2hList('#modal-h2h-content', [], e.message || 'Не удалось загрузить историю');
        }
    }

    async function loadScoreModalInsights(match) {
        const modalMatchId = match.publicId;
        try {
            const insights = await apiCached('/match/' + encodeURIComponent(match.homeCode) + '/' + encodeURIComponent(match.awayCode) + '/insights');
            if (!state.selectedMatch || state.selectedMatch.publicId !== modalMatchId) {
                return;
            }
            renderTeamFormDots('#modal-home-form', match.homeCode, insights.homeForm || []);
            renderTeamFormDots('#modal-away-form', match.awayCode, insights.awayForm || []);
            renderModalNews(insights.news || []);
        } catch (_) {
            if (!state.selectedMatch || state.selectedMatch.publicId !== modalMatchId) {
                return;
            }
            renderTeamFormDots('#modal-home-form', match.homeCode, []);
            renderTeamFormDots('#modal-away-form', match.awayCode, []);
            renderModalNews([]);
        }
    }

    function renderTeamFormDots(containerSelector, teamCode, items) {
        const container = $(containerSelector);
        if (!container) return;
        container.innerHTML = '';
        if (!items || !items.length) {
            container.innerHTML = '<span class="team-form-empty">—</span>';
            return;
        }
        items.forEach((item) => {
            const btn = document.createElement('button');
            btn.type = 'button';
            btn.className = 'team-form-btn';
            const dot = document.createElement('span');
            dot.className = 'form-dot ' + formDotClassByOutcome(item.outcome);
            dot.setAttribute('aria-hidden', 'true');
            btn.appendChild(dot);
            btn.setAttribute('aria-label', teamCode + ' vs ' + (item.opponentCode || '?'));
            btn.addEventListener('click', () => {
                const own = item.ownScore ?? 0;
                const opp = item.opponentScore ?? 0;
                const opponent = item.opponentCode || '?';
                const datePart = item.kickoff ? (' · ' + item.kickoff) : '';
                showToast(teamCode + ' ' + own + ':' + opp + ' ' + opponent + datePart);
            });
            container.appendChild(btn);
        });
    }

    function renderModalNews(newsItems) {
        const container = $('#modal-news-content');
        if (!container) return;
        if (!newsItems || !newsItems.length) {
            container.innerHTML = '<p class="empty-state">Новостей пока нет</p>';
            return;
        }
        container.innerHTML = '';
        newsItems.forEach((item) => {
            const row = document.createElement('a');
            row.className = 'h2h-item modal-news-item';
            row.href = item.url;
            row.target = '_blank';
            row.rel = 'noopener noreferrer';
            const published = item.publishedAt ? ('<span class="modal-news-time">' + item.publishedAt + '</span>') : '';
            row.innerHTML =
                '<span class="modal-news-title">' + item.title + '</span>' +
                published;
            container.appendChild(row);
        });
    }

    function normalizeOutcome(outcome) {
        const value = (outcome || '').toUpperCase();
        if (value === 'W' || value === 'L' || value === 'D') return value;
        return 'D';
    }

    function formDotClassByOutcome(outcome) {
        const value = normalizeOutcome(outcome);
        if (value === 'W') return 'form-dot-win';
        if (value === 'L') return 'form-dot-loss';
        return 'form-dot-draw';
    }

    function formDotClassByScores(teamScore, oppScore) {
        if (teamScore > oppScore) return 'form-dot-win';
        if (teamScore < oppScore) return 'form-dot-loss';
        return 'form-dot-draw';
    }

    function renderH2hList(containerSelector, items, errorMessage) {
        const container = $(containerSelector);
        if (!container) return;
        if (errorMessage) {
            container.innerHTML = '<p class="empty-state">' + errorMessage + '</p>';
            return;
        }
        if (!items || !items.length) {
            container.innerHTML = '<p class="empty-state">Нет данных по очным встречам</p>';
            return;
        }
        container.innerHTML = '';
        items.forEach((h) => {
            const row = document.createElement('div');
            row.className = 'h2h-item';
            row.innerHTML =
                '<div class="h2h-item-head">' +
                '<span>' + (h.leagueName || 'Лига') + '</span>' +
                '<span>' + (h.kickoff || '') + '</span>' +
                '</div>' +
                '<div class="h2h-item-score">' + h.homeCode + ' ' + (h.homeScore ?? '-') + ' : ' + (h.awayScore ?? '-') + ' ' + h.awayCode + '</div>';
            container.appendChild(row);
        });
    }

    async function openTeamModal(teamCode) {
        state.selectedTeamCode = teamCode;
        state.teamModalOpened = true;
        $('#team-modal-title').textContent = teamCode;
        $('#team-last-list').innerHTML = '<li class="empty-state">Загрузка…</li>';
        $('#team-next-list').innerHTML = '<li class="empty-state">Загрузка…</li>';
        $('#team-modal').classList.remove('hidden');
        tg.BackButton.show();
        try {
            const data = await api('/team/' + encodeURIComponent(teamCode) + '/matches');
            $('#team-modal-title').textContent = data.teamName + ' (' + data.teamCode + ')';
            fillTeamMatchesList('#team-last-list', data.lastMatches || []);
            fillTeamMatchesList('#team-next-list', data.upcomingMatches || []);
        } catch (e) {
            $('#team-last-list').innerHTML = '<li class="empty-state">' + (e.message || 'Ошибка') + '</li>';
            $('#team-next-list').innerHTML = '<li class="empty-state">—</li>';
        }
    }

    function fillTeamMatchesList(selector, matches) {
        const list = $(selector);
        list.innerHTML = '';
        if (!matches.length) {
            list.innerHTML = '<li class="empty-state">Нет матчей</li>';
            return;
        }
        matches.forEach((m) => list.appendChild(renderTeamMatchItem(m, openH2hModalForMatch)));
    }

    function openH2hModalForMatch(match) {
        state.h2hModalOpened = true;
        $('#h2h-modal-title').textContent = 'История: ' + match.homeCode + ' — ' + match.awayCode;
        $('#h2h-modal-content').innerHTML = '<p class="empty-state">Загрузка…</p>';
        $('#h2h-modal').classList.remove('hidden');
        tg.BackButton.show();
        api('/h2h/' + encodeURIComponent(match.homeCode) + '/' + encodeURIComponent(match.awayCode))
            .then((items) => renderH2hList('#h2h-modal-content', items))
            .catch((e) => renderH2hList('#h2h-modal-content', [], e.message || 'Ошибка загрузки'));
    }

    function closeTeamModal() {
        $('#team-modal').classList.add('hidden');
        state.teamModalOpened = false;
        if (shouldHideBackButton()) {
            tg.BackButton.hide();
        }
    }

    function closeH2hModal() {
        $('#h2h-modal').classList.add('hidden');
        state.h2hModalOpened = false;
        if (shouldHideBackButton()) {
            tg.BackButton.hide();
        }
    }

    async function savePrediction(match, homeScore, awayScore) {
        setScoreButtonsDisabled(true);
        showToast('Сохраняем прогноз…', '');
        try {
            const res = await apiWithRetry('/predictions', {
                method: 'POST',
                body: JSON.stringify({
                    homeCode: match.homeCode,
                    awayCode: match.awayCode,
                    homeScore,
                    awayScore
                })
            });
            if (!res.ok) {
                showToast(res.message || 'Не удалось сохранить прогноз', 'error');
                return;
            }
            const scoresMatch = res.predictHome === homeScore && res.predictAway === awayScore;
            if (!scoresMatch) {
                const verified = await verifyPredictionSaved(match, homeScore, awayScore);
                if (!verified) {
                    showToast('Прогноз мог не сохраниться. Проверьте «Мои прогнозы» или попробуйте снова.', 'error');
                    return;
                }
            }
            showToast(res.message || 'Прогноз сохранён', 'success');
            tg.HapticFeedback?.notificationOccurred('success');
            closeScoreModal();
            await refreshAfterPredictionChange();
        } catch (e) {
            const verified = await verifyPredictionSaved(match, homeScore, awayScore);
            if (verified) {
                showToast('Прогноз сохранён (подтверждено на сервере)', 'success');
                tg.HapticFeedback?.notificationOccurred('success');
                closeScoreModal();
                await refreshAfterPredictionChange();
                return;
            }
            showToast(e.message || 'Не удалось сохранить прогноз', 'error');
        } finally {
            setScoreButtonsDisabled(false);
        }
    }

    async function deletePrediction() {
        const m = state.selectedMatch;
        if (!m) return;
        setScoreButtonsDisabled(true);
        try {
            const res = await apiWithRetry(
                '/predictions?homeCode=' + encodeURIComponent(m.homeCode) + '&awayCode=' + encodeURIComponent(m.awayCode),
                { method: 'DELETE' }
            );
            showToast(res.message, res.ok ? 'success' : 'error');
            if (res.ok) {
                closeScoreModal();
                state.todayLoaded = false;
                const predictBlock = $('#predict-matches');
                const myBlock = $('#my-predictions');
                if (!predictBlock.classList.contains('hidden') && predictBlock.dataset.weekId) {
                    await loadPredictMatches(parseInt(predictBlock.dataset.weekId, 10));
                }
                if (!myBlock.classList.contains('hidden') && myBlock.dataset.weekId) {
                    await loadMyPredictions(parseInt(myBlock.dataset.weekId, 10));
                }
                if ($('#screen-today').classList.contains('active')) {
                    await loadTodayMatches();
                }
            }
        } catch (e) {
            showToast(e.message || 'Не удалось удалить прогноз', 'error');
        } finally {
            setScoreButtonsDisabled(false);
        }
    }

    async function showScreen(name) {
        $$('.screen').forEach(s => s.classList.remove('active'));
        $$('.nav-btn').forEach(b => b.classList.remove('active'));
        $('#screen-' + name).classList.add('active');
        document.querySelector('[data-screen="' + name + '"]').classList.add('active');
        try {
            if (name === 'today' && !state.todayLoaded) {
                await loadTodayMatches();
            }
            if (name === 'stats' && !state.chartLoaded) {
                await loadPointsChart();
            }
        } catch (e) {
            showToast(e.message, 'error');
        }
    }

    function showPredictWeek(weekId) {
        $('#predict-weeks').classList.add('hidden');
        const block = $('#predict-matches');
        block.classList.remove('hidden');
        state.predictWeekOpened = true;
        block.dataset.weekId = weekId;
        loadPredictMatches(weekId);
        tg.BackButton.show();
    }

    function showMyWeek(weekId) {
        $('#my-weeks').classList.add('hidden');
        const block = $('#my-predictions');
        block.classList.remove('hidden');
        state.myWeekOpened = true;
        block.dataset.weekId = weekId;
        loadMyPredictions(weekId);
        tg.BackButton.show();
    }

    function bindEvents() {
        $$('.nav-btn').forEach(btn => {
            btn.addEventListener('click', () => showScreen(btn.dataset.screen));
        });

        window.addEventListener('resize', () => {
            if (state.chartLoaded) loadPointsChart().catch(() => {});
        });

        $$('.segmented-btn').forEach(btn => {
            btn.addEventListener('click', async () => {
                $$('.segmented-btn').forEach(b => b.classList.remove('active'));
                btn.classList.add('active');
                const mode = btn.dataset.leaderboard;
                state.leaderboardMode = mode || '';
                if (mode === 'current' && state.currentWeekId) {
                    await loadLeaderboard(state.currentWeekId);
                } else {
                    await loadLeaderboard(null);
                }
            });
        });

        $('#predict-back-weeks').addEventListener('click', () => {
            $('#predict-matches').classList.add('hidden');
            $('#predict-weeks').classList.remove('hidden');
            state.predictWeekOpened = false;
            tg.BackButton.hide();
        });

        $('#my-back-weeks').addEventListener('click', () => {
            $('#my-predictions').classList.add('hidden');
            $('#my-weeks').classList.remove('hidden');
            const review = $('#my-review-card');
            if (review) review.classList.add('hidden');
            state.myWeekOpened = false;
            tg.BackButton.hide();
        });

        $('#modal-close').addEventListener('click', closeScoreModal);
        $('#live-modal-close').addEventListener('click', closeLiveMatchModal);
        $('#modal-delete').addEventListener('click', deletePrediction);
        $('#team-modal-close').addEventListener('click', closeTeamModal);
        $('#h2h-modal-close').addEventListener('click', closeH2hModal);
        const livePitch = $('#modal-live-pitch');
        if (livePitch) {
            livePitch.addEventListener('click', () => {
                if (!isLiveModalOpen()) return;
                if (state.livePitchStatsOpened) {
                    hideLivePitchStatsOverlay();
                } else {
                    showLivePitchStatsOverlay();
                }
            });
        }

        tg.BackButton.onClick(() => {
            if (state.h2hModalOpened) {
                closeH2hModal();
                return;
            }
            if (state.teamModalOpened) {
                closeTeamModal();
                return;
            }
            if (isLiveModalOpen()) {
                closeLiveMatchModal();
                return;
            }
            if (!$('#score-modal').classList.contains('hidden')) {
                closeScoreModal();
                return;
            }
            if (state.predictWeekOpened) {
                $('#predict-matches').classList.add('hidden');
                $('#predict-weeks').classList.remove('hidden');
                state.predictWeekOpened = false;
                tg.BackButton.hide();
                return;
            }
            if (state.myWeekOpened) {
                $('#my-predictions').classList.add('hidden');
                $('#my-weeks').classList.remove('hidden');
                state.myWeekOpened = false;
                tg.BackButton.hide();
                return;
            }
            tg.BackButton.hide();
        });
    }

    async function init() {
        reportClientLog('INFO', 'init.start', 'hasTelegramWebApp=' + hasTelegramWebApp + ', isLocalDev=' + isLocalDev);
        if (!tg.initData && !isLocalDev) {
            $('#user-greeting').textContent = 'Откройте через Telegram';
            showToast('Приложение работает только внутри Telegram', 'error');
            reportClientLog('WARN', 'init.noTelegramContext', 'initData is empty outside local dev');
            return;
        }

        try {
            bindEvents();
            if (isLocalDev && !tg.initData) {
                $('#user-greeting').textContent = 'Локальный режим (dev)';
                document.getElementById('app-title').textContent = 'EPL Predictions [dev]';
            }
            await loadProfile();
            await loadLiveEventTranslations();
            await Promise.all([
                loadHomeLiveModule(),
                refreshLeaderboardByMode(),
                loadStandings(),
                loadPointsChart(),
                loadWeeksGrid('#predict-weeks', showPredictWeek),
                loadWeeksGrid('#my-weeks', showMyWeek)
            ]);
            startTodayPolling();
            if (tg.themeParams) {
                document.documentElement.style.setProperty('--tg-theme-bg-color', tg.themeParams.bg_color);
            }
            reportClientLog('INFO', 'init.success', 'miniapp ready');
        } catch (e) {
            showToast(e.message, 'error');
            $('#user-greeting').textContent = e.message;
            const stack = e && e.stack ? e.stack : '-';
            reportClientLog('ERROR', 'init.failed', (e && e.message ? e.message : 'unknown') + ' stack=' + stack);
        }
    }

    init();
})();
