package com.cptnfizzbin.keycard.lib;

/**
 * Non-fatal diagnostics sink for KeyCard's own warnings (currently: an
 * unregistered dynamic Action/Subject encountered at {@code Policy}'s
 * {@code can}/{@code cannot}/{@code require} time) - mirrors the JS
 * {@code Logger} shape closely enough to keep the two implementations
 * conceptually aligned.
 */
public interface Logger {
    default void warn(String message) {
        this.log(System.Logger.Level.WARNING, message);
    }

    void log(System.Logger.Level level, String message);
}
