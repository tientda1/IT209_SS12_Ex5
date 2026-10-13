# Bài 5: Chẩn đoán & khắc phục 3 sự cố đứt gãy Pipeline CI/CD

Kịch bản nộp bài: `.github/workflows/broken-pipeline.yml` (đã sửa, tích xanh).
Tài liệu này phân tích từng lỗi theo 4 bước: **Triệu chứng (log) → Nguyên nhân gốc →
Cách sửa → Vì sao đúng**, kèm mã "trước/sau".

> Sau khi sửa cả 3 lỗi, pipeline chuyển trạng thái **tích xanh** (xem
> `screenshots/`).

---

## Tóm tắt 3 lỗi

| # | Lỗi | Thông điệp điển hình trên GitHub Actions | Dòng/Step gây lỗi | Cách sửa |
|---|-----|------------------------------------------|-------------------|----------|
| 1 | Permission denied | `./gradlew: Permission denied` — exit code **126** | Step chạy `./gradlew` | Thêm `run: chmod +x ./gradlew` trước đó |
| 2 | Authentication failed | `login attempt to https://ghcr.io/v2/ failed with status: 401 Unauthorized` | Step `docker/login-action` | Đúng tên secret `${{ secrets.REGISTRY_TOKEN }}` |
| 3 | Build context error | `failed to read dockerfile: ... no such file or directory` | Step `docker/build-push-action` | Đặt `context: .` và `file: ./Dockerfile` |

---

## Lỗi 1 — Permission denied khi chạy Gradle Wrapper

### Triệu chứng (log)

```text
Run ./gradlew test bootJar --no-daemon
/home/runner/work/IT209_SS12_Ex5/IT209_SS12_Ex5/gradlew: line 1: ./gradlew: Permission denied
Error: Process completed with exit code 126.
```

### Nguyên nhân gốc

`gradlew` là một shell script cần **bit thực thi (executable, `+x`)**. Trên Git,
quyền này được lưu ở chế độ file (mode `100755`). Khi commit từ **Windows**, Git
thường chạy với `core.fileMode = false` nên **không ghi lại bit exec** → file vào
repo với mode `100644`. Trên runner Linux (`ubuntu-latest`), file chỉ có quyền
`rw-r--r--`, nên gọi `./gradlew` bị từ chối. Mã thoát **126** đúng nghĩa "không
thực thi được" (khác 127 = "không tìm thấy lệnh").

### Cách sửa

Thêm một step cấp quyền thực thi **trước** khi gọi wrapper:

```diff
+      - name: Cấp quyền thực thi cho Gradle wrapper
+        run: chmod +x ./gradlew
+
       - name: Chạy kiểm thử Gradle và đóng gói JAR
         run: ./gradlew test bootJar --no-daemon
```

### Vì sao đúng

`chmod +x ./gradlew` đặt lại bit thực thi ngay trên workspace của runner trước khi
lệnh `./gradlew` được gọi, không phụ thuộc vào mode đã commit.

---

## Lỗi 2 — Authentication failed khi login Docker Registry

### Triệu chứng (log)

```text
Run docker/login-action@v3
  Logging into ghcr.io...
Error: Cannot perform an interactive login from a non TTY device
Error: login attempt to https://ghcr.io/v2/ failed with status: 401 Unauthorized
Error: Process completed with exit code 1.
```

### Nguyên nhân gốc

Bước login truyền **sai tên biến secret**:

```yaml
password: ${{ secrets.REGISTRY_TOKENS }}   # ❌ thừa chữ "S"
```

GitHub Actions **không báo lỗi cú pháp** khi một secret không tồn tại — biểu thức
`${{ secrets.KHONG_TON_TAI }}` chỉ trả về **chuỗi rỗng**. Vì vậy `docker login`
nhận mật khẩu rỗng và máy chủ `ghcr.io` trả **401 Unauthorized**. Đây là lỗi rất
khó thấy vì YAML vẫn hợp lệ; phải so khớp **chính tả** giữa tên secret trong code
và tên đã khai báo ở **Settings → Secrets and variables → Actions**.

### Cách sửa

```diff
       - name: Đăng nhập vào GitHub Container Registry
         uses: docker/login-action@v3
         with:
           registry: ghcr.io
           username: ${{ github.actor }}
-          password: ${{ secrets.REGISTRY_TOKENS }}
+          password: ${{ secrets.REGISTRY_TOKEN }}
```

### Vì sao đúng

Tên biến khớp đúng với secret `REGISTRY_TOKEN` (PAT/GITHUB_TOKEN có quyền
`write:packages`), nên `docker login` nhận được mật khẩu thật và trả
`Login Succeeded`.

---

## Lỗi 3 — Build context error khi build Docker Image

### Triệu chứng (log)

```text
Run docker/build-push-action@v5
ERROR: failed to build: failed to solve: failed to read dockerfile:
open /home/runner/work/IT209_SS12_Ex5/IT209_SS12_Ex5/app/Dockerfile:
no such file or directory
Error: Process completed with exit code 1.
```

### Nguyên nhân gốc

Bước build chỉ định sai thư mục làm việc:

```yaml
with:
  context: ./app            # ❌ trỏ vào thư mục không tồn tại
  file: ./app/Dockerfile    # ❌ Dockerfile thực tế nằm ở gốc repo
```

`docker/build-push-action` gửi **build context** tới Docker daemon rồi tìm
`Dockerfile` **bên trong context đó**. Vì `context: ./app` không tồn tại (và
Dockerfile thật nằm ở gốc repo), Docker không đọc được Dockerfile → lỗi
`failed to read dockerfile`.

### Cách sửa

```diff
       - name: Đóng gói và đẩy Docker Image lên GHCR
         uses: docker/build-push-action@v5
         with:
-          context: ./app
-          file: ./app/Dockerfile
+          context: .
+          file: ./Dockerfile
           push: true
           tags: ghcr.io/${{ github.repository }}:latest
```

### Vì sao đúng

`context: .` dùng thư mục gốc repo làm build context — nơi chứa cả `Dockerfile`,
`gradlew`, `build.gradle` và `src/`. `file: ./Dockerfile` trỏ đúng file Dockerfile
ở gốc, khớp với các lệnh `COPY` bên trong (multi-stage build cần `gradlew`,
`settings.gradle`, `build.gradle`, `gradle/`, `src/`).

---

## Kiểm chứng pipeline tích xanh

1. Push nhánh `main`:

   ```bash
   git add .
   git commit -m "fix(ci): chmod +x gradlew, đúng secret REGISTRY_TOKEN, đúng docker context"
   git push origin main
   ```

2. Vào tab **Actions** → chọn workflow **Broken Pipeline (Đã sửa)**.
3. Mở từng step để xác nhận:
   - **Cấp quyền thực thi cho Gradle wrapper**: `chmod +x ./gradlew` chạy OK.
   - **Chạy kiểm thử Gradle và đóng gói JAR**: `BUILD SUCCESSFUL`.
   - **Đăng nhập vào GitHub Container Registry**: `Login Succeeded`.
   - **Đóng gói và đẩy Docker Image lên GHCR**: push thành công image
     `ghcr.io/<owner>/<repo>:latest`.
4. Cả job kết thúc với **tích xanh (success)** → chụp màn hình lưu vào
   `screenshots/`.

## Ghi chú vận hành

- `permissions: packages: write` là bắt buộc để đẩy image lên GHCR.
- Tên image GHCR phải **viết thường**; nếu `github.repository` có chữ hoa, thêm
  bước `echo "IMAGE=ghcr.io/${GITHUB_REPOSITORY,,}" >> $GITHUB_ENV` rồi dùng
  `${{ env.IMAGE }}`.
- Với các secret không tồn tại, hãy **so khớp chính tả** ngay từ đầu — GitHub
  không cảnh báo, chỉ âm thầm truyền chuỗi rỗng.
