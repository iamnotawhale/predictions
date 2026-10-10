package zhigalin.predictions.service.notification;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.football.Team;

/**
 * In-memory bookkeeping for the single "live score" chat message per match.
 * A match can be identified by ESPN id, public id or team codes depending on how
 * the update arrived, so every alias is tracked and cleared together.
 */
final class LiveScoreState {
    private final ConcurrentHashMap<String, Integer> messageIds = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> lastTexts = new ConcurrentHashMap<>();
    /** Keys whose current live chat message is an animation (edit via editMessageMedia). */
    private final Set<String> animationKeys = ConcurrentHashMap.newKeySet();

    /** Preferred key: ESPN id, else public id, else team codes. */
    String key(Match match, Team home, Team away) {
        if (hasEspnId(match)) {
            return espnKey(match);
        }
        if (match.getPublicId() != 0) {
            return publicKey(match);
        }
        String homeCode = home != null ? home.getCode() : String.valueOf(match.getHomeTeamId());
        String awayCode = away != null ? away.getCode() : String.valueOf(match.getAwayTeamId());
        return "teams:" + homeCode + "-" + awayCode;
    }

    /** Looks up the message id by primary key, the persisted id, then every alias; caches under primaryKey. */
    Integer existingMessageId(Match match, String primaryKey, Team home, Team away) {
        Integer existing = messageIds.get(primaryKey);
        if (existing != null) {
            return existing;
        }
        if (match.getLiveScoreMessageId() != null) {
            messageIds.put(primaryKey, match.getLiveScoreMessageId());
            return match.getLiveScoreMessageId();
        }
        for (String alias : aliasKeys(match, home, away)) {
            existing = messageIds.get(alias);
            if (existing != null) {
                messageIds.put(primaryKey, existing);
                return existing;
            }
        }
        return null;
    }

    /** Forgets the match under its primary key and all aliases. */
    void clear(Match match, Team home, Team away) {
        List<String> keys = new ArrayList<>();
        keys.add(key(match, home, away));
        keys.addAll(aliasKeys(match, home, away));
        for (String k : keys) {
            messageIds.remove(k);
            lastTexts.remove(k);
            animationKeys.remove(k);
        }
    }

    String lastText(String key) {
        return lastTexts.get(key);
    }

    void putLastText(String key, String text) {
        lastTexts.put(key, text);
    }

    void putMessageId(String key, Integer messageId) {
        messageIds.put(key, messageId);
    }

    void removeMessageId(String key) {
        messageIds.remove(key);
    }

    void markAnimation(String key) {
        animationKeys.add(key);
    }

    void unmarkAnimation(String key) {
        animationKeys.remove(key);
    }

    boolean isAnimation(String key) {
        return animationKeys.contains(key);
    }

    /** pub → espn → teams, in lookup order; only those that apply to this match. */
    private static List<String> aliasKeys(Match match, Team home, Team away) {
        List<String> keys = new ArrayList<>();
        if (match.getPublicId() != 0) {
            keys.add(publicKey(match));
        }
        if (hasEspnId(match)) {
            keys.add(espnKey(match));
        }
        if (home != null && away != null) {
            keys.add("teams:" + home.getCode() + "-" + away.getCode());
        }
        return keys;
    }

    private static boolean hasEspnId(Match match) {
        return match.getEspnId() != null && !match.getEspnId().isBlank();
    }

    private static String espnKey(Match match) {
        return "espn:" + match.getEspnId();
    }

    private static String publicKey(Match match) {
        return "pub:" + match.getPublicId();
    }
}
