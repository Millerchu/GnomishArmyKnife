# GAK 打包与绿联 NAS 部署

本文提供两种可执行方案：

- 方案 A: 在 NAS 上直接构建镜像并启动（推荐，流程直观）
- 方案 B: 在本地构建镜像并上传到 NAS 启动（适合 NAS 构建性能较弱或网络受限）
- 方案 C: 本地一键 `deploy.sh`（按固定 NAS 配置自动构建、上传、远端启动）

## 目录

- `Dockerfile`: 多阶段构建，产出 `gak-app:1.0.0`
- `docker-compose.yml`: 启动 `gak-app` + `postgres`
- `.env.example`: 环境变量模板
- `scripts/*.sh`: 一键脚本

## 前置要求

- NAS 已安装 Docker（支持 `docker compose`）
- 若使用方案 B，本地也需要 Docker + SSH/SCP 到 NAS

## 方案 A: NAS 本机构建并启动

1. 将整个工程上传到 NAS 某目录（例如 `/volume1/docker/gak-parent`）。
2. 在 NAS 执行：

```bash
cd /volume1/docker/gak-parent
./deploy/nas/scripts/build-image-on-nas.sh
cd deploy/nas
cp .env.example .env
vi .env
./scripts/up.sh
```

3. 查看状态与日志：

```bash
./scripts/logs.sh
docker compose --env-file .env -f docker-compose.yml ps
```

## 方案 B: 本地构建并上传到 NAS

在本地工程根目录执行：

```bash
./deploy/nas/scripts/package-and-upload.sh <nas_user@nas_host> <nas_project_dir> [ssh_port]
```

示例：

```bash
./deploy/nas/scripts/package-and-upload.sh admin@192.168.1.20 /volume1/docker/gak 22
```

然后 SSH 到 NAS 执行：

```bash
cd /volume1/docker/gak
cp .env.example .env
vi .env
./scripts/up.sh
```

## 方案 C: 参考前端脚本风格的一键部署

脚本：

- `deploy/nas/scripts/deploy.sh`

先按你的 NAS 改脚本顶部配置：

- `NAS_USER`
- `NAS_HOST`
- `NAS_SCP_DIR`
- `NAS_SSH_DIR`

然后在本地工程根目录执行：

```bash
./deploy/nas/scripts/deploy.sh
```

这个脚本会自动执行：

- 本地 `docker build`
- 本地 `docker save` 导出镜像
- 上传镜像和部署文件到 NAS
- NAS 侧自动 `docker load` + `docker compose up -d`
- 本地/远端临时包清理

## 关键配置

编辑 `.env`：

- `APP_PORT`: 应用暴露端口，默认 `8080`
- `POSTGRES_DB/POSTGRES_USER/POSTGRES_PASSWORD`: 数据库信息
- `SPRING_SQL_INIT_MODE`: 默认 `always`，若不希望每次启动初始化 schema，改成 `never`

## 运维命令

```bash
# 停止
./scripts/down.sh

# 查看日志
./scripts/logs.sh

# 重启（先停后起）
./scripts/down.sh && ./scripts/up.sh
```

## 升级流程

1. 更新代码。
2. 重新执行方案 A 的 `build-image-on-nas.sh` + `up.sh`，或方案 B 的 `package-and-upload.sh` + `up.sh`。
3. 数据卷 `deploy/nas/data/postgres` 会保留数据库数据。
