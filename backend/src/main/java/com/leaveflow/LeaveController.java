package com.leaveflow;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class LeaveController {
    private final JdbcTemplate db;

    public LeaveController(JdbcTemplate db) { this.db = db; }

    @GetMapping("/health")
    Map<String, String> health() { return Map.of("status", "UP", "service", "LeaveFlow API"); }

    @GetMapping("/state-machine")
    Map<String, Object> stateMachine() {
        Map<String, Object> result = new LinkedHashMap<>();
        for (var state : Workflow.State.values())
            result.put(state.name(), Workflow.allowed(state).stream().map(Enum::name).toList());
        return result;
    }

    @GetMapping("/leave/balance")
    Map<String, Object> balance(@RequestParam(defaultValue = "employee@example.com") String email,
                                @RequestParam(defaultValue = "2026") int year) {
        var user = db.queryForMap("SELECT id FROM users WHERE email = ?", email);
        long userId = ((Number) user.get("id")).longValue();
        BigDecimal total = db.queryForObject(
            "SELECT COALESCE(SUM(total_days), 0) FROM leave_balances WHERE user_id = ? AND year = ?",
            BigDecimal.class, userId, year);
        BigDecimal used = db.queryForObject(
            "SELECT COALESCE(SUM(used_days), 0) FROM leave_balances WHERE user_id = ? AND year = ?",
            BigDecimal.class, userId, year);
        BigDecimal pending = db.queryForObject(
            "SELECT COALESCE(SUM(days), 0) FROM leave_requests WHERE user_id = ? AND status IN ('PENDING_MANAGER', 'PENDING_HR', 'ESCALATED') AND EXTRACT(YEAR FROM start_date) = ?",
            BigDecimal.class, userId, year);
        return Map.of("year", year, "totalDays", total, "approvedUsed", used, "pending", pending,
            "available", total.subtract(used).subtract(pending));
    }

    @GetMapping("/leave/requests/{id}")
    Map<String, Object> request(@PathVariable long id) {
        return db.queryForMap("""
            SELECT r.*, u.name, u.email, u.team AS team_name
            FROM leave_requests r JOIN users u ON u.id = r.user_id WHERE r.id = ?
            """, id);
    }

    @PostMapping("/leave/requests/{id}/events/{event}")
    ResponseEntity<?> transition(@PathVariable long id, @PathVariable Workflow.Event event,
                                 @RequestParam(defaultValue = "manager@example.com") String actor,
                                 @RequestParam(required = false) String comment) {
        var row = db.queryForMap("""
            SELECT r.status::text AS status, r.user_id, r.leave_type, r.start_date, r.days,
                   u.manager_id, u.role::text AS employee_role
            FROM leave_requests r JOIN users u ON u.id = r.user_id WHERE r.id = ?
            """, id);
        Workflow.State old = Workflow.State.valueOf((String) row.get("status"));
        Workflow.State next;
        try {
            next = Workflow.next(old, event);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("code", "INVALID_STATE_TRANSITION", "message", e.getMessage()));
        }

        var actorRow = db.queryForMap("SELECT id, role::text AS role FROM users WHERE email = ?", actor);
        String actorRole = (String) actorRow.get("role");
        boolean managerAction = event.name().startsWith("MANAGER");
        if (managerAction && (!"MANAGER".equals(actorRole)
                || !Objects.equals(row.get("manager_id"), actorRow.get("id")))) {
            return ResponseEntity.status(403).body(Map.of("code", "FORBIDDEN", "message", "Only the assigned manager may decide this stage."));
        }
        if (event.name().startsWith("HR") && !"HR".equals(actorRole)) {
            return ResponseEntity.status(403).body(Map.of("code", "FORBIDDEN", "message", "Only HR may decide this stage."));
        }

        String commentColumn = managerAction ? "manager_comment" : "hr_comment";
        db.update("UPDATE leave_requests SET status = ?::leave_status, " + commentColumn + " = ?, updated_at = NOW() WHERE id = ?",
            next.name(), comment, id);
        if (event == Workflow.Event.HR_APPROVE) {
            db.update("""
                UPDATE leave_balances SET used_days = used_days + ?
                WHERE user_id = ? AND leave_type = ? AND year = ?
                """, row.get("days"), row.get("user_id"), row.get("leave_type"),
                ((java.sql.Date) row.get("start_date")).toLocalDate().getYear());
        }
        db.update("""
            INSERT INTO leave_audit_events(request_id, actor, actor_role, action, previous_state, new_state, comment)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """, id, actor, actorRole, event.name(), old.name(), next.name(), comment);
        return ResponseEntity.ok(Map.of("id", id, "previousState", old, "status", next,
            "allowedActions", Workflow.allowed(next)));
    }

    @GetMapping("/manager/conflicts/{id}")
    Map<String, Object> conflict(@PathVariable long id) {
        var request = db.queryForMap("""
            SELECT r.start_date, r.end_date, r.user_id, u.team
            FROM leave_requests r JOIN users u ON u.id = r.user_id WHERE r.id = ?
            """, id);
        String team = (String) request.get("team");
        int size = db.queryForObject("SELECT COUNT(*) FROM users WHERE team = ?", Integer.class, team);
        int absent = db.queryForObject("""
            SELECT COUNT(DISTINCT r.user_id) FROM leave_requests r JOIN users u ON u.id = r.user_id
            WHERE u.team = ? AND r.id <> ? AND r.status = 'APPROVED'
              AND r.start_date <= ? AND r.end_date >= ?
            """, Integer.class, team, id, request.get("end_date"), request.get("start_date"));
        BigDecimal threshold = db.queryForObject("SELECT max_percentage FROM team_leave_rules WHERE team = ?",
            BigDecimal.class, team);
        BigDecimal pct = size == 0 ? BigDecimal.ZERO : BigDecimal.valueOf((absent + 1) * 100.0 / size)
            .setScale(1, RoundingMode.HALF_UP);
        return Map.of("team", team, "teamSize", size, "alreadyAbsent", absent, "requested", 1,
            "projectedAbsencePercent", pct, "thresholdPercent", threshold,
            "risk", pct.compareTo(threshold) > 0 ? "HIGH RISK" : "NORMAL", "automaticRejection", false);
    }

    @PostMapping("/demo/escalation/run")
    Map<String, Object> demoEscalate() { return runEscalation(); }

    @Scheduled(fixedDelayString = "${demo.escalation-check-interval-ms:60000}")
    public Map<String, Object> runEscalation() {
        int manager = db.update("""
            WITH escalated AS (
                UPDATE leave_requests SET status = 'ESCALATED', updated_at = NOW()
                WHERE status = 'PENDING_MANAGER' AND manager_deadline < NOW()
                RETURNING id
            )
            INSERT INTO leave_audit_events(request_id, actor, actor_role, action, previous_state, new_state, comment)
            SELECT id, 'system', 'SYSTEM', 'ESCALATE_MANAGER', 'PENDING_MANAGER', 'ESCALATED',
                   'Manager approval deadline expired.'
            FROM escalated
            """);
        return Map.of("managerEscalated", manager, "hrEscalated", 0);
    }
}
