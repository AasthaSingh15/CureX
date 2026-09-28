package com.leaveflow;

import java.util.Map;
import java.util.Set;

public final class Workflow {
    private Workflow() {}

    public enum State { PENDING_MANAGER, PENDING_HR, APPROVED, REJECTED, ESCALATED }
    public enum Event { MANAGER_APPROVE, MANAGER_REJECT, HR_APPROVE, HR_REJECT, ESCALATE_MANAGER }

    private static final Map<State, Map<Event, State>> TRANSITIONS = Map.of(
        State.PENDING_MANAGER, Map.of(
            Event.MANAGER_APPROVE, State.PENDING_HR,
            Event.MANAGER_REJECT, State.REJECTED,
            Event.ESCALATE_MANAGER, State.ESCALATED),
        State.ESCALATED, Map.of(
            Event.MANAGER_APPROVE, State.PENDING_HR,
            Event.MANAGER_REJECT, State.REJECTED),
        State.PENDING_HR, Map.of(
            Event.HR_APPROVE, State.APPROVED,
            Event.HR_REJECT, State.REJECTED)
    );

    public static State next(State state, Event event) {
        State next = TRANSITIONS.getOrDefault(state, Map.of()).get(event);
        if (next == null) throw new IllegalArgumentException("INVALID_STATE_TRANSITION: " + state + " cannot handle " + event);
        return next;
    }

    public static Set<Event> allowed(State state) {
        return TRANSITIONS.getOrDefault(state, Map.of()).keySet();
    }
}
