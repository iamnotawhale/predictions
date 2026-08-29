package zhigalin.predictions.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class TeamCodeMapperTest {

    @Test
    void mapsEspnAliasesToInternalCodes() {
        assertEquals("AST", TeamCodeMapper.toInternalCode("AVL"));
        assertEquals("BRI", TeamCodeMapper.toInternalCode("BHA"));
        assertEquals("WES", TeamCodeMapper.toInternalCode("WHU"));
        assertEquals("MCI", TeamCodeMapper.toInternalCode("MNC"));
        assertEquals("NOT", TeamCodeMapper.toInternalCode("NFO"));
        assertEquals("MUN", TeamCodeMapper.toInternalCode("MAN"));
    }

    @Test
    void leavesUnknownCodesUnchanged() {
        assertEquals("ARS", TeamCodeMapper.toInternalCode("ARS"));
        assertEquals("LIV", TeamCodeMapper.toInternalCode("LIV"));
    }
}

class TelegramMarkdownV2Test {

    @Test
    void escapesMarkdownV2SpecialCharacters() {
        assertEquals("a\\_b\\*c", TelegramMarkdownV2.escape("a_b*c"));
        assertEquals("1\\.0", TelegramMarkdownV2.escape("1.0"));
        assertEquals("Hello", TelegramMarkdownV2.escape("Hello"));
    }

    @Test
    void handlesNullAndEmpty() {
        assertNull(TelegramMarkdownV2.escape(null));
        assertEquals("", TelegramMarkdownV2.escape(""));
    }
}
