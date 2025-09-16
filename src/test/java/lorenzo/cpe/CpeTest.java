package lorenzo.cpe;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class CpeTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "cpe:2.3:a:apache:http_server:2.4.53:*:*:*:*:*:*:*",
            "cpe:2.3:a:apache:http_server:2.4.53:-:*:*:*:*:*:*",
            "cpe:2.3:a:microsoft:iis:*:*:*:*:*:*:*:*",
            "cpe:2.3:a:vendor:prod:-:*:*:*:*:*:*:*",
            "cpe:2.3:a:vendor:prod:ver_*:*:*:*:*:*:*:*"
    })
    @DisplayName("Test CPE parsing")
    void cpeAssert(final String cpe) {
        // Arrange / Act
        Cpe parsedCpe = Cpe.parse(cpe);

        // Assert
        assertNotNull(parsedCpe);
    }

    private static Stream<Arguments> generateMatchingArgs() {
        return Stream.of(
                Arguments.of("cpe:2.3:a:apache:http_server:2.4.53:*:*:*:*:*:*:*",
                        "cpe:2.3:a:apache:http_server:2.4.53:-:*:*:*:*:*:*", true),
                Arguments.of("cpe:2.3:a:microsoft:iis:*:*:*:*:*:*:*:*",
                        "cpe:2.3:a:apache:http_server:2.4.53:*:*:*:*:*:*:*", false),
                Arguments.of("cpe:2.3:a:apache:http_server:2.4.53:*:*:*:*:*:*:*",
                        "cpe:2.3:a:apache:http_server:2.4.53:*:*:*:*:*:*:*", true),
                Arguments.of("cpe:2.3:a:vendor:prod:-:*:*:*:*:*:*:*", "cpe:2.3:a:vendor:prod:ver_*:*:*:*:*:*:*:*",
                        false));
    }

    @ParameterizedTest
    @MethodSource("generateMatchingArgs")
    void cpeMatchTests(String cpe1, String cpe2, boolean expectedResult) {
        // Arrange
        Cpe c1 = assertDoesNotThrow(() -> Cpe.parse(cpe1));
        Cpe c2 = assertDoesNotThrow(() -> Cpe.parse(cpe2));

        // Act
        boolean result = c1.matches(c2);

        // Assert
        assertEquals(expectedResult, result);
    }
}
