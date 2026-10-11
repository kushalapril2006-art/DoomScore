package com.gridcc.doomscore.android.core
import org.junit.Assert.*
import org.junit.Test

class CounterConnectionTest {
    @Test fun queuedOldScansAndFramesAreRejectedAfterRebind() {
        val c=CounterConnection();val first=c.bind();assertTrue(c.accepts(first))
        c.release();assertFalse(c.accepts(first));val second=c.bind()
        assertTrue(c.accepts(second));assertFalse(c.accepts(first))
    }
    @Test fun paymentDisconnectCannotSilentlyEnableTheServiceAgain() {
        val c=CounterConnection();val token=c.bind();c.release()
        repeat(10) {assertFalse(c.connected);assertFalse(c.accepts(token));assertFalse(c.accepts(c.token()))}
    }
    @Test fun repeatedUnbindAndDestroyRemainDisconnected() {
        val c=CounterConnection();assertFalse(c.accepts(0));c.bind();c.release();c.release()
        assertFalse(c.connected);assertFalse(c.accepts(c.token()))
        assertTrue(c.accepts(c.bind()))
    }
}
