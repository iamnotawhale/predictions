(function () {
    const tg = window.Telegram.WebApp;
    const isLocalDev = location.hostname === 'localhost' || location.hostname === '127.0.0.1';
    if (tg.initData) {
        tg.ready();
        tg.expand();
    }

    const CHART_COLORS = ['#2ea6ff', '#5cd97a', '#e85c4a', '#f5c542', '#b07aff', '#ff8ec4'];
    const TODAY_POLL_INTERVAL_MS = 15000;
    const FINISHED_STATUSES = new Set(['ft', 'aet', 'pen', 'canc', 'abd', 'awrd', 'wo']);
    const NOT_STARTED_STATUSES = new Set(['ns', 'pst', 'tbd']);

    const state = {
        profile: null,
        currentWeekId: null,
        selectedMatch: null,
        leaderboardMode: '',
        chartLoaded: false,
        todayLoaded: false,
        todaySnapshotInitialized: false,
        todayScoresByMatchId: {},
        scoreNotificationsQueue: [],
        activeScoreNotificationId: null,
        todayPollingTimerId: null
    };

    const $ = (sel) => document.querySelector(sel);
    const $$ = (sel) => document.querySelectorAll(sel);

    const MUTATION_RETRY_DELAYS_MS = [0, 600, 1500];

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
        const res = await fetch('/api/miniapp' + path, {
            ...options,
            headers: { ...headers(), ...(options.headers || {}) }
        });
        const data = await res.json().catch(() => ({}));
        if (!res.ok) {
            throw new ApiError(data.message || 'Ошибка запроса', res.status, data);
        }
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
                    throw e;
                }
                lastError = e;
                if (attempt === maxAttempts - 1) {
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
        await loadWeeksGrid('#predict-weeks', showPredictWeek);
        await loadWeeksGrid('#my-weeks', showMyWeek);
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
        if (m.status === 'ft' && m.homeScore != null) {
            return m.homeScore + ' : ' + m.awayScore;
        }
        if (m.hasPrediction && m.predictHome != null) {
            return m.predictHome + ' : ' + m.predictAway;
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

    function renderTodayMatchItem(m, onClick) {
        const li = document.createElement('li');
        li.className = 'list-item';
        li.innerHTML =
            '<div class="list-item-main">' +
            '<div class="list-item-title">' + m.homeCode + ' — ' + m.awayCode + '</div>' +
            '<div class="list-item-sub">' + (m.homeName || '') + ' vs ' + (m.awayName || '') + '</div>' +
            '</div>' +
            '<div class="list-item-meta">' +
            '<div class="score-pill">' + todayScoreLabel(m) + '</div>' +
            todayStatusBadge(m) +
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
        $('#user-greeting').textContent = name + ' · тур ' + profile.currentWeekId;
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
            li.innerHTML =
                '<span class="rank">' + (i + 1) + '</span>' +
                '<div class="list-item-main"><div class="list-item-title">' + e.login.toUpperCase() + '</div></div>' +
                '<span class="pts">' + e.points + '</span>';
            list.appendChild(li);
        });
    }

    async function loadTodayMatches() {
        const data = await api('/today');
        const previousScores = { ...state.todayScoresByMatchId };
        processTodayScoreUpdates(data.matches, previousScores);
        renderTodayMatchesList(data.matches);
        state.todayLoaded = true;
    }

    async function pollTodayMatchesForUpdates() {
        const data = await api('/today');
        const previousScores = { ...state.todayScoresByMatchId };
        processTodayScoreUpdates(data.matches, previousScores);
        if ($('#screen-today').classList.contains('active')) {
            renderTodayMatchesList(data.matches);
            state.todayLoaded = true;
        }
    }

    function startTodayPolling() {
        if (state.todayPollingTimerId) {
            clearInterval(state.todayPollingTimerId);
        }
        pollTodayMatchesForUpdates().catch(() => {});
        state.todayPollingTimerId = setInterval(() => {
            pollTodayMatchesForUpdates().catch(() => {});
        }, TODAY_POLL_INTERVAL_MS);
    }

    function drawPointsChart(data) {
        const canvas = $('#points-chart');
        const ctx = canvas.getContext('2d');
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
            return;
        }

        let maxY = 0;
        data.series.forEach(s => {
            s.points.forEach(p => {
                if (p >= 0) maxY = Math.max(maxY, p);
            });
        });
        maxY = Math.max(maxY, 4);

        const weekCount = data.weeks.length;
        const xAt = (i) => pad.left + (weekCount <= 1 ? plotW / 2 : (i / (weekCount - 1)) * plotW);
        const yAt = (v) => pad.top + plotH - (v / maxY) * plotH;

        ctx.strokeStyle = 'rgba(255,255,255,0.1)';
        ctx.lineWidth = 1;
        for (let g = 0; g <= 4; g++) {
            const y = pad.top + (g / 4) * plotH;
            ctx.beginPath();
            ctx.moveTo(pad.left, y);
            ctx.lineTo(pad.left + plotW, y);
            ctx.stroke();
        }

        ctx.fillStyle = '#8b98a5';
        ctx.font = '10px sans-serif';
        ctx.textAlign = 'center';
        data.weeks.forEach((w, i) => {
            if (weekCount > 12 && i % 2 !== 0) return;
            ctx.fillText(String(w), xAt(i), height - 8);
        });

        data.series.forEach((s, si) => {
            const color = CHART_COLORS[si % CHART_COLORS.length];
            ctx.strokeStyle = color;
            ctx.fillStyle = color;
            ctx.lineWidth = 2;
            let started = false;
            ctx.beginPath();
            s.points.forEach((p, i) => {
                if (p < 0) {
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
                if (p < 0) return;
                ctx.beginPath();
                ctx.arc(xAt(i), yAt(p), 3, 0, Math.PI * 2);
                ctx.fill();
            });
        });

        const legend = $('#chart-legend');
        legend.innerHTML = '';
        data.series.forEach((s, si) => {
            const li = document.createElement('li');
            li.innerHTML =
                '<span class="dot" style="background:' + CHART_COLORS[si % CHART_COLORS.length] + '"></span>' +
                s.label + ' · ' + s.login;
            legend.appendChild(li);
        });
        state.chartLoaded = true;
    }

    async function loadPointsChart() {
        const data = await api('/chart');
        drawPointsChart(data);
    }

    async function loadStandings() {
        const rows = await api('/standings');
        const tbody = $('#standings-body');
        tbody.innerHTML = '';
        rows.forEach(r => {
            const tr = document.createElement('tr');
            tr.innerHTML =
                '<td>' + r.place + '</td>' +
                '<td><strong>' + r.code + '</strong></td>' +
                '<td>' + r.played + '</td>' +
                '<td>' + r.points + '</td>';
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
            return;
        }
        matches.forEach(m => list.appendChild(renderMatchItem(m, openScoreModal)));
    }

    function openScoreModal(match) {
        state.selectedMatch = match;
        $('#modal-match-title').textContent = match.homeCode + ' — ' + match.awayCode;
        $('#modal-kickoff').textContent = match.kickoff || '';
        const grid = $('#score-grid');
        grid.innerHTML = '';
        const deleteBtn = $('#modal-delete');

        if (!match.canPredict) {
            deleteBtn.classList.add('hidden');
            grid.innerHTML = '<p class="empty-state">Прогноз недоступен</p>';
            $('#score-modal').classList.remove('hidden');
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
    }

    function closeScoreModal() {
        $('#score-modal').classList.add('hidden');
        state.selectedMatch = null;
        tg.BackButton.hide();
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
        block.dataset.weekId = weekId;
        loadPredictMatches(weekId);
        tg.BackButton.show();
        tg.BackButton.onClick(() => {
            $('#predict-matches').classList.add('hidden');
            $('#predict-weeks').classList.remove('hidden');
            tg.BackButton.hide();
        });
    }

    function showMyWeek(weekId) {
        $('#my-weeks').classList.add('hidden');
        const block = $('#my-predictions');
        block.classList.remove('hidden');
        block.dataset.weekId = weekId;
        loadMyPredictions(weekId);
        tg.BackButton.show();
        tg.BackButton.onClick(() => {
            $('#my-predictions').classList.add('hidden');
            $('#my-weeks').classList.remove('hidden');
            tg.BackButton.hide();
        });
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
            tg.BackButton.hide();
        });

        $('#my-back-weeks').addEventListener('click', () => {
            $('#my-predictions').classList.add('hidden');
            $('#my-weeks').classList.remove('hidden');
            tg.BackButton.hide();
        });

        $('#modal-close').addEventListener('click', closeScoreModal);
        $('#modal-delete').addEventListener('click', deletePrediction);

        tg.BackButton.onClick(closeScoreModal);
    }

    async function init() {
        if (!tg.initData && !isLocalDev) {
            $('#user-greeting').textContent = 'Откройте через Telegram';
            showToast('Приложение работает только внутри Telegram', 'error');
            return;
        }

        try {
            bindEvents();
            if (isLocalDev && !tg.initData) {
                $('#user-greeting').textContent = 'Локальный режим (dev)';
                document.getElementById('app-title').textContent = 'EPL Predictions [dev]';
            }
            await loadProfile();
            await Promise.all([
                loadLeaderboard(null),
                loadStandings(),
                loadPointsChart(),
                loadWeeksGrid('#predict-weeks', showPredictWeek),
                loadWeeksGrid('#my-weeks', showMyWeek)
            ]);
            startTodayPolling();
            if (tg.themeParams) {
                document.documentElement.style.setProperty('--tg-theme-bg-color', tg.themeParams.bg_color);
            }
        } catch (e) {
            showToast(e.message, 'error');
            $('#user-greeting').textContent = e.message;
        }
    }

    init();
})();
