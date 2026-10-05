# GreenCorridor: Smart Traffic Junction Simulator with Emergency Green Corridor

**Course:** Object Oriented Programming through Java (B.Tech CSE)  
**Project:** Java Course End Project  
**Author / Developer:** Bhanu Teja ([@Bhanu-teja-VCE](https://github.com/Bhanu-teja-VCE))  
**Interactive Visual Guide:** 🌐 [Open `project-explained.html`](project-explained.html) for a complete visual architectural breakdown and interactive diagrams.

---

## 📌 1. Project Overview & Problem Statement

In densely populated cities, emergency response vehicles (ambulances, fire engines) routinely lose critical, life-saving minutes stuck in heavy traffic gridlocks and red lights. In conventional urban setups, emergency corridors are set up manually by deploying traffic police personnel at each intersection along the route.

**GreenCorridor** is a full-featured Java desktop simulation application that models a busy urban road network with up to three coordinated signalized junctions. Built on multi-threaded object-oriented principles, every single vehicle is an autonomous thread navigating roads, obeying lane disciplines, responding to traffic lights, and pulling over when sirens are detected.

The simulator compares three distinct operational modes on identical traffic conditions:
1. **No Priority (Baseline):** Emergency vehicles wait at signals and in traffic queues just like any ordinary civilian vehicle.
2. **Local Sensor (Actuated):** Signals turn green only when the emergency vehicle reaches an inductive loop sensor placed immediately before the stop line (how standard "smart" signals work today).
3. **Green Corridor (Coordinated Pre-emption):** When an emergency vehicle is dispatched, its intended trajectory is preemptively communicated to all downstream junctions. Signals clear existing vehicular queues in advance so the emergency vehicle experiences zero red-light halts.

Every simulation run records telemetry and trip statistics into a MySQL database via JDBC, enabling comprehensive comparative analytics.

---

## 🖼️ Application Screenshots

| Live Traffic Simulation | Thread State Monitor |
| :---: | :---: |
| ![Live Traffic](screenshots/02-traffic.png) | ![Thread Monitor](screenshots/05-thread-monitor.png) |
| *Real-time canvas rendering 30 FPS* | *Live thread lifecycle and priority tracking* |

| Emergency Vehicle Green Corridor | Performance & Comparison Reports |
| :---: | :---: |
| ![Green Corridor](screenshots/03-corridor-before-arrival.png) | ![Reports](screenshots/09-reports.png) |
| *Junction signals clearing traffic ahead of ambulance* | *Stored procedure analytics comparing modes* |

---

## 🚀 2. Key Features Across the 4 GUI Tabs

The application GUI is built with Java Swing and organized into four core functional tabs:

### 🚦 Tab 1: Live Simulation
* **Interactive Canvas:** Animated at ~30 FPS rendering vehicles, junctions, stop lines, pedestrian crossings, and detector loops.
* **Simulation Controls:** Start (`Enter`/`F5`), Pause/Resume (`Space`/`F6`), Stop (`Esc`/`F7`), and speed acceleration (`1x`, `2x`, `4x`, `8x`).
* **Emergency Dispatch:** Trigger an ambulance (`A`/`F8`) or fire engine (`F`/`F9`) to City Hospital.
* **Interactive Map Controls:**
  * Hover over any vehicle to inspect its thread ID, thread priority, velocity, and current state.
  * Click on any junction to manually end green phase early (police override).
  * Click any road entry arrow to dispatch emergency vehicles directly to that entry point.
* **Live Telemetry:** Dynamic tiles tracking active vehicles, completed trips, average delays, and real-time event logs.

### 🧵 Tab 2: Thread Monitor
* Visualizes every active thread in the simulation in real time.
* Displays thread name, thread life-cycle state (`NEW`, `RUNNABLE`, `TIMED_WAITING`, `WAITING`, `TERMINATED`), execution priority (`1` to `10`), and current activity description.
* Refreshes twice per second to demonstrate Java multithreading and concurrency mechanics in action.

### 📋 Tab 3: Scenarios (CRUD)
* Full Create, Read, Update, and Delete (CRUD) interface for traffic scenarios.
* Configure parameters: vehicle spawn rates, vehicle mix ratios (`CAR:45,BIKE:25,AUTO:15,BUS:10,TRUCK:5`), signal durations, and priority mode.
* **Object Serialization:** Export and import custom scenarios to and from `.gcs` binary files using Java Object Streams (`ObjectInputStream` / `ObjectOutputStream`).

### 📊 Tab 4: Reports & Analytics
* Historical log of simulation runs retrieved from the database.
* Graphical comparison charts powered by MySQL stored procedures (`sp_mode_comparison`) comparing emergency delays vs. ordinary traffic impacts.
* Export detailed trip-level telemetry to CSV format for external analysis.

---

## 📈 3. Benchmark Results & Findings

Extensive headless benchmarking (`Experiment` test suite, 5 runs of "Morning Peak" scenario, 240 simulated seconds, ~370 vehicles per run) demonstrates clear real-world outcomes:

| Priority Mode | Emergency Vehicle Delay | Ordinary Traffic Delay | 95% of Trips Completed In | Completed Trips |
| :--- | :---: | :---: | :---: | :---: |
| **No Priority** | 6.1 s | 12.2 s | 26.1 s | 381 |
| **Local Sensor** | 4.2 s (32% reduction) | 14.4 s | 43.1 s | 356 |
| **Green Corridor** | **1.3 s (79% reduction)** | 14.1 s | **35.3 s** | 368 |

### Key Takeaways:
1. **Green Corridor virtually eliminates emergency delays:** Advance clearance ensures that yellow (3s) and all-red clearance intervals (2s) complete *before* the ambulance arrives at the intersection.
2. **Local Sensors cause sudden braking and halts:** The ambulance must wait at the stop line while the cross-street completes its yellow clearance.
3. **Minimal impact on civilian traffic:** While cross-street traffic experiences slight additional wait times (~16% increase), the green corridor distributes this cost smoothly, allowing 95% of trips to finish faster than under local sensor spikes.

---

## 🏗️ 4. Concurrency & Multithreading Architecture

The project demonstrates production-grade Java concurrency:

```
 Swing Event Dispatch Thread (EDT) ─── Paints Canvas (30 FPS), handles UI events
 │
 ├─ Supervisor        (extends Thread)  ── Monitors duration, cleanly halts simulation
 ├─ Spawner           (extends Thread)  ── Stochastically spawns vehicle threads (NEW)
 ├─ Signal-J1..J3     (Runnable, Prio 7)── Controls phase cycles and pre-emption triggers
 │        ▲ notifyAll() on every phase change
 │        │ wait() at red light stop lines
 ├─ Car-1, Bus-2 …    (Runnable, Prio 5)── Autonomous vehicle threads
 ├─ Ambulance-X       (Runnable, Prio 10)─ Priority thread requesting green corridor & overtaking
 │        │ put(trip)
 │        ▼
 │   EventBuffer (Bounded Producer-Consumer, wait() / notifyAll())
 │        │ take()
 └─ DB-Logger         (extends Thread, Prio 1) ── Background batch INSERTs into MySQL & log file
```

### Safety, Deadlock, and Starvation Prevention:
* **Junction Box Critical Section:** A vehicle only enters the junction box if there is guaranteed clearance beyond it. The junction box is guarded by a `synchronized` lock so crossing lanes can never enter simultaneously.
* **Strict Lock Ordering:** Lane locks never nest into other locked components. The only nested synchronization (`Signal` &rarr; `Lane`) adheres strictly to uniform acquisition order.
* **Overtaking / Pulling-over Protocol:** Civilian vehicles pull over to the left curb only if all leading vehicles have also pulled over, preventing vehicular gridlocks inside junctions.
* **Starvation Prevention:** A cross-street held red by an emergency pre-emption is guaranteed a mandatory minimum green phase (4s) before any subsequent pre-emption can lock the junction again.

---

## 📚 5. Complete Java Syllabus Coverage (Units I – V)

This project was built specifically to demonstrate mastery across all five core units of the Java syllabus:

| Unit | Syllabus Topics | Where & How It Is Implemented in Code |
| :---: | :--- | :--- |
| **Unit I** | **OOP Fundamentals, Classes & Objects** | Encapsulated model entities [`Scenario.java`](app/src/main/java/com/greencorridor/model/Scenario.java), [`TripRecord.java`](app/src/main/java/com/greencorridor/model/TripRecord.java). |
| | **Constructors & Overloading** | Overloaded constructors in `Scenario()`, `Database.getConnection()`, `StatTile.setValue()`. |
| | **`this`, `static`, Arrays** | Static numbering in `Vehicle.nextNumber()`, static constants in `SimConfig`, array grid maps in `Junction`. |
| | **Inheritance (`super`)** | Hierarchy: `Vehicle` &rarr; `EmergencyVehicle` &rarr; `Ambulance` / `FireEngine`. Calls to `super(length, width, maxSpeed, accel)`. |
| | **Polymorphism & Dynamic Dispatch** | Polymorphic rendering via `Drawable.draw()` and dynamic vehicle physics in `Vehicle.drawBody()`. Dynamic repository selection (`ScenarioRepository`). |
| | **Abstract Classes & `final`** | `abstract class Vehicle`, `abstract class SimEvent`; `final class Ambulance`, `final class SimConfig`. |
| | **Interfaces & Multiple Inheritance** | Interfaces: `Drawable`, `Prioritized`, `Monitorable`, `EngineListener`. Interface extension: `EmergencyResponder extends Prioritized, Drawable`. |
| | **Packages & Access Specifiers** | Structured packaging: `model`, `road`, `vehicle`, `engine`, `db`, `io`, `exception`, `ui`. Protected hook methods `beforeStep()`, package-private members. |
| **Unit II** | **Exception Handling** | `try-catch-finally`, `try-with-resources` across JDBC operations. Custom exceptions: `InvalidScenarioException`, `DatabaseUnavailableException`, `ScenarioFileException`. |
| | **Thread Creation & Lifecycle** | Creating threads via `extends Thread` (`VehicleSpawner`, `DatabaseLogger`) and `implements Runnable` (`Vehicle`, `SignalController`). Visualized live in Thread Monitor. |
| | **Thread Priorities** | Explicit priority allocation: Emergency vehicles (`MAX_PRIORITY = 10`), Signal Controllers (`7`), Ordinary Vehicles (`5`), Database Logger (`MIN_PRIORITY = 1`). |
| | **Synchronization & Inter-thread Communication** | `synchronized` blocks in `Lane` and `Junction.tryEnterBox()`. Inter-thread signaling with `wait()` and `notifyAll()` in bounded `EventBuffer` (producer-consumer) and `SignalController.awaitGreen()`. |
| | **String & StringBuffer** | Dynamic SQL statements and CSV records assembled using `StringBuffer` and `StringBuilder` for performance. |
| **Unit III** | **Collections Framework** | `LinkedList` for vehicle lane queues and event buffer; `HashMap` for road network key lookups; `HashSet` for called junction sets; `TreeSet` with custom `Comparator` for top delayed trips; `TreeMap` for type-wise delays. |
| | **Arrays & StringTokenizer** | Tokenizing vehicle mix specifications (`Scenario.parseVehicleMix`), sorting percentiles with `Arrays.sort()`. |
| | **File Streams & Serialization** | `FileInputStream`, `FileOutputStream`, `FileReader`, and `FileWriter`. Object serialization (`implements Serializable`) for saving/loading scenario `.gcs` files. |
| **Unit IV** | **Swing GUI Components** | `JFrame`, `JTabbedPane`, `JPanel`, `JTable`, `JButton`, `JComboBox`, `JSplitPane`, `JScrollPane`, `JDialog`, `JFileChooser`. |
| | **Layout Managers** | `BorderLayout`, `FlowLayout`, `GridLayout`, `CardLayout` (switching scenario views & offline/online state), `BoxLayout`, and `GridBagLayout`. |
| | **Event Delegation Model** | `ActionListener`, `MouseAdapter`, `KeyAdapter`, `WindowAdapter`, `ChangeListener` with hotkeys and menu accelerators. |
| **Unit V** | **JDBC & Database Architecture** | Pure Java Type 4 MySQL Connector (`mysql-connector-j`). Connection management via `DriverManager` in [`Database.java`](app/src/main/java/com/greencorridor/db/Database.java). |
| | **Statements & PreparedStatements** | Safe parameterized queries in `MySqlScenarioRepository` and batch execution in `DatabaseLogger`. |
| | **CallableStatements** | Executing MySQL stored procedures: `sp_start_run`, `sp_finish_run`, and `sp_mode_comparison`. |
| | **Transactions & Resilience** | Manual commit/rollback (`connection.setAutoCommit(false)`), database metadata checks, and seamless offline mode fallback. |

---

## 🗄️ 6. Database Schema Design

The application works with a MySQL 8 database named `green_corridor`:

```
               ┌────────────────────┐
               │      scenario      │
               └─────────┬──────────┘
                         │ 1:N
                         ▼
               ┌────────────────────┐
               │   simulation_run   │
               └─────────┬──────────┘
             1:N ┌───────┴────────┐ 1:N
                 ▼                ▼
     ┌──────────────────┐  ┌──────────────────┐
     │   vehicle_trip   │  │ preemption_event │
     └──────────────────┘  └──────────────────┘
```

* **`scenario`**: Defines road layout, signal cycles, spawn rates, and priority mode.
* **`simulation_run`**: Records metadata for each execution (start time, end time, total vehicles, average delays).
* **`vehicle_trip`**: Telemetry for each individual vehicle trip (entry time, exit time, distance, delay).
* **`preemption_event`**: Logs of emergency vehicle activations, which signals were switched, and response latencies.
* **Stored Procedures:**
  * `sp_start_run`: Initializes the run record and returns the generated run ID.
  * `sp_finish_run`: Aggregates completed vehicle trip data and computes summary statistics.
  * `sp_mode_comparison`: Averages key metrics grouped by priority mode for reports.

*Full SQL script is available at:* [`app/src/main/resources/sql/green_corridor.sql`](app/src/main/resources/sql/green_corridor.sql).

---

## 💻 7. How to Build and Run

### System Requirements:
* **Java:** JDK 8 or newer (JDK 8, 11, 17, and 21 are fully supported).
* **OS:** Windows, macOS, or Linux.
* **MySQL (Optional):** MySQL 8.0 (if not running, the application automatically runs in offline/in-memory mode).

### Option 1: Quick Launch (Windows)
Double-click [`run_project.bat`](run_project.bat) in the project root folder.

### Option 2: Run via Pre-built JAR
Open your terminal in the `app` folder and run:
```bash
java -jar target/GreenCorridor.jar
```

### Option 3: Compile and Package with Maven
```bash
cd app
mvn clean package
java -jar target/GreenCorridor.jar
```
*(Or use `mvn exec:java` to run directly from source).*

### Option 4: Open in IDE (IntelliJ IDEA / Eclipse / VS Code)
1. Open your IDE and select **Open** &rarr; select the `app` folder (or `pom.xml`).
2. Allow Maven dependencies to resolve.
3. Locate `src/main/java/com/greencorridor/Main.java`.
4. Right-click and select **Run 'Main.main()'**.

---

## ⌨️ 8. Keyboard & Mouse Controls Reference

| Key / Shortcut | Action Description |
| :--- | :--- |
| **Enter / F5** | Start simulation |
| **Space / F6** | Pause or Resume simulation |
| **Esc / F7** | Stop simulation |
| **A / F8** | Dispatch Ambulance to City Hospital |
| **F / F9** | Dispatch Fire Engine on cross-street |
| **1, 2, 3, 4** | Set simulation speed: `1x`, `2x`, `4x`, `8x` |
| **H** | Toggle on-canvas help overlay |
| **F1** | Open controls dialog |

### Mouse Interactions:
* **Hover over Vehicle:** Displays real-time thread ID, thread state, velocity, and priority.
* **Click Junction:** Manually ends current green phase early (police manual override).
* **Click Road Entry Arrow:** Spawns an ambulance at that entry point (*Shift + Click* spawns a fire engine).

---

## 🎯 9. Viva Questions & Presentation Guide for Faculty (Mam)

When demonstrating this project to your faculty, here are typical questions and recommended answers:

1. **Why use multiple threads instead of a single timer loop?**  
   *Answer:* In real traffic, vehicles operate autonomously. Giving each vehicle its own thread models true concurrency—each vehicle calculates its own perception-reaction delay, follows safe distances, and responds to signals independently without a centralized blocking loop.
2. **How is deadlock prevented when hundreds of threads interact?**  
   *Answer:* Through strict hierarchical lock ordering and non-blocking reservation. A vehicle will never enter an intersection box unless there is confirmed space ahead. This prevents the classic "box gridlock" where circular waiting occurs.
3. **What is the difference between Local Sensor and Green Corridor?**  
   *Answer:* Local sensors detect vehicles only when they are right at the junction, forcing ambulances to brake while the signal switches yellow (3s). The Green Corridor preempts the route in advance so signals are already green before the ambulance arrives, reducing delay by ~79%.
4. **How does the project handle offline execution if MySQL is down?**  
   *Answer:* The application implements the Repository Design Pattern. If MySQL is unreachable, `InMemoryScenarioRepository` and `RunLogWriter` take over seamlessly without crashing the UI or interrupting the simulation.
5. **How is Object Serialization used?**  
   *Answer:* Scenarios implement `java.io.Serializable`. Users can export custom scenarios into binary `.gcs` files and re-import them using `ObjectInputStream` and `ObjectOutputStream`.

---

## 📄 License & Credits
Developed as part of the Object Oriented Programming through Java (OOPJ) Course End Project. All rights reserved.
