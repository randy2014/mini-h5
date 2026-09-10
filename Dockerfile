FROM maven:3.9.9-eclipse-temurin-17 AS builder
WORKDIR /workspace

COPY pom.xml .
COPY mini-novel-common/pom.xml mini-novel-common/pom.xml
COPY mini-novel-core/pom.xml mini-novel-core/pom.xml
COPY mini-novel-book/pom.xml mini-novel-book/pom.xml
COPY mini-novel-media/pom.xml mini-novel-media/pom.xml
COPY mini-novel-user/pom.xml mini-novel-user/pom.xml
COPY mini-novel-vip/pom.xml mini-novel-vip/pom.xml
COPY mini-novel-crawler/pom.xml mini-novel-crawler/pom.xml
COPY mini-novel-api/pom.xml mini-novel-api/pom.xml
COPY mini-novel-admin/pom.xml mini-novel-admin/pom.xml
COPY mini-novel-application/pom.xml mini-novel-application/pom.xml

COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    find /root/.m2/repository -name "*.lastUpdated" -delete 2>/dev/null || true; \
    mvn -pl mini-novel-api -am -Dtest=VipBookPageVoTest,NovelControllerChapterPaginationTest,VipControllerCategoryTest -Dsurefire.failIfNoSpecifiedTests=false test && \
    mvn -pl mini-novel-application -am package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
ENV TZ=Asia/Shanghai
# ffmpeg: 多媒体池子视频转码/抽帧依赖；安装失败不阻塞构建（VPS 网络不稳时视频功能降级，图片/后端不受影响）
RUN apt-get update && apt-get install -y --no-install-recommends ffmpeg ca-certificates \
    && rm -rf /var/lib/apt/lists/* || echo "WARN: ffmpeg install failed, video features degraded"
COPY --from=builder /workspace/mini-novel-application/target/mini-novel-application-0.1.0-SNAPSHOT.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar", "--spring.profiles.active=docker"]
