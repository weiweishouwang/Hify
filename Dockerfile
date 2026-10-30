# ── 构建阶段 ──────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

# 先只拷 pom，依赖解析走镜像层缓存——改代码时不会重新下载依赖
COPY pom.xml .
COPY hify-shared/pom.xml hify-shared/
COPY hify-ai/pom.xml hify-ai/
COPY hify-tool/pom.xml hify-tool/
COPY hify-knowledge/pom.xml hify-knowledge/
COPY hify-mcp/pom.xml hify-mcp/
COPY hify-agent/pom.xml hify-agent/
COPY hify-conversation/pom.xml hify-conversation/
COPY hify-workflow/pom.xml hify-workflow/
COPY hify-app/pom.xml hify-app/

RUN mvn -B -q dependency:go-offline -DskipTests || true

COPY . .
RUN mvn -B -q clean package -DskipTests

# ── 运行阶段 ──────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre

# curl 供容器健康检查使用
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# 非 root 运行
RUN groupadd --system --gid 10001 hify \
    && useradd --system --uid 10001 --gid hify --no-create-home hify

WORKDIR /app
COPY --from=build /build/hify-app/target/hify-app-*.jar app.jar

USER hify
EXPOSE 8080

# 与 CLAUDE.md §1.4 的内存预算一致：堆 512m，容器内存上限需 ≥ 堆 + native
ENV JAVA_TOOL_OPTIONS="-Xmx512m -XX:MaxMetaspaceSize=128m -XX:MaxDirectMemorySize=128m -XX:+ExitOnOutOfMemoryError -XX:+UseG1GC"

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
