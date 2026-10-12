# Phase 5: E2E and full verification

## Affected Areas

- `bootstrap/src/test/java/com/tienphat/ticketingsystem/order/TicketPurchaseLifecycleApiE2ETest.java`

## Scenarios (PostgreSQL + Redis + RabbitMQ containers, existing fixtures)

1. **History and tickets after payment** — reserve 2 → relay → PENDING_PAYMENT → pay →
   `GET /orders` lists it first with `PAID`; `GET /orders/{id}/tickets` returns 2 tickets with
   ticket type + event names; `GET /tickets` returns the same 2; another customer gets 403 on
   `/orders/{id}/tickets` and an empty `/orders` page.
2. **Cancel restores stock** — stock 5, reserve 2 → PENDING_PAYMENT → cancel → 200 `CANCELLED`;
   Redis stock back to 5; intent state `RELEASED`; user can reserve `maxPerUser` again;
   second cancel → 200 and stock still 5; payment callback → 409.
3. **Cancel guards** — cancel a PAID order → 409; cancel while CREATING (before relay) → 409.
4. **Release failure is repaired** — `failNextRelease()` → cancel fails; retry cancel → 200,
   stock restored exactly once.
5. **Close-sale** — owner closes → 200 `CLOSED`; new reservation → 409; another organizer → 403.

## Verification

```bash
mvn clean install
```

Report: per-module test counts, any failures with output. No commit; suggest branch
`feature/order-history-cancel-close-sale` and a conventional commit message.

## Exit Criteria

Full reactor build green with Docker available.
