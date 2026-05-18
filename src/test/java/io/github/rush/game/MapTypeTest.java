package io.github.rush.game;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MapTypeTest {

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
        "NORMAL,      rush_island-normal.schem",
        "OLD_SCHOOL,  rush_island-old_school.schem",
        "NETHER,      rush_island-nether.schem",
        "END,         rush_island-end.schem",
        "AQUAMARINE,  rush_island-aquamarine.schem",
        "SUMMER,      rush_island-summer.schem",
        "CHERRY,      rush_island-cherry.schem",
        "WINTER,      rush_island-winter.schem"
    })
    void schematicNameMatchesExpectedFilename(String typeName, String expected) {
        MapType type = MapType.valueOf(typeName.trim());
        assertEquals(expected.trim(), type.schematicName());
    }
}
