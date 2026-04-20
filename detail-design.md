Tài liệu Detail Design (Thiết kế chi tiết) cho **Tool Quản lý DU1** được phân rã dựa trên 9 yêu cầu nghiệp vụ cốt lõi. Để hệ thống hoạt động trơn tru, chúng ta sẽ bổ sung thêm một module nền tảng là **Quản lý Hệ thống & Phân quyền (RBAC)**.


---

### 0. Module Nền tảng: Authentication & Authorization (Bổ sung)
Trước khi đi vào 9 module, hệ thống cần có cơ chế định danh.
* **Chức năng nhỏ:**
    * Đăng nhập/Đăng xuất (Hỗ trợ SSO với Google/Microsoft của công ty hoặc Local Auth).
    * Phân quyền Role-based (Admin DU, HR/Admin, Member).
* **Actions:** `Login`, `Logout`, `Assign Role`.

---

### 1. Quản lý Member
Module cốt lõi lưu trữ thông tin nhân sự để phục vụ cho các module khác (như thông báo sinh nhật, ngày join công ty, đi trễ).
* **Chức năng nhỏ:**
    * **Quản lý hồ sơ:** CRUD thông tin cơ bản (Tên, Email, SĐT, Ngày sinh, Ngày gia nhập, Vị trí/Level).
    * **Quản lý trạng thái:** Active (đang làm việc), Inactive (đã nghỉ).
    * **Import/Export:** Cập nhật danh sách hàng loạt qua file Excel.
* **Actions:** 
    * **UI Actions:** `View List`, `Search/Filter`, `Add New`, `Edit`, `Deactivate`, `Import Excel`, `Export Excel`.
    * **System Actions:** Tự động tính thâm niên dựa trên "Ngày gia nhập".

---

### 2. Quản lý Chủ đề Seminar
Quản lý luồng tổ chức seminar nội bộ từ khâu đề xuất đến khi hoàn thành.
* **Chức năng nhỏ:**
    * **Đề xuất & Vote:** Member có thể submit chủ đề. Mọi người có thể upvote/downvote.
    * **Lên lịch:** Assign speaker, chọn thời gian, địa điểm/link meeting.
    * **Kho lưu trữ:** Upload slide/tài liệu sau khi seminar kết thúc.
* **Actions:** 
    * **UI Actions:** `Submit Topic`, `Vote Topic`, `Approve Topic` (Admin), `Schedule Seminar`, `Upload Materials`, `Mark as Done`.

---

### 3. Quản lý Khảo sát Công ty, Tập đoàn
Theo dõi tiến độ hoàn thành các bài khảo sát bắt buộc từ cấp trên để tránh bị nhắc nhở.
* **Chức năng nhỏ:**
    * **Tạo chiến dịch khảo sát:** Nhập link khảo sát, deadline.
    * **Tracking status:** Đánh dấu trạng thái (Chưa làm, Đã làm). Member tự check hoặc Admin check thủ công.
    * **Nhắc nhở (Remind):** Gửi thông báo hối thúc những người chưa hoàn thành.
* **Actions:** 
    * **UI Actions:** `Create Survey`, `Mark as Completed` (Member tự report), `View Progress` (Admin xem biểu đồ tiến độ), `Trigger Remind`.

---

### 4. Quản lý Sự kiện (Event)
Tổ chức team building, tất niên, sinh nhật tháng.
* **Chức năng nhỏ:**
    * **Lập kế hoạch:** Tên sự kiện, thời gian, địa điểm, mô tả.
    * **Quản lý tham gia (RSVP):** Gửi form xác nhận tham gia (Yes/No/Maybe).
    * **Check-in:** Quét QR code hoặc Admin điểm danh tay tại sự kiện (phục vụ cộng điểm).
* **Actions:** 
    * **UI Actions:** `Create Event`, `RSVP`, `Check-in`, `View Attendees`.

---

### 5. Quản lý Điểm (Gamification)
Hệ thống lõi để tạo động lực, tích hợp chặt chẽ với các module khác.
* **Chức năng nhỏ:**
    * **Định nghĩa Rule cấu hình điểm:** (VD: Trình bày seminar = +50đ, Đăng bài FB = +10đ, Đi trễ = -5đ, Thắng minigame = +20đ).
    * **Cộng/Trừ điểm tự động & thủ công:** Hệ thống tự trigger khi có event xảy ra, hoặc Admin cộng tay kèm lý do.
    * **Leaderboard:** Bảng xếp hạng điểm theo tháng/quý/năm.
    * **Lịch sử điểm (Audit Log):** Xem chi tiết dòng chảy điểm của từng cá nhân.
* **Actions:** 
    * **UI Actions:** `Config Rules`, `Add/Deduct Points manually`, `View Leaderboard`, `View Point History`.
    * **System Actions:** Lắng nghe event từ module 2, 4, 6, 9 để trigger thay đổi điểm.

---

### 6. Lucky Draw
Công cụ giải trí dùng trong các buổi seminar hoặc event.
* **Chức năng nhỏ:**
    * **Cấu hình vòng quay:** Chọn danh sách người tham gia (chỉ lấy những người đang online/đã check-in sự kiện).
    * **Quản lý giải thưởng:** Setup số lượng giải (Nhất, Nhì, Ba).
    * **Quay số & Lưu vết:** Thực hiện hiệu ứng quay, lưu kết quả người trúng và tự động cộng điểm (nếu phần thưởng là điểm).
* **Actions:** 
    * **UI Actions:** `Setup Participants`, `Setup Prizes`, `Spin!`, `View Winners List`.

---

### 7. Quản lý Order Nước
Giải quyết bài toán gom đơn trà sữa/cafe mỗi chiều.
* **Chức năng nhỏ:**
    * **Quản lý Menu:** Thêm/sửa danh sách món từ các quán quen (Tên món, Giá, Topping).
    * **Mở phiên Order (Session):** Set thời gian bắt đầu/kết thúc order.
    * **Member Đặt món:** Giao diện cho user chọn món, note (ít đá, nhiều đường).
    * **Thống kê/Chốt đơn:** Gom nhóm theo món để dễ gọi điện quán, tính tổng tiền, tính tiền từng người.
* **Actions:** 
    * **UI Actions:** `Manage Menu`, `Open Session`, `Close Session`, `Add to Cart`, `View Summary`, `Mark as Paid` (Tracking ai đã chuyển khoản).

---

### 8. Quản lý Thông báo (Scheduler / Notification)
Hệ thống Cron Job chạy ngầm và gửi thông báo đa kênh (Email, Slack, ChatOps hoặc in-app).
* **Chức năng nhỏ:**
    * **Quản lý Template:** Template tin nhắn chúc mừng sinh nhật, kỷ niệm ngày vào công ty, nhắc khảo sát.
    * **Quản lý Lịch trình (Cron Jobs):** * Job chạy 8h sáng mỗi ngày: Check sinh nhật / Anniversary hôm nay.
        * Job chạy trước sự kiện/seminar 1 tiếng: Nhắc nhở tham gia.
        * Job nhắc nhở khảo sát trước deadline 1 ngày.
* **Actions:** 
    * **UI Actions:** `CRUD Templates`, `Config Notification Channels` (Webhook URL).
    * **System Actions:** `CronTrigger_Birthday`, `CronTrigger_EventReminder`, `SendMessage`.

---

### 9. Quản lý Đi Trễ
* **Chức năng nhỏ:**
    * **Log đi trễ:** Ghi nhận ngày, giờ đi trễ, lý do. Có thể import từ máy chấm công công ty hoặc báo cáo thủ công.
    * **Xử lý phạt:** Tự động trừ điểm trong module Quản lý Điểm, hoặc tính tiền phạt đưa vào quỹ chung.
    * **Báo cáo:** Thống kê top đi trễ của tháng.
* **Actions:** 
    * **UI Actions:** `Log Late Arrival`, `View Late Report`, `Export Late List`.
    * **System Actions:** Trigger trừ điểm/tính tiền quỹ.

---

