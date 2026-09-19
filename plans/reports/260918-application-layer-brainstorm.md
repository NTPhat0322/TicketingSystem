# Brainstorm: Triển khai tầng Application (khởi động sau khi domain layer hoàn thành)

**Date:** 2026-09-18

## Ideas Explored

- **Use-case/Interactor (Clean Architecture)** — mỗi hành động nghiệp vụ là 1 class, input/output rõ ràng. **Chọn.**
- **CQRS với mediator pattern** — cân nhắc nhưng không chọn, vì thêm 1 lớp điều phối (mediator) không cần thiết cho quy mô hiện tại.
- **Application service theo nhóm nghiệp vụ** (EventService, TicketService...) — cân nhắc nhưng nhường chỗ cho use-case vì use-case tách bạch input/output tốt hơn cho testing.
- **Port repository: định nghĩa lại trong application vs tái dùng port đã có trong domain** — domain đã có sẵn `EventRepository`, `TicketTypeRepository`, `OrderRepository`, v.v. nên application tái dùng thẳng, không định nghĩa lại.
- **Transaction management: `@Transactional` (Spring) vs port `TransactionRunner`/`UnitOfWork` tự viết** — thảo luận kỹ vì module `application` hiện tại chỉ phụ thuộc `domain` (0 dependency framework). Chọn `@Transactional` (chấp nhận thêm `spring-tx`) vì đơn giản, ít code hơn; đánh đổi là application không còn 100% framework-agnostic.
- **UseCase interface: generic `UseCase<I, O>` + Command riêng cho từng use-case** — kết hợp cả 2 hướng (interface chung để test/mock nhất quán, Command cụ thể để type-safe từng nghiệp vụ). Chọn.
- **Validation input: Bean Validation ở presentation vs application tự validate Command** — chọn Bean Validation (`@NotBlank`, `@Valid`...) ở tầng presentation; application/domain chỉ lo business rule.
- **DTO mapping: MapStruct vs map thủ công** — chọn MapStruct (parent `pom.xml` đã khai báo sẵn `mapstruct.version`).
- **Outbox event: use-case tự ghi `OutboxEvent` cùng transaction vs domain tự sinh domain event nội bộ** — domain chưa có cơ chế domain-event (`Order.eventId` chỉ là FK, không phải sự kiện). Chọn: use-case tự gọi `OutboxEvent.record()` + `OutboxEventRepository.save()` cùng transaction (transactional outbox pattern, đúng như javadoc của `OutboxEvent` mô tả). *Lưu ý phát hiện khi scout: `OutboxEventType` hiện chỉ đóng khung 3 giá trị (`ORDER_CREATED`, `ORDER_EXPIRED`, `PAYMENT_SUCCESS`), không có loại nào cho `Event`/`TicketType` — nên outbox không áp dụng cho scope P1 này, chỉ liên quan các phase Order/Payment sau.*
- **Exception handling: bubble domain exception lên global handler vs wrap Result/Either type** — chọn bubble thẳng lên `@RestControllerAdvice` ở presentation; application không biết gì về HTTP.
- **Phân quyền (role-based)**: kiểm tra trong use-case vs để presentation xử lý (`@PreAuthorize`) — chọn để presentation xử lý, application không biết về authorization.
- **List Event**: phát hiện `EventRepository` hiện chưa có method list/findAll nào — chỉ có `save()`/`findById()`. Quyết định mở rộng port: thêm `findAll()` có phân trang (public listing), không giới hạn theo organizer.
- **Delete/Deactivate**: không có hard-delete trong domain (`EventRepository`/`TicketTypeRepository` không có `delete()`). Xác nhận map vào domain method sẵn có: `Event.cancel()` và `TicketType.close()` — không thêm hard-delete vào domain.

## User's Direction

Triển khai use-case/interactor theo Clean Architecture cho tầng application, bắt đầu (P1) với CRUD Event & TicketType (Create/Update/GetById/List/Deactivate), tái dùng port có sẵn trong domain, dùng `@Transactional` cho transaction boundary, MapStruct cho mapping, Bean Validation + global exception handler ở presentation, authorization cũng để presentation xử lý.

## Open Questions

- Cấu trúc package cụ thể trong module `application` (theo aggregate/feature hay theo layer command/usecase) — chưa chốt, để `/ck:plan` quyết định dựa trên convention hiện có của domain.
- `findAll()` mới thêm vào `EventRepository` cần kiểu phân trang cụ thể (`Pageable` của Spring Data, hay tự định nghĩa `PageRequest`/`PageResult` thuần domain để giữ port framework-agnostic) — cần quyết định khi viết phase implementation.
- Event: `Update` áp dụng cho field nào khi event đã qua `DRAFT` (vd: có cho sửa `name`/`schedule` sau khi `PUBLISHED` không, hay chỉ cho sửa lúc `DRAFT`)? Domain hiện không giới hạn field nào được sửa theo status — cần quyết định rule nghiệp vụ trước khi viết `UpdateEventUseCase`.
- Phase sau (Order/Payment/Ticket) cần bổ sung thêm `OutboxEventType`/`AggregateType` nếu muốn dùng outbox cho các luồng đó — ngoài scope P1.

## Risks

- Mở rộng `EventRepository`/`TicketTypeRepository` (thêm `findAll`, v.v.) là thay đổi domain port — cần đồng bộ với bất kỳ implementation infra nào đã tồn tại (hiện infra module chưa có gì nên rủi ro thấp, nhưng cần lưu ý nếu infra đã bắt đầu).
- Chấp nhận `spring-tx` trong `application` phá vỡ tính "0-dependency ngoài domain" đã giữ từ đầu — nếu sau này đổi ý muốn thuần túy, sẽ phải refactor lại toàn bộ use-case đã viết.
- `UpdateEventUseCase`/`UpdateTicketTypeUseCase` chưa có rule rõ ràng về field nào được sửa theo từng trạng thái — nếu không chốt trước khi cook, dễ dẫn đến việc cho sửa tùy tiện field không nên sửa sau khi event đã `ON_SALE`.
