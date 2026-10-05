package com.greencorridor.model;

import com.greencorridor.exception.InvalidScenarioException;
import com.greencorridor.exception.ScenarioFileException;
import com.greencorridor.io.ScenarioFiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScenarioTest {

    @Test
    void builtInScenariosAreValid() throws InvalidScenarioException {
        for (Scenario s : Scenario.builtInDefaults()) {
            s.validate();
        }
        assertEquals(7, Scenario.builtInDefaults().size());
    }

    @Test
    void vehicleMixIsParsedInOrder() throws InvalidScenarioException {
        Scenario s = new Scenario("Mix test");
        s.setVehicleMix("car:10, BUS:5 ,auto:0");
        Map<String, Integer> mix = s.parseVehicleMix();
        assertEquals("[CAR, BUS, AUTO]", mix.keySet().toString());
        assertEquals(Integer.valueOf(5), mix.get("BUS"));
    }

    @Test
    void badVehicleMixNamesTheField() {
        Scenario s = new Scenario("Mix test");
        for (String bad : new String[]{"CAR45", "TRAIN:10", "CAR:x", "CAR:0,BUS:0", "CAR:5,CAR:5", "CAR:101"}) {
            s.setVehicleMix(bad);
            InvalidScenarioException e = assertThrows(InvalidScenarioException.class, s::validate, bad);
            assertEquals("vehicleMix", e.getField());
        }
    }

    @Test
    void outOfRangeValuesAreRejected() {
        Scenario s = new Scenario("ab");
        assertEquals("name", assertThrows(InvalidScenarioException.class, s::validate).getField());
        s.setName("Range test");
        s.setJunctionCount(4);
        assertEquals("junctionCount", assertThrows(InvalidScenarioException.class, s::validate).getField());
        s.setJunctionCount(2);
        s.setEmergencyEverySec(5);
        assertEquals("emergencyEverySec", assertThrows(InvalidScenarioException.class, s::validate).getField());
    }

    @Test
    void copyIsIndependent() {
        Scenario a = Scenario.builtInDefaults().get(0);
        Scenario b = a.copy();
        assertNotSame(a, b);
        b.setName("Changed");
        assertEquals("Morning Peak - Green Corridor", a.getName());
    }

    @Test
    void serializationRoundTrip(@TempDir Path dir) throws Exception {
        Scenario original = Scenario.builtInDefaults().get(4);
        original.setId(99);
        File file = dir.resolve("hospital.gcs").toFile();
        ScenarioFiles.save(original, file);
        Scenario loaded = ScenarioFiles.load(file);
        assertEquals(original.getName(), loaded.getName());
        assertEquals(original.getEmergencyEverySec(), loaded.getEmergencyEverySec());
        assertEquals(original.getPriorityMode(), loaded.getPriorityMode());
        assertEquals(0, loaded.getId(), "an imported scenario is new to the database");
    }

    @Test
    void foreignObjectsAreRefused(@TempDir Path dir) throws Exception {
        File file = dir.resolve("evil.gcs").toFile();
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(file))) {
            out.writeObject(new ArrayList<String>());
        }
        ScenarioFileException e = assertThrows(ScenarioFileException.class, () -> ScenarioFiles.load(file));
        assertTrue(e.getMessage().contains("not allowed"), e.getMessage());
    }
}
