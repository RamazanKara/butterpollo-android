package com.limelight.nvstream.http;

public final class LaunchConfirmation {
    public enum State { WAITING, CONFIRMED, CANCELLED, TIMED_OUT }

    private static final String REPLACE = "An app is already running. Launch this app again within 60 seconds to confirm that you want to close it.";
    private static final String TERMINATE = "This will close the active stream but leave Remote Monitor and Remote Input connected. Launch Terminate again within 60 seconds to confirm this was intentional.";
    private final long deadline;
    private final boolean terminate;
    private State state = State.WAITING;

    public LaunchConfirmation(boolean terminate, long nowMs) {
        this.terminate = terminate;
        deadline = nowMs + 60_000;
    }

    public static boolean isRequired(NvApp app, HostHttpResponseException error) {
        // 410 also reports completed actions. Never confirm an unknown response by retrying it.
        return error.getErrorCode() == 410 &&
                (app.getControl() == NvApp.Control.TERMINATE ? TERMINATE.equals(error.getErrorMessage()) :
                        app.getControl() == NvApp.Control.NONE && REPLACE.equals(error.getErrorMessage()));
    }

    public static boolean isCompleted(NvApp app, HostHttpResponseException error) {
        if (error.getErrorCode() != 410) {
            return false;
        }
        switch (app.getControl()) {
            case DISCONNECT_MONITOR:
                return "Remote monitor disconnected".equals(error.getErrorMessage());
            case DISCONNECT_INPUT:
                return "Remote input disconnected".equals(error.getErrorMessage());
            case TERMINATE:
                return "Application terminated".equals(error.getErrorMessage());
            default:
                return false;
        }
    }

    public static long nowMs() {
        return System.nanoTime() / 1_000_000;
    }

    public boolean isTerminate() {
        return terminate;
    }

    public synchronized State getState(long nowMs) {
        if (state == State.WAITING && nowMs >= deadline) {
            state = State.TIMED_OUT;
            notifyAll();
        }
        return state;
    }

    public synchronized void confirm(long nowMs) {
        if (getState(nowMs) == State.WAITING) {
            state = State.CONFIRMED;
            notifyAll();
        }
    }

    public synchronized void cancel() {
        if (state == State.WAITING) {
            state = State.CANCELLED;
            notifyAll();
        }
    }

    public synchronized State await() throws InterruptedException {
        while (getState(nowMs()) == State.WAITING) {
            wait(Math.max(1, deadline - nowMs()));
        }
        return state;
    }
}
