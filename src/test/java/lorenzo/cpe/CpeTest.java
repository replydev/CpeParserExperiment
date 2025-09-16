package lorenzo.cpe;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
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

}
