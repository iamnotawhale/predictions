(function () {
    const {
        CACHE_TTL_MS,
        CHART_COLORS,
        FINISHED_STATUSES,
        FORMATION_PLACE_ROWS,
        LIVE_MODAL_POLL_MS,
        LIVE_PRESTART_WINDOW_SECONDS,
        MUTATION_RETRY_DELAYS_MS,
        NOT_STARTED_STATUSES,
        PITCH_NAME_PARTICLE,
        PITCH_NON_PLAYER_TOKEN,
        PLAYER_STAT_LABELS_RU,
        PLAYER_STAT_PRIORITY,
        TODAY_POLL_IDLE_MS,
        TODAY_POLL_LIVE_MS,
        allFormationPlayers,
        buildFormationRows,
        clamp,
        competitionBadgeHtml,
        competitionCssKey,
        competitionShort,
        countFormationSlots,
        detectGoalHighlightTeam,
        escapeHtml,
        fairOdds,
        fallbackFormationFromLineup,
        fallbackFormationRows,
        findLatestPitchEvent,
        flipPitchPercent,
        fmtStatNum,
        fmtStatPct,
        formDotClassByOutcome,
        formDotClassByScores,
        formatBenchLabel,
        formationJerseyNumber,
        formationPlaceSlots,
        formationPlayerLabel,
        formationPlayersById,
        hasLiveOrSoonMatches,
        hasSecondHalfActivity,
        hexToRgba,
        inferFormationKey,
        injuryKindMeta,
        isBlockedShotType,
        isFinishedStatus,
        isLiveModuleMatch,
        isLiveNowMatch,
        isMatchHalftimeBreak,
        isNotStartedStatus,
        isOffsideEventType,
        isOnTargetShotType,
        isPreLiveSoonMatch,
        isRetryableStatus,
        isShotEventType,
        kickoffDateLabel,
        kickoffSecondsLeft,
        kickoffTimeLabel,
        liveEventIcon,
        livePitchEventKey,
        mapEventToPitchCoords,
        marketChipHtml,
        matchScoreLabel,
        matchStatusBadge,
        niceChartStep,
        normalizeKitColor,
        normalizeOutcome,
        normalizeStatus,
        parseKickoffLabel,
        parseLiveEventMinute,
        parsePercentWidth,
        pctLabel,
        pitchOffsidePlayerFromText,
        pitchPlayerLabel,
        resolveEventPeriod,
        resolveEventTeamSide,
        resolveGoalLineX,
        resolveOffsidePoints,
        resolvePitchSlot,
        resolveShotPoints,
        rowsFromPlaceMap,
        shortPlayerName,
        shotTrajectoryStyle,
        shouldFlipPitchY,
        shouldMirrorPitchX,
        sortMatchesByKickoff,
        surnameFromPlayerName,
        todayGroupKey,
        todayGroupLabel,
        todayScoreLabel,
        todayStatusBadge
    } = window.MiniApp;
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

    function resolveTelegramScheme() {
        if (tg.colorScheme === 'light' || tg.colorScheme === 'dark') {
            return tg.colorScheme;
        }
        try {
            return window.matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark';
        } catch (_) {
            return 'dark';
        }
    }

    function syncTelegramChrome(scheme) {
        const header = scheme === 'light' ? '#ece8fd' : '#0b0910';
        const bar = scheme === 'light' ? '#ffffff' : '#14111a';
        try {
            if (typeof tg.setHeaderColor === 'function') tg.setHeaderColor(header);
            if (typeof tg.setBackgroundColor === 'function') tg.setBackgroundColor(header);
            if (typeof tg.setBottomBarColor === 'function') tg.setBottomBarColor(bar);
        } catch (_) { /* older clients */ }
    }

    function applyThemeFromTelegram(options = {}) {
        const scheme = resolveTelegramScheme();
        const root = document.documentElement;
        if (!options.instant) {
            root.setAttribute('data-switching', '');
        }
        root.setAttribute('data-theme', scheme);
        syncTelegramChrome(scheme);
        if (!options.instant) {
            requestAnimationFrame(() => {
                root.removeAttribute('data-switching');
            });
        }
        if (state.chartLoaded && state.chartData) {
            drawPointsChart(state.chartData);
        }
        return scheme;
    }

    function initThemeControls() {
        applyThemeFromTelegram({ instant: true });
        try {
            if (typeof tg.onEvent === 'function') {
                tg.onEvent('themeChanged', () => applyThemeFromTelegram({ instant: true }));
            }
        } catch (_) { /* no-op */ }
    }

    function cssVar(name, fallback) {
        try {
            const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
            return value || fallback;
        } catch (_) {
            return fallback;
        }
    }

    const state = {
        profile: null,
        currentWeekId: null,
        selectedMatch: null,
        selectedTeamCode: null,
        teamModalOpened: false,
        h2hModalOpened: false,
        predictWeekOpened: false,
        predictCupsOpened: false,
        predictCupCompetition: null,
        myWeekOpened: false,
        myCupsOpened: false,
        myCupCompetition: null,
        myPredictionsNavOpened: false,
        profileStatsLoaded: false,
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
        recommendationDetailsOpen: false,
        aiModalOpened: false,
        aiMode: 'score',
        aiCell: null,
        currentRecommendation: null
    };

    const $ = (sel) => document.querySelector(sel);
    const $$ = (sel) => document.querySelectorAll(sel);

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

    function preloadLogos(items) {
        const urls = new Set();
        (items || []).forEach((item) => {
            if (!item) return;
            if (item.homeLogo) urls.add(item.homeLogo);
            if (item.awayLogo) urls.add(item.awayLogo);
            if (item.logo) urls.add(item.logo);
        });
        urls.forEach((src) => {
            if (!src || src.indexOf('/api/miniapp/logos/') !== 0) return;
            const img = new Image();
            img.decoding = 'async';
            img.src = src;
        });
    }

    function setLogoSrc(img, src) {
        if (!img) return;
        if (!src) {
            img.removeAttribute('src');
            img.style.visibility = 'hidden';
            return;
        }
        let tries = 0;
        img.style.visibility = 'visible';
        img.onerror = () => {
            if (tries++ < 2) {
                setTimeout(() => { img.src = src; }, 250 * tries);
            } else {
                img.style.visibility = 'hidden';
            }
        };
        img.src = src;
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
            if (match.cup || match.bonusMatchId) {
                const data = await api('/today');
                const item = (data.matches || []).find(m =>
                    m.cup && Number(m.bonusMatchId || m.publicId) === Number(match.bonusMatchId || match.publicId)
                );
                return !!(item && item.hasPrediction
                    && item.predictHome === homeScore
                    && item.predictAway === awayScore);
            }
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

    function applyPredictionToScoreModal(match, homeScore, awayScore) {
        if (!match) return;
        const hasPrediction = homeScore != null && awayScore != null;
        match.hasPrediction = hasPrediction;
        match.predictHome = hasPrediction ? homeScore : null;
        match.predictAway = hasPrediction ? awayScore : null;
        if (state.selectedMatch
            && state.selectedMatch.homeCode === match.homeCode
            && state.selectedMatch.awayCode === match.awayCode) {
            state.selectedMatch.hasPrediction = match.hasPrediction;
            state.selectedMatch.predictHome = match.predictHome;
            state.selectedMatch.predictAway = match.predictAway;
        }
        $$('#score-modal .score-btn').forEach((btn) => {
            const [h, a] = (btn.textContent || '').split(':').map((v) => parseInt(v, 10));
            btn.classList.toggle('selected', hasPrediction && h === homeScore && a === awayScore);
        });
        const deleteBtn = $('#modal-delete');
        if (deleteBtn) {
            deleteBtn.classList.toggle('hidden', !hasPrediction);
        }
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

    function renderMatchItem(m, onClick) {
        const li = document.createElement('li');
        let cls = 'list-item';
        if (m.weekBonus) cls += ' list-item-week-bonus';
        if (m.cup) {
            cls += ' list-item-cup cup-' + competitionCssKey(m.competition);
            if (m.hasPrediction) cls += ' cup-predicted';
            else if (m.canPredict) cls += ' needs-predict';
        } else if (m.hasPrediction) {
            cls += ' epl-predicted';
        } else if (m.canPredict) {
            cls += ' needs-predict';
        }
        li.className = cls;
        const metaBadges = [];
        if (m.weekBonus) metaBadges.push('<span class="badge badge-bonus">бонус</span>');
        const cupBadge = m.cup ? competitionBadgeHtml(m, !!m.hasPrediction) : '';
        li.innerHTML =
            '<div class="list-item-main">' +
            '<div class="list-item-title">' +
            '<span class="list-item-codes">' + escapeHtml(m.homeCode) + ' — ' + escapeHtml(m.awayCode) + '</span>' +
            cupBadge +
            '</div>' +
            '<div class="list-item-sub">' + escapeHtml(m.homeName) + ' vs ' + escapeHtml(m.awayName) + '</div>' +
            '</div>' +
            '<div class="list-item-meta">' +
            '<div class="score-pill">' + escapeHtml(matchScoreLabel(m)) + '</div>' +
            metaBadges.join('') +
            matchStatusBadge(m) +
            '</div>';
        if (onClick) li.addEventListener('click', () => onClick(m));
        return li;
    }

    function renderTeamMatchItem(m, onClick, kind) {
        const isNext = kind === 'next';
        const formClass = isNext ? 'form-dot-neutral' : teamFormDotClass(m);
        const li = document.createElement('li');
        li.className = 'list-item team-match-item ' + (isNext ? 'team-match-next' : 'team-match-past');
        const datePart = kickoffDateLabel(m.kickoff);
        const sub = isNext
            ? [datePart, (m.weekId != null ? (m.weekId + ' тур') : '')].filter(Boolean).join(' · ')
            : [(m.kickoff || ''), (m.weekId != null ? (m.weekId + ' тур') : '')].filter(Boolean).join(' · ');
        const metaMain = isNext
            ? '<div class="team-match-kick">' + escapeHtml(kickoffTimeLabel(m.kickoff)) + '</div>'
            : '<span class="form-dot ' + formClass + '" aria-hidden="true"></span>'
                + '<div class="score-pill">' + (m.homeScore != null ? (m.homeScore + ' : ' + m.awayScore) : '—') + '</div>';
        li.innerHTML =
            '<div class="list-item-main">' +
            '<div class="list-item-title">' + escapeHtml(m.homeCode || '') + ' — ' + escapeHtml(m.awayCode || '') + '</div>' +
            '<div class="list-item-sub">' + escapeHtml(sub) + '</div>' +
            '</div>' +
            '<div class="list-item-meta">' +
            metaMain +
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
        let cls = 'list-item';
        if (m.weekBonus) cls += ' list-item-week-bonus';
        if (m.cup) {
            cls += ' list-item-cup cup-' + competitionCssKey(m.competition);
            if (m.hasPrediction) cls += ' cup-predicted';
            else if (m.canPredict) cls += ' needs-predict';
        } else if (m.hasPrediction) {
            cls += ' epl-predicted';
        } else {
            cls += ' needs-predict';
        }
        li.className = cls;
        const metaBadges = [];
        if (m.weekBonus) metaBadges.push('<span class="badge badge-bonus">бонус</span>');
        if (!m.hasPrediction && m.canPredict && !m.cup) {
            metaBadges.push('<span class="badge badge-warn">нет прогноза</span>');
        } else if (!m.hasPrediction && m.canPredict && m.cup) {
            metaBadges.push('<span class="badge badge-warn badge-cup-' + competitionCssKey(m.competition) + '">нет прогноза</span>');
        }
        const cupBadge = m.cup ? competitionBadgeHtml(m, !!m.hasPrediction) : '';
        li.innerHTML =
            '<div class="list-item-main">' +
            '<div class="list-item-title">' +
            '<span class="list-item-codes">' + escapeHtml(m.homeCode) + ' — ' + escapeHtml(m.awayCode) + '</span>' +
            cupBadge +
            '</div>' +
            '<div class="list-item-sub">' + escapeHtml(m.homeName) + ' vs ' + escapeHtml(m.awayName) + '</div>' +
            '</div>' +
            '<div class="list-item-meta">' +
            '<div class="score-pill">' + escapeHtml(todayScoreLabel(m)) + '</div>' +
            todayStatusBadge(m) +
            metaBadges.join('') +
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
        let lastGroup = null;
        ordered.forEach(m => {
            const group = todayGroupKey(m);
            if (group !== lastGroup) {
                lastGroup = group;
                const label = document.createElement('li');
                label.className = 'week-label';
                label.textContent = todayGroupLabel(m);
                list.appendChild(label);
            }
            list.appendChild(renderTodayMatchItem(m, openScoreModal));
        });
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
            '<img class="score-alert-logo" src="' + escapeHtml(item.homeLogo) + '" alt="' + escapeHtml(item.homeCode) + '" onerror="this.style.visibility=\'hidden\'">' +
            '<div class="score-alert-center">' +
            '<span class="score-alert-code score-alert-home-code">' + escapeHtml(item.homeCode) + '</span>' +
            '<span class="score-alert-score">' + initialHome + '-' + initialAway + '</span>' +
            '<span class="score-alert-code score-alert-away-code">' + escapeHtml(item.awayCode) + '</span>' +
            '</div>' +
            '<img class="score-alert-logo" src="' + escapeHtml(item.awayLogo) + '" alt="' + escapeHtml(item.awayCode) + '" onerror="this.style.visibility=\'hidden\'">' +
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
                scoreEl.classList.remove('score-bump');
                void scoreEl.offsetWidth;
                scoreEl.classList.add('score-bump');
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
        const currentWeekBtn = $('#leaderboard-current-week-btn');
        if (currentWeekBtn && profile.currentWeekId) {
            currentWeekBtn.textContent = profile.currentWeekId + ' тур';
        }
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
            li.className = 'list-item leaderboard-item';
            li.style.cursor = 'default';
            const shownPoints = e.provisionalPoints != null ? e.provisionalPoints : e.points;
            const liveMeta = e.provisionalPoints != null && data.liveActive
                ? '<span class="leaderboard-live-meta">база ' + e.points
                    + ' · live ' + (e.liveDelta >= 0 ? '+' : '') + e.liveDelta + '</span>'
                : '';
            li.innerHTML =
                '<span class="rank">' + (i + 1) + '</span>' +
                '<div class="list-item-main">' +
                '<div class="list-item-title">' +
                '<span class="list-item-codes">' + escapeHtml(e.login.toUpperCase()) + '</span>' +
                liveMeta +
                '</div>' +
                '</div>' +
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
        preloadLogos(data.matches);
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
            ctx.fillStyle = cssVar('--hint', '#9a8fb0');
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
            ctx.fillStyle = cssVar('--hint', '#9a8fb0');
            ctx.font = '14px sans-serif';
            ctx.fillText('Нет данных', pad.left, height / 2);
            return;
        }
        minY = Math.min(minY, 0);
        maxY = Math.max(maxY, minY + 1);
        const padY = Math.max(1, (maxY - minY) * 0.06);
        const dataMin = minY;
        const dataMax = maxY + padY;

        const majorStep = niceChartStep(dataMax - dataMin, 5);
        const minorStep = majorStep >= 10 ? majorStep / 5 : (majorStep >= 5 ? 1 : majorStep);
        const axisMin = Math.floor(dataMin / majorStep) * majorStep;
        const axisMax = Math.max(axisMin + majorStep, Math.ceil(dataMax / majorStep) * majorStep);
        const yRange = axisMax - axisMin;

        const weekCount = data.weeks.length;
        const xAt = (i) => pad.left + (weekCount <= 1 ? plotW / 2 : (i / (weekCount - 1)) * plotW);
        const yAt = (v) => pad.top + plotH - ((v - axisMin) / yRange) * plotH;

        const isMajor = (tick) => Math.abs(tick / majorStep - Math.round(tick / majorStep)) < 1e-6;
        for (let tick = axisMin; tick <= axisMax + 1e-6; tick += minorStep) {
            const y = yAt(tick);
            const major = isMajor(tick);
            ctx.strokeStyle = major ? 'rgba(255,255,255,0.14)' : 'rgba(255,255,255,0.06)';
            ctx.lineWidth = 1;
            ctx.beginPath();
            ctx.moveTo(pad.left, y);
            ctx.lineTo(pad.left + plotW, y);
            ctx.stroke();
            if (!major) {
                continue;
            }
            ctx.fillStyle = cssVar('--hint', '#9a8fb0');
            ctx.font = '10px sans-serif';
            ctx.textAlign = 'right';
            ctx.fillText(String(Math.round(tick)), pad.left - 6, y + 3);
        }

        ctx.fillStyle = cssVar('--hint', '#9a8fb0');
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
                escapeHtml(s.login.toUpperCase());
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
        tooltip.textContent = node.login.toUpperCase() + ' · нед. ' + node.week + ': ' + node.points;
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
        preloadLogos(rows);
        const tbody = $('#standings-body');
        tbody.innerHTML = '';
        rows.forEach(r => {
            const tr = document.createElement('tr');
            tr.style.cursor = 'pointer';
            if (r.liveScore) {
                tr.classList.add('is-live');
            }
            const delta = Number(r.placeDelta || 0);
            let deltaHtml = '<span class="standings-delta-slot"><span class="standings-delta flat" aria-hidden="true"></span></span>';
            if (delta > 0) {
                deltaHtml = '<span class="standings-delta-slot"><span class="standings-delta up" title="+' + delta + '" aria-label="поднялись на ' + delta + '"></span></span>';
            } else if (delta < 0) {
                deltaHtml = '<span class="standings-delta-slot"><span class="standings-delta down" title="' + delta + '" aria-label="опустились на ' + Math.abs(delta) + '"></span></span>';
            }
            let livePill = '';
            if (r.liveScore) {
                const resultClass = r.liveResult === 'W' ? 'win' : (r.liveResult === 'L' ? 'lose' : 'draw');
                livePill = '<span class="standings-live-pill ' + resultClass + '">'
                    + escapeHtml(String(r.liveScore).replace(/[-–]/g, ':'))
                    + '</span>';
            }
            tr.innerHTML =
                '<td class="standings-place"><span class="standings-place-inner">' + deltaHtml
                + '<span class="standings-place-num">' + r.place + '</span></span></td>' +
                '<td class="standings-club"><span class="club-cell">' +
                '<img class="club-logo" src="' + escapeHtml(r.logo) + '" alt="' + escapeHtml(r.code) + '" onerror="this.style.visibility=\'hidden\'">' +
                '<strong>' + escapeHtml(r.code) + '</strong>' +
                livePill +
                '</span></td>' +
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
        try {
            const cups = await api('/cups');
            const anyPred = (cups || []).some(c => c.hasPredictions);
            const cupBtn = document.createElement('button');
            cupBtn.type = 'button';
            cupBtn.className = 'week-btn week-btn-cups' + (anyPred ? ' has-predictions' : '');
            cupBtn.textContent = 'Кубки';
            cupBtn.addEventListener('click', () => {
                if (containerId === '#predict-weeks') showPredictCups(cups || []);
                else showMyCups(cups || []);
            });
            container.appendChild(cupBtn);
        } catch (_) {
            /* cups optional if API unavailable */
        }
        weeks.forEach(w => {
            const btn = document.createElement('button');
            btn.type = 'button';
            btn.className = 'week-btn' + (w.hasPredictions ? ' has-predictions' : '');
            btn.textContent = w.id;
            btn.addEventListener('click', () => onSelect(w.id));
            container.appendChild(btn);
        });
    }

    async function loadPredictMatches(weekId) {
        const matches = await api('/weeks/' + weekId + '/matches');
        preloadLogos(matches);
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
                const ai = (item.recommendedHome != null && item.recommendedAway != null)
                    ? (' · AI ' + item.recommendedHome + ':' + item.recommendedAway)
                    : '';
                li.innerHTML =
                    '<div class="list-item-main">' +
                    '<div class="list-item-title">' + escapeHtml(item.homeCode) + ' — ' + escapeHtml(item.awayCode) + '</div>' +
                    '<div class="list-item-sub">факт ' + actual + ' · прогноз ' + pred + ai + '</div>' +
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
        closeAiModal(false);
        stopLiveMatchModalPolling();
        state.selectedMatch = match;
        state.recommendationDetailsOpen = false;
        state.currentRecommendation = null;
        renderH2hList('#modal-h2h-content', []);
        renderTeamFormDots('#modal-home-form', match.homeCode, []);
        renderTeamFormDots('#modal-away-form', match.awayCode, []);
        renderModalNews([]);
        renderModalPrematch([], [], [], match);
        renderModalRecommendation(null);
        $('#score-grid').classList.remove('hidden');
        $('#modal-h2h-section').classList.remove('hidden');
        $('#modal-news-section').classList.remove('hidden');
        setModalCenterRegular(match);
        $('#modal-home-code').textContent = match.homeCode || 'HOME';
        $('#modal-away-code').textContent = match.awayCode || 'AWAY';
        setLogoSrc($('#modal-home-logo'), match.homeLogo || '');
        setLogoSrc($('#modal-away-logo'), match.awayLogo || '');
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
            if (!match.cup) {
                loadScoreModalH2h(match).catch(() => {});
                loadScoreModalInsights(match).catch(() => {});
            } else {
                $('#modal-h2h-section').classList.add('hidden');
                $('#modal-news-section').classList.add('hidden');
                renderModalPrematch([], [], [], match);
            }
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
        if (!match.cup) {
            loadScoreModalH2h(match).catch(() => {});
            loadScoreModalInsights(match).catch(() => {});
        } else {
            $('#modal-h2h-section').classList.add('hidden');
            $('#modal-news-section').classList.add('hidden');
            renderModalPrematch([], [], [], match);
        }
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
        setLogoSrc($('#live-modal-home-logo'), match.homeLogo || '');
        setLogoSrc($('#live-modal-away-logo'), match.awayLogo || '');
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
        const liveUrl = match.cup
            ? ('/cups/match/' + encodeURIComponent(match.bonusMatchId || match.publicId) + '/live-details')
            : ('/match/' + encodeURIComponent(match.homeCode) + '/' + encodeURIComponent(match.awayCode) + '/live-details');
        const data = await api(liveUrl);
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
            (jerseyStyle.length ? ' style="' + escapeHtml(jerseyStyle.join(';')) + '"' : '') + '>' +
            (hasImage ? '' : escapeHtml(formationJerseyNumber(player))) +
            '</span>' +
            '<span class="formation-player-name">' + escapeHtml(formationPlayerLabel(player) || '?') + '</span>';
        btn.addEventListener('click', (e) => {
            e.stopPropagation();
            openPlayerModal(player, kitColor);
        });
        return btn;
    }

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
                    '<span class="home">' + escapeHtml(item.homeValue ?? '—') + '</span>' +
                    '<span class="label">' + escapeHtml(item.label) + '</span>' +
                    '<span class="away">' + escapeHtml(item.awayValue ?? '—') + '</span>';
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

    function pitchEventLabel(event) {
        const minute = event.minute || 'live';
        const typeLabel = prettyLiveEventType(event.type);
        const player = pitchPlayerLabel(event);
        if (player) {
            return (minute + ' ' + player + ' · ' + typeLabel).trim();
        }
        return (minute + ' ' + typeLabel).trim();
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
            const minute = e.minute ? '<span>' + escapeHtml(e.minute) + '</span>' : '<span>live</span>';
            const translatedText = translateLiveEventText(e.text || '', e.type);
            row.innerHTML =
                '<div class="h2h-item-head"><span>' + icon + '</span>' + minute + '</div>' +
                '<div class="h2h-item-score">' + escapeHtml(translatedText) + '</div>';
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

    function closeScoreModal(clearSelection) {
        closeAiModal(false);
        const modal = $('#score-modal');
        if (modal) modal.classList.add('hidden');
        if (clearSelection !== false) {
            state.selectedMatch = null;
            state.currentRecommendation = null;
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
            && !state.aiModalOpened
            && !state.predictWeekOpened
            && !state.predictCupsOpened
            && !state.myWeekOpened
            && !state.myCupsOpened
            && !state.myPredictionsNavOpened;
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
            renderModalPrematch(
                insights.injuries || [],
                insights.homeLineup || [],
                insights.awayLineup || [],
                match
            );
            renderModalRecommendation(insights.recommendation || null);
        } catch (_) {
            if (!state.selectedMatch || state.selectedMatch.publicId !== modalMatchId) {
                return;
            }
            renderTeamFormDots('#modal-home-form', match.homeCode, []);
            renderTeamFormDots('#modal-away-form', match.awayCode, []);
            renderModalNews([]);
            renderModalPrematch([], [], [], match);
            renderModalRecommendation(null);
        }
    }

    function renderModalRecommendation(recommendation) {
        const section = $('#modal-recommendation-section');
        const summaryEl = $('#modal-recommendation-summary');
        const scoreEl = $('#modal-recommendation-score');
        if (!section || !summaryEl || !scoreEl) return;

        const enabled = state.profile && state.profile.bettingRecommenderEnabled;
        state.currentRecommendation = (enabled && recommendation) ? recommendation : null;
        if (!state.currentRecommendation) {
            section.classList.add('hidden');
            return;
        }

        section.classList.remove('hidden');
        const score = recommendation.recommendedHome + ':' + recommendation.recommendedAway;
        scoreEl.textContent = score;
        const lambda = 'λ '
            + Number(recommendation.expectedHomeGoals).toFixed(1)
            + ' : '
            + Number(recommendation.expectedAwayGoals).toFixed(1);
        const tipPct = Math.round(Number(recommendation.scoreProbability || 0) * 100);
        summaryEl.textContent = lambda + ' · tip ' + tipPct + '% · открыть матрицу';
    }

    const AI_STAT_METRICS = ['Ожидаемые голы', 'Шанс победы', 'Атака', 'Оборона', 'xG / матч', 'Форма'];

    function signedPoints(v) {
        const n = Number(v);
        return (n > 0 ? '+' : n < 0 ? '−' : '') + Math.abs(n).toFixed(2).replace(/\.?0+$/, '');
    }

    function aiCellValue(mode, insight, h, a, p) {
        const t = insight && insight.breakdown && insight.breakdown[h] && insight.breakdown[h][a];
        if (mode === 'score' || !t) return p;
        if (mode === 'chance') return 1 - t[3];
        const s = insight.scoring;
        return t[0] * s.exact + t[1] * s.diff + t[2] * s.outcome + t[3] * s.miss;
    }

    function aiCellLabel(mode, v) {
        if (mode === 'points') return signedPoints(v);
        return Math.round(v * 100) + '%';
    }

    function renderAiHeatmap(matrix, insight, rec, mode) {
        const el = $('#ai-heatmap');
        if (!el) return;
        const size = 6;
        let maxV = 0;
        let minV = Infinity;
        for (let h = 0; h < size; h++) {
            for (let a = 0; a < size; a++) {
                const v = aiCellValue(mode, insight, h, a, Number((matrix[h] || [])[a] || 0));
                if (v > maxV) maxV = v;
                if (v < minV) minV = v;
            }
        }
        const lo = mode === 'points' ? Math.min(0, minV) : 0;
        const modeScore = insight && insight.mostLikely;
        const sel = state.aiCell;
        let html = '<div class="ai-heatmap-corner" title="дома \\ в гостях"></div>';
        for (let a = 0; a < size; a++) html += '<div class="ai-heatmap-axis">' + a + '</div>';
        for (let h = 0; h < size; h++) {
            html += '<div class="ai-heatmap-axis">' + h + '</div>';
            for (let a = 0; a < size; a++) {
                const p = Number((matrix[h] || [])[a] || 0);
                const v = aiCellValue(mode, insight, h, a, p);
                const heat = maxV > lo ? Math.round(((v - lo) / (maxV - lo)) * 70) : 0;
                const isPick = h === rec.recommendedHome && a === rec.recommendedAway;
                const isMode = modeScore && h === modeScore.home && a === modeScore.away;
                const isSel = sel && sel.h === h && sel.a === a;
                html += '<button type="button" class="ai-heatmap-cell'
                    + (isPick ? ' is-pick' : '') + (isMode ? ' is-mode' : '') + (isSel ? ' is-selected' : '')
                    + '" data-h="' + h + '" data-a="' + a + '" style="--heat:' + heat + ';--i:' + (h + a) + '">'
                    + '<span class="ai-heat-score">' + h + ':' + a + '</span>'
                    + '<span class="ai-heat-pct">' + aiCellLabel(mode, v) + '</span>'
                    + '</button>';
            }
        }
        el.innerHTML = html;
    }

    function renderAiCellDetail(matrix, insight, h, a) {
        const el = $('#ai-cell-detail');
        if (!el) return;
        const t = insight && insight.breakdown && insight.breakdown[h] && insight.breakdown[h][a];
        if (!t) {
            el.innerHTML = '';
            return;
        }
        const s = insight.scoring;
        const ev = t[0] * s.exact + t[1] * s.diff + t[2] * s.outcome + t[3] * s.miss;
        const row = (cls, label, pts, p) => '<li class="' + cls + '"><span>' + label
            + '</span><span>' + signedPoints(pts) + ' · <b>' + pctLabel(p) + '</b></span></li>';
        el.innerHTML = '<div class="ai-cell-title">Если поставить <b>' + h + ':' + a + '</b></div>'
            + '<ul class="ai-cell-tiers">'
            + row('t-exact', 'Точный счёт', s.exact, t[0])
            + row('t-diff', 'Та же разница голов', s.diff, t[1])
            + row('t-outcome', 'Угадан исход', s.outcome, t[2])
            + row('t-miss', 'Мимо', s.miss, t[3])
            + '</ul>'
            + '<div class="ai-cell-total">Шанс очков <b>' + pctLabel(1 - t[3])
            + '</b> · в среднем <b>' + signedPoints(ev) + '</b> за матч</div>';
    }

    function renderAiPointsBar(insight, rec) {
        const bar = $('#ai-points-bar');
        const legend = $('#ai-points-legend');
        const t = insight && insight.breakdown
            && insight.breakdown[rec.recommendedHome] && insight.breakdown[rec.recommendedHome][rec.recommendedAway];
        if (!t) {
            bar.classList.add('hidden');
            legend.classList.add('hidden');
            return;
        }
        const s = insight.scoring;
        const names = ['exact', 'diff', 'outcome', 'miss'];
        const labels = ['точный', 'разница', 'исход', 'мимо'];
        const pts = [s.exact, s.diff, s.outcome, s.miss];
        bar.innerHTML = t.map((p, i) => '<span class="seg seg-' + names[i] + '" style="flex:' + Math.max(p, 0.001) + '"></span>').join('');
        legend.innerHTML = t.map((p, i) => '<span class="leg"><i class="dot seg-' + names[i] + '"></i>'
            + labels[i] + ' ' + signedPoints(pts[i]) + ' · <b>' + pctLabel(p) + '</b></span>').join('');
        bar.classList.remove('hidden');
        legend.classList.remove('hidden');
    }

    function openAiModal() {
        const recommendation = state.currentRecommendation;
        const match = state.selectedMatch;
        const modal = $('#ai-modal');
        if (!recommendation || !modal) return;

        const homeCode = (match && match.homeCode) || 'HOME';
        const awayCode = (match && match.awayCode) || 'AWAY';
        const dist = recommendation.distribution;
        const insight = recommendation.insight;
        const hasMatrix = !!(dist && dist.matrix && dist.matrix.length);
        const pickT = insight && insight.breakdown
            && (insight.breakdown[recommendation.recommendedHome] || [])[recommendation.recommendedAway];

        $('#ai-modal-title').textContent = homeCode + ' — ' + awayCode;
        $('#ai-modal-tip-score').textContent =
            recommendation.recommendedHome + ':' + recommendation.recommendedAway;
        $('#ai-modal-tip-prob').textContent = pickT
            ? 'Очки получим с шансом ' + pctLabel(1 - pickT[3]) + ' · точный счёт ' + pctLabel(pickT[0])
              + ' · в среднем ' + signedPoints(insight.expectedPoints) + ' за матч'
            : 'Шанс точного счёта ' + pctLabel(recommendation.scoreProbability);
        renderAiPointsBar(insight, recommendation);

        const reasonsSection = $('#ai-modal-reasons-section');
        const reasons = (insight && insight.reasons) || [];
        const reasonsEl = $('#ai-reasons');
        reasonsEl.innerHTML = '';
        reasons.forEach((line) => {
            const li = document.createElement('li');
            li.textContent = line;
            reasonsEl.appendChild(li);
        });
        reasonsSection.classList.toggle('hidden', !reasons.length);

        const marketsSection = $('#ai-modal-markets');
        const heatmapSection = $('#ai-modal-heatmap-section');
        if (hasMatrix) {
            marketsSection.classList.remove('hidden');
            heatmapSection.classList.remove('hidden');
            $('#ai-markets-1x2').innerHTML =
                marketChipHtml('1', dist.homeWin)
                + marketChipHtml('X', dist.draw)
                + marketChipHtml('2', dist.awayWin);
            $('#ai-markets-extra').innerHTML =
                marketChipHtml('ОЗ', dist.btts)
                + marketChipHtml('ТБ 1.5', dist.over15)
                + marketChipHtml('ТБ 2.5', dist.over25)
                + marketChipHtml('ТБ 3.5', dist.over35);
            state.aiMode = 'score';
            state.aiCell = { h: recommendation.recommendedHome, a: recommendation.recommendedAway };
            $$('#ai-heat-modes .ai-heat-mode').forEach((b) => b.classList.toggle('is-active', b.dataset.mode === 'score'));
            renderAiHeatmap(dist.matrix, insight, recommendation, state.aiMode);
            renderAiCellDetail(dist.matrix, insight, state.aiCell.h, state.aiCell.a);
            const heatEl = $('#ai-heatmap');
            heatEl.classList.remove('intro');
            void heatEl.offsetWidth;
            heatEl.classList.add('intro');
            setTimeout(() => heatEl.classList.remove('intro'), 1200);
        } else {
            marketsSection.classList.add('hidden');
            heatmapSection.classList.add('hidden');
            $('#ai-heatmap').innerHTML = '';
        }

        const statsSection = $('#ai-modal-stats-section');
        const rows = (recommendation.explanationRows || []).filter((r) => AI_STAT_METRICS.includes(r.metric));
        const tableWrap = $('#ai-modal-table-wrap');
        if (rows.length) {
            let html = '<table class="modal-recommendation-table"><thead><tr>'
                + '<th></th>'
                + '<th class="col-side">' + escapeHtml(homeCode) + '</th>'
                + '<th class="col-side">' + escapeHtml(awayCode) + '</th>'
                + '</tr></thead><tbody>';
            rows.forEach((row) => {
                html += '<tr>'
                    + '<td class="col-metric">' + escapeHtml(row.metric || '') + '</td>'
                    + '<td class="col-side">' + escapeHtml(row.home != null ? row.home : '—') + '</td>'
                    + '<td class="col-side">' + escapeHtml(row.away != null ? row.away : '—') + '</td>'
                    + '</tr>';
            });
            tableWrap.innerHTML = html + '</tbody></table>';
            statsSection.classList.remove('hidden');
        } else {
            tableWrap.innerHTML = '';
            statsSection.classList.add('hidden');
        }

        state.aiModalOpened = true;
        modal.classList.remove('hidden');
        tg.BackButton.show();
    }

    function onAiHeatmapInteraction(event) {
        const recommendation = state.currentRecommendation;
        const dist = recommendation && recommendation.distribution;
        if (!dist || !dist.matrix) return;
        const insight = recommendation.insight;
        const modeBtn = event.target.closest('.ai-heat-mode');
        const cell = event.target.closest('.ai-heatmap-cell');
        if (modeBtn) {
            state.aiMode = modeBtn.dataset.mode;
            $$('#ai-heat-modes .ai-heat-mode').forEach((b) => b.classList.toggle('is-active', b === modeBtn));
        } else if (cell) {
            state.aiCell = { h: Number(cell.dataset.h), a: Number(cell.dataset.a) };
        } else {
            return;
        }
        renderAiHeatmap(dist.matrix, insight, recommendation, state.aiMode);
        renderAiCellDetail(dist.matrix, insight, state.aiCell.h, state.aiCell.a);
    }

    function closeAiModal(manageBackButton) {
        const modal = $('#ai-modal');
        if (modal) modal.classList.add('hidden');
        state.aiModalOpened = false;
        if (manageBackButton !== false && shouldHideBackButton()) {
            tg.BackButton.hide();
        } else if (manageBackButton !== false && state.selectedMatch && !$('#score-modal').classList.contains('hidden')) {
            tg.BackButton.show();
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
            btn.setAttribute('aria-label', item.home
                ? (teamCode + ' ' + (item.ownScore ?? 0) + ':' + (item.opponentScore ?? 0) + ' ' + (item.opponentCode || '?'))
                : ((item.opponentCode || '?') + ' ' + (item.opponentScore ?? 0) + ':' + (item.ownScore ?? 0) + ' ' + teamCode));
            btn.addEventListener('click', () => {
                const own = item.ownScore ?? 0;
                const opp = item.opponentScore ?? 0;
                const opponent = item.opponentCode || '?';
                const datePart = item.kickoff ? (' · ' + item.kickoff) : '';
                // Real match order: home first, so away games read e.g. NOT 0:0 TOT
                const line = item.home
                    ? (teamCode + ' ' + own + ':' + opp + ' ' + opponent)
                    : (opponent + ' ' + opp + ':' + own + ' ' + teamCode);
                showToast(line + datePart);
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
            row.href = /^https?:\/\//i.test(item.url || '') ? item.url : '#';
            row.target = '_blank';
            row.rel = 'noopener noreferrer';
            const published = item.publishedAt ? ('<span class="modal-news-time">' + escapeHtml(item.publishedAt) + '</span>') : '';
            row.innerHTML =
                '<span class="modal-news-title">' + escapeHtml(item.title) + '</span>' +
                published;
            container.appendChild(row);
        });
    }

    function renderModalPrematch(injuries, homeLineup, awayLineup, match) {
        const section = $('#modal-prematch-section');
        const lineupsBlock = $('#modal-lineups-block');
        const lineupsContainer = $('#modal-lineups-content');
        const injuriesBlock = $('#modal-injuries-block');
        const injuriesContainer = $('#modal-injuries-content');
        if (!section) return;

        const homeCode = (match && match.homeCode) || 'HOME';
        const awayCode = (match && match.awayCode) || 'AWAY';
        const homeXi = homeLineup || [];
        const awayXi = awayLineup || [];
        const hasLineups = homeXi.length > 0 || awayXi.length > 0;
        const injuryItems = injuries || [];
        const hasInjuries = injuryItems.length > 0;

        if (lineupsBlock && lineupsContainer) {
            if (!hasLineups) {
                lineupsBlock.classList.add('hidden');
                lineupsContainer.innerHTML = '';
            } else {
                lineupsBlock.classList.remove('hidden');
                lineupsContainer.innerHTML = '<div class="modal-lineups-grid">'
                    + lineupColHtml(homeCode, homeXi)
                    + lineupColHtml(awayCode, awayXi)
                    + '</div>';
            }
        }

        if (injuriesBlock && injuriesContainer) {
            if (!hasInjuries) {
                injuriesBlock.classList.add('hidden');
                injuriesContainer.innerHTML = '';
            } else {
                injuriesBlock.classList.remove('hidden');
                const home = injuryItems.filter((i) => i.teamCode === homeCode);
                const away = injuryItems.filter((i) => i.teamCode === awayCode);
                injuriesContainer.innerHTML = '<div class="modal-injuries-grid">'
                    + injuryColHtml(homeCode, home)
                    + injuryColHtml(awayCode, away)
                    + '</div>';
            }
        }

        section.classList.toggle('hidden', !hasLineups && !hasInjuries);
    }

    function lineupColHtml(code, list) {
        if (!list.length) {
            return '<div class="modal-lineups-col">'
                + '<div class="modal-lineups-code">' + escapeHtml(code) + '</div>'
                + '<p class="empty-state">—</p></div>';
        }
        let html = '<div class="modal-lineups-col">'
            + '<div class="modal-lineups-code">' + escapeHtml(code) + '</div><ul class="modal-lineups-list">';
        list.forEach((p) => {
            const num = p.number != null ? String(p.number) : '';
            html += '<li>'
                + (num ? ('<span class="modal-lineups-num">' + escapeHtml(num) + '</span>') : '')
                + '<span class="modal-lineups-name">' + escapeHtml(shortPlayerName(p.name)) + '</span>'
                + '</li>';
        });
        html += '</ul></div>';
        return html;
    }

    function injuryColHtml(code, list) {
        if (!list.length) {
            return '<div class="modal-injuries-col">'
                + '<div class="modal-injuries-code">' + escapeHtml(code) + '</div>'
                + '<p class="empty-state">—</p></div>';
        }
        let html = '<div class="modal-injuries-col">'
            + '<div class="modal-injuries-code">' + escapeHtml(code) + '</div><ul class="modal-injuries-list">';
        list.forEach((item) => {
            const meta = injuryKindMeta(item.kind);
            html += '<li>'
                + '<span class="modal-injury-kind ' + meta.cls + '" title="' + escapeHtml(meta.title) + '" aria-label="'
                + escapeHtml(meta.title) + '">' + meta.symbol + '</span>'
                + '<span class="modal-injuries-name">' + escapeHtml(item.playerName || '') + '</span>'
                + '</li>';
        });
        html += '</ul></div>';
        return html;
    }

    function renderH2hList(containerSelector, items, errorMessage) {
        const container = $(containerSelector);
        if (!container) return;
        if (errorMessage) {
            container.innerHTML = '<p class="empty-state">' + escapeHtml(errorMessage) + '</p>';
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
                '<span>' + escapeHtml(h.leagueName || 'Лига') + '</span>' +
                '<span>' + escapeHtml(h.kickoff) + '</span>' +
                '</div>' +
                '<div class="h2h-item-score">' + escapeHtml(h.homeCode) + ' ' + (h.homeScore ?? '-') + ' : ' + (h.awayScore ?? '-') + ' ' + escapeHtml(h.awayCode) + '</div>';
            container.appendChild(row);
        });
    }

    function bindTeamProfileTap(el, getCode) {
        if (!el || el.dataset.teamTapBound === '1') return;
        el.dataset.teamTapBound = '1';
        el.classList.add('is-clickable');
        el.setAttribute('role', 'button');
        el.tabIndex = 0;
        const open = (e) => {
            e.preventDefault();
            e.stopPropagation();
            const code = typeof getCode === 'function' ? getCode() : getCode;
            if (!code) {
                showToast('Нет профиля команды', 'error');
                return;
            }
            openTeamModal(code);
        };
        el.addEventListener('click', open);
        el.addEventListener('keydown', (e) => {
            if (e.key === 'Enter' || e.key === ' ') open(e);
        });
    }

    function wireMatchHeaderTeamTaps() {
        bindTeamProfileTap($('#modal-home-logo'), () => state.selectedMatch && state.selectedMatch.homeCode);
        bindTeamProfileTap($('#modal-away-logo'), () => state.selectedMatch && state.selectedMatch.awayCode);
        bindTeamProfileTap($('#modal-home-code'), () => state.selectedMatch && state.selectedMatch.homeCode);
        bindTeamProfileTap($('#modal-away-code'), () => state.selectedMatch && state.selectedMatch.awayCode);
        bindTeamProfileTap($('#live-modal-home-logo'), () => state.selectedMatch && state.selectedMatch.homeCode);
        bindTeamProfileTap($('#live-modal-away-logo'), () => state.selectedMatch && state.selectedMatch.awayCode);
        bindTeamProfileTap($('#live-modal-home-code'), () => state.selectedMatch && state.selectedMatch.homeCode);
        bindTeamProfileTap($('#live-modal-away-code'), () => state.selectedMatch && state.selectedMatch.awayCode);
    }

    async function openTeamModal(teamCode) {
        if (!teamCode) {
            showToast('Нет профиля команды', 'error');
            return;
        }
        state.selectedTeamCode = teamCode;
        state.teamModalOpened = true;
        $('#team-modal-title').textContent = teamCode;
        $('#team-profile-code').textContent = teamCode;
        $('#team-profile-meta').textContent = 'Загрузка…';
        setLogoSrc($('#team-profile-logo'), '');
        renderTeamFormDots('#team-profile-form', teamCode, []);
        $('#team-stats-section').classList.add('hidden');
        $('#team-stats-grid').innerHTML = '';
        $('#team-leaders-section').classList.add('hidden');
        $('#team-leaders-table-wrap').innerHTML = '';
        $('#team-last-list').innerHTML = '<li class="empty-state">Загрузка…</li>';
        $('#team-next-list').innerHTML = '<li class="empty-state">Загрузка…</li>';
        $('#team-modal').classList.remove('hidden');
        tg.BackButton.show();
        try {
            const data = await api('/team/' + encodeURIComponent(teamCode));
            $('#team-modal-title').textContent = data.teamName || teamCode;
            $('#team-profile-code').textContent = data.teamCode || teamCode;
            setLogoSrc($('#team-profile-logo'), data.logo || '');
            const metaParts = [];
            if (data.place != null) metaParts.push(data.place + ' место');
            if (data.points != null) metaParts.push(data.points + ' очк.');
            if (data.played != null) {
                metaParts.push('И ' + data.played
                    + ' · В ' + (data.won ?? '—')
                    + ' · Н ' + (data.drawn ?? '—')
                    + ' · П ' + (data.lost ?? '—'));
            }
            if (data.goalsFor != null && data.goalsAgainst != null) {
                metaParts.push('Мячи ' + data.goalsFor + ':' + data.goalsAgainst);
            }
            $('#team-profile-meta').textContent = metaParts.join(' · ') || '';
            renderTeamFormDots('#team-profile-form', data.teamCode || teamCode, data.form || []);
            renderTeamSeasonStats(data.seasonStats || null);
            renderTeamLeaders(data.leaders || []);
            fillTeamMatchesList('#team-last-list', data.lastMatches || [], 'past');
            fillTeamMatchesList('#team-next-list', data.upcomingMatches || [], 'next');
        } catch (e) {
            $('#team-profile-meta').textContent = e.message || 'Ошибка';
            $('#team-stats-section').classList.add('hidden');
            $('#team-stats-grid').innerHTML = '';
            $('#team-last-list').innerHTML = '<li class="empty-state">' + escapeHtml(e.message || 'Ошибка') + '</li>';
            $('#team-next-list').innerHTML = '<li class="empty-state">—</li>';
        }
    }

    function renderTeamSeasonStats(stats) {
        const section = $('#team-stats-section');
        const grid = $('#team-stats-grid');
        if (!section || !grid) return;
        if (!stats) {
            section.classList.add('hidden');
            grid.innerHTML = '';
            return;
        }
        const cells = [
            { label: 'xG', value: fmtStatNum(stats.xg) },
            { label: 'xGA', value: fmtStatNum(stats.xga) },
            { label: 'Г / М', value: fmtStatNum(stats.scoredPerMatch) },
            { label: 'Пр / М', value: fmtStatNum(stats.concededPerMatch) },
            { label: 'ОЗ', value: fmtStatPct(stats.bttsPct) },
            { label: 'СМ %', value: fmtStatPct(stats.csPct) },
            { label: 'ТБ 2.5', value: fmtStatPct(stats.over25Pct) },
            { label: 'ППГ дом', value: fmtStatNum(stats.homePpg) },
            { label: 'ППГ выезд', value: fmtStatNum(stats.awayPpg) },
            { label: 'Владение', value: fmtStatPct(stats.possessionPct) },
            { label: 'Удары / М', value: fmtStatNum(stats.shotsPerMatch) },
            { label: 'В створ / М', value: fmtStatNum(stats.shotsOnTargetPerMatch) },
            { label: 'Сухие', value: stats.cleanSheets != null ? String(stats.cleanSheets) : null }
        ].filter((c) => c.value != null);

        if (!cells.length) {
            section.classList.add('hidden');
            grid.innerHTML = '';
            return;
        }
        section.classList.remove('hidden');
        grid.innerHTML = cells.map((c) =>
            '<div class="team-stat-cell">'
            + '<span class="team-stat-label">' + escapeHtml(c.label) + '</span>'
            + '<span class="team-stat-value">' + escapeHtml(c.value) + '</span>'
            + '</div>'
        ).join('');
    }

    function renderTeamLeaders(leaders) {
        const section = $('#team-leaders-section');
        const wrap = $('#team-leaders-table-wrap');
        if (!section || !wrap) return;
        if (!leaders.length) {
            section.classList.add('hidden');
            wrap.innerHTML = '';
            return;
        }
        section.classList.remove('hidden');
        let html = '<table class="team-leaders-table"><thead><tr>'
            + '<th>Игрок</th>'
            + '<th class="num">И</th>'
            + '<th class="num">Г</th>'
            + '<th class="num">А</th>'
            + '<th class="num">Сейв</th>'
            + '</tr></thead><tbody>';
        leaders.forEach((p) => {
            const saves = p.saves != null ? String(p.saves) : '—';
            html += '<tr>'
                + '<td>' + escapeHtml(p.name || '')
                + (p.position ? ('<span class="pos">' + escapeHtml(p.position) + '</span>') : '')
                + '</td>'
                + '<td class="num">' + (p.appearances ?? 0) + '</td>'
                + '<td class="num">' + (p.goals ?? 0) + '</td>'
                + '<td class="num">' + (p.assists ?? 0) + '</td>'
                + '<td class="num">' + saves + '</td>'
                + '</tr>';
        });
        html += '</tbody></table>';
        wrap.innerHTML = html;
    }

    function fillTeamMatchesList(selector, matches, kind) {
        const list = $(selector);
        list.innerHTML = '';
        if (!matches.length) {
            list.innerHTML = '<li class="empty-state">Нет матчей</li>';
            return;
        }
        matches.forEach((m) => list.appendChild(renderTeamMatchItem(m, openH2hModalForMatch, kind)));
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
            const body = {
                homeCode: match.homeCode,
                awayCode: match.awayCode,
                homeScore,
                awayScore
            };
            if (match.cup && match.bonusMatchId != null) {
                body.bonusMatchId = match.bonusMatchId;
            }
            const res = await apiWithRetry('/predictions', {
                method: 'POST',
                body: JSON.stringify(body)
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
            applyPredictionToScoreModal(match, homeScore, awayScore);
            await refreshAfterPredictionChange();
        } catch (e) {
            const verified = await verifyPredictionSaved(match, homeScore, awayScore);
            if (verified) {
                showToast('Прогноз сохранён (подтверждено на сервере)', 'success');
                tg.HapticFeedback?.notificationOccurred('success');
                applyPredictionToScoreModal(match, homeScore, awayScore);
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
            const path = (m.cup && m.bonusMatchId != null)
                ? '/predictions?bonusMatchId=' + encodeURIComponent(m.bonusMatchId)
                : '/predictions?homeCode=' + encodeURIComponent(m.homeCode) + '&awayCode=' + encodeURIComponent(m.awayCode);
            const res = await apiWithRetry(path, { method: 'DELETE' });
            showToast(res.message, res.ok ? 'success' : 'error');
            if (res.ok) {
                applyPredictionToScoreModal(m, null, null);
                await refreshAfterPredictionChange();
            }
        } catch (e) {
            showToast(e.message || 'Не удалось удалить прогноз', 'error');
        } finally {
            setScoreButtonsDisabled(false);
        }
    }

    /** One-shot entrance animation (CSS .entering); removed so background re-renders do not replay it. */
    function playEntrance(el) {
        el.classList.remove('entering');
        void el.offsetWidth;
        el.classList.add('entering');
        setTimeout(() => el.classList.remove('entering'), 900);
    }

    async function showScreen(name) {
        $$('.screen').forEach(s => s.classList.remove('active'));
        $$('.nav-btn').forEach(b => b.classList.remove('active'));
        const screenEl = $('#screen-' + name);
        screenEl.classList.add('active');
        playEntrance(screenEl);
        document.querySelector('[data-screen="' + name + '"]').classList.add('active');
        try {
            if (name === 'today' && !state.todayLoaded) {
                await loadTodayMatches();
            }
            if (name === 'stats' && !state.chartLoaded) {
                await loadPointsChart();
            }
            if (name === 'my') {
                resetProfileHomeView(false);
                await loadProfileStats();
            }
        } catch (e) {
            showToast(e.message, 'error');
        }
    }

    function resetProfileHomeView(showBack) {
        const home = $('#profile-home');
        if (home) home.classList.remove('hidden');
        $('#my-weeks').classList.add('hidden');
        $('#my-cups').classList.add('hidden');
        $('#my-predictions').classList.add('hidden');
        const review = $('#my-review-card');
        if (review) review.classList.add('hidden');
        state.myWeekOpened = false;
        state.myCupsOpened = false;
        state.myCupCompetition = null;
        state.myPredictionsNavOpened = false;
        if (!showBack) {
            tg.BackButton.hide();
        }
    }

    async function openMyPredictionsNav() {
        const home = $('#profile-home');
        if (home) home.classList.add('hidden');
        $('#my-predictions').classList.add('hidden');
        $('#my-cups').classList.add('hidden');
        $('#my-weeks').classList.remove('hidden');
        state.myPredictionsNavOpened = true;
        state.myWeekOpened = false;
        state.myCupsOpened = false;
        state.myCupCompetition = null;
        await loadWeeksGrid('#my-weeks', showMyWeek);
        tg.BackButton.show();
    }

    function closeMyPredictionsNav() {
        resetProfileHomeView(false);
    }

    function renderProfileTeamList(container, rows) {
        container.innerHTML = '';
        if (!rows || !rows.length) {
            container.innerHTML = '<li class="empty-state">Пока нет данных</li>';
            return;
        }
        rows.forEach((row) => {
            const li = document.createElement('li');
            const hasAvg = row.avgPoints != null && row.matches > 0;
            li.className = 'profile-team-row' + (hasAvg ? '' : ' muted');
            const logo = row.logo
                ? '<img class="profile-team-logo" src="' + escapeHtml(row.logo) + '" alt="">'
                : '<span class="profile-team-logo"></span>';
            let avgLabel = '—';
            if (hasAvg) {
                const avg = row.avgPoints;
                avgLabel = (avg > 0 ? '+' : '') + String(avg);
            }
            li.innerHTML =
                logo +
                '<div class="profile-team-main">' +
                '<div class="profile-team-code">' + escapeHtml(row.teamCode) + '</div>' +
                '<div class="profile-team-sub">' + escapeHtml(hasAvg ? (row.matches + ' матч.') : 'нет матчей') + '</div>' +
                '</div>' +
                '<div class="profile-team-pts">' + escapeHtml(avgLabel) + '</div>';
            li.title = row.hint || '';
            container.appendChild(li);
        });
    }

    function renderProfileStats(data) {
        $('#profile-login').textContent = data.login || 'Профиль';
        $('#profile-week-label').textContent = data.weekLabel || ('Сезон ' + data.season);
        $('#profile-meta').textContent =
            'Сезон ' + (data.seasonPoints ?? 0) + ' очк. · тур ' + (data.currentWeekPoints ?? 0) + ' очк.' +
            (data.predictedFinished ? ' · ' + data.predictedFinished + ' матч. с прогнозом' : '');

        const bonus = $('#profile-bonus-chip');
        if (data.bonusMatchLabel) {
            bonus.classList.remove('hidden');
            bonus.innerHTML = 'Бонус этого тура: <strong>' + escapeHtml(data.bonusMatchLabel) + '</strong>';
        } else {
            bonus.classList.add('hidden');
            bonus.textContent = '';
        }

        const highlights = $('#profile-highlights');
        highlights.innerHTML = '';
        (data.highlights || []).forEach((h) => {
            const cell = document.createElement('div');
            cell.className = 'team-stat-cell';
            cell.title = h.hint || '';
            cell.innerHTML =
                '<span class="team-stat-label">' + escapeHtml(h.label) + '</span>' +
                '<span class="team-stat-value">' + escapeHtml(h.value) + '</span>';
            highlights.appendChild(cell);
        });

        const breakdown = $('#profile-breakdown');
        breakdown.innerHTML = '';
        (data.breakdown || []).forEach((row) => {
            const el = document.createElement('div');
            el.className = 'profile-breakdown-row';
            const width = parsePercentWidth(row.percent);
            el.innerHTML =
                '<div class="profile-breakdown-top">' +
                '<span class="profile-breakdown-label">' + escapeHtml(row.label) + '</span>' +
                '<span class="profile-breakdown-value">' + escapeHtml(row.count + ' · ' + row.percent) + '</span>' +
                '</div>' +
                '<div class="profile-breakdown-bar"><span style="width:' + width + '%"></span></div>' +
                '<p class="profile-breakdown-desc">' + escapeHtml(row.description || '') + '</p>';
            breakdown.appendChild(el);
        });

        const teamsSection = $('#profile-teams-section');
        const teams = data.teams || [];
        teamsSection.classList.toggle('hidden', !teams.length);
        if (teams.length) {
            renderProfileTeamList($('#profile-teams-list'), teams);
        }

        const habitsSection = $('#profile-habits-section');
        const habits = $('#profile-habits');
        habits.innerHTML = '';
        const habitRows = data.habits || [];
        habitsSection.classList.toggle('hidden', !habitRows.length);
        habitRows.forEach((h) => {
            const el = document.createElement('div');
            el.className = 'profile-habit-row';
            el.innerHTML =
                '<div class="profile-habit-top">' +
                '<span class="profile-habit-label">' + escapeHtml(h.label) + '</span>' +
                '<span class="profile-habit-value">' + escapeHtml(h.value) + '</span>' +
                '</div>' +
                '<p class="profile-habit-desc">' + escapeHtml(h.description || '') + '</p>';
            habits.appendChild(el);
        });

        const weeksSection = $('#profile-weeks-section');
        const weeks = $('#profile-weeks');
        weeks.innerHTML = '';
        const weekBits = [];
        if (data.bestWeek) {
            weekBits.push({
                label: 'Лучший тур',
                value: data.bestWeek.weekId + ' · ' + data.bestWeek.points + ' очк.',
                description: data.bestWeek.description
            });
        }
        if (data.worstWeek && (!data.bestWeek || data.worstWeek.weekId !== data.bestWeek.weekId || data.worstWeek.points !== data.bestWeek.points)) {
            weekBits.push({
                label: 'Слабый тур',
                value: data.worstWeek.weekId + ' · ' + data.worstWeek.points + ' очк.',
                description: data.worstWeek.description
            });
        }
        weekBits.push({
            label: 'Бонус-матчи',
            value: data.bonusMatchCount
                ? (data.bonusMatchCount + (data.bonusAvgPoints != null ? ' · ср. ' + data.bonusAvgPoints : ''))
                : 'пока нет',
            description: data.bonusHint || ''
        });
        weeksSection.classList.remove('hidden');
        weekBits.forEach((h) => {
            const el = document.createElement('div');
            el.className = 'profile-habit-row';
            el.innerHTML =
                '<div class="profile-habit-top">' +
                '<span class="profile-habit-label">' + escapeHtml(h.label) + '</span>' +
                '<span class="profile-habit-value">' + escapeHtml(String(h.value)) + '</span>' +
                '</div>' +
                '<p class="profile-habit-desc">' + escapeHtml(h.description || '') + '</p>';
            weeks.appendChild(el);
        });
    }

    async function loadProfileStats() {
        const data = await api('/profile/stats');
        renderProfileStats(data);
        state.profileStatsLoaded = true;
    }

    function renderCupCompetitionsGrid(containerId, cups, onSelect, onBack) {
        const container = $(containerId);
        if (!container) return;
        container.innerHTML = '';
        if (onBack) {
            const back = document.createElement('button');
            back.type = 'button';
            back.className = 'back-link';
            back.innerHTML = '&#8592; Назад';
            back.addEventListener('click', onBack);
            container.appendChild(back);
        }
        if (!cups.length) {
            const empty = document.createElement('div');
            empty.className = 'empty-state';
            empty.textContent = 'Актуальных кубковых матчей нет';
            container.appendChild(empty);
            return;
        }
        const title = document.createElement('div');
        title.className = 'cup-section-title';
        title.textContent = 'Кубковые турниры';
        container.appendChild(title);
        const list = document.createElement('div');
        list.className = 'cup-card-list';
        cups.forEach(c => {
            const key = competitionCssKey(c.competition);
            const btn = document.createElement('button');
            btn.type = 'button';
            btn.className = 'cup-card cup-' + key + (c.hasPredictions ? ' has-predictions' : '');
            btn.innerHTML =
                '<span class="cup-card-accent" aria-hidden="true"></span>' +
                '<span class="cup-card-body">' +
                '<span class="cup-card-label">' + escapeHtml(c.label || competitionShort(c.competition)) + '</span>' +
                '</span>' +
                '<span class="cup-card-chevron" aria-hidden="true">›</span>';
            btn.addEventListener('click', () => onSelect(c));
            list.appendChild(btn);
        });
        container.appendChild(list);
    }

    async function showPredictCups(preloaded) {
        const cups = preloaded || await api('/cups');
        $('#predict-weeks').classList.add('hidden');
        $('#predict-matches').classList.add('hidden');
        const grid = $('#predict-cups');
        grid.classList.remove('hidden');
        grid.classList.add('cup-panel');
        state.predictCupsOpened = true;
        state.predictWeekOpened = false;
        state.predictCupCompetition = null;
        renderCupCompetitionsGrid('#predict-cups', cups, showPredictCupCompetition, () => {
            closePredictCupNav();
            tg.BackButton.hide();
        });
        tg.BackButton.show();
    }

    async function showPredictCupCompetition(comp) {
        state.predictCupCompetition = comp.competition;
        $('#predict-cups').classList.add('hidden');
        const block = $('#predict-matches');
        block.classList.remove('hidden');
        state.predictWeekOpened = true;
        block.dataset.weekId = '';
        block.dataset.competition = comp.competition;
        $('#predict-week-title').textContent = comp.label || competitionShort(comp.competition);
        const list = $('#predict-match-list');
        list.innerHTML = '';
        try {
            const matches = await api('/cups/' + encodeURIComponent(comp.competition) + '/matches');
            const ordered = sortMatchesByKickoff(matches || []);
            if (!ordered.length) {
                list.innerHTML = '<li class="empty-state">Матчей нет</li>';
            } else {
                ordered.forEach(m => list.appendChild(renderMatchItem(m, openScoreModal)));
            }
        } catch (e) {
            list.innerHTML = '<li class="empty-state">' + escapeHtml(e.message || 'Ошибка') + '</li>';
        }
        tg.BackButton.show();
    }

    async function showMyCups(preloaded) {
        const cups = preloaded || await api('/cups');
        const home = $('#profile-home');
        if (home) home.classList.add('hidden');
        $('#my-weeks').classList.add('hidden');
        $('#my-predictions').classList.add('hidden');
        const grid = $('#my-cups');
        grid.classList.remove('hidden');
        grid.classList.add('cup-panel');
        state.myCupsOpened = true;
        state.myWeekOpened = false;
        state.myCupCompetition = null;
        state.myPredictionsNavOpened = true;
        renderCupCompetitionsGrid('#my-cups', cups, showMyCupCompetition, () => {
            closeMyCupNav();
        });
        tg.BackButton.show();
    }

    async function showMyCupCompetition(comp) {
        state.myCupCompetition = comp.competition;
        $('#my-cups').classList.add('hidden');
        const block = $('#my-predictions');
        block.classList.remove('hidden');
        state.myWeekOpened = true;
        block.dataset.weekId = '';
        block.dataset.competition = comp.competition;
        const card = $('#my-review-card');
        const list = $('#my-review-list');
        const title = $('#my-review-title');
        card.classList.remove('hidden');
        title.textContent = (comp.label || competitionShort(comp.competition)) + ' · …';
        list.innerHTML = '';
        try {
            const data = await api('/cups/' + encodeURIComponent(comp.competition) + '/review');
            title.textContent = (data.label || competitionShort(comp.competition)) + ' · ' + data.totalPoints + ' очк.';
            if (!data.items.length) {
                list.innerHTML = '<li class="empty-state">Нет матчей</li>';
            } else {
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
                        '<div class="list-item-title">' + escapeHtml(item.homeCode) + ' — ' + escapeHtml(item.awayCode) + '</div>' +
                        '<div class="list-item-sub">факт ' + actual + ' · прогноз ' + pred + '</div>' +
                        '</div>' +
                        '<span class="pts">' + pts + '</span>';
                    list.appendChild(li);
                });
            }
        } catch (e) {
            title.textContent = comp.label || competitionShort(comp.competition);
            list.innerHTML = '<li class="empty-state">' + escapeHtml(e.message || 'Ошибка') + '</li>';
        }
        tg.BackButton.show();
    }

    function closePredictCupNav() {
        $('#predict-matches').classList.add('hidden');
        $('#predict-cups').classList.add('hidden');
        $('#predict-weeks').classList.remove('hidden');
        state.predictWeekOpened = false;
        state.predictCupsOpened = false;
        state.predictCupCompetition = null;
    }

    function closeMyCupNav() {
        $('#my-predictions').classList.add('hidden');
        $('#my-cups').classList.add('hidden');
        $('#my-weeks').classList.remove('hidden');
        const review = $('#my-review-card');
        if (review) review.classList.add('hidden');
        state.myWeekOpened = false;
        state.myCupsOpened = false;
        state.myCupCompetition = null;
        state.myPredictionsNavOpened = true;
        tg.BackButton.show();
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
        const home = $('#profile-home');
        if (home) home.classList.add('hidden');
        $('#my-weeks').classList.add('hidden');
        const block = $('#my-predictions');
        block.classList.remove('hidden');
        state.myWeekOpened = true;
        state.myPredictionsNavOpened = true;
        block.dataset.weekId = weekId;
        delete block.dataset.competition;
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
            if (state.predictCupCompetition) {
                $('#predict-matches').classList.add('hidden');
                $('#predict-cups').classList.remove('hidden');
                state.predictWeekOpened = false;
                state.predictCupCompetition = null;
                tg.BackButton.show();
                return;
            }
            if (state.predictCupsOpened) {
                closePredictCupNav();
                tg.BackButton.hide();
                return;
            }
            $('#predict-matches').classList.add('hidden');
            $('#predict-weeks').classList.remove('hidden');
            state.predictWeekOpened = false;
            tg.BackButton.hide();
        });

        $('#my-back-weeks').addEventListener('click', () => {
            if (state.myCupCompetition) {
                $('#my-predictions').classList.add('hidden');
                $('#my-cups').classList.remove('hidden');
                const review = $('#my-review-card');
                if (review) review.classList.add('hidden');
                state.myWeekOpened = false;
                state.myCupCompetition = null;
                tg.BackButton.show();
                return;
            }
            if (state.myCupsOpened) {
                closeMyCupNav();
                return;
            }
            if (state.myWeekOpened) {
                $('#my-predictions').classList.add('hidden');
                $('#my-weeks').classList.remove('hidden');
                const review = $('#my-review-card');
                if (review) review.classList.add('hidden');
                state.myWeekOpened = false;
                tg.BackButton.show();
                return;
            }
            closeMyPredictionsNav();
        });

        const profileOpenPredictions = $('#profile-open-predictions');
        if (profileOpenPredictions) {
            profileOpenPredictions.addEventListener('click', () => {
                openMyPredictionsNav().catch((e) => showToast(e.message || 'Ошибка', 'error'));
            });
        }

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
        const recommendationOpen = $('#modal-recommendation-open');
        if (recommendationOpen) {
            recommendationOpen.addEventListener('click', openAiModal);
        }
        const aiHeatSection = $('#ai-modal-heatmap-section');
        if (aiHeatSection) aiHeatSection.addEventListener('click', onAiHeatmapInteraction);
        const aiModalClose = $('#ai-modal-close');
        if (aiModalClose) {
            aiModalClose.addEventListener('click', () => closeAiModal());
        }
        $('#live-modal-close').addEventListener('click', closeLiveMatchModal);
        $('#modal-delete').addEventListener('click', deletePrediction);
        $('#team-modal-close').addEventListener('click', closeTeamModal);
        $('#h2h-modal-close').addEventListener('click', closeH2hModal);
        wireMatchHeaderTeamTaps();
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
            if (state.aiModalOpened) {
                closeAiModal();
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
            if (state.predictWeekOpened && state.predictCupCompetition) {
                $('#predict-matches').classList.add('hidden');
                $('#predict-cups').classList.remove('hidden');
                state.predictWeekOpened = false;
                state.predictCupCompetition = null;
                tg.BackButton.show();
                return;
            }
            if (state.predictCupsOpened) {
                closePredictCupNav();
                tg.BackButton.hide();
                return;
            }
            if (state.predictWeekOpened) {
                $('#predict-matches').classList.add('hidden');
                $('#predict-weeks').classList.remove('hidden');
                state.predictWeekOpened = false;
                tg.BackButton.hide();
                return;
            }
            if (state.myWeekOpened && state.myCupCompetition) {
                $('#my-predictions').classList.add('hidden');
                $('#my-cups').classList.remove('hidden');
                const review = $('#my-review-card');
                if (review) review.classList.add('hidden');
                state.myWeekOpened = false;
                state.myCupCompetition = null;
                tg.BackButton.show();
                return;
            }
            if (state.myCupsOpened) {
                closeMyCupNav();
                return;
            }
            if (state.myWeekOpened) {
                $('#my-predictions').classList.add('hidden');
                $('#my-weeks').classList.remove('hidden');
                const review = $('#my-review-card');
                if (review) review.classList.add('hidden');
                state.myWeekOpened = false;
                tg.BackButton.show();
                return;
            }
            if (state.myPredictionsNavOpened) {
                closeMyPredictionsNav();
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
            initThemeControls();
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
