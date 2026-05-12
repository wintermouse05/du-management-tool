**Findings**
- `High` Refresh token rotation chưa atomic, nên reuse detection có thể hụt trong case multi-tab hoặc 2 request refresh gần như đồng thời. [RefreshTokenRepository.java](/home/wintermouse/Documents/Development/du-management-tool/du-management-backend/src/main/java/org/example/dumanagementbackend/repository/RefreshTokenRepository.java:13) chỉ `findByTokenHash` bình thường, còn [RefreshTokenService.java](/home/wintermouse/Documents/Development/du-management-tool/du-management-backend/src/main/java/org/example/dumanagementbackend/service/RefreshTokenService.java:79) đọc token, check revoked, rồi tới [RefreshTokenService.java](/home/wintermouse/Documents/Development/du-management-tool/du-management-backend/src/main/java/org/example/dumanagementbackend/service/RefreshTokenService.java:116) mới save. Hai transaction song song có thể cùng thấy token chưa revoked và cùng tạo replacement token hợp lệ. Nên thêm `PESSIMISTIC_WRITE` lock hoặc optimistic `@Version`/conditional update.

- `High` WebSocket sẽ mất các subscription theo page sau khi access token refresh. [AppLayout.vue](/home/wintermouse/Documents/Development/du-management-tool/du-management-frontend/src/layouts/AppLayout.vue:82) disconnect/reconnect khi `auth.token` đổi, nhưng chỉ subscribe lại `/user/queue/notifications` ở [AppLayout.vue](/home/wintermouse/Documents/Development/du-management-tool/du-management-frontend/src/layouts/AppLayout.vue:20). Các view như [OrdersView.vue](/home/wintermouse/Documents/Development/du-management-tool/du-management-frontend/src/views/orders/OrdersView.vue:177) chỉ subscribe một lần khi mounted, và [websocket.ts](/home/wintermouse/Documents/Development/du-management-tool/du-management-frontend/src/services/websocket.ts:56) không queue/resubscribe. Kết quả: đang ở Orders/Leaderboard/Survey/LuckyDraw lâu hơn TTL access token thì realtime update có thể ngừng.

- `Medium` Access token vẫn persist trong `localStorage`, chưa match hoàn toàn với best practice bạn nêu là memory hoặc HTTP-only cookie. Ghi token ở [auth.ts](/home/wintermouse/Documents/Development/du-management-tool/du-management-frontend/src/stores/auth.ts:24) và [http.ts](/home/wintermouse/Documents/Development/du-management-tool/du-management-frontend/src/api/http.ts:44) làm token dễ bị đọc nếu có XSS. Ngoài ra [auth.ts](/home/wintermouse/Documents/Development/du-management-tool/du-management-frontend/src/stores/auth.ts:85) tin token cũ trong localStorage mà không refresh/validate ngay lúc bootstrap.

- `Medium` Reset password đang issue refresh token mới nhưng không revoke các refresh token cũ của user. [PasswordResetService.java](/home/wintermouse/Documents/Development/du-management-tool/du-management-backend/src/main/java/org/example/dumanagementbackend/service/PasswordResetService.java:90) đổi mật khẩu, sau đó [PasswordResetService.java](/home/wintermouse/Documents/Development/du-management-tool/du-management-backend/src/main/java/org/example/dumanagementbackend/service/PasswordResetService.java:99) cấp token mới, nhưng session cũ vẫn refresh được tới khi hết hạn. Thường sau password reset nên revoke toàn bộ token family/session hiện có của user.

- `Medium` CSRF đang disable trong khi refresh token nằm trong cookie. [SecurityConfig.java](/home/wintermouse/Documents/Development/du-management-tool/du-management-backend/src/main/java/org/example/dumanagementbackend/config/SecurityConfig.java:31) disable CSRF và [SecurityConfig.java](/home/wintermouse/Documents/Development/du-management-tool/du-management-backend/src/main/java/org/example/dumanagementbackend/config/SecurityConfig.java:37) permit `/refresh`/`/logout`. Với default `SameSite=Lax` ở [application.properties](/home/wintermouse/Documents/Development/du-management-tool/du-management-backend/src/main/resources/application.properties:24) rủi ro thấp hơn, nhưng nếu production cần `SameSite=None` cho cross-site frontend/backend thì cần CSRF token hoặc origin check chặt.

- `Low` Chưa có cleanup expired refresh token. Entity/repo đã lưu `expiresAt`, nhưng chưa thấy scheduler/delete expired token. DB sẽ phình dần, nhất là vì mỗi refresh tạo record mới.

**Đã Match Tốt**
- Login/register/reset-password đều set refresh cookie và trả access token mới.
- `/api/auth/refresh` rotate refresh token và trả access token mới.
- Logout revoke refresh token hiện tại và clear cookie.
- Frontend đã bật `withCredentials`, có interceptor refresh 401 và retry request một lần.
- Cookie path `/api/auth` là hợp lý vì refresh token chỉ cần gửi tới auth endpoints.

**Verification**
- `./mvnw -q -DskipTests compile`: pass.
- `npm run build`: pass.
- `./mvnw -q test`: không pass do môi trường test hiện tại, không phải lỗi compile của refresh flow. Có lỗi PostgreSQL connection bị sandbox chặn (`Operation not permitted`) và Mockito inline mock maker không self-attach được trên JDK 21 trong môi trường này.

Next steps hợp lý:
1. Sửa atomic rotation + revoke all sessions on password reset.
2. Chuyển access token frontend sang memory-only.
3. Nâng `wsService` thành subscription registry để reconnect xong tự resubscribe toàn bộ topic đang active.
4. Thêm CSRF/origin protection cho cookie refresh flow nếu deploy cross-site.