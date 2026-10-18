(function () {
    'use strict';
    window.MiniApp = window.MiniApp || {};
    const CHART_COLORS = ['#b08cff', '#5cb87a', '#d4604a', '#e8a840', '#64d4e0', '#ff8ec4'];

    const TODAY_POLL_LIVE_MS = 10000;

    const TODAY_POLL_IDLE_MS = 60000;

    const LIVE_MODAL_POLL_MS = 10000;

    const LIVE_PRESTART_WINDOW_SECONDS = 10 * 60;

    const CACHE_TTL_MS = 45000;

    const FINISHED_STATUSES = new Set(['ft', 'aet', 'pen', 'canc', 'abd', 'awrd', 'wo']);

    const NOT_STARTED_STATUSES = new Set(['ns', 'pst', 'tbd']);

    const MUTATION_RETRY_DELAYS_MS = [0, 600, 1500];

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
        return '<span class="badge live">' + escapeHtml(m.status) + '</span>';
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
            return '<span class="badge live">' + escapeHtml(m.status || 'live') + '</span>';
        }
        if (m.canPredict) return '<span class="badge">можно</span>';
        if (m.hasPrediction) return '<span class="badge predicted">прогноз</span>';
        return '<span class="badge">' + escapeHtml(m.status || 'ожидание') + '</span>';
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

    function competitionCssKey(code) {
        if (code === 'eng.fa') return 'fa';
        if (code === 'eng.league_cup') return 'carabao';
        if (code === 'uefa.champions') return 'ucl';
        if (code === 'uefa.europa') return 'uel';
        if (code === 'uefa.europa.conf') return 'uecl';
        return 'cup';
    }

    function competitionShort(code) {
        if (!code) return 'Cup';
        if (code === 'eng.fa') return 'FA';
        if (code === 'eng.league_cup') return 'Carabao';
        if (code === 'uefa.champions') return 'UCL';
        if (code === 'uefa.europa') return 'UEL';
        if (code === 'uefa.europa.conf') return 'UECL';
        return code;
    }

    function competitionBadgeHtml(m, dim) {
        if (!m || !m.cup) return '';
        const key = competitionCssKey(m.competition);
        const cls = 'badge badge-cup badge-cup-' + key + (dim ? ' dim' : '');
        return '<span class="' + cls + '">' + escapeHtml(competitionShort(m.competition)) + '</span>';
    }

    function escapeHtml(s) {
        return String(s == null ? '' : s).replace(/[&<>"']/g, c => ({
            '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
        }[c]));
    }

    function kickoffTimeLabel(kickoff) {
        if (!kickoff) return '—';
        const m = String(kickoff).match(/(\d{1,2}:\d{2})\s*$/);
        return m ? m[1] : kickoff;
    }

    function kickoffDateLabel(kickoff) {
        if (!kickoff) return '';
        const m = String(kickoff).match(/^(\d{1,2}\.\d{1,2})/);
        return m ? m[1] : '';
    }

    function todayGroupKey(m) {
        if (m && m.cup) return 'cup:' + (m.competition || 'cup');
        return 'week:' + (m.weekId != null ? m.weekId : '');
    }

    function todayGroupLabel(m) {
        if (m && m.cup) return competitionShort(m.competition);
        return (m.weekId != null ? m.weekId : '') + ' тур';
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

    function hasLiveOrSoonMatches(matches) {
        return (matches || []).some(isLiveModuleMatch);
    }

    function niceChartStep(range, targetTicks) {
        const safeRange = Math.max(range, 1);
        const rough = safeRange / Math.max(targetTicks, 2);
        const pow = Math.pow(10, Math.floor(Math.log10(rough)));
        const n = rough / pow;
        let step;
        if (n <= 1) step = 1;
        else if (n <= 2) step = 2;
        else if (n <= 5) step = 5;
        else step = 10;
        step = step * pow;
        // Prefer multiples of 5 for point totals when the span is wide enough
        if (safeRange >= 15 && step < 5) {
            return 5;
        }
        return step;
    }

    function normalizeKitColor(color) {
        if (!color) return '';
        const value = String(color).trim();
        if (!value) return '';
        const hex = value.startsWith('#') ? value : ('#' + value);
        return /^#[0-9a-f]{3,8}$/i.test(hex) ? hex : '';
    }

    function jerseyTextColor(hex) {
        const value = String(hex || '').replace('#', '');
        if (value.length !== 6) return '#ffffff';
        const n = parseInt(value, 16);
        const luminance = 0.299 * ((n >> 16) & 255) + 0.587 * ((n >> 8) & 255) + 0.114 * (n & 255);
        return luminance > 150 ? '#1a1a1a' : '#ffffff';
    }

    function jerseyLuminance(hex) {
        const n = parseInt(hex.slice(1), 16);
        return 0.299 * ((n >> 16) & 255) + 0.587 * ((n >> 8) & 255) + 0.114 * (n & 255);
    }

    function jerseySvg(input) {
        const hexOk = (v) => /^#[0-9a-f]{6}$/i.test(String(v || ''));
        const isGk = !!(input && input.isGk);
        const body = isGk ? '#4a5560' : (hexOk(input && input.body) ? input.body : '#3a4a58');
        const text = jerseyTextColor(body);
        const trim = !isGk && hexOk(input && input.trim) && Math.abs(jerseyLuminance(body) - jerseyLuminance(input.trim)) >= 40
            ? input.trim
            : text;
        const label = escapeHtml(String(input && input.number != null ? input.number : ''));
        return '<svg class="formation-jersey-svg" viewBox="0 0 48 45" aria-hidden="true">' +
            '<path d="M12 6 L17 3 Q24 8 31 3 L36 6 L46 16 L41 22 L36 19 L36 41 L12 41 L12 19 L7 22 L2 16 Z" ' +
            'fill="' + body + '" stroke="rgba(0,0,0,0.55)" stroke-width="1.2" stroke-linejoin="round"/>' +
            '<path d="M17 3 Q24 8 31 3" fill="none" stroke="' + trim + '" stroke-width="2.2"/>' +
            '<path d="M2 16 L7 22 M46 16 L41 22" stroke="' + trim + '" stroke-width="2.2" stroke-linecap="round"/>' +
            '<text x="24" y="29" text-anchor="middle" font-size="14" font-weight="700" fill="' + text + '">' + label + '</text>' +
            '</svg>';
    }

    function clamp(value, min, max) {
        const num = Number(value);
        if (!Number.isFinite(num)) return min;
        if (num < min) return min;
        if (num > max) return max;
        return num;
    }

    function fairOdds(p) {
        if (!p || p <= 0) return '—';
        return (1 / p).toFixed(2);
    }

    function pctLabel(p) {
        return Math.round(Number(p || 0) * 100) + '%';
    }

    function marketChipHtml(label, p) {
        return '<div class="ai-market-chip">'
            + '<span class="ai-market-label">' + escapeHtml(label) + '</span>'
            + '<span class="ai-market-pct">' + pctLabel(p) + '</span>'
            + '<span class="ai-market-odds">' + fairOdds(p) + '</span>'
            + '</div>';
    }

    function injuryKindMeta(kind) {
        switch ((kind || '').toLowerCase()) {
            case 'injury':
                return { symbol: '✕', title: 'травма', cls: 'modal-injury-kind--injury' };
            case 'suspension':
                return { symbol: '▮', title: 'дисквалификация', cls: 'modal-injury-kind--suspension' };
            case 'doubt':
                return { symbol: '?', title: 'под вопросом', cls: 'modal-injury-kind--doubt' };
            default:
                return { symbol: '–', title: 'отсутствие', cls: 'modal-injury-kind--other' };
        }
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

    function parsePercentWidth(percentStr) {
        if (!percentStr) return 0;
        const n = parseFloat(String(percentStr).replace('%', '').replace(',', '.'));
        if (!Number.isFinite(n)) return 0;
        return Math.max(0, Math.min(100, n));
    }

    Object.assign(window.MiniApp, {
        CHART_COLORS,
        TODAY_POLL_LIVE_MS,
        TODAY_POLL_IDLE_MS,
        LIVE_MODAL_POLL_MS,
        LIVE_PRESTART_WINDOW_SECONDS,
        CACHE_TTL_MS,
        FINISHED_STATUSES,
        NOT_STARTED_STATUSES,
        MUTATION_RETRY_DELAYS_MS,
        hexToRgba,
        isRetryableStatus,
        normalizeStatus,
        isNotStartedStatus,
        isFinishedStatus,
        matchScoreLabel,
        matchStatusBadge,
        todayScoreLabel,
        todayStatusBadge,
        parseKickoffLabel,
        sortMatchesByKickoff,
        competitionCssKey,
        competitionShort,
        competitionBadgeHtml,
        escapeHtml,
        kickoffTimeLabel,
        kickoffDateLabel,
        todayGroupKey,
        todayGroupLabel,
        isLiveNowMatch,
        kickoffSecondsLeft,
        isPreLiveSoonMatch,
        isLiveModuleMatch,
        hasLiveOrSoonMatches,
        niceChartStep,
        normalizeKitColor,
        jerseyTextColor,
        jerseySvg,
        clamp,
        fairOdds,
        pctLabel,
        marketChipHtml,
        injuryKindMeta,
        normalizeOutcome,
        formDotClassByOutcome,
        formDotClassByScores,
        parsePercentWidth
    });
})();
