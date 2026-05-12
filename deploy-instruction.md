Dự án này hợp nhất nhất với mô hình:

`Browser -> Nginx -> Vue static files`
`Browser -> Nginx /api, /ws -> Spring Boot :8080 -> PostgreSQL :5432`

Mình thấy repo đang là Spring Boot Maven + PostgreSQL ở [application.properties](/home/wintermouse/Documents/Development/du-management-tool/du-management-backend/src/main/resources/application.properties:1), frontend Vue/Vite ở [package.json](/home/wintermouse/Documents/Development/du-management-tool/du-management-frontend/package.json:1).

**1. Chuẩn Bị EC2**

Tạo EC2 Ubuntu 22.04 hoặc 24.04 LTS.

Khuyến nghị tối thiểu:

```text
Instance: t3.small hoặc t3.medium
Storage: 20GB+
Security Group:
  22    SSH    chỉ IP của bạn
  80    HTTP   0.0.0.0/0
  443   HTTPS  0.0.0.0/0
```

Không mở public `8080` và `5432`. Backend và database chỉ nên chạy nội bộ trong EC2.

Nếu có domain, gắn Elastic IP cho EC2 rồi tạo DNS A record trỏ domain về Elastic IP. AWS có lưu ý Elastic IP là IP tĩnh nhưng hiện có thể bị tính phí kể cả khi đang dùng.

**2. SSH Vào Server**

```bash
ssh -i your-key.pem ubuntu@YOUR_EC2_PUBLIC_IP
```

Cài runtime:

```bash
sudo apt update
sudo apt install -y git curl unzip nginx openjdk-17-jdk maven docker.io docker-compose-v2
sudo systemctl enable --now nginx docker
sudo usermod -aG docker ubuntu
```

Thoát SSH rồi đăng nhập lại để group `docker` có hiệu lực.

Cài Node.js 22 để build frontend:

```bash
curl -fsSL https://deb.nodesource.com/setup_22.x | sudo -E bash -
sudo apt install -y nodejs
node -v
npm -v
```

**3. Clone Project**

```bash
sudo mkdir -p /opt/du-management
sudo chown ubuntu:ubuntu /opt/du-management
cd /opt/du-management

git clone YOUR_GIT_REPO_URL .
```

Nếu bạn không dùng Git remote, có thể upload source bằng `scp`/SFTP.

**4. Chạy PostgreSQL**

Repo đã có Docker Compose cho PostgreSQL ở:

```bash
/opt/du-management/du-management-backend/docker-compose.yml
```

Nên đổi password `postgres` trong file đó trước khi chạy. Tốt hơn nữa, sửa port binding thành localhost:

```yaml
ports:
  - "127.0.0.1:5432:5432"
```

Sau đó chạy:

```bash
cd /opt/du-management/du-management-backend
docker compose up -d
docker ps
```

Kiểm tra DB:

```bash
docker exec -it du_management_postgres psql -U postgres -d du_management
```

**5. Sửa 2 Điểm Quan Trọng Cho Production**

Frontend đang hard-code WebSocket tới `localhost:8080` trong [websocket.ts](/home/wintermouse/Documents/Development/du-management-tool/du-management-frontend/src/services/websocket.ts:12). Khi user mở website thật, `localhost` là máy của user, không phải EC2, nên WebSocket sẽ lỗi.

Đổi:

```ts
webSocketFactory: () => new SockJS('http://localhost:8080/ws'),
```

thành:

```ts
webSocketFactory: () => new SockJS(`${window.location.origin}/ws`),
```

Backend WebSocket cũng đang chỉ allow origin localhost trong [WebSocketConfig.java](/home/wintermouse/Documents/Development/du-management-tool/du-management-backend/src/main/java/org/example/dumanagementbackend/config/WebSocketConfig.java:26). Thêm domain/IP production:

```java
.setAllowedOriginPatterns(
    "http://localhost:5173",
    "http://localhost:8080",
    "http://YOUR_DOMAIN_OR_IP",
    "https://YOUR_DOMAIN"
)
```

Nếu chưa có domain và chỉ test bằng IP, dùng `http://YOUR_EC2_PUBLIC_IP`.

**6. Build Backend**

```bash
cd /opt/du-management/du-management-backend
./mvnw clean package -DskipTests
```

File `.jar` sẽ nằm ở:

```bash
target/du-management-backend-0.0.1-SNAPSHOT.jar
```

Tạo thư mục upload:

```bash
sudo mkdir -p /var/lib/du-management/uploads/seminars
sudo chown -R ubuntu:ubuntu /var/lib/du-management
```

Tạo file môi trường:

```bash
sudo mkdir -p /etc/du-management
sudo nano /etc/du-management/backend.env
```

Nội dung mẫu:

```env
SERVER_PORT=8080

SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5432/du_management
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=YOUR_STRONG_DB_PASSWORD

SPRING_JPA_HIBERNATE_DDL_AUTO=update
SPRING_JPA_SHOW_SQL=false

APP_JWT_SECRET=GENERATE_A_BASE64_SECRET
APP_FRONTEND_URL=https://YOUR_DOMAIN

APP_RBAC_SEED_DEFAULT_USERS=true
APP_DEFAULT_ADMIN_PASSWORD=CHANGE_ME_ADMIN_PASSWORD
APP_DEFAULT_HR_PASSWORD=CHANGE_ME_HR_PASSWORD
APP_DEFAULT_MEMBER_PASSWORD=CHANGE_ME_MEMBER_PASSWORD

SEMINAR_UPLOAD_DIR=/var/lib/du-management/uploads/seminars

NOTIFICATION_EMAIL_ENABLED=false
MAIL_USERNAME=
MAIL_APP_PASSWORD=

CHATOPS_ENABLED=false
```

Tạo secret:

```bash
openssl rand -base64 32
```

Sau lần chạy đầu tiên, nên đổi:

```env
APP_RBAC_SEED_DEFAULT_USERS=false
```

**7. Tạo systemd Service Cho Backend**

```bash
sudo nano /etc/systemd/system/du-backend.service
```

Nội dung:

```ini
[Unit]
Description=DU Management Backend
After=network.target docker.service
Requires=docker.service

[Service]
User=ubuntu
WorkingDirectory=/opt/du-management/du-management-backend
EnvironmentFile=/etc/du-management/backend.env
ExecStart=/usr/bin/java -jar /opt/du-management/du-management-backend/target/du-management-backend-0.0.1-SNAPSHOT.jar
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```

Chạy backend:

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now du-backend
sudo systemctl status du-backend
```

Xem log:

```bash
journalctl -u du-backend -f
```

Test local trên EC2:

```bash
curl http://127.0.0.1:8080/api-docs
```

**8. Build Frontend**

```bash
cd /opt/du-management/du-management-frontend
npm ci
npm run build
```

Copy build output vào Nginx:

```bash
sudo mkdir -p /var/www/du-management
sudo rsync -a --delete dist/ /var/www/du-management/
sudo chown -R www-data:www-data /var/www/du-management
```

**9. Cấu Hình Nginx**

```bash
sudo nano /etc/nginx/sites-available/du-management
```

Nội dung nếu dùng HTTP trước:

```nginx
server {
    listen 80;
    server_name YOUR_DOMAIN_OR_EC2_IP;

    root /var/www/du-management;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /ws {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_read_timeout 3600;
    }

    location /api-docs {
        proxy_pass http://127.0.0.1:8080;
    }

    location /swagger-ui {
        proxy_pass http://127.0.0.1:8080;
    }

    location /swagger-ui.html {
        proxy_pass http://127.0.0.1:8080;
    }
}
```

Enable site:

```bash
sudo ln -s /etc/nginx/sites-available/du-management /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx
```

Mở:

```text
http://YOUR_DOMAIN_OR_EC2_IP
```

**10. Bật HTTPS**

Nếu có domain:

```bash
sudo apt install -y certbot python3-certbot-nginx
sudo certbot --nginx -d YOUR_DOMAIN
```

Sau đó sửa `APP_FRONTEND_URL` thành HTTPS nếu chưa sửa:

```env
APP_FRONTEND_URL=https://YOUR_DOMAIN
```

Restart backend:

```bash
sudo systemctl restart du-backend
```

**11. Checklist Kiểm Tra**

```bash
docker ps
sudo systemctl status du-backend
sudo nginx -t
curl http://127.0.0.1:8080/api-docs
curl http://YOUR_DOMAIN_OR_IP/api-docs
journalctl -u du-backend -n 100
```

Trên browser kiểm tra:

```text
https://YOUR_DOMAIN
https://YOUR_DOMAIN/swagger-ui.html
```

Nếu login lỗi, kiểm tra JWT/env/backend log. Nếu realtime notification/lucky draw không chạy, kiểm tra `/ws` trong browser DevTools Network.

**12. Quy Trình Deploy Lần Sau**

Mỗi lần cập nhật code:

```bash
cd /opt/du-management
git pull

cd du-management-backend
./mvnw clean package -DskipTests
sudo systemctl restart du-backend

cd ../du-management-frontend
npm ci
npm run build
sudo rsync -a --delete dist/ /var/www/du-management/
sudo systemctl reload nginx
```

Nguồn AWS mình đối chiếu: EC2 Security Groups, kết nối EC2, và Elastic IP trong tài liệu chính thức AWS:  
https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ec2-security-groups.html  
https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/connect.html  
https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/elastic-ip-addresses-eip.html