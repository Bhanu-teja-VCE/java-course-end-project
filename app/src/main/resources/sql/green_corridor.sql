-- =====================================================================
--  GreenCorridor - Smart Traffic Junction Simulator
--  MySQL 8.0 database: tables, stored procedures and sample scenarios.
--
--  Run it in MySQL Workbench (File > Open SQL Script, then Execute), or:
--      mysql -u root -p < green_corridor.sql
--  The application can also run it for you: menu Database > Create tables.
--  WARNING: it drops and re-creates the tables, so saved runs are erased.
-- =====================================================================

CREATE DATABASE IF NOT EXISTS green_corridor;
USE green_corridor;

DROP PROCEDURE IF EXISTS sp_mode_comparison;
DROP PROCEDURE IF EXISTS sp_finish_run;
DROP PROCEDURE IF EXISTS sp_start_run;
DROP TABLE IF EXISTS preemption_event;
DROP TABLE IF EXISTS vehicle_trip;
DROP TABLE IF EXISTS simulation_run;
DROP TABLE IF EXISTS scenario;

-- ---------------------------------------------------------------------
-- A traffic situation that can be simulated (CRUD from the Scenarios tab)
-- ---------------------------------------------------------------------
CREATE TABLE scenario (
    scenario_id         INT AUTO_INCREMENT PRIMARY KEY,
    name                VARCHAR(60)  NOT NULL,
    description         VARCHAR(255) NOT NULL DEFAULT '',
    junction_count      TINYINT      NOT NULL DEFAULT 3,
    main_rate_per_min   INT          NOT NULL DEFAULT 18,
    cross_rate_per_min  INT          NOT NULL DEFAULT 8,
    signal_mode         ENUM('FIXED','ADAPTIVE')        NOT NULL DEFAULT 'FIXED',
    priority_mode       ENUM('NONE','LOCAL','CORRIDOR') NOT NULL DEFAULT 'CORRIDOR',
    main_green_sec      INT          NOT NULL DEFAULT 12,
    cross_green_sec     INT          NOT NULL DEFAULT 8,
    yellow_sec          INT          NOT NULL DEFAULT 3,
    vehicle_mix         VARCHAR(120) NOT NULL DEFAULT 'CAR:45,BIKE:25,AUTO:15,BUS:8,TRUCK:7',
    emergency_every_sec INT          NOT NULL DEFAULT 0,
    duration_sec        INT          NOT NULL DEFAULT 180,
    random_seed         INT          NOT NULL DEFAULT 42,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_scenario_name UNIQUE (name),
    CONSTRAINT chk_junction_count CHECK (junction_count BETWEEN 1 AND 3),
    CONSTRAINT chk_duration CHECK (duration_sec BETWEEN 30 AND 1800)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- One execution of a scenario. Modes are copied in case the scenario changes later.
-- ---------------------------------------------------------------------
CREATE TABLE simulation_run (
    run_id                  INT AUTO_INCREMENT PRIMARY KEY,
    scenario_id             INT NULL,
    scenario_name           VARCHAR(60) NOT NULL,
    signal_mode             ENUM('FIXED','ADAPTIVE')        NOT NULL,
    priority_mode           ENUM('NONE','LOCAL','CORRIDOR') NOT NULL,
    junction_count          TINYINT     NOT NULL,
    started_at              DATETIME    NOT NULL,
    ended_at                DATETIME    NULL,
    sim_seconds             INT         NOT NULL DEFAULT 0,
    status                  ENUM('RUNNING','COMPLETED','STOPPED') NOT NULL DEFAULT 'RUNNING',
    vehicles_completed      INT         NOT NULL DEFAULT 0,
    avg_delay_sec           DECIMAL(8,2) NULL,
    max_delay_sec           DECIMAL(8,2) NULL,
    avg_stops               DECIMAL(6,2) NULL,
    emergency_count         INT         NOT NULL DEFAULT 0,
    emergency_avg_delay_sec DECIMAL(8,2) NULL,
    throughput_per_min      DECIMAL(8,2) NULL,
    CONSTRAINT fk_run_scenario FOREIGN KEY (scenario_id)
        REFERENCES scenario (scenario_id) ON DELETE SET NULL
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- Every vehicle that crossed the map during a run (batch-inserted by the logger thread)
-- ---------------------------------------------------------------------
CREATE TABLE vehicle_trip (
    trip_id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    run_id        INT          NOT NULL,
    vehicle_no    INT          NOT NULL,
    vehicle_type  VARCHAR(16)  NOT NULL,
    is_emergency  BOOLEAN      NOT NULL DEFAULT FALSE,
    direction     ENUM('EAST','WEST','NORTH','SOUTH') NOT NULL,
    entry_sim_ms  BIGINT       NOT NULL,
    exit_sim_ms   BIGINT       NOT NULL,
    travel_sec    DECIMAL(8,2) NOT NULL,
    delay_sec     DECIMAL(8,2) NOT NULL,
    wait_sec      DECIMAL(8,2) NOT NULL,
    stops         INT          NOT NULL,
    CONSTRAINT fk_trip_run FOREIGN KEY (run_id)
        REFERENCES simulation_run (run_id) ON DELETE CASCADE,
    INDEX idx_trip_run (run_id, is_emergency)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- Every time a junction gave right of way to an emergency vehicle
-- ---------------------------------------------------------------------
CREATE TABLE preemption_event (
    event_id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    run_id           INT          NOT NULL,
    junction_no      TINYINT      NOT NULL,
    vehicle_no       INT          NOT NULL,
    vehicle_type     VARCHAR(16)  NOT NULL,
    direction        ENUM('EAST','WEST','NORTH','SOUTH') NOT NULL,
    requested_sim_ms BIGINT       NOT NULL,
    green_sim_ms     BIGINT       NULL,
    cleared_sim_ms   BIGINT       NOT NULL,
    response_sec     DECIMAL(8,2) NOT NULL,
    CONSTRAINT fk_preemption_run FOREIGN KEY (run_id)
        REFERENCES simulation_run (run_id) ON DELETE CASCADE,
    INDEX idx_preemption_run (run_id)
) ENGINE = InnoDB;

DELIMITER $$

-- Creates the run row when a simulation starts and returns its id (OUT parameter).
CREATE PROCEDURE sp_start_run(
    IN  p_scenario_id    INT,
    IN  p_scenario_name  VARCHAR(60),
    IN  p_signal_mode    VARCHAR(10),
    IN  p_priority_mode  VARCHAR(10),
    IN  p_junction_count TINYINT,
    OUT p_run_id         INT)
BEGIN
    INSERT INTO simulation_run (scenario_id, scenario_name, signal_mode, priority_mode,
                                junction_count, started_at, status)
    VALUES (p_scenario_id, p_scenario_name, p_signal_mode, p_priority_mode,
            p_junction_count, NOW(), 'RUNNING');
    SET p_run_id = LAST_INSERT_ID();
END$$

-- Closes a run: works out its figures from the stored trips and returns the key ones.
CREATE PROCEDURE sp_finish_run(
    IN  p_run_id              INT,
    IN  p_sim_seconds         INT,
    IN  p_status              VARCHAR(10),
    OUT p_trips               INT,
    OUT p_avg_delay           DECIMAL(8,2),
    OUT p_emergency_avg_delay DECIMAL(8,2))
BEGIN
    DECLARE v_max_delay DECIMAL(8,2);
    DECLARE v_avg_stops DECIMAL(6,2);
    DECLARE v_emergency_count INT;

    SELECT COUNT(*) INTO p_trips
      FROM vehicle_trip WHERE run_id = p_run_id;

    SELECT ROUND(AVG(delay_sec), 2), MAX(delay_sec), ROUND(AVG(stops), 2)
      INTO p_avg_delay, v_max_delay, v_avg_stops
      FROM vehicle_trip WHERE run_id = p_run_id AND is_emergency = FALSE;

    SELECT COUNT(*), ROUND(AVG(delay_sec), 2)
      INTO v_emergency_count, p_emergency_avg_delay
      FROM vehicle_trip WHERE run_id = p_run_id AND is_emergency = TRUE;

    UPDATE simulation_run
       SET ended_at                = NOW(),
           sim_seconds             = p_sim_seconds,
           status                  = p_status,
           vehicles_completed      = p_trips,
           avg_delay_sec           = p_avg_delay,
           max_delay_sec           = v_max_delay,
           avg_stops               = v_avg_stops,
           emergency_count         = v_emergency_count,
           emergency_avg_delay_sec = p_emergency_avg_delay,
           throughput_per_min      = ROUND(p_trips * 60 / NULLIF(p_sim_seconds, 0), 2)
     WHERE run_id = p_run_id;
END$$

-- Average results per priority mode, for the comparison chart. Only runs that went
-- the full duration count, so a run stopped early does not distort the averages.
-- p_signal_mode = 'ALL', 'FIXED' or 'ADAPTIVE'.
CREATE PROCEDURE sp_mode_comparison(IN p_signal_mode VARCHAR(10))
BEGIN
    SELECT priority_mode,
           COUNT(*)                               AS runs,
           ROUND(AVG(avg_delay_sec), 2)           AS avg_delay_sec,
           ROUND(AVG(emergency_avg_delay_sec), 2) AS emergency_avg_delay_sec,
           ROUND(AVG(throughput_per_min), 2)      AS throughput_per_min
      FROM simulation_run
     WHERE status = 'COMPLETED'
       AND vehicles_completed > 0
       AND (p_signal_mode = 'ALL' OR signal_mode = p_signal_mode)
     GROUP BY priority_mode
     ORDER BY FIELD(priority_mode, 'NONE', 'LOCAL', 'CORRIDOR');
END$$

DELIMITER ;

-- ---------------------------------------------------------------------
-- Sample scenarios (the three "Morning Peak" ones differ only in priority mode,
-- so running all three gives a fair comparison)
-- ---------------------------------------------------------------------
INSERT INTO scenario (name, description, junction_count, main_rate_per_min, cross_rate_per_min,
                      signal_mode, priority_mode, main_green_sec, cross_green_sec, yellow_sec,
                      vehicle_mix, emergency_every_sec, duration_sec, random_seed)
VALUES
('Morning Peak - Green Corridor',
 'Busy main road, 3 junctions, an emergency every 40 s. Junctions ahead are cleared in advance.',
 3, 22, 10, 'FIXED', 'CORRIDOR', 12, 8, 3, 'CAR:45,BIKE:25,AUTO:15,BUS:8,TRUCK:7', 40, 240, 42),
('Morning Peak - Local Sensor',
 'Same traffic. A junction turns green only when the emergency vehicle reaches its sensor.',
 3, 22, 10, 'FIXED', 'LOCAL', 12, 8, 3, 'CAR:45,BIKE:25,AUTO:15,BUS:8,TRUCK:7', 40, 240, 42),
('Morning Peak - No Priority',
 'Same traffic. Emergency vehicles wait at red lights like every other vehicle.',
 3, 22, 10, 'FIXED', 'NONE', 12, 8, 3, 'CAR:45,BIKE:25,AUTO:15,BUS:8,TRUCK:7', 40, 240, 42),
('Evening Rush - Adaptive Signals',
 'Heavy cross traffic handled by vehicle-actuated signals, with a green corridor.',
 3, 18, 14, 'ADAPTIVE', 'CORRIDOR', 12, 8, 3, 'CAR:45,BIKE:25,AUTO:15,BUS:8,TRUCK:7', 60, 240, 42),
('Hospital Road - Frequent Emergencies',
 'Two junctions near the city hospital. An emergency every 25 s; every third one is a fire engine on a cross street.',
 2, 20, 10, 'FIXED', 'CORRIDOR', 12, 8, 3, 'CAR:45,BIKE:25,AUTO:15,BUS:8,TRUCK:7', 25, 180, 42),
('Single Junction Demo',
 'One junction and light traffic. Good for explaining the signal cycle in the viva.',
 1, 10, 6, 'FIXED', 'CORRIDOR', 12, 8, 3, 'CAR:45,BIKE:25,AUTO:15,BUS:8,TRUCK:7', 0, 120, 42),
('Night - Light Traffic',
 'Few vehicles. Adaptive signals keep the main road green until someone arrives.',
 3, 6, 3, 'ADAPTIVE', 'CORRIDOR', 12, 8, 3, 'CAR:40,BIKE:30,AUTO:10,BUS:5,TRUCK:15', 0, 120, 42);
