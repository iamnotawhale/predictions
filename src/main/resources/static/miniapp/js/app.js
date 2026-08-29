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
        lastSeenLatestPitchEventKey: null,
        homeFormation: null,
        awayFormation: null,
        formationSide: 'home',
        liveEventRuCompiled: null,
        apiCache: {},
        recommendationDetailsOpen: false
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
        } else {
            await refreshLeaderboardByMode();
        }
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

    function parseKickoffLabel(label) {
        const m = /^(\d{2})\.(\d{2})\s+(\d{2}):(\d{2})$/.exec(String(label || '').trim());
        if (!m) return Number.MAX_SAFE_INTEGER;
        // month*1e6 + day*1e4 + hour*100 + minute — enough for same-season lists
        return Number(m[2]) * 1e6 + Number(m[1]) * 1e4 + Number(m[3]) * 100 + Number(m[4]);
    }

    function sortMatchesByKickoff(matches) {
        return (matches || []).slice().sort((a, b) => {
            const sa = a.kickoffSecondsLeft;
            const sb = b.kickoffSecondsLeft;
            if (typeof sa === 'number' && typeof sb === 'number' && sa !== sb) {
                return sa - sb;
            }
            const pa = parseKickoffLabel(a.kickoff);
            const pb = parseKickoffLabel(b.kickoff);
            if (pa !== pb) return pa - pb;
            return (Number(a.publicId) || 0) - (Number(b.publicId) || 0);
        });
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
        const ordered = sortMatchesByKickoff(matches);
        if (!ordered.length) {
            list.innerHTML = '<li class="empty-state">Сегодня матчей нет</li>';
            return;
        }
        let lastWeek = null;
        ordered.forEach(m => {
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

    function kickoffSecondsLeft(m) {
        if (!isNotStartedStatus(m.status)) return null;
        if (typeof m.kickoffSecondsLeft === 'number') return m.kickoffSecondsLeft;
        if (typeof m.predictSecondsLeft !== 'number') return null;
        // legacy: predictSecondsLeft counted to kickoff + 5m
        return m.predictSecondsLeft - 300;
    }

    function isPreLiveSoonMatch(m) {
        const sec = kickoffSecondsLeft(m);
        if (sec == null) return false;
        if (sec <= 0) return true;
        return sec <= LIVE_PRESTART_WINDOW_SECONDS;
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
        let scoresChanged = false;
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
                    scoresChanged = true;
                }
                return;
            }

            if (prev !== score) {
                enqueueScoreNotification(m, prev, detectGoalHighlightTeam(prev, score) || 'both');
                scoresChanged = true;
            }
        });
        state.todayScoresByMatchId = nextScores;
        state.todaySnapshotInitialized = true;
        return scoresChanged;
    }

    async function loadProfile() {
        const profile = await api('/profile');
        state.profile = profile;
        state.currentWeekId = profile.currentWeekId;
        const user = tg.initDataUnsafe?.user;
        const name = user ? (user.first_name + (user.last_name ? ' ' + user.last_name : '')) : profile.login;
        $('#user-greeting').textContent = name + ' · ' + (profile.weekLabel || ('тур ' + profile.currentWeekId));
        syncRecommenderToggle(profile.bettingRecommenderEnabled);
        syncRecommenderRefreshButton(!!profile.admin);
        const verEl = $('.miniapp-version');
        if (verEl) {
            const base = verEl.getAttribute('data-base') || verEl.textContent.trim();
            verEl.setAttribute('data-base', base);
            verEl.textContent = profile.dnsHint ? base + ' · ' + profile.dnsHint : base;
        }
    }

    function syncRecommenderToggle(enabled) {
        const toggle = $('#betting-recommender-toggle');
        if (!toggle) return;
        toggle.checked = !!enabled;
    }

    function syncRecommenderRefreshButton(isAdmin) {
        const btn = $('#betting-recommender-refresh');
        if (!btn) return;
        btn.classList.toggle('hidden', !isAdmin);
    }

    function clearInsightsCache() {
        Object.keys(state.apiCache).forEach((key) => {
            if (key.startsWith('/match/') && key.endsWith('/insights')) {
                delete state.apiCache[key];
            }
        });
    }

    async function refreshBettingRecommendations() {
        const btn = $('#betting-recommender-refresh');
        if (btn) {
            btn.disabled = true;
            btn.classList.add('is-loading');
        }
        showToast('Обновляю рекомендации…');
        try {
            const res = await api('/admin/betting-recommender/refresh', { method: 'POST' });
            clearInsightsCache();
            showToast(res.message || 'Рекомендации обновлены', 'success');
            if (state.selectedMatch && !$('#score-modal').classList.contains('hidden')) {
                loadScoreModalInsights(state.selectedMatch, true).catch(() => {});
            }
        } catch (e) {
            showToast(e.message || 'Не удалось обновить рекомендации', 'error');
            throw e;
        } finally {
            if (btn) {
                btn.disabled = false;
                btn.classList.remove('is-loading');
            }
        }
    }

    async function setBettingRecommender(enabled) {
        const toggle = $('#betting-recommender-toggle');
        if (toggle) toggle.disabled = true;
        try {
            const res = await api('/profile/betting-recommender', {
                method: 'POST',
                body: JSON.stringify({ enabled })
            });
            if (state.profile) {
                state.profile.bettingRecommenderEnabled = enabled;
            }
            clearInsightsCache();
            if (res && res.message) {
                showToast(res.message);
            }
            if (state.selectedMatch && !$('#score-modal').classList.contains('hidden')) {
                loadScoreModalInsights(state.selectedMatch, true).catch(() => {});
            }
        } catch (e) {
            syncRecommenderToggle(state.profile && state.profile.bettingRecommenderEnabled);
            throw e;
        } finally {
            if (toggle) toggle.disabled = false;
        }
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
        const scoresChanged = processTodayScoreUpdates(data.matches, previousScores);
        renderHomeLiveModule(data.matches);
        if ($('#screen-today').classList.contains('active')) {
            renderTodayMatchesList(data.matches);
            state.todayLoaded = true;
        }
        if (scoresChanged || state.todayHasLive) {
            await refreshLeaderboardByMode();
        }
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
            if (r.liveScore) {
                tr.classList.add('is-live');
            }
            const delta = Number(r.placeDelta || 0);
            let deltaHtml = '<span class="standings-delta flat" aria-hidden="true"></span>';
            if (delta > 0) {
                deltaHtml = '<span class="standings-delta up" title="+' + delta + '" aria-label="поднялись на ' + delta + '"></span>';
            } else if (delta < 0) {
                deltaHtml = '<span class="standings-delta down" title="' + delta + '" aria-label="опустились на ' + Math.abs(delta) + '"></span>';
            }
            let livePill = '';
            if (r.liveScore) {
                const resultClass = r.liveResult === 'W' ? 'win' : (r.liveResult === 'L' ? 'lose' : 'draw');
                livePill = '<span class="standings-live-pill ' + resultClass + '">'
                    + escapeHtml(String(r.liveScore).replace('-', '–'))
                    + '</span>';
            }
            tr.innerHTML =
                '<td class="standings-place">' + deltaHtml + '<span class="standings-place-num">' + r.place + '</span></td>' +
                '<td class="club-cell">' +
                '<img class="club-logo" src="' + (r.logo || '') + '" alt="' + (r.code || '') + '" onerror="this.style.visibility=\'hidden\'">' +
                '<strong>' + r.code + '</strong>' +
                livePill +
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
        const ordered = sortMatchesByKickoff(matches);
        if (!ordered.length) {
            list.innerHTML = '<li class="empty-state">Матчей нет</li>';
            return;
        }
        ordered.forEach(m => list.appendChild(renderMatchItem(m, openScoreModal)));
    }

    async function loadMyPredictions(weekId) {
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
                li.className = 'list-item review-item';
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
        state.recommendationDetailsOpen = false;
        renderH2hList('#modal-h2h-content', []);
        renderTeamFormDots('#modal-home-form', match.homeCode, []);
        renderTeamFormDots('#modal-away-form', match.awayCode, []);
        renderModalNews([]);
        renderModalRecommendation(null);
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
        state.lastSeenLatestPitchEventKey = null;
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
        $('#modal-live-events').innerHTML = '<p class="empty-state">Загрузка…</p>';
        state.lastLiveEvents = [];
        state.selectedPitchEventKey = null;
        state.lastSeenLatestPitchEventKey = null;
        state.homeFormation = null;
        state.awayFormation = null;
        state.formationSide = 'home';
        updateFormationTabs(match, null, null);
        renderFormationPitch(null);
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
            renderFormationPitch(null);
            $('#modal-live-events').innerHTML = '<p class="empty-state">Матч уже не в live</p>';
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
        state.homeFormation = data.homeFormation || null;
        state.awayFormation = data.awayFormation || null;
        if (!state.homeFormation && !state.awayFormation) {
            state.homeFormation = fallbackFormationFromLineup('home', match.homeCode, data.homeLineup || []);
            state.awayFormation = fallbackFormationFromLineup('away', match.awayCode, data.awayLineup || []);
        }
        updateFormationTabs(match, state.homeFormation, state.awayFormation);
        const active = state.formationSide === 'away' ? state.awayFormation : state.homeFormation;
        if (!active && state.awayFormation) state.formationSide = 'away';
        if (!active && state.homeFormation) state.formationSide = 'home';
        renderActiveFormation();
        state.livePitchHomeColor = data.homeColor || '#ffffff';
        state.livePitchAwayColor = data.awayColor || '#ffffff';
        renderLiveEvents(data.events || [], state.selectedMatch);
        renderLivePitchStats(data.matchStats || [], state.selectedMatch);
    }

    function fallbackFormationFromLineup(side, teamCode, lineup) {
        if (!lineup || !lineup.length) return null;
        return {
            side: side,
            teamCode: teamCode,
            formation: '',
            kitColor: '',
            starters: lineup.map((p, idx) => ({
                id: side + '-' + idx,
                number: p.number || 0,
                name: p.name || '',
                shortName: p.name || '',
                lastName: (p.name || '').split(/\s+/).pop(),
                position: p.position || '',
                positionName: p.position || '',
                formationPlace: idx + 1,
                starter: true,
                subbedOut: false,
                subbedIn: false,
                subPartnerId: '',
                subPartnerName: '',
                jerseyImage: null,
                goals: null,
                assists: null,
                yellowCards: null,
                redCards: null,
                stats: []
            })),
            bench: []
        };
    }

    function updateFormationTabs(match, homeFormation, awayFormation) {
        const homeTab = $('#formation-tab-home');
        const awayTab = $('#formation-tab-away');
        if (!homeTab || !awayTab) return;
        const homeLabel = (homeFormation && homeFormation.formation)
            ? ((match?.homeCode || 'HOME') + ' · ' + homeFormation.formation)
            : (match?.homeCode || 'HOME');
        const awayLabel = (awayFormation && awayFormation.formation)
            ? ((match?.awayCode || 'AWAY') + ' · ' + awayFormation.formation)
            : (match?.awayCode || 'AWAY');
        homeTab.textContent = homeLabel;
        awayTab.textContent = awayLabel;
        homeTab.classList.toggle('active', state.formationSide === 'home');
        awayTab.classList.toggle('active', state.formationSide === 'away');
        homeTab.disabled = !homeFormation;
        awayTab.disabled = !awayFormation;
    }

    function renderActiveFormation() {
        const match = state.selectedMatch;
        updateFormationTabs(match, state.homeFormation, state.awayFormation);
        const formation = state.formationSide === 'away' ? state.awayFormation : state.homeFormation;
        renderFormationPitch(formation);
    }

    function allFormationPlayers(formation) {
        return [...(formation?.starters || []), ...(formation?.bench || [])];
    }

    function formationPlayersById(formation) {
        const byId = new Map();
        allFormationPlayers(formation).forEach((p) => {
            if (p.id) byId.set(String(p.id), p);
        });
        return byId;
    }

    function resolvePitchSlot(formation, starter, byId) {
        const formationPlace = starter.formationPlace || 0;
        if (!starter?.subbedOut) {
            return { player: starter, isSubstitution: false, formationPlace };
        }
        let replacement = null;
        if (starter.subPartnerId && byId.has(String(starter.subPartnerId))) {
            replacement = byId.get(String(starter.subPartnerId));
        } else {
            replacement = allFormationPlayers(formation).find(
                (p) => p.subbedIn && String(p.subPartnerId) === String(starter.id)
            );
        }
        if (replacement) {
            return { player: replacement, isSubstitution: true, formationPlace };
        }
        return { player: starter, isSubstitution: false, subbedOutOnly: true, formationPlace };
    }

    // Opta/ESPN formationPlace rows: attack → GK (top → bottom)
    const FORMATION_PLACE_ROWS = {
        '4-2-3-1': [[9], [11, 10, 7], [4, 8], [3, 6, 5, 2], [1]],
        '4-3-3': [[11, 9, 7], [8, 10, 4], [3, 6, 5, 2], [1]],
        '4-4-2': [[11, 9], [8, 10, 6, 7], [3, 5, 4, 2], [1]],
        '4-1-4-1': [[9], [11, 8, 10, 7], [4], [3, 6, 5, 2], [1]],
        '3-5-2': [[11, 9], [8, 10, 6], [3, 5, 4], [1]],
        '3-4-3': [[11, 9, 7], [8, 10], [3, 5, 4], [1]],
        '3-4-2-1': [[9], [11, 7], [8, 10], [3, 5, 4], [1]],
        '5-3-2': [[11, 9], [8, 10, 6], [3, 5, 4, 2, 7], [1]],
        '5-4-1': [[9], [11, 8, 10, 7], [3, 5, 4, 2, 6], [1]],
        '4-5-1': [[9], [11, 8, 10, 6, 7], [3, 5, 4, 2], [1]],
        '4-3-2-1': [[9], [11, 7], [8, 10, 4], [3, 6, 5, 2], [1]],
        '4-4-1-1': [[9], [10], [11, 8, 6, 7], [3, 5, 4, 2], [1]]
    };

    function inferFormationKey(formation) {
        const key = (formation?.formation || '').trim();
        if (FORMATION_PLACE_ROWS[key]) return key;
        return '4-2-3-1';
    }

    function formationPlaceSlots(formation) {
        const byId = formationPlayersById(formation);
        const byPlace = new Map();
        (formation?.starters || []).forEach((p) => {
            const place = Number(p.formationPlace);
            if (place > 0) {
                byPlace.set(place, resolvePitchSlot(formation, p, byId));
            }
        });
        return byPlace;
    }

    function rowsFromPlaceMap(byPlace, placeRows) {
        return placeRows
            .map((places) => places.map((place) => byPlace.get(Number(place))).filter(Boolean))
            .filter((row) => row.length);
    }

    function buildFormationRows(formation) {
        if (!formation || !formation.starters || !formation.starters.length) return [];
        const byPlace = formationPlaceSlots(formation);
        if (!byPlace.size) return [];
        const key = inferFormationKey(formation);
        const rows = rowsFromPlaceMap(byPlace, FORMATION_PLACE_ROWS[key]);
        if (rows.length) return rows;
        return fallbackFormationRows(formation, byPlace);
    }

    function fallbackFormationRows(formation, byPlace) {
        if (!byPlace) byPlace = formationPlaceSlots(formation);
        const parts = String(formation.formation || '')
            .split('-')
            .map((n) => parseInt(n, 10))
            .filter((n) => Number.isFinite(n) && n > 0);
        if (!parts.length) {
            return rowsFromPlaceMap(byPlace, FORMATION_PLACE_ROWS['4-2-3-1']);
        }
        const slots = (formation.starters || [])
            .map((p) => ({
                starter: p,
                slot: byPlace.get(Number(p.formationPlace))
            }))
            .filter(({ slot }) => slot);
        const gk = slots
            .filter(({ starter }) => Number(starter.formationPlace) === 1 || /^G/i.test(starter.position || ''))
            .map(({ slot }) => slot);
        const rest = slots
            .filter(({ starter }) => !(Number(starter.formationPlace) === 1 || /^G/i.test(starter.position || '')))
            .sort((a, b) => (Number(a.starter.formationPlace) || 99) - (Number(b.starter.formationPlace) || 99))
            .map(({ slot }) => slot);
        const defenseToAttack = [];
        let idx = 0;
        parts.forEach((count) => {
            defenseToAttack.push(rest.slice(idx, idx + count));
            idx += count;
        });
        if (idx < rest.length) {
            defenseToAttack.push(rest.slice(idx));
        }
        const rows = defenseToAttack.reverse().filter((row) => row.length);
        if (gk.length) rows.push(gk);
        return rows.length ? rows : rowsFromPlaceMap(byPlace, FORMATION_PLACE_ROWS['4-2-3-1']);
    }

    function formationPlayerLabel(player) {
        return (player?.lastName || player?.shortName || player?.name || '').trim();
    }

    function formationJerseyNumber(player) {
        const num = Number(player?.number);
        if (Number.isFinite(num) && num > 0) return String(num);
        const label = formationPlayerLabel(player);
        return label ? label.charAt(0).toUpperCase() : '?';
    }

    function formatBenchLabel(player) {
        const name = formationPlayerLabel(player);
        if (!name) return null;
        const parts = [];
        const num = Number(player.number);
        if (Number.isFinite(num) && num > 0) parts.push(String(num));
        parts.push(name);
        const pos = (player.position || '').trim();
        if (pos && !/^SUB$/i.test(pos)) parts.push(pos);
        return parts.join(' · ');
    }

    function setFormationLineupVisible(hasPitch, hasBench) {
        const lineup = $('#formation-lineup');
        const empty = $('#formation-empty');
        if (lineup) {
            lineup.classList.toggle('hidden', !hasPitch);
            lineup.classList.toggle('has-bench', !!hasBench);
        }
        if (empty) empty.classList.toggle('hidden', hasPitch);
    }

    function renderFormationBench(formation) {
        const benchEl = $('#formation-bench');
        if (!benchEl) return false;
        benchEl.innerHTML = '';
        if (!formation || !formation.bench || !formation.bench.length) {
            benchEl.classList.add('hidden');
            return false;
        }
        const unused = formation.bench.filter((p) => !p.subbedIn);
        if (!unused.length) {
            benchEl.classList.add('hidden');
            return false;
        }
        benchEl.classList.remove('hidden');
        const list = document.createElement('ul');
        list.className = 'formation-bench-list';
        unused.forEach((player) => {
            const label = formatBenchLabel(player);
            if (!label) return;
            const item = document.createElement('li');
            item.className = 'formation-bench-item';
            item.textContent = label;
            list.appendChild(item);
        });
        if (!list.childElementCount) {
            benchEl.classList.add('hidden');
            return false;
        }
        benchEl.appendChild(list);
        return true;
    }

    function renderFormationPitch(formation) {
        const pitch = $('#formation-pitch');
        if (!pitch) return;
        pitch.innerHTML = '';
        const rows = buildFormationRows(formation);
        if (!rows.length) {
            pitch.classList.add('hidden');
            setFormationLineupVisible(false, false);
            return;
        }
        pitch.classList.remove('hidden');
        const kitColor = normalizeKitColor(formation.kitColor);
        rows.forEach((row) => {
            const rowEl = document.createElement('div');
            rowEl.className = 'formation-row';
            row.forEach((slot) => rowEl.appendChild(createFormationPlayerButton(slot, kitColor)));
            pitch.appendChild(rowEl);
        });
        setFormationLineupVisible(true, renderFormationBench(formation));
    }

    function normalizeKitColor(color) {
        if (!color) return '';
        const value = String(color).trim();
        if (!value) return '';
        return value.startsWith('#') ? value : ('#' + value);
    }

    function createFormationPlayerButton(slot, kitColor) {
        const player = slot.player;
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'formation-player' +
            (slot.subbedOutOnly ? ' subbed-out' : '') +
            (slot.isSubstitution ? ' is-substitute-in' : '');
        const isGk = slot.formationPlace === 1 || /^G/i.test(player.position || '');
        const hasImage = !!player.jerseyImage;
        const jerseyStyle = [];
        if (hasImage) {
            jerseyStyle.push('background-image:url(\'' + player.jerseyImage.replace(/'/g, '%27') + '\')');
        } else if (kitColor) {
            jerseyStyle.push('background-color:' + kitColor);
        } else if (isGk) {
            jerseyStyle.push('background-color:#4a5560');
        } else {
            jerseyStyle.push('background-color:#3a4a58');
        }
        const badges = [];
        if ((player.goals || 0) > 0) {
            badges.push('<span class="formation-badge">⚽' + player.goals + '</span>');
        }
        if ((player.assists || 0) > 0) {
            badges.push('<span class="formation-badge">🅐' + player.assists + '</span>');
        }
        if ((player.yellowCards || 0) > 0) {
            badges.push('<span class="formation-badge">🟨</span>');
        }
        if ((player.redCards || 0) > 0) {
            badges.push('<span class="formation-badge">🟥</span>');
        }
        const subIcon = slot.isSubstitution
            ? '<span class="formation-sub-icon" title="Замена">↕</span>'
            : '';
        btn.innerHTML =
            (badges.length ? '<div class="formation-badges">' + badges.join('') + '</div>' : '') +
            subIcon +
            '<span class="formation-jersey' + (isGk ? ' is-gk' : '') + (hasImage ? ' has-image' : '') + '"' +
            (jerseyStyle.length ? ' style="' + jerseyStyle.join(';') + '"' : '') + '>' +
            (hasImage ? '' : formationJerseyNumber(player)) +
            '</span>' +
            '<span class="formation-player-name">' + escapeHtml(formationPlayerLabel(player) || '?') + '</span>';
        btn.addEventListener('click', (e) => {
            e.stopPropagation();
            openPlayerModal(player, kitColor);
        });
        return btn;
    }

    function escapeHtml(text) {
        return String(text || '')
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;');
    }

    const PLAYER_STAT_LABELS_RU = {
        totalGoals: 'Голы',
        goalAssists: 'Ассисты',
        totalShots: 'Удары',
        shotsOnTarget: 'В створ',
        foulsCommitted: 'Фолы',
        foulsSuffered: 'Фолы на игроке',
        yellowCards: 'Жёлтые',
        redCards: 'Красные',
        offsides: 'Офсайды',
        saves: 'Сейвы',
        goalsConceded: 'Пропущено',
        shotsFaced: 'Удары в створ',
        ownGoals: 'Автоголы',
        appearances: 'Выход на поле',
        subIns: 'Замены'
    };

    const PLAYER_STAT_PRIORITY = [
        'totalGoals', 'goalAssists', 'totalShots', 'shotsOnTarget',
        'saves', 'goalsConceded', 'shotsFaced',
        'foulsCommitted', 'foulsSuffered', 'offsides',
        'yellowCards', 'redCards', 'ownGoals'
    ];

    function openPlayerModal(player, kitColor) {
        const modal = $('#player-modal');
        if (!modal || !player) return;
        $('#player-modal-title').textContent = player.name || player.shortName || 'Игрок';
        $('#player-modal-sub').textContent =
            (player.positionName || player.position || '') +
            (player.number ? ' · ' + player.number : '');
        const jersey = $('#player-modal-jersey');
        if (jersey) {
            const hasImage = !!player.jerseyImage;
            jersey.textContent = hasImage ? '' : (player.number || '');
            jersey.style.backgroundImage = hasImage
                ? 'url(\'' + player.jerseyImage.replace(/'/g, '%27') + '\')'
                : '';
            jersey.style.backgroundColor = hasImage ? '' : (kitColor || '');
            jersey.classList.toggle('has-image', hasImage);
            jersey.classList.toggle('is-gk', /^G/i.test(player.position || '') || player.formationPlace === 1);
        }
        const badges = $('#player-modal-badges');
        if (badges) {
            const chips = [];
            if ((player.goals || 0) > 0) chips.push('<span class="player-modal-chip">Голы: ' + player.goals + '</span>');
            if ((player.assists || 0) > 0) chips.push('<span class="player-modal-chip">Ассисты: ' + player.assists + '</span>');
            if (player.subbedOut) {
                chips.push('<span class="player-modal-chip">Заменён' +
                    (player.subPartnerName ? ': ' + escapeHtml(player.subPartnerName) : '') + '</span>');
            }
            if (player.subbedIn) {
                chips.push('<span class="player-modal-chip">Вышел на замену' +
                    (player.subPartnerName ? ' · ' + escapeHtml(player.subPartnerName) : '') + '</span>');
            }
            badges.innerHTML = chips.join('');
        }
        const statsBox = $('#player-modal-stats');
        if (statsBox) {
            const stats = (player.stats || []).slice();
            const byName = new Map(stats.map((s) => [s.name, s]));
            const ordered = [];
            PLAYER_STAT_PRIORITY.forEach((name) => {
                const s = byName.get(name);
                if (s && s.value != null && s.value !== '' && s.value !== '0') {
                    ordered.push(s);
                    byName.delete(name);
                }
            });
            // also show zeros for key attacking/gk stats if present
            ['totalGoals', 'goalAssists', 'saves', 'goalsConceded'].forEach((name) => {
                const s = byName.get(name);
                if (s) {
                    ordered.push(s);
                    byName.delete(name);
                }
            });
            byName.forEach((s) => {
                if (s.value && s.value !== '0') ordered.push(s);
            });
            if (!ordered.length) {
                statsBox.innerHTML = '<p class="empty-state">Нет статистики</p>';
            } else {
                statsBox.innerHTML = ordered.map((s) => {
                    const label = PLAYER_STAT_LABELS_RU[s.name] || s.label || s.abbreviation || s.name;
                    return '<div class="player-modal-stat-label">' + escapeHtml(label) + '</div>' +
                        '<div class="player-modal-stat-value">' + escapeHtml(s.value) + '</div>';
                }).join('');
            }
        }
        modal.classList.remove('hidden');
    }

    function closePlayerModal() {
        const modal = $('#player-modal');
        if (modal) modal.classList.add('hidden');
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
     * Mirror X when (away XOR second half); flip Y in the complementary cases
     * (home XOR 1st half, away XOR 2nd half).
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

    function shouldFlipPitchY(event, match) {
        return !shouldMirrorPitchX(event, match);
    }

    function mapEventToPitchCoords(event, match) {
        if (!event) return event;
        const mapped = Object.assign({}, event);
        if (shouldMirrorPitchX(event, match)) {
            mapped.fieldX = flipPitchPercent(event.fieldX);
            mapped.field2X = flipPitchPercent(event.field2X);
        }
        if (shouldFlipPitchY(event, match)) {
            mapped.fieldY = flipPitchPercent(event.fieldY);
            mapped.field2Y = flipPitchPercent(event.field2Y);
            mapped.goalPositionY = flipPitchPercent(event.goalPositionY);
        }
        return mapped;
    }

    function isOffsideEventType(type) {
        return (type || '').toLowerCase().includes('offside');
    }

    function resolveOffsidePoints(event) {
        const hasLine = Number.isFinite(event.fieldX) && Number.isFinite(event.fieldY);
        const hasPlayer = Number.isFinite(event.field2X) && Number.isFinite(event.field2Y);
        if (!hasLine || !hasPlayer) return null;
        return {
            line: { x: event.fieldX, y: event.fieldY },
            player: { x: event.field2X, y: event.field2Y }
        };
    }

    /**
     * Shot points on pitch (after home/away mapping):
     * Blocked: origin → fieldEnd (+ mid marker = blocker).
     * On-target: origin → fieldEnd (= keeper) when present; else → goalmouth.
     * Other: origin → goalmouth / fieldEnd (no mid).
     * Offside: fieldStart = offside line, field2 = caught player (see resolveOffsidePoints).
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

    function pitchOffsidePlayerFromText(event) {
        for (const blob of [event.text || '', event.shortText || '']) {
            const text = String(blob).trim();
            if (!text) continue;
            const caught = text.match(/\.\s*(.+?)\s+is caught offside\b/i);
            if (caught) {
                const name = surnameFromPlayerName(caught[1]);
                if (name) return name;
            }
        }
        return '';
    }

    function pitchPlayerLabel(event) {
        if (isOffsideEventType(event.type)) {
            const fromText = pitchOffsidePlayerFromText(event);
            if (fromText) return fromText;
        }

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
        const fx = Number.isFinite(event.fieldX) ? event.fieldX.toFixed(1) : '';
        const fy = Number.isFinite(event.fieldY) ? event.fieldY.toFixed(1) : '';
        return [
            event.type || '',
            event.shortText || '',
            event.text || '',
            event.playerName || '',
            fx,
            fy
        ].join('|');
    }

    function findLatestPitchEvent(events) {
        return (events || []).find((e) => Number.isFinite(e.fieldX) && Number.isFinite(e.fieldY)) || null;
    }

    function syncLatestPitchEvent(events) {
        const latest = findLatestPitchEvent(events);
        const latestKey = latest ? livePitchEventKey(latest) : null;
        if (latestKey && latestKey !== state.lastSeenLatestPitchEventKey) {
            state.selectedPitchEventKey = null;
        }
        if (latestKey) {
            state.lastSeenLatestPitchEventKey = latestKey;
        }
        return latest;
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
        const latest = syncLatestPitchEvent(state.lastLiveEvents);
        let target = null;
        if (state.selectedPitchEventKey) {
            target = state.lastLiveEvents.find((e) => livePitchEventKey(e) === state.selectedPitchEventKey);
            if (!target) {
                state.selectedPitchEventKey = null;
            }
        }
        if (!target) {
            if (isMatchHalftimeBreak(match, state.lastLiveEvents)) {
                resetLivePitchMarker();
                return;
            }
            target = latest;
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
        const offsidePoints = isOffsideEventType(event.type) ? resolveOffsidePoints(event) : null;

        let markerX;
        let markerY;
        let mid = null;
        let end = null;
        let trajectoryStyle = null;
        let midColor = resolveOppositeTeamColor(match, event);

        if (offsidePoints) {
            markerX = clamp(offsidePoints.player.x, 0, 100);
            markerY = clamp(offsidePoints.player.y, 0, 100);
            mid = {
                x: clamp(offsidePoints.line.x, 0, 100),
                y: clamp(offsidePoints.line.y, 0, 100)
            };
            end = { x: markerX, y: markerY };
            trajectoryStyle = 'dashed';
            midColor = teamColor;
        } else {
            markerX = clamp(event.fieldX, 0, 100);
            markerY = clamp(event.fieldY, 0, 100);
            const showShotTrail = isShotEventType(event.type);
            const points = showShotTrail ? resolveShotPoints(event) : null;
            trajectoryStyle = showShotTrail ? shotTrajectoryStyle(event.type) : null;
            mid = points && points.mid
                ? { x: clamp(points.mid.x, 0, 100), y: clamp(points.mid.y, 0, 100) }
                : null;
            end = points && points.end
                ? { x: clamp(points.end.x, 0, 100), y: clamp(points.end.y, 0, 100) }
                : null;
        }

        marker.style.left = markerX + '%';
        marker.style.top = markerY + '%';

        const dot = marker.querySelector('.live-pitch-dot');
        if (dot) {
            dot.style.background = teamColor || '#ffffff';
            dot.style.boxShadow = '0 0 0 2px rgba(0, 0, 0, 0.35), 0 0 8px ' + (teamColor || '#ffffff') + '88';
        }

        const labelEl = $('#live-pitch-marker-label');
        if (labelEl) {
            labelEl.textContent = label || 'live';
        }
        positionLivePitchBadge(marker, pitch, markerX, markerY);
        marker.classList.remove('hidden');

        const lineStartX = offsidePoints ? mid.x : markerX;
        const lineStartY = offsidePoints ? mid.y : markerY;

        if (shotLine) {
            if (end && trajectoryStyle) {
                shotLine.setAttribute('x1', String(lineStartX));
                shotLine.setAttribute('y1', String(lineStartY));
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
            state.lastSeenLatestPitchEventKey = null;
            resetLivePitchMarker();
            return;
        }
        syncLatestPitchEvent(events);
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
        closePlayerModal();
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

    async function loadScoreModalInsights(match, forceFresh) {
        const modalMatchId = match.publicId;
        const path = '/match/' + encodeURIComponent(match.homeCode) + '/' + encodeURIComponent(match.awayCode) + '/insights';
        try {
            const insights = forceFresh ? await api(path) : await apiCached(path);
            if (!state.selectedMatch || state.selectedMatch.publicId !== modalMatchId) {
                return;
            }
            renderTeamFormDots('#modal-home-form', match.homeCode, insights.homeForm || []);
            renderTeamFormDots('#modal-away-form', match.awayCode, insights.awayForm || []);
            renderModalNews(insights.news || []);
            renderModalRecommendation(insights.recommendation || null);
        } catch (_) {
            if (!state.selectedMatch || state.selectedMatch.publicId !== modalMatchId) {
                return;
            }
            renderTeamFormDots('#modal-home-form', match.homeCode, []);
            renderTeamFormDots('#modal-away-form', match.awayCode, []);
            renderModalNews([]);
            renderModalRecommendation(null);
        }
    }

    function renderModalRecommendation(recommendation) {
        const section = $('#modal-recommendation-section');
        const details = $('#modal-recommendation-details');
        const summaryEl = $('#modal-recommendation-summary');
        const scoreEl = $('#modal-recommendation-score');
        const linesEl = $('#modal-recommendation-lines');
        if (!section || !details || !summaryEl || !scoreEl || !linesEl) return;

        const enabled = state.profile && state.profile.bettingRecommenderEnabled;
        if (!enabled || !recommendation) {
            section.classList.add('hidden');
            details.classList.add('hidden');
            state.recommendationDetailsOpen = false;
            return;
        }

        section.classList.remove('hidden');
        const score = recommendation.recommendedHome + ':' + recommendation.recommendedAway;
        scoreEl.textContent = score;
        summaryEl.textContent = recommendation.summary
            || ('Ожидаемые голы ' + Number(recommendation.expectedHomeGoals).toFixed(1)
                + ' : ' + Number(recommendation.expectedAwayGoals).toFixed(1)
                + ' · шанс счёта '
                + Math.round(Number(recommendation.scoreProbability || 0) * 100) + '%');

        linesEl.innerHTML = '';
        const lines = recommendation.explanationLines || [];
        lines.forEach((line) => {
            const li = document.createElement('li');
            li.textContent = line;
            linesEl.appendChild(li);
        });

        if (!state.recommendationDetailsOpen) {
            details.classList.add('hidden');
        } else if (lines.length) {
            details.classList.remove('hidden');
        } else {
            details.classList.add('hidden');
        }
    }

    function toggleRecommendationDetails() {
        const details = $('#modal-recommendation-details');
        const linesEl = $('#modal-recommendation-lines');
        if (!details || !linesEl || !linesEl.children.length) return;
        state.recommendationDetailsOpen = !state.recommendationDetailsOpen;
        details.classList.toggle('hidden', !state.recommendationDetailsOpen);
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
        const recommenderToggle = $('#betting-recommender-toggle');
        if (recommenderToggle) {
            recommenderToggle.addEventListener('change', () => {
                setBettingRecommender(recommenderToggle.checked).catch(() => {});
            });
        }
        const recommenderRefresh = $('#betting-recommender-refresh');
        if (recommenderRefresh) {
            recommenderRefresh.addEventListener('click', () => {
                refreshBettingRecommendations().catch(() => {});
            });
        }
        const recommendationToggle = $('#modal-recommendation-toggle');
        if (recommendationToggle) {
            recommendationToggle.addEventListener('click', toggleRecommendationDetails);
        }
        $('#live-modal-close').addEventListener('click', closeLiveMatchModal);
        $('#modal-delete').addEventListener('click', deletePrediction);
        $('#team-modal-close').addEventListener('click', closeTeamModal);
        $('#h2h-modal-close').addEventListener('click', closeH2hModal);
        const playerModalClose = $('#player-modal-close');
        if (playerModalClose) playerModalClose.addEventListener('click', closePlayerModal);
        const playerModal = $('#player-modal');
        if (playerModal) {
            playerModal.addEventListener('click', (e) => {
                if (e.target === playerModal) closePlayerModal();
            });
        }
        document.querySelectorAll('.formation-tab').forEach((tab) => {
            tab.addEventListener('click', () => {
                if (tab.disabled) return;
                state.formationSide = tab.dataset.side || 'home';
                renderActiveFormation();
            });
        });
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
            if (playerModal && !playerModal.classList.contains('hidden')) {
                closePlayerModal();
                return;
            }
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
