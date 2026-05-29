package com.zestflow.executor.lifecycle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChainStateMachineTest {

    @Test
    void initialState() {
        ChainStateMachine sm = new ChainStateMachine();
        assertEquals(ChainState.INIT, sm.currentState());
    }

    @Test
    void normalFlow() {
        ChainStateMachine sm = new ChainStateMachine();
        assertTrue(sm.transit(ChainState.LOADING));
        assertTrue(sm.transit(ChainState.READY));
        assertTrue(sm.transit(ChainState.RUNNING));
        assertTrue(sm.transit(ChainState.SUCCESS));
        assertEquals(ChainState.SUCCESS, sm.currentState());
    }

    @Test
    void cannotTransitFromTerminal() {
        ChainStateMachine sm = new ChainStateMachine();
        sm.transit(ChainState.LOADING);
        sm.transit(ChainState.READY);
        sm.transit(ChainState.RUNNING);
        sm.transit(ChainState.SUCCESS);

        assertFalse(sm.transit(ChainState.RUNNING));
        assertTrue(sm.isTerminated());
    }

    @Test
    void failedToSuccessNotAllowed() {
        ChainStateMachine sm = new ChainStateMachine();
        sm.transit(ChainState.READY);
        sm.transit(ChainState.RUNNING);
        sm.transit(ChainState.FAILED);

        assertFalse(sm.transit(ChainState.SUCCESS));
        assertTrue(sm.isTerminated());
    }

    @Test
    void timeoutIsTerminal() {
        ChainStateMachine sm = new ChainStateMachine();
        sm.transit(ChainState.READY);
        sm.transit(ChainState.RUNNING);
        sm.transit(ChainState.TIMEOUT);

        assertTrue(sm.isTerminated());
        assertEquals(ChainState.TIMEOUT, sm.currentState());
    }

    @Test
    void stoppedIsTerminal() {
        ChainStateMachine sm = new ChainStateMachine();
        sm.transit(ChainState.READY);
        sm.transit(ChainState.RUNNING);
        sm.transit(ChainState.STOPPED);

        assertTrue(sm.isTerminated());
        assertEquals(ChainState.STOPPED, sm.currentState());
    }

    @Test
    void failedCanCompensate() {
        ChainStateMachine sm = new ChainStateMachine();
        sm.transit(ChainState.READY);
        sm.transit(ChainState.RUNNING);
        sm.transit(ChainState.FAILED);
        assertTrue(sm.transit(ChainState.COMPENSATED));
        assertTrue(sm.isTerminated());
    }
}
