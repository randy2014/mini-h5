# Mini Novel

H5 小说应用后端骨架，采用 Spring Boot 多模块单体架构，包含前台 API、后台管理、小说内容、用户、VIP 和爬虫任务模块。

## Modules

```text
mini-novel-common        通用返回、异常、分页对象
mini-novel-core          全局配置、异常处理、MyBatis-Plus、OpenAPI
mini-novel-book          小说、章节、分类
mini-novel-user          前台用户、书架、阅读历史
mini-novel-vip           VIP 套餐、订单、会员权益判断
mini-novel-crawler       采集源、采集任务、爬虫执行入口
mini-novel-api           H5 前台接口
mini-novel-admin         后台管理接口
mini-novel-application   Spring Boot 启动模块
```

## Stack

- Java 17
- Spring Boot 3
- MyBatis-Plus
- MySQL 8
- Redis
- Sa-Token
- Springdoc OpenAPI
- Jsoup
- Spring Scheduler / Async

不包含 Elasticsearch、RabbitMQ、Spring Cloud，第一版保持单体部署。

## Quick Start

1. 创建数据库并导入 `sql/schema.sql`
2. 修改 `mini-novel-application/src/main/resources/application.yml` 中的 MySQL 和 Redis 配置
3. 确认本机已安装 JDK 17 和 Maven
4. 启动应用：

```bash
mvn -pl mini-novel-application -am spring-boot:run
```

接口文档地址：

```text
http://localhost:8080/swagger-ui.html
```

## Docker

本地 Docker 一键启动 MySQL、Redis、后端应用和 H5 前端：

```bash
docker compose up --build
```

启动后访问 H5：

```text
http://localhost:5173
```

后端接口：

```text
http://localhost:8080/api/home
http://localhost:8080/api/novels/1
http://localhost:8080/api/novels/1/chapters
http://localhost:8080/api/novels/chapters/1
http://localhost:8080/swagger-ui.html
```

VIP 章节预览：

```bash
curl http://localhost:8080/api/novels/chapters/2
curl -H "X-User-Id: 2" http://localhost:8080/api/novels/chapters/2
```

重置数据库数据：

```bash
docker compose down -v
docker compose up --build
```

## H5 Frontend

前端项目位于 `mini-novel-h5`，本地开发时先启动后端，再启动 H5：

```bash
cd mini-novel-h5
npm install
npm run dev
```

Vite 开发服务会把 `/api` 代理到 `http://localhost:8080`。

## Initial APIs

```text
GET  /api/home
GET  /api/novels/{novelId}
GET  /api/novels/{novelId}/chapters
GET  /api/novels/chapters/{chapterId}

GET  /admin/novels
POST /admin/crawl-tasks/run
```

章节阅读接口暂时通过 `X-User-Id` 请求头模拟登录用户，后续会替换为 Sa-Token 登录态。
