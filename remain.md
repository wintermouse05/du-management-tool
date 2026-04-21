
**Các action còn thiếu theo tài liệu**

1. Module 0 (Auth/Authorization): thiếu SSO Google/Microsoft.
Hiện chỉ có local auth JWT username/password ở AuthController, AuthService, SecurityConfig, và UI login thường ở LoginView.vue.

2. Module 1 (Member): thiếu Search/Filter, Import Excel, Export Excel.
Backend member hiện chỉ có create/get/update/deactivate ở MemberController, UI ở MembersView.vue cũng chưa có import/export và filter rõ ràng.

3. Module 1 (System action): thiếu tự động tính thâm niên từ ngày join.
Service member hiện chỉ lưu joinDate, chưa có logic tính tenure ở MemberService.

4. Module 2 (Seminar): thiếu Upload Materials.
Model và API seminar chưa có trường/tác vụ tài liệu ở Seminar.java, SeminarController, UI cũng chưa có ở SeminarsView.vue.

5. Module 3 (Survey): thiếu action Mark as Completed trên UI member (backend đã có).
Backend + API có endpoint complete ở SurveyController và surveys.ts, nhưng màn hình SurveysView.vue chưa gọi action này.

6. Module 4 (Event): thiếu action RSVP trên UI (backend đã có).
API đã có rsvp ở EventController và events.ts, nhưng chưa thấy thao tác RSVP trong EventsView.vue và EventDetailView.vue.

7. Module 5 (Gamification - system action): thiếu auto trigger điểm từ module Seminar/Event/Lucky Draw.
Hiện gamification chủ yếu manual ở GamificationService; mới thấy tích hợp tự động từ đi trễ ở LateRecordService.

8. Module 6 (Lucky Draw): thiếu Setup Participants đúng nghĩa.
Hiện quay theo userId nhập tay ở LuckyDrawView.vue, và backend chưa có participant pool riêng ở LuckyDrawController, LuckyDrawSession.java.

9. Module 7 (Order): thiếu Edit menu item; thiếu View Summary dạng tổng hợp.
Menu backend chỉ có create/list ở OrderController, UI OrdersView.vue chưa có sửa/xóa món và chưa có màn summary gộp theo món/tổng tiền như tài liệu mô tả.

10. Module 8 (Notification): thiếu Config Notification Channels (Webhook URL).
Hiện có jobs + templates + trigger survey ở NotificationController, NotificationsView.vue, nhưng chưa có action cấu hình channel/webhook; cấu hình hiện tại thiên về email env ở application.properties.

11. Module 9 (Late): thiếu Export Late List.
Backend late chỉ create/get/summary ở LateRecordController, UI LateRecordsView.vue chưa có nút export.

Nếu bạn muốn, mình có thể làm tiếp ngay theo 1 trong 2 hướng:
1. Lập checklist ưu tiên theo mức độ cần thiết (MVP trước).
2. Bóc tách thành backlog kỹ thuật: mỗi action thiếu = endpoint + UI + DB + test cần làm.