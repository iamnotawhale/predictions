(function (global) {
    function escapeRegex(value) {
        return String(value).replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    }

    function addedTimeLabel(minutes) {
        const n = Number(minutes);
        const mod10 = n % 10;
        const mod100 = n % 100;
        if (mod10 === 1 && mod100 !== 11) return 'минуту';
        if (mod10 >= 2 && mod10 <= 4 && (mod100 < 10 || mod100 >= 20)) return 'минуты';
        return 'минут';
    }

    function compilePattern(enPattern) {
        const names = [];
        let source = '^';
        for (let i = 0; i < enPattern.length; ) {
            if (enPattern[i] === '{') {
                const end = enPattern.indexOf('}', i);
                names.push(enPattern.slice(i + 1, end));
                source += '(.+?)';
                i = end + 1;
            } else {
                source += escapeRegex(enPattern[i]);
                i += 1;
            }
        }
        source += '$';
        return { regex: new RegExp(source, 'i'), names };
    }

    function compileTranslations(raw) {
        const exactMap = new Map((raw.exactMatches || []).map((item) => [item.en, item.ru]));
        const phraseTemplates = (raw.phraseTemplates || []).map((item) => {
            const compiled = compilePattern(item.enPattern);
            return {
                id: item.id,
                names: compiled.names,
                regex: compiled.regex,
                ruTemplate: item.ruTemplate,
                narrativeFrom: item.narrativeFrom || null
            };
        });
        const phraseReplacements = (raw.phraseReplacements || [])
            .slice()
            .sort((a, b) => b.en.length - a.en.length)
            .map((item) => ({
                regex: new RegExp(escapeRegex(item.en), 'gi'),
                ru: item.ru
            }));
        const assistReplacements = (raw.assistReplacements || []).map((item) => ({
            regex: new RegExp(item.pattern, 'i'),
            replacement: item.replacement
        }));
        return {
            raw,
            exactMap,
            phraseTemplates,
            phraseReplacements,
            assistReplacements,
            wrappedPrefixes: raw.wrappedPrefixes || [],
            partialPrefixes: raw.partialPrefixes || [],
            typeFallbacks: raw.typeFallbacks || [],
            goal: raw.goal || { enPrefix: 'Goal!', ruPrefix: 'ГОЛ! ' },
            eventTypeLabels: raw.eventTypeLabels || {}
        };
    }

    function applyPhraseReplacements(text, compiled) {
        let out = text;
        compiled.phraseReplacements.forEach((item) => {
            out = out.replace(item.regex, item.ru);
        });
        compiled.assistReplacements.forEach((item) => {
            out = out.replace(item.regex, item.replacement);
        });
        return out;
    }

    function fillTemplate(template, names, groups, compiled) {
        let ru = template;
        names.forEach((name, index) => {
            let value = groups[index + 1];
            if (name === 'minutesLabel') {
                value = addedTimeLabel(groups[index + 1]);
            }
            ru = ru.split('{' + name + '}').join(value);
        });
        return ru;
    }

    function matchPhraseTemplate(raw, compiled) {
        for (const template of compiled.phraseTemplates) {
            const match = raw.match(template.regex);
            if (!match) continue;
            if (template.narrativeFrom) {
                const idx = template.names.indexOf(template.narrativeFrom);
                if (idx < 0) continue;
                const narrative = applyPhraseReplacements(match[idx + 1], compiled);
                const names = template.names.slice();
                const groups = match.slice();
                const narrativeKey = template.narrativeFrom + 'Ru';
                names.push(narrativeKey);
                groups.push(narrative);
                return fillTemplate(template.ruTemplate, names, groups, compiled);
            }
            if (template.id === 'added_time') {
                const names = template.names.slice();
                names.push('minutesLabel');
                const groups = match.slice();
                groups.push(addedTimeLabel(groups[1]));
                return fillTemplate(template.ruTemplate, names, groups, compiled);
            }
            return fillTemplate(template.ruTemplate, template.names, match, compiled);
        }
        return null;
    }

    function translateNarrative(text, compiled) {
        return applyPhraseReplacements((text || '').trim(), compiled);
    }

    function translateLiveEventText(text, type, compiled) {
        const raw = (text || '').trim();
        if (!raw || !compiled) return raw;

        if (compiled.exactMap.has(raw)) {
            return compiled.exactMap.get(raw);
        }

        const templated = matchPhraseTemplate(raw, compiled);
        if (templated) return templated;

        for (const item of compiled.partialPrefixes) {
            if (raw.startsWith(item.enPrefix)) {
                return item.ruPrefix + raw.slice(item.enPrefix.length);
            }
        }

        const goalPrefix = compiled.goal.enPrefix || 'Goal!';
        if (raw.startsWith(goalPrefix)) {
            const body = raw.slice(goalPrefix.length).trim();
            const dot = body.indexOf('.');
            if (dot < 0) {
                return compiled.goal.ruPrefix + translateNarrative(body, compiled);
            }
            const scorePart = body.slice(0, dot + 1).trim();
            const narrative = body.slice(dot + 1).trim();
            return compiled.goal.ruPrefix + scorePart + (narrative ? (' ' + translateNarrative(narrative, compiled)) : '');
        }

        for (const item of compiled.wrappedPrefixes) {
            if (raw.startsWith(item.enPrefix)) {
                return item.ruPrefix + translateNarrative(raw.slice(item.enPrefix.length), compiled);
            }
        }

        const normalizedType = (type || '').toLowerCase();
        for (const item of compiled.typeFallbacks) {
            if (normalizedType.includes(item.typeIncludes)) {
                return item.ruPrefix + translateNarrative(raw, compiled);
            }
        }

        const partial = translateNarrative(raw, compiled);
        return partial === raw ? raw : partial;
    }

    function prettyLiveEventType(type, compiled) {
        const labels = compiled?.eventTypeLabels || {};
        const normalized = (type || '').toLowerCase().trim();
        if (labels[normalized]) return labels[normalized];
        if (normalized.includes('goal') && normalized.includes('free')) return labels['goal---free-kick'] || 'ГОЛ (ШТРАФНОЙ)';
        if (normalized.includes('goal') && normalized.includes('header')) return labels['goal---header'] || 'ГОЛ (ГОЛОВОЙ)';
        if (normalized.includes('goal')) return labels.goal || 'ГОЛ';
        if (normalized.includes('penalty')) return 'ПЕНАЛЬТИ';
        if (normalized.includes('yellow')) return labels['yellow-card'] || 'ЖК';
        if (normalized.includes('red')) return labels['red-card'] || 'КК';
        if (normalized.includes('sub')) return labels.substitution || 'ЗАМЕНА';
        if (normalized.includes('offside')) return labels.offside || 'ОФСАЙД';
        if (normalized.includes('shot-on-target')) return labels['shot-on-target'] || 'В СТВОР';
        if (normalized.includes('shot-off-target')) return labels['shot-off-target'] || 'МИМО';
        if (normalized.includes('shot-blocked')) return labels['shot-blocked'] || 'БЛОК';
        if (normalized.includes('woodwork')) return labels['shot-hit-woodwork'] || 'ШТАНГА';
        if (normalized.includes('corner')) return labels['corner-awarded'] || 'УГЛОВОЙ';
        if (normalized.includes('save')) return 'СЕЙВ';
        if (normalized.includes('foul')) return labels.foul || 'ФОЛ';
        if (normalized.includes('var')) return 'VAR';
        if (normalized.includes('kickoff') || normalized.includes('period')) return labels.kickoff || 'НАЧАЛО';
        return normalized ? normalized.toUpperCase().replaceAll('-', ' ') : 'СОБЫТИЕ';
    }

    async function loadLiveEventTranslations(url) {
        const response = await fetch(url);
        if (!response.ok) {
            throw new Error('live-event-ru load failed: ' + response.status);
        }
        const raw = await response.json();
        return compileTranslations(raw);
    }

    global.LiveEventRu = {
        compileTranslations,
        loadLiveEventTranslations,
        translateLiveEventText,
        prettyLiveEventType
    };
}(window));
