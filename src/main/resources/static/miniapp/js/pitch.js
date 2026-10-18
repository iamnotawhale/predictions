(function () {
    'use strict';
    window.MiniApp = window.MiniApp || {};
    const { normalizeStatus } = window.MiniApp;

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

    function fallbackFormationFromLineup(side, teamCode, lineup) {
        if (!lineup || !lineup.length) return null;
        return {
            side: side,
            teamCode: teamCode,
            formation: '',
            kitColor: '',
            kitAlternateColor: '',
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
        '3-5-2': [[11, 9], [8, 10, 6], [2, 7, 3], [4, 5, 6], [1]],
        '3-4-3': [[11, 9, 7], [8, 10], [2, 3], [4, 5, 6], [1]],
        // ESPN 3-4-2-1: wide mids 2/3, CBs 4/5/6, AMs 10/11, striker 9 (not 3/5/4 + 11/7)
        '3-4-2-1': [[9], [10, 11], [2, 7, 8, 3], [4, 5, 6], [1]],
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

    function countFormationSlots(rows) {
        return (rows || []).reduce((n, row) => n + row.length, 0);
    }

    function buildFormationRows(formation) {
        if (!formation || !formation.starters || !formation.starters.length) return [];
        const byPlace = formationPlaceSlots(formation);
        if (!byPlace.size) return [];
        const key = inferFormationKey(formation);
        const rows = rowsFromPlaceMap(byPlace, FORMATION_PLACE_ROWS[key]);
        const starterCount = formation.starters.length;
        if (rows.length && countFormationSlots(rows) >= starterCount) return rows;
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

    function shortPlayerName(name) {
        if (!name) return '';
        const parts = String(name).trim().split(/\s+/);
        return parts[parts.length - 1] || name;
    }

    function fmtStatNum(v) {
        if (v == null || Number.isNaN(Number(v))) return null;
        const n = Number(v);
        return Number.isInteger(n) ? String(n) : n.toFixed(1);
    }

    function fmtStatPct(v) {
        if (v == null || Number.isNaN(Number(v))) return null;
        const n = Number(v);
        const shown = n <= 1.5 ? n * 100 : n;
        return (Math.round(shown * 10) / 10) + '%';
    }

    Object.assign(window.MiniApp, {
        detectGoalHighlightTeam,
        fallbackFormationFromLineup,
        allFormationPlayers,
        formationPlayersById,
        resolvePitchSlot,
        FORMATION_PLACE_ROWS,
        inferFormationKey,
        formationPlaceSlots,
        rowsFromPlaceMap,
        countFormationSlots,
        buildFormationRows,
        fallbackFormationRows,
        formationPlayerLabel,
        formationJerseyNumber,
        formatBenchLabel,
        PLAYER_STAT_LABELS_RU,
        PLAYER_STAT_PRIORITY,
        parseLiveEventMinute,
        hasSecondHalfActivity,
        isMatchHalftimeBreak,
        isShotEventType,
        isBlockedShotType,
        isOnTargetShotType,
        resolveGoalLineX,
        flipPitchPercent,
        resolveEventPeriod,
        shouldMirrorPitchX,
        shouldFlipPitchY,
        mapEventToPitchCoords,
        isOffsideEventType,
        resolveOffsidePoints,
        resolveShotPoints,
        resolveEventTeamSide,
        PITCH_NON_PLAYER_TOKEN,
        PITCH_NAME_PARTICLE,
        surnameFromPlayerName,
        pitchOffsidePlayerFromText,
        pitchPlayerLabel,
        livePitchEventKey,
        findLatestPitchEvent,
        shotTrajectoryStyle,
        liveEventIcon,
        shortPlayerName,
        fmtStatNum,
        fmtStatPct
    });
})();
