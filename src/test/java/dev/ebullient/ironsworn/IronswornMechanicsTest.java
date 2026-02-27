package dev.ebullient.ironsworn;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class IronswornMechanicsTest {

    @Test
    void lookupOracleResult_turningPoint_majorPlotTwist_exists() throws Exception {
        DataswornService datasworn = new DataswornService();
        IronswornMechanics mechanics = new IronswornMechanics();

        var field = IronswornMechanics.class.getDeclaredField("datasworn");
        field.setAccessible(true);
        field.set(mechanics, datasworn);

        assertDoesNotThrow(() -> mechanics.lookupOracleResult("turning_point", "major_plot_twist", 1));
    }
}
