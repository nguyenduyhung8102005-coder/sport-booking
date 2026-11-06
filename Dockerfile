# syntax=docker/dockerfile:1

# =====================================================================================
# Multi-stage build: stage 1 có JDK + Maven để BUILD, stage 2 chỉ có JRE để CHẠY.
# Image cuối không chứa Maven, mã nguồn, thư viện build -> nhỏ hơn nhiều và ít lỗ hổng hơn.
# =====================================================================================

# ---------- Stage 1: build ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
COPY src ./src

# --mount=type=cache: giữ thư mục ~/.m2 giữa các lần build -> không tải lại thư viện mỗi lần.
# -DskipTests: test đã chạy ở bước riêng (mvn test / CI); integration test cần Docker, không chạy được bên trong build.
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package

# Tách fat jar thành app.jar (code của mình, nhỏ, đổi thường xuyên) + lib/ (thư viện, lớn, ít đổi).
# Docker cache theo layer: sửa code chỉ build lại / đẩy lại layer app.jar vài trăm KB, không phải cả ~80 MB.
RUN cp target/*.jar app.jar \
 && java -Djarmode=tools -jar app.jar extract --destination extracted

# ---------- Stage 2: runtime ----------
FROM eclipse-temurin:21-jre-alpine

# Không chạy bằng root: nếu app bị khai thác, kẻ tấn công không có quyền root trong container
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app

COPY --from=build /workspace/extracted/lib/ ./lib/
COPY --from=build /workspace/extracted/app.jar ./app.jar

USER app
EXPOSE 8080

# JVM tự nhận giới hạn RAM của container; dùng tối đa 75% cho heap (phần còn lại cho metaspace, thread...)
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"

# Docker tự kiểm tra sức khỏe; Compose dùng kết quả này cho "condition: service_healthy"
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=5 \
  CMD wget -qO- http://localhost:8080/actuator/health/readiness > /dev/null || exit 1

# exec: java trở thành process số 1 -> nhận được tín hiệu SIGTERM khi "docker stop" để tắt êm (graceful shutdown)
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
