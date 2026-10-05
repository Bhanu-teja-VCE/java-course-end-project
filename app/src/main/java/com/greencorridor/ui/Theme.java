package com.greencorridor.ui;

import java.awt.Color;
import java.awt.Font;

/** Colours and fonts shared by every screen. */
public final class Theme {

    private Theme() {
    }

    // ----- brand and window -----
    public static final Color BRAND = new Color(0x1B7F4B);
    public static final Color BRAND_DARK = new Color(0x135C36);
    public static final Color PANEL = new Color(0xF4F6F5);
    public static final Color CARD = Color.WHITE;
    public static final Color BORDER = new Color(0xD5DBD8);
    public static final Color TEXT = new Color(0x1F2A26);
    public static final Color MUTED = new Color(0x5F6B66);
    public static final Color DANGER = new Color(0xC62828);
    public static final Color WARNING = new Color(0xB26A00);

    // ----- map -----
    public static final Color GROUND = new Color(0xE9E5DA);
    public static final Color BLOCK = new Color(0xD8D1C1);
    public static final Color BLOCK_EDGE = new Color(0xC2B9A6);
    public static final Color PARK = new Color(0xB7D3A1);
    public static final Color TREE = new Color(0x7FAE66);
    public static final Color HOSPITAL = new Color(0xF3E3E3);
    public static final Color SIDEWALK = new Color(0xC9C3B4);
    public static final Color ASPHALT = new Color(0x3B4047);
    public static final Color MARKING = new Color(0xF2F2F2);
    public static final Color BOX_HATCH = new Color(242, 201, 76, 120);
    public static final Color LABEL_BG = new Color(0, 0, 0, 150);

    // ----- signals -----
    public static final Color LIGHT_RED = new Color(0xFF3B30);
    public static final Color LIGHT_YELLOW = new Color(0xFFCC00);
    public static final Color LIGHT_GREEN = new Color(0x34C759);
    public static final Color HOUSING = new Color(0x22282C);
    public static final Color CORRIDOR = new Color(52, 199, 89, 70);
    public static final Color SENSOR = new Color(0x4FC3F7);

    // ----- thread states (Thread Monitor) -----
    public static final Color STATE_NEW = new Color(0x90A4AE);
    public static final Color STATE_RUNNABLE = new Color(0x2E7D32);
    public static final Color STATE_TIMED_WAITING = new Color(0x1565C0);
    public static final Color STATE_WAITING = new Color(0xEF6C00);
    public static final Color STATE_BLOCKED = new Color(0xC62828);
    public static final Color STATE_TERMINATED = new Color(0x455A64);

    // ----- charts -----
    public static final Color SERIES_EMERGENCY = new Color(0xD84343);
    public static final Color SERIES_GENERAL = new Color(0x3F6FB5);

    public static final Font TITLE = new Font(Font.SANS_SERIF, Font.BOLD, 16);
    public static final Font HEADING = new Font(Font.SANS_SERIF, Font.BOLD, 13);
    public static final Font BODY = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    public static final Font SMALL = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
    public static final Font VALUE = new Font(Font.SANS_SERIF, Font.BOLD, 20);
    public static final Font MONO = new Font(Font.MONOSPACED, Font.PLAIN, 12);

    /** Colour of a thread state in the Thread Monitor. */
    public static Color stateColor(Thread.State state) {
        switch (state) {
            case NEW:
                return STATE_NEW;
            case RUNNABLE:
                return STATE_RUNNABLE;
            case TIMED_WAITING:
                return STATE_TIMED_WAITING;
            case WAITING:
                return STATE_WAITING;
            case BLOCKED:
                return STATE_BLOCKED;
            default:
                return STATE_TERMINATED;
        }
    }
}
