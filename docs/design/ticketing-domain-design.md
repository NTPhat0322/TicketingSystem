# Ticketing / Flash Sale — Domain & Database Design

> Trạng thái: **draft, code-first**. Schema dưới đây chưa cần chuẩn hoá 100%, mục tiêu là đủ rõ để bắt đầu viết entity/use case. Sẽ tinh chỉnh khi code bộc lộ vấn đề thật.

## 1. Bối cảnh

Hệ thống đặt vé sự kiện có pha **flash sale** (số lượng giới hạn, traffic cao trong thời gian ngắn). Bài toán trọng tâm: chống **oversell** khi hàng ngàn user cùng bấm "Mua" một lúc, mà không phải tách microservices.

**Chốt: mọi đơn đặt vé đều đi qua đúng một đường — Redis+Lua giữ chỗ, RabbitMQ ghi đơn bất đồng bộ.** Không phân biệt "event flash sale" và "event thường", không có cờ bật/tắt. Lý do: traffic cao là chuyện *không đoán trước được* (một event thường vẫn có thể viral), nên chiến lược an toàn phải là mặc định chứ không phải tuỳ chọn; và mỗi nhánh rẽ là một code path nữa phải viết, test và debug — nhánh ít dùng luôn là nhánh hỏng lặng lẽ.

Kiến trúc tổng thể (đã thống nhất trước đó):

- **Spring Boot (Order Service)** — xử lý logic đặt vé.
- **Redis** — nguồn sự thật cho tồn kho lúc bán (inventory cache), trừ kho bằng **Lua script** để đảm bảo atomic (check + decrement trong 1 bước, vì Redis single-threaded).
- **RabbitMQ** — hàng đợi ghi Order bất đồng bộ (giải phóng API thread nhanh) + delay queue (TTL + Dead Letter Exchange, hoặc Redisson delayed queue) để tự động huỷ giữ chỗ quá hạn.
- **PostgreSQL** — nguồn dữ liệu gốc (source of truth) cho user, event, order, payment, ticket.
- Toàn bộ chạy trong **1 project Spring Boot duy nhất** (monolith), RabbitMQ/Redis chỉ dùng để giải phóng thread, không kéo theo microservices.

### Luồng dữ liệu 4 giai đoạn

1. **Cache warming** (trước sale, **bắt buộc cho mọi event**): job đọc `total_quantity` từ Postgres, nạp vào Redis key `stock:ticket_type:{id}`. Vì Redis là nguồn sự thật duy nhất lúc bán, event chưa warm sẽ bị báo "hết vé" oan — đây là điều kiện tiên quyết để `Event` được phép chuyển sang `ON_SALE`, không phải một bước tuỳ chọn.
2. **Trừ kho bằng Lua script** (trong lúc sale): API thread gọi Redis, Lua script check-and-decrement atomic — đồng thời kiểm luôn hạn mức `max_per_user` bằng counter riêng của user (xem §2.4). Thành công → trả về `orderId` (sinh sẵn ở application, UUID). Thất bại → báo hết vé / vượt hạn mức ngay.
3. **Tạo đơn bất đồng bộ**: API thread **không** insert Postgres ngay. Đóng gói `{orderId, userId, ticketTypeId, quantity}` thành message, ném vào RabbitMQ, trả "Thành công" cho user ngay (~10ms). Một Worker riêng đọc queue, ghi `orders` + `order_items` xuống Postgres theo tốc độ DB chịu được.
4. **Huỷ vé quá hạn**: khi tạo hold, đồng thời gửi 1 message có delay = `hold_duration_sec` (qua DLX hoặc Redisson). Hết hạn, Worker check: đơn đã thanh toán thì bỏ qua; chưa thanh toán thì set `EXPIRED` + `INCR` lại Redis stock.

## 2. Các quyết định thiết kế quan trọng (đã chốt)

Đây là phần dễ bị bỏ sót nếu chỉ nhìn sơ đồ kiến trúc — ghi lại rõ ràng vì ảnh hưởng trực tiếp tới cách viết Worker/UseCase.

### 2.1. `Order.id` là UUID sinh ở tầng application, không phải auto-increment DB

Tại thời điểm Lua script trừ kho Redis thành công (giai đoạn 2), **Postgres chưa hề có row Order nào**. Nhưng client cần một mã đơn để poll trạng thái ngay lập tức. Do đó `orderId` phải được sinh **trước khi ghi DB** (ở API thread), rồi mang theo xuyên suốt: key Redis hold (`hold:order:{orderId}`) → message RabbitMQ → row Postgres (do Worker insert sau). Gợi ý dùng UUIDv7 (time-ordered) thay vì UUIDv4 thuần để tránh phân mảnh B-tree index khi insert nhiều.

### 2.2. `ticket_types.sold_quantity` KHÔNG realtime — chỉ Redis mới realtime lúc bán

Trong lúc sale, Redis là nguồn sự thật duy nhất cho tồn kho. `sold_quantity` ở Postgres cố ý "trễ" — đây là eventual consistency có chủ đích, không phải bug.

**Chốt: `sold_quantity` chỉ được cộng dồn khi `Payment.status = SUCCESS`** (không cộng khi Order mới được tạo/giữ chỗ). Vì chỉ còn đúng một đường đặt vé (§2.4), quy tắc này đúng theo nghĩa đen cho **mọi** event — `sold_quantity` có đúng một nơi ghi duy nhất trong toàn hệ thống. Hệ quả:

- **Worker `order.create.queue`**: chỉ `INSERT INTO orders` + `INSERT INTO order_items` (idempotent theo `orders.id`, dùng `ON CONFLICT DO NOTHING` hoặc unique constraint). **Không đụng `ticket_types`.**
- **Worker `order.expire.queue`** (delay/DLX): nếu `orders.status` vẫn `PENDING_PAYMENT` → set `EXPIRED` + `INCR` lại Redis stock. **Không cần sửa `ticket_types` ở Postgres** vì nó chưa từng bị trừ ở đó. Nếu đã `PAID` thì bỏ qua (race giữa message hết hạn và thanh toán vừa kịp).
- **`ConfirmPaymentUseCase` (webhook thanh toán)** — nơi DUY NHẤT tăng `sold_quantity`, trong cùng 1 transaction:
  1. `payments.status = SUCCESS`, `orders.status = PAID`.
  2. `UPDATE ticket_types SET sold_quantity = sold_quantity + qty` (lúc này traffic đã thấp hơn nhiều so với lúc flash sale nên update trực tiếp là đủ, không cần Lua/Redis nữa).
  3. Sinh N row `tickets` (mỗi `order_item.quantity` → N vé, mỗi vé 1 `ticket_code` unique). **Ticket chỉ tồn tại từ khi PAID**, không sinh ở bước reserve.
  4. Ghi `outbox_events` (`PAYMENT_SUCCESS`) để bắn email/notification sau, tránh dual-write trực tiếp vào MQ.

**Đối soát cuối sale**: so sánh `total_quantity - (đơn PAID + đơn PENDING_PAYMENT chưa hết hạn)` với số dư còn lại trong Redis. Lệch thì alert.

### 2.3. Idempotency ở 2 điểm bắt buộc

- Worker tạo Order từ MQ (at-least-once delivery) → unique constraint trên `orders.id`.
- Webhook thanh toán (provider có thể gọi lại) → unique constraint trên `payments.transaction_ref`.

### 2.4. Một chiến lược trừ kho duy nhất: luôn Redis+Lua (đã bỏ cờ `flash_sale_mode`)

Bản draft trước có 2 chiến lược trừ kho chọn theo cờ `events.flash_sale_mode`: Redis+Lua cho event flash sale, Postgres+optimistic lock cho event thường. **Cờ này đã bị bỏ hẳn.** Mọi event đều trừ kho qua Redis+Lua.

Lý do bỏ:

- **An toàn phải là mặc định.** Nhánh optimistic-lock chỉ đúng khi contention thấp; nhưng không ai biết trước event nào sẽ viral. Một cờ đặt sai (hoặc quên đặt) biến lỗi cấu hình thành oversell thật.
- **Hai code path = một path ít được test.** Nhánh "event thường" chạy êm 99% thời gian nên hỏng lặng lẽ, và chỉ lộ ra đúng lúc tệ nhất.
- **Xoá được một mâu thuẫn nội tại.** Với 2 chiến lược, `sold_quantity` có 2 thời điểm ghi khác nhau (lúc reserve ở path optimistic-lock, lúc payment SUCCESS ở path Redis) trên cùng một cột — §2.2 và mục này mâu thuẫn nhau. Bỏ cờ thì `sold_quantity` chỉ còn một nơi ghi.

Hệ quả trên domain model:

- **`TicketType` không có method ghi kho lúc reserve.** Không có `reserve()` / `release()` ghi Postgres — việc giữ chỗ và trả chỗ do Redis lo hoàn toàn. `confirmSale(qty)` là mutator **duy nhất** tăng `sold_quantity`, gọi từ `ConfirmPaymentUseCase`.
- **`ticket_types.version` mất lý do tồn tại chính** (không còn optimistic lock lúc reserve). Giữ lại chỉ để chống ghi đè khi organizer sửa giá/số lượng đồng thời — là mối quan tâm của tầng persistence (`@Version`), không phải của domain.
- **`max_per_user` phải kiểm trong chính Lua script.** Lúc reserve, Postgres **chưa** có row order nào (§2.1) nên không thể query "user này đã mua bao nhiêu". Lua script vì vậy giữ thêm counter `user:limit:{ticketTypeId}:{userId}`: trong cùng một script, `INCRBY` counter → nếu vượt `max_per_user` thì `DECRBY` trả lại và fail luôn, chưa từng chạm tới key `stock:*`. Tính atomic của Lua làm hai phép kiểm này compose được với nhau.

Chi phí đã cân nhắc và chấp nhận:

- **Redis thành SPOF thật sự cho toàn bộ việc bán vé.** Không có fallback sang Postgres — vì fallback chính là code path thứ hai quay lại, chỉ khác là kích hoạt bởi sự cố thay vì bởi cờ, tức là chạy đúng lúc hệ thống đang rối nhất. Redis chết → dừng bán vé (fail closed), không bán bằng đường khác. Bù lại bằng Redis HA (Sentinel/Cluster) + AOF persistence ở tầng vận hành.
- **Cache warming thành bước bắt buộc, không còn tuỳ chọn.** Quên warm = báo hết vé oan. Warm-on-miss (tự nạp khi key vắng) **không** dùng được vì có race: hai request cùng miss sẽ cùng nạp lại `total_quantity` và xoá sạch phần đã bán.

### 2.5. Transactional Outbox thay vì dual-write

Bất kỳ chỗ nào vừa ghi Postgres vừa cần bắn message (MQ/email/…) trong cùng nghiệp vụ, dùng bảng `outbox_events` + 1 publisher job polling, thay vì publish trực tiếp trong transaction DB (tránh mất event khi crash giữa chừng).

## 3. Domain / Aggregate

Ánh xạ vào các module Clean Architecture đã có sẵn trong repo (`domain / application / infrastructure / presentation / bootstrap`):

- **domain** — POJO thuần (không JPA annotation) + business method (`confirmSale()`, `expire()`, `pay()`...) + port interface (`TicketTypeRepository`, `StockCachePort`...). Không có `OrderEventPublisherPort` publish thẳng vào MQ — đó đúng là dual-write mà §2.5 tránh; event đi qua `OutboxEventRepository`.
- **application** — use case orchestrate domain qua port (`ReserveTicketUseCase`, `ConfirmPaymentUseCase`, `ExpireOrderUseCase`...).
- **infrastructure** — JPA `@Entity` (persistence layer), Redis/RabbitMQ adapter implement port, MapStruct map Entity ↔ Domain model.
- **presentation** — REST controller + DTO.

Aggregate chính:

| Aggregate | Vai trò |
|---|---|
| `User` | Tài khoản khách mua vé / organizer / admin |
| `Event` | Sự kiện, chứa danh sách `TicketType` |
| `TicketType` | "Kho vé" theo hạng vé — ánh xạ trực tiếp với Redis stock key |
| `Order` | Đơn đặt vé, vòng đời `PENDING_PAYMENT → PAID / EXPIRED / CANCELLED / FAILED`, chứa `OrderItem` |
| `Ticket` | Vé thực phát hành sau khi thanh toán (mã QR, check-in) |
| `Payment` | Giao dịch thanh toán gắn với Order |
| `OutboxEvent` | Hạ tầng (không phải domain nghiệp vụ) — đảm bảo consistency khi publish event |

## 4. Database schema (code-first)

```sql
users
  id                uuid pk
  email             varchar unique
  phone             varchar
  password_hash     varchar
  full_name         varchar
  role              varchar        -- CUSTOMER, ORGANIZER, ADMIN
  created_at, updated_at

events
  id                uuid pk
  organizer_id      uuid fk -> users
  name              varchar
  description       text
  venue_name        varchar
  start_time        timestamp
  end_time          timestamp
  sale_start_time   timestamp
  sale_end_time     timestamp
  status            varchar        -- DRAFT, PUBLISHED, ON_SALE, CLOSED, CANCELLED
  created_at, updated_at

ticket_types
  id                uuid pk
  event_id          uuid fk -> events
  name              varchar        -- "Vé VIP", "Vé thường"
  price             numeric(12,2)
  total_quantity    int
  sold_quantity     int default 0  -- CHỈ update bởi ConfirmPaymentUseCase, không đụng bởi order-create/expire worker
  max_per_user      int default 4  -- enforce trong Lua script lúc reserve (§2.4), không query được từ Postgres
  hold_duration_sec int default 600
  version           int            -- optimistic lock cho admin/organizer sửa ticket_type; KHÔNG dùng để trừ kho (§2.4)
  status            varchar        -- ACTIVE, SOLD_OUT, CLOSED
  created_at, updated_at

orders
  id                uuid pk        -- sinh ở application (UUIDv7), KHÔNG auto-increment
  order_code        varchar unique -- mã hiển thị cho user
  user_id           uuid fk -> users
  event_id          uuid fk -> events   -- denormalize cho tiện query
  status            varchar        -- PENDING_PAYMENT, PAID, EXPIRED, CANCELLED, FAILED
  total_amount      numeric(12,2)
  reserved_at       timestamp
  expires_at        timestamp      -- worker delay-queue dùng để double-check
  paid_at           timestamp
  created_at, updated_at

order_items
  id                uuid pk
  order_id          uuid fk -> orders
  ticket_type_id    uuid fk -> ticket_types
  quantity          int
  unit_price        numeric(12,2)
  subtotal          numeric(12,2)

tickets                              -- chỉ được tạo khi payment SUCCESS
  id                uuid pk
  ticket_code       varchar unique   -- dùng làm QR
  order_item_id     uuid fk -> order_items
  ticket_type_id    uuid fk -> ticket_types   -- denormalize
  owner_user_id     uuid fk -> users
  status            varchar          -- ISSUED, CHECKED_IN, CANCELLED
  issued_at, checked_in_at

payments
  id                uuid pk
  order_id          uuid fk -> orders unique
  provider          varchar          -- VNPAY, MOMO, STRIPE...
  amount            numeric(12,2)
  status            varchar          -- PENDING, SUCCESS, FAILED
  transaction_ref   varchar unique   -- chống webhook gọi trùng (idempotency)
  paid_at           timestamp
  created_at

outbox_events
  id                uuid pk
  aggregate_type    varchar   -- ORDER, PAYMENT
  aggregate_id      uuid
  event_type        varchar   -- ORDER_CREATED, ORDER_EXPIRED, PAYMENT_SUCCESS
  payload           jsonb
  status            varchar   -- PENDING, PUBLISHED, FAILED
  created_at, published_at
```

Denormalize có chủ đích (chấp nhận ở giai đoạn code-first, chuẩn hoá lại sau nếu cần): `orders.event_id`, `tickets.ticket_type_id`.

## 5. Redis / RabbitMQ key mapping

- Redis:
  - `stock:ticket_type:{ticketTypeId}` = int (số vé còn lại). Nạp bởi cache-warming job, trừ bởi Lua script, `INCR` lại bởi expire worker.
  - `user:limit:{ticketTypeId}:{userId}` = int (số vé user này đang giữ/đã mua cho hạng vé này). Kiểm và tăng trong **cùng** Lua script với `stock:*` để enforce `max_per_user` (§2.4). TTL đặt tới `sale_end_time` (không phải `hold_duration_sec` — hạn mức tính trên cả đợt bán, không phải trên một lần giữ chỗ).
    `DECRBY` lại ở **cả ba** trường hợp trả chỗ: hold hết hạn, user huỷ đơn, và **hoàn tiền/chargeback sau khi đã PAID**. Chốt chính sách: hoàn tiền **có** trả lại hạn mức cho user. Lý do: `max_per_user` giới hạn số vé một người *đang giữ*, mà người đã được hoàn tiền thì không giữ vé nào — đốt hạn mức của họ là phạt một hành vi hợp lệ. Không tạo lỗ hổng lạm dụng: vé trả về nằm trong pool chung của Redis cho mọi người, không giữ riêng cho người vừa hoàn, nên vòng lặp mua→hoàn không mang lại lợi thế gì. Luôn `DECRBY` cùng lúc với `INCR` lại `stock:*`, không tách rời — lệch một trong hai là user mất hạn mức âm thầm.
  - `hold:order:{orderId}` = hash `{userId, ticketTypeId, qty}`, TTL = `hold_duration_sec`.
- RabbitMQ:
  - Exchange `order.exchange` → queue `order.create.queue` (Worker ghi `orders` + `order_items`, KHÔNG đụng `ticket_types`).
  - Queue `order.hold.ttl.queue` với `x-message-ttl = hold_duration_sec` + DLX trỏ về `order.expire.queue` → Worker set `EXPIRED` + `INCR` Redis nếu đơn chưa thanh toán.

## 6. Câu hỏi còn mở (chưa cần chốt ngay, để lại khi bắt tay viết use case)

- Chi tiết use case / API contract cho từng bước (reserve, confirm payment, expire) chưa được đặc tả — sẽ làm khi bắt đầu scaffold code.
- Chưa quyết định thư viện cụ thể cho delay queue (RabbitMQ DLX+TTL thuần vs Redisson delayed queue).
- Chưa thiết kế cơ chế check-in vé (`tickets.status = CHECKED_IN`) — flow riêng, ngoài phạm vi flash sale.
- Chưa chốt cách vận hành Redis HA (Sentinel vs Cluster) và cấu hình persistence — quan trọng hơn trước vì §2.4 làm Redis thành SPOF có chủ đích của toàn bộ việc bán vé.
- Chưa chốt cache-warming job được kích hoạt ở đâu: gắn vào chính transition `Event.startSale()` (an toàn hơn, không thể quên) hay là một scheduled job riêng đọc `sale_start_time`.
