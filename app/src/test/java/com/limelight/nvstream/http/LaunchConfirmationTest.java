package com.limelight.nvstream.http;

import org.junit.Test;

import static com.limelight.nvstream.http.LaunchConfirmation.State.*;
import static org.junit.Assert.*;

public class LaunchConfirmationTest {
    @Test
    public void onlyHostConfirmationMessagesAllowASecondLaunch() throws Exception {
        NvApp game = new NvApp("Game", 73, false);
        NvApp terminate = new NvApp("Terminate", 2147483504, false);
        assertTrue(LaunchConfirmation.isRequired(game, NvHTTPRoleTest.error("replace-confirm")));
        assertTrue(LaunchConfirmation.isRequired(terminate, NvHTTPRoleTest.error("terminate-confirm")));
        assertFalse(LaunchConfirmation.isRequired(game, NvHTTPRoleTest.error("terminate-confirm")));
        assertFalse(LaunchConfirmation.isRequired(terminate, NvHTTPRoleTest.error("replace-confirm")));
        for (int code : new int[] {401, 403, 409, 410, 503}) {
            assertFalse(LaunchConfirmation.isRequired(game, new HostHttpResponseException(code, "Gone")));
        }
        assertFalse(LaunchConfirmation.isRequired(game, NvHTTPRoleTest.error("role-conflict")));
    }

    @Test
    public void completed410ActionsAreNotConfirmationRequestsOrStreamFailures() throws Exception {
        String[] replies = {"monitor-disconnected", "input-disconnected", "terminate-done"};
        for (int i = 0; i < replies.length; i++) {
            NvApp app = new NvApp("Action", 2147483502 + i, false);
            HostHttpResponseException error = NvHTTPRoleTest.error(replies[i]);
            assertTrue(LaunchConfirmation.isCompleted(app, error));
            assertFalse(LaunchConfirmation.isRequired(app, error));
            assertFalse(LaunchConfirmation.isCompleted(new NvApp("Game", 73, false), error));
        }
    }

    @Test
    public void confirmationNeedsAnExplicitDecisionBeforeSixtySeconds() {
        LaunchConfirmation request = new LaunchConfirmation(false, 100);
        assertEquals(WAITING, request.getState(60_099));
        request.confirm(60_099);
        assertEquals(CONFIRMED, request.getState(60_100));
        request.cancel();
        assertEquals(CONFIRMED, request.getState(70_000));
    }

    @Test
    public void deadlineMatchesHostExpiryAndCannotBeConfirmedLater() throws Exception {
        LaunchConfirmation request = new LaunchConfirmation(true, 100);
        assertEquals(TIMED_OUT, request.getState(60_100));
        request.confirm(60_100);
        request.cancel();
        assertEquals(TIMED_OUT, request.await());
    }

    @Test
    public void cancellationIsTerminalAndUnblocksTheWaiter() throws Exception {
        LaunchConfirmation request = new LaunchConfirmation(false, LaunchConfirmation.nowMs());
        java.util.concurrent.ExecutorService worker = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            java.util.concurrent.Future<LaunchConfirmation.State> result = worker.submit(request::await);
            request.cancel();
            assertEquals(CANCELLED, result.get(2, java.util.concurrent.TimeUnit.SECONDS));
            request.confirm(LaunchConfirmation.nowMs());
            assertEquals(CANCELLED, request.getState(LaunchConfirmation.nowMs()));
        } finally {
            worker.shutdownNow();
        }
    }
}
