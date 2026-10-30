# Hify 开发命令
#
#   make            看帮助
#   make start      后台起全栈（后端 + 前端 dev server）
#   make stop       优雅停掉
#   make restart    重启
#   make build      构建后端 + 前端
#   make clean      清理构建产物
#   make package    打包成可分发的 tar.gz
#
# 细节见 CLAUDE.md §2.6。

.DEFAULT_GOAL := help
.SHELLFLAGS := -eu -o pipefail -c
.SHELL := /bin/bash

MVN      ?= mvn
NPM      ?= npm
WEB_DIR  := hify-web
DIST_DIR := dist
LOG_DIR  := logs

# 版本从根 pom 里取，跟着 pom 走不用手改
VERSION := $(shell grep -m1 -A2 '<artifactId>hify-parent</artifactId>' pom.xml \
              | grep -m1 '<version>' \
              | sed -e 's/.*<version>\(.*\)<\/version>.*/\1/')
PKG_NAME := hify-$(VERSION)

# jar 名字带版本号，别写死
APP_JAR := $(shell ls -1 hify-app/target/hify-app-*.jar 2>/dev/null | grep -v '\.original$$' | head -1)

# 中间件服务：make up / down 只管这三个，不碰 api 与 web 容器
# （本地跑应用时后端是 start.sh 起的，compose 里那两个容器别掺进来）
MIDDLEWARE := mysql postgres redis

.PHONY: help start stop restart build build-fast clean package test status logs distclean \
        up down purge ps

## help: 列出所有可用命令
help:
	@echo "Hify 开发命令（当前版本 $(VERSION)）"
	@echo ""
	@grep -E '^## ' $(MAKEFILE_LIST) | sed -e 's/^## /  /'
	@echo ""
	@echo "中间件与应用的分工："
	@echo "  make up / down      中间件（mysql + postgres + redis，走 docker compose）"
	@echo "  make start / stop   应用进程（后端 + 前端，走 start.sh / stop.sh）"
	@echo ""

## start: 后台启动应用（后端 + 前端 dev server）
start:
	@./start.sh --background

## stop: 优雅停止应用进程（后端 + 前端）
stop:
	@./stop.sh

## restart: 停掉应用再重新起
restart: stop start
	@echo "重启完成"

## up: 启动中间件（mysql + postgres + redis）
up:
	@if docker compose up --help 2>&1 | grep -q -- '--wait'; then \
	  docker compose up -d --wait --wait-timeout 90 $(MIDDLEWARE); \
	  echo "==> 中间件已就绪（健康检查都过了）"; \
	else \
	  echo "==> 这个 compose 版本不支持 --wait，起完自己用 make ps 确认"; \
	  docker compose up -d $(MIDDLEWARE); \
	fi

## down: 停止中间件（保留容器与数据卷）
down:
	@docker compose stop $(MIDDLEWARE)
	@echo "==> 中间件已停（数据卷还在，make up 就能再起来）"
	@echo "==> 注意：compose 里的 api / web 若在跑，会连不上数据库了"

## purge: 彻底清理中间件（停全部服务 + 删数据卷，不可恢复）
purge:
	@echo "==> 这会停掉所有 compose 服务并删除数据卷（MySQL 数据、向量数据都会没）"
	@docker compose down -v

## ps: 看 compose 服务的状态
ps:
	@docker compose ps

## build: 构建后端（含测试）+ 前端
build:
	@echo "==> 构建后端（mvn verify，含测试与依赖环检查）"
	@$(MVN) -B verify
	@echo "==> 构建前端（vue-tsc 类型检查 + vite build）"
	@cd $(WEB_DIR) && $(NPM) run build
	@echo "==> 构建完成"

## build-fast: 跳过后端测试的快速构建
build-fast:
	@$(MVN) -B package -DskipTests
	@cd $(WEB_DIR) && $(NPM) run build

## test: 跑后端与前端的所有测试
test:
	@$(MVN) -B test
	@cd $(WEB_DIR) && $(NPM) test

## clean: 清理构建产物（不动日志与依赖）
clean:
	@echo "==> 清理后端构建产物"
	@$(MVN) -B clean
	@echo "==> 清理前端构建产物"
	@cd $(WEB_DIR) && rm -rf dist node_modules/.vite
	@rm -rf $(DIST_DIR)
	@echo "==> 清理完成"

## distclean: 彻底清理（连日志、依赖、docker 卷一起删）
distclean: clean
	@rm -rf $(LOG_DIR)
	@cd $(WEB_DIR) && rm -rf node_modules
	@echo "==> 已删除日志与前端依赖"

## package: 打包成可分发的 tar.gz
package: build
	@echo "==> 组装 $(PKG_NAME)"
	@rm -rf $(DIST_DIR)/$(PKG_NAME)
	@mkdir -p $(DIST_DIR)/$(PKG_NAME)
	@test -n "$(APP_JAR)" || { echo "找不到后端 jar，先 make build"; exit 1; }
	@cp "$(APP_JAR)" $(DIST_DIR)/$(PKG_NAME)/app.jar
	@cp -R $(WEB_DIR)/dist $(DIST_DIR)/$(PKG_NAME)/web
	@cp $(WEB_DIR)/nginx.conf $(DIST_DIR)/$(PKG_NAME)/nginx.conf
	@cp docker-compose.yml Dockerfile .env.example $(DIST_DIR)/$(PKG_NAME)/
	@cp -R docker $(DIST_DIR)/$(PKG_NAME)/docker
	@cp start.sh stop.sh $(DIST_DIR)/$(PKG_NAME)/
	@printf '%s\n' \
	  "Hify $(VERSION)" \
	  "" \
	  "目录说明：" \
	  "  app.jar        后端可执行 jar（java -jar app.jar）" \
	  "  web/           前端静态产物（用 nginx 托管，反代 /api 到后端）" \
	  "  nginx.conf     nginx 配置（含 SSE 必需的四项设置）" \
	  "  docker-compose.yml / Dockerfile / docker/  容器化部署" \
	  "  .env.example   端口与凭据模板" \
	  > $(DIST_DIR)/$(PKG_NAME)/README.txt
	@cd $(DIST_DIR) && tar -czf $(PKG_NAME).tar.gz $(PKG_NAME)
	@rm -rf $(DIST_DIR)/$(PKG_NAME)
	@echo "==> 产物：$(DIST_DIR)/$(PKG_NAME).tar.gz"
	@ls -lh $(DIST_DIR)/$(PKG_NAME).tar.gz

## status: 看当前跑着的东西
status:
	@echo "后端："
	@if [ -f $(LOG_DIR)/api.pid ]; then \
	  p=$$(cat $(LOG_DIR)/api.pid); \
	  if ps -p $$p >/dev/null 2>&1; then echo "  运行中 (pid $$p)"; else echo "  pid 文件在但进程没了 ($$p)"; fi; \
	else echo "  未启动"; fi
	@echo "前端："
	@if [ -f $(LOG_DIR)/web.pid ]; then \
	  p=$$(cat $(LOG_DIR)/web.pid); \
	  if ps -p $$p >/dev/null 2>&1; then echo "  运行中 (pid $$p)"; else echo "  pid 文件在但进程没了 ($$p)"; fi; \
	else echo "  未启动"; fi

## logs: 跟踪后端与前端日志
logs:
	@tail -f $(LOG_DIR)/api.log $(LOG_DIR)/web.log
