# Bài 5: Chẩn đoán & khắc phục 3 sự cố đứt gãy Pipeline CI/CD

Phân tích log lỗi thực tế trên GitHub Actions và sửa trực tiếp để khôi phục luồng
CI/CD tích xanh.

## Cấu trúc

```
ex5/
├── .github/
│   └── workflows/
│       └── broken-pipeline.yml   # workflow đã sửa cả 3 lỗi
├── gradle/wrapper/               # Gradle wrapper
├── src/
│   ├── main/java/com/example/
│   │   ├── App.java              # @SpringBootApplication
│   │   └── HelloController.java  # REST GET /hello
│   └── test/java/com/example/AppTest.java
├── Dockerfile                    # multi-stage: build JAR -> runtime JRE
├── .dockerignore
├── build.gradle                  # Spring Boot 3.3.5
├── settings.gradle
├── troubleshooting.md            # phân tích 3 lỗi: log / nguyên nhân / cách sửa
├── screenshots/                  # ảnh chụp màn hình pipeline tích xanh
└── README.md
```

## 3 lỗi đã khắc phục

| # | Lỗi | Cách sửa |
|---|-----|----------|
| 1 | **Permission denied** — `./gradlew: Permission denied` (exit 126) | Thêm `run: chmod +x ./gradlew` trước bước test |
| 2 | **Authentication failed** — `401 Unauthorized` khi login `ghcr.io` | Đúng tên secret: `${{ secrets.REGISTRY_TOKEN }}` |
| 3 | **Build context error** — `failed to read dockerfile` | `context: .` và `file: ./Dockerfile` |

Chi tiết phân tích (log lỗi → nguyên nhân gốc → cách sửa → vì sao đúng) xem
[`troubleshooting.md`](./troubleshooting.md).

## Kiểm tra trên GitHub Actions

1. Push lên `main` → tab **Actions** → workflow **Broken Pipeline (Đã sửa)**.
2. Xác nhận: Gradle `BUILD SUCCESSFUL`, Docker `Login Succeeded`, image được đẩy
   lên `ghcr.io/<owner>/<repo>:latest`.
3. Job kết thúc **tích xanh** → chụp màn hình lưu vào `screenshots/`.

## Chạy thử cục bộ

```bash
./gradlew test bootJar          # test + đóng gói JAR (build/libs/*.jar)
docker build -t ghcr-demo:local .
docker run --rm -p 8080:8080 ghcr-demo:local   # mở http://localhost:8080/hello
```
