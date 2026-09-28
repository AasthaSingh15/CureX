# Leave workflow

```mermaid
stateDiagram-v2
  PENDING_MANAGER --> PENDING_HR: MANAGER_APPROVE
  PENDING_MANAGER --> REJECTED: MANAGER_REJECT
  PENDING_MANAGER --> ESCALATED: Manager deadline expires
  ESCALATED --> PENDING_HR: MANAGER_APPROVE
  ESCALATED --> REJECTED: MANAGER_REJECT
  PENDING_HR --> APPROVED: HR_APPROVE
  PENDING_HR --> REJECTED: HR_REJECT
```

Escalation changes visibility and creates an audit event; it never grants approval. Terminal states have no outgoing actions. The database stores a single `ESCALATED` status, matching the supplied schema.
