# GreenCorridor: build and run

Plain Java 8 code with Swing and JDBC. The only library is the MySQL driver
(Connector/J 9.7, Type 4), which Maven downloads.

## 1. Requirements

- JDK 8 or newer (8, 11, 17 and 21 all work)
- MySQL Server 8.0 (MySQL Workbench is handy but not required)
- Maven 3.6 or newer, or an IDE that includes it (IntelliJ IDEA, Eclipse, NetBeans)

## 2. Database

Either way works:

- **Let the app do it.** Start the app, enter your MySQL user and password under
  *Database > Connection settings*, and answer **Yes** when it offers to create the tables.
- **Run the script yourself** in MySQL Workbench (File > Open SQL Script, then Execute), or:

  ```bash
  mysql -u root -p < src/main/resources/sql/green_corridor.sql
  ```

The script creates the database `green_corridor` with four tables, three stored procedures and
seven sample scenarios. Running it again resets everything.

The connection settings are saved in `db.properties` next to the program, so every lab
machine can keep its own password. That file and the `logs/` folder are not committed to git.

## 3. Build and run

From this folder:

```bash
mvn package                       # compiles, runs the tests, builds target/GreenCorridor.jar
java -jar target/GreenCorridor.jar
```

`mvn package -DskipTests` skips the tests. `mvn exec:java` runs the app straight from the source.
On Windows you can also double-click `run.bat` after building.

**In an IDE:** open this folder as a Maven project (IntelliJ: *Open* > `pom.xml`; Eclipse:
*Import > Existing Maven Project*; NetBeans: *Open Project*), then run `com.greencorridor.Main`.

Without MySQL the app still runs in **offline mode**. Scenarios are kept in memory, each run is
written to the `logs/` folder, and the Reports tab asks you to connect.

## 4. Using it

1. On **Live Simulation**, pick a scenario (for example *Morning Peak - Green Corridor*) and press
   **Start** (or Enter).
2. Press **Send ambulance** (or A). Watch the lights along its route turn green before it arrives,
   and the cars pull over to let it pass.
3. Open **Thread Monitor** to watch every thread's state change live.
4. To compare the modes, run *Morning Peak - No Priority*, *- Local Sensor* and *- Green Corridor*
   to the end (x8 speed takes 30 s each), then open **Reports**.

| Key | Action | Key | Action |
|-----|--------|-----|--------|
| Enter / F5 | Start | A / F8 | Ambulance to City Hospital |
| Space / F6 | Pause or resume | F / F9 | Fire engine on a cross street |
| Esc / F7 | Stop | 1 2 3 4 | Speed x1, x2, x4, x8 |
| H | Help on the map | F1 | Controls |

**Mouse on the map:**

- Hover a vehicle to see its thread state and priority.
- Click a junction to end its green early.
- Click a road entry arrow to send an ambulance there (Shift+click sends a fire engine).

## 5. Tests and tools

```bash
mvn test
```

There are 26 JUnit 5 tests:

- scenario validation and serialization
- the producer-consumer buffer
- the SQL script parser
- signal timing and pre-emption
- lane rules
- a real multithreaded run, watched by a safety checker
- MySQL CRUD and the stored procedures

The MySQL tests use a separate database, `green_corridor_test`, with the credentials from
`db.properties`, and are skipped when MySQL is not reachable.

These tools live in `src/test/java` and are not part of the jar:

- `com.greencorridor.tools.Experiment` runs scenarios without a window and prints a comparison
  table. These are the numbers in the project README.

  ```bash
  mvn -q test-compile dependency:build-classpath -Dmdep.outputFile=cp.txt
  java -cp "target/classes:target/test-classes:$(cat cp.txt)" com.greencorridor.tools.Experiment 0 8 5
  ```

  The arguments are the scenario index, the speed and the number of repeats.

- `com.greencorridor.ui.ScreenshotTour` drives the real window and saves the screenshots in
  `../screenshots`.

## 6. Troubleshooting

| Problem | Fix |
|---------|-----|
| "Access denied for user" | Enter the right user and password under *Database > Connection settings*. |
| "Communications link failure" | MySQL is not running, or not on port 3306. Start the MySQL service. |
| "Public Key Retrieval is not allowed" | Already handled in the connection URL (`allowPublicKeyRetrieval=true`). Update `db.properties` if you edited the URL by hand. |
| Stored procedure errors when running the script by hand | Run the whole file at once; it uses `DELIMITER $$` around the procedures. |
| Keyboard shortcuts do nothing | Click the map once so it has keyboard focus. |
