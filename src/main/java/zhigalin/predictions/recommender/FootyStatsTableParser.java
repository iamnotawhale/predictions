package zhigalin.predictions.recommender;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

final class FootyStatsTableParser {

    private FootyStatsTableParser() {
    }

    static Map<String, List<String>> parseTableByClassContains(Document doc, String classContains) {
        return parseTableByClassContains(doc, classContains, null);
    }

    static Map<String, List<String>> parseTableByClassContains(
            Document doc,
            String classContains,
            String classExcludes
    ) {
        Map<String, List<String>> rows = new LinkedHashMap<>();
        for (Element table : doc.select("table.full-league-table")) {
            String tableClass = table.className();
            if (!tableClass.contains(classContains)) {
                continue;
            }
            if (classExcludes != null && tableClass.contains(classExcludes)) {
                continue;
            }
            mergeRows(rows, parseTable(table));
        }
        return rows;
    }

    static Map<String, List<String>> parseTableAfterHeading(Document doc, String headingContains) {
        String needle = headingContains.toLowerCase(Locale.ROOT);
        for (Element heading : doc.select("h2.widget-title")) {
            if (!heading.text().toLowerCase(Locale.ROOT).contains(needle)) {
                continue;
            }
            Element parent = heading.parent();
            if (parent != null) {
                Element table = parent.selectFirst("table.full-league-table");
                if (table != null) {
                    return parseTable(table);
                }
            }
            Element sibling = heading.nextElementSibling();
            while (sibling != null) {
                Element table = sibling.selectFirst("table.full-league-table");
                if (table != null) {
                    return parseTable(table);
                }
                sibling = sibling.nextElementSibling();
            }
        }
        return Map.of();
    }

    static Map<String, List<String>> parseFirstTable(Document doc) {
        Element table = doc.selectFirst("table.full-league-table");
        if (table == null) {
            return Map.of();
        }
        return parseTable(table);
    }

    static Map<String, List<String>> parseTable(Element table) {
        Map<String, List<String>> rows = new LinkedHashMap<>();
        mergeRows(rows, parseTableBody(table));
        return rows;
    }

    private static void mergeRows(Map<String, List<String>> target, Map<String, List<String>> source) {
        for (Map.Entry<String, List<String>> entry : source.entrySet()) {
            target.putIfAbsent(entry.getKey(), entry.getValue());
        }
    }

    private static Map<String, List<String>> parseTableBody(Element table) {
        Map<String, List<String>> rows = new LinkedHashMap<>();
        for (Element row : table.select("tbody > tr")) {
            Optional<String> teamCode = teamCodeFromRow(row);
            if (teamCode.isEmpty()) {
                continue;
            }
            List<String> values = dataCells(row);
            if (!values.isEmpty()) {
                rows.put(teamCode.get(), values);
            }
        }
        return rows;
    }

    static Optional<String> teamCodeFromRow(Element row) {
        Element teamCell = row.selectFirst("td.team");
        if (teamCell == null) {
            return Optional.empty();
        }
        Element link = teamCell.selectFirst("a[href^=/clubs/]");
        if (link == null) {
            return Optional.empty();
        }
        String name = link.ownText();
        if (name == null || name.isBlank()) {
            name = link.text();
        }
        return FootyStatsTeamNameMapper.toTeamCode(name.trim());
    }

    static List<String> dataCells(Element row) {
        Elements cells = row.select("> td");
        List<String> values = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < cells.size(); i++) {
            if (cells.get(i).hasClass("team")) {
                start = i + 1;
                break;
            }
        }
        if (start == 0 && cells.size() > 2) {
            start = 3;
        }
        for (int i = start; i < cells.size(); i++) {
            String text = cells.get(i).text().trim();
            if (!text.isBlank()) {
                values.add(text);
            }
        }
        return values;
    }

    static Double parsePercent(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().replace("%", "");
        return parseNullable(normalized);
    }

    static Double parseNullable(String value) {
        if (value == null || value.isBlank() || value.contains("/")) {
            return null;
        }
        try {
            String normalized = value.trim().replace(",", ".").replace("+", "");
            return Double.parseDouble(normalized);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static int mpOffset(List<String> cells) {
        if (cells.isEmpty()) {
            return 0;
        }
        Double first = parseNullable(cells.getFirst());
        if (first != null && first == first.intValue() && cells.size() > 3) {
            return 1;
        }
        return 0;
    }

    static Double cellAfterMp(List<String> cells, int index) {
        return cellNumber(cells, mpOffset(cells) + index);
    }

    static Double cellPercentAfterMp(List<String> cells, int index) {
        return cellPercent(cells, mpOffset(cells) + index);
    }

    static Double cellPercent(List<String> cells, int index) {
        if (index >= cells.size()) {
            return null;
        }
        return parsePercent(cells.get(index));
    }

    static Double cellNumber(List<String> cells, int index) {
        if (index >= cells.size()) {
            return null;
        }
        return parseNullable(cells.get(index));
    }

    static String cell(List<String> cells, int index) {
        if (index >= cells.size()) {
            return null;
        }
        return cells.get(index);
    }

    static double ppgFromPointsAndMp(List<String> cells, int pointsIndex, int mpIndex) {
        Double points = cellNumber(cells, pointsIndex);
        Double mp = cellNumber(cells, mpIndex);
        if (points == null || mp == null || mp <= 0) {
            return 0;
        }
        return points / mp;
    }

    static String normalizeLabel(String label) {
        return label == null ? "" : label.trim().toLowerCase(Locale.ROOT);
    }
}
