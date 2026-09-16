package zhigalin.predictions.service.predict;

/**
 * Per-fixture scoring tables.
 * <ul>
 *   <li>EPL — exact 4 / GD 2 / outcome 1 / miss or no bet −1</li>
 *   <li>EPL personal week bonus — exact 5 / GD 3 / outcome 2 / miss 0 / no bet −1</li>
 *   <li>Cup bonus — exact 2 / GD or outcome 1 / miss or no bet 0</li>
 * </ul>
 */
public enum ScoringMode {
    EPL,
    EPL_WEEK_BONUS,
    CUP
}
