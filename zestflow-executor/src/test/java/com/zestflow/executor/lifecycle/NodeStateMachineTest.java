package com.zestflow.executor.lifecycle;

import com.zestflow.common.constant.ChainConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NodeStateMachineTest {

    @Test
    void initialState() {
        NodeStateMachine sm = new NodeStateMachine();
        assertEquals(ChainConstants.NODE_CREATED, sm.current());
    }

    @Test
    void normalExecutionFlow() {
        NodeStateMachine sm = new NodeStateMachine();
        assertTrue(sm.transit(ChainConstants.NODE_READY));
        assertTrue(sm.transit(ChainConstants.NODE_RUNNING));
        assertTrue(sm.transit(ChainConstants.NODE_SUCCESS));
        assertEquals(ChainConstants.NODE_SUCCESS, sm.current());
        assertTrue(sm.isTerminated());
    }

    @Test
    void failedState() {
        NodeStateMachine sm = new NodeStateMachine();
        sm.transit(ChainConstants.NODE_READY);
        sm.transit(ChainConstants.NODE_RUNNING);
        sm.transit(ChainConstants.NODE_FAILED);
        assertTrue(sm.isTerminated());
        assertEquals(ChainConstants.NODE_FAILED, sm.current());
    }

    @Test
    void cannotSkipToComplete() {
        NodeStateMachine sm = new NodeStateMachine();
        assertFalse(sm.transit(ChainConstants.NODE_SUCCESS));
        assertEquals(ChainConstants.NODE_CREATED, sm.current());
    }
}
