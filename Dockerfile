FROM maven:3.9.9-eclipse-temurin-17 AS builder
WORKDIR /workspace

RUN printf '%s\n' \
  '<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"' \
  '          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"' \
  '          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd">' \
  '  <mirrors>' \
  '    <mirror>' \
  '      <id>aliyunmaven</id>' \
  '      <mirrorOf>*</mirrorOf>' \
  '      <name>Aliyun Maven</name>' \
  '      <url>https://maven.aliyun.com/repository/public</url>' \
  '    </mirror>' \
  '  </mirrors>' \
  '</settings>' > /usr/share/maven/conf/settings.xml

COPY pom.xml .
COPY mini-novel-common/pom.xml mini-novel-common/pom.xml
COPY mini-novel-core/pom.xml mini-novel-core/pom.xml
COPY mini-novel-book/pom.xml mini-novel-book/pom.xml
COPY mini-novel-user/pom.xml mini-novel-user/pom.xml
COPY mini-novel-vip/pom.xml mini-novel-vip/pom.xml
COPY mini-novel-crawler/pom.xml mini-novel-crawler/pom.xml
COPY mini-novel-api/pom.xml mini-novel-api/pom.xml
COPY mini-novel-admin/pom.xml mini-novel-admin/pom.xml
COPY mini-novel-application/pom.xml mini-novel-application/pom.xml

COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    find /root/.m2/repository -name "*.lastUpdated" -delete 2>/dev/null || true; \
    mvn -U -pl mini-novel-api -am -Dtest=VipBookPageVoTest,NovelControllerChapterPaginationTest -Dsurefire.failIfNoSpecifiedTests=false test && \
    mvn -U -pl mini-novel-application -am package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
ENV TZ=Asia/Shanghai
COPY --from=builder /workspace/mini-novel-application/target/mini-novel-application-0.1.0-SNAPSHOT.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar", "--spring.profiles.active=docker"]
