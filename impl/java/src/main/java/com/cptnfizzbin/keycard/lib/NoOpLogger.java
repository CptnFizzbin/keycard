package com.cptnfizzbin.keycard.lib;

/** No-op {@link Logger} - use via {@link Logger#NO_OP}, never instantiated directly. */
final class NoOpLogger implements Logger {
    @Override
    public void warn(String message) {}
}
