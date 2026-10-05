package com.greencorridor.io;

import com.greencorridor.exception.ScenarioFileException;
import com.greencorridor.model.Scenario;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Saves a scenario to a ".gcs" file with object serialization, so it can be shared
 * between computers (for example, carried to the lab on a pen drive) and loaded back.
 */
public final class ScenarioFiles {

    public static final String EXTENSION = "gcs";

    private ScenarioFiles() {
    }

    public static void save(Scenario scenario, File file) throws ScenarioFileException {
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(file))) {
            out.writeObject(scenario);
        } catch (IOException e) {
            throw new ScenarioFileException("Could not write " + file.getName() + ": " + e.getMessage(), e);
        }
    }

    public static Scenario load(File file) throws ScenarioFileException {
        try (ObjectInputStream in = new SafeObjectInputStream(new FileInputStream(file))) {
            Object obj = in.readObject();
            if (!(obj instanceof Scenario)) {
                throw new ScenarioFileException(file.getName() + " is not a GreenCorridor scenario", null);
            }
            Scenario scenario = (Scenario) obj;
            scenario.setId(0);                         // it is new to this database
            scenario.validate();
            return scenario;
        } catch (IOException | ClassNotFoundException e) {
            throw new ScenarioFileException("Could not read " + file.getName() + ": " + e.getMessage(), e);
        } catch (com.greencorridor.exception.InvalidScenarioException e) {
            throw new ScenarioFileException(file.getName() + " contains an invalid scenario: " + e.getMessage(), e);
        }
    }

    /**
     * Reading arbitrary objects from a file is a known security risk, so this stream
     * only accepts the classes a scenario is made of.
     */
    private static class SafeObjectInputStream extends ObjectInputStream {

        private static final Set<String> ALLOWED = new HashSet<String>(Arrays.asList(
                Scenario.class.getName(),
                com.greencorridor.model.SignalMode.class.getName(),
                com.greencorridor.model.PriorityMode.class.getName(),
                Enum.class.getName(),
                String.class.getName()));

        SafeObjectInputStream(InputStream in) throws IOException {
            super(in);
        }

        @Override
        protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
            if (!ALLOWED.contains(desc.getName())) {
                throw new InvalidClassException(desc.getName(), "not allowed in a scenario file");
            }
            return super.resolveClass(desc);
        }
    }
}
