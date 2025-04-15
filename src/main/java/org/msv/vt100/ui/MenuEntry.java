package org.msv.vt100.ui;

class MenuEntry {
    private final String label;
    private final Runnable action;
    private final boolean separator;

    public MenuEntry(String label, Runnable action) {
        this(label, action, false);
    }

    public MenuEntry(String label, Runnable action, boolean separator) {
        this.label = label;
        this.action = action;
        this.separator = separator;
    }

    public static MenuEntry separator() {
        return new MenuEntry("", null, true);
    }

    public boolean isSeparator() {
        return separator;
    }

    public String label() {
        return label;
    }

    public Runnable action() {
        return action;
    }
}