# GAK 绿联 NAS Docker Compose 部署手册

本文面向不熟悉 Docker 部署的使用场景，目标是在绿联 NAS 上同时部署：

- 前端：`gak-web`，Nginx 托管 Vue 静态文件，并把 `/api` 转发给后端。
- 后端：`gak-app`，Spring Boot 应用。
- 数据库：`postgres`，持久化到 NAS 磁盘。
- 预留中间件：`redis`，默认不启动，需要时一行配置启用。

## 一、部署结构

当前部署文件放在后端仓库：

```text
GnomishArmyKnife/
  deploy/nas/
    docker-compose.yml
    .env.example
    Dockerfile
    scripts/
GnomishArmyKnife-Web/
  Dockerfile
  nginx.conf
```

推荐在本地和 NAS 都保持这两个项目为同级目录：

```text
gak/
  GnomishArmyKnife/
  GnomishArmyKnife-Web/
```

如果你的前端目录不在默认位置，执行脚本时指定：

```bash
FRONTEND_ROOT=/你的路径/GnomishArmyKnife-Web ./deploy/nas/scripts/build-image-on-nas.sh
```

## 二、端口说明

默认端口如下：

| 服务 | 容器内端口 | NAS 对外端口 | 说明 |
| --- | --- | --- | --- |
| `gak-web` | `80` | `18080` | 浏览器访问入口 |
| `gak-app` | `8080` | `127.0.0.1:18081` | 默认只给 NAS 本机调试 |
| `postgres` | `5432` | `127.0.0.1:25432` | 默认只给 NAS 本机调试 |
| `redis` | `6379` | 不暴露 | 默认不启动 |

正常使用只需要访问：

```text
http://NAS_IP:18080
```

前端请求 `/api/auth/login` 时，Nginx 会转发到后端的 `/auth/login`，所以浏览器端不需要直接访问后端端口。

## 三、NAS 前置准备

1. 在绿联 NAS 的应用中心安装 Docker/容器管理相关套件。
2. 确认 NAS 支持 `docker compose`。如果 NAS Web 管理界面支持“项目/Compose”，也可以直接粘贴 `docker-compose.yml` 使用。
3. 准备一个部署目录，例如 SSH 登录 NAS 后看到的路径：

```bash
/volume1/Projects/GAK-App
```

4. 确保 SSH 可用。绿联 NAS 通常可在控制面板里开启 SSH，后续脚本会用到 `ssh` 和 `scp`。
5. 注意绿联 NAS 的 `scp` 上传路径和 SSH 登录后的真实路径可能不同。参考本项目旧前端脚本的做法，推荐使用：
   - `scp` 上传路径：`/Projects/GAK-App`
   - SSH 真实路径：`/volume1/Projects/GAK-App`

## 四、首次部署，推荐方式：本地构建后上传

这种方式更稳：构建镜像消耗本地电脑性能，NAS 只负责运行容器。

在本地后端仓库目录执行：

```bash
cd /Users/millerchu/workspace/gak/GnomishArmyKnife
./deploy/nas/scripts/package-and-upload.sh admin@NAS_IP /Projects/GAK-App /volume1/Projects/GAK-App 22
```

脚本会自动探测远端 Docker 权限：普通 `docker` 可用时直接使用；如果 SSH 用户不在 NAS 的 `docker` 用户组里，会自动改用 `sudo docker`，并在远端执行时提示输入 NAS 用户密码。也可以手动指定：

```bash
REMOTE_DOCKER_CMD="sudo docker" ./deploy/nas/scripts/package-and-upload.sh admin@NAS_IP /Projects/GAK-App /volume1/Projects/GAK-App 22
```

参数含义：

- `admin@NAS_IP`：你的 NAS SSH 用户和 IP。
- `/Projects/GAK-App`：`scp` 上传时使用的 NAS 路径。
- `/volume1/Projects/GAK-App`：SSH 登录 NAS 后看到的真实部署目录。
- `22`：SSH 端口，如果你改过端口就替换成实际值。

上传完成后，SSH 登录 NAS：

```bash
ssh admin@NAS_IP
cd /volume1/Projects/GAK-App
cp .env.example .env
vi .env
```

至少修改这些值：

```env
WEB_PORT=18080
POSTGRES_PASSWORD=换成强密码
REDIS_PASSWORD=换成强密码
```

启动：

```bash
./scripts/up.sh
```

如果当前 NAS 用户没有直接执行 Docker 的权限：

```bash
DOCKER_CMD="sudo docker" ./scripts/up.sh
```

查看状态：

```bash
docker compose --env-file .env -f docker-compose.yml ps
```

浏览器访问：

```text
http://NAS_IP:18080
```

## 五、可选方式：在 NAS 上直接构建

如果你想把源码上传到 NAS，让 NAS 自己构建镜像，需要保持两个项目同级：

```text
/volume1/docker/gak-source/
  GnomishArmyKnife/
  GnomishArmyKnife-Web/
```

进入后端仓库执行：

```bash
cd /volume1/docker/gak-source/GnomishArmyKnife
./deploy/nas/scripts/build-image-on-nas.sh
cd deploy/nas
cp .env.example .env
vi .env
./scripts/up.sh
```

这种方式会在 NAS 上下载 Maven、npm 和 Docker 基础镜像，首次执行会比较慢。

## 六、`.env` 配置说明

```env
TZ=Asia/Shanghai
IMAGE_TAG=1.0.0

WEB_PORT=18080
APP_BIND=127.0.0.1
APP_PORT=18081
JAVA_OPTS=-Xms256m -Xmx768m

POSTGRES_BIND=127.0.0.1
POSTGRES_PORT=25432
POSTGRES_DB=mydb
POSTGRES_USER=gak
POSTGRES_PASSWORD=change_me_strong_password

SPRING_SQL_INIT_MODE=always

ENABLE_REDIS=false
REDIS_PASSWORD=change_me_redis_password
```

建议：

- `POSTGRES_PASSWORD` 和 `REDIS_PASSWORD` 必须改成强密码。
- `WEB_PORT` 如果与 NAS 其他服务冲突，可以改为 `18088` 等端口。
- `POSTGRES_PORT` 默认是 `25432`，只用于 NAS 本机调试数据库。如果该端口也被占用，可以继续改成其他未占用端口，例如 `25433`。
- `APP_BIND` 和 `POSTGRES_BIND` 默认是 `127.0.0.1`，表示只允许 NAS 本机访问。若确实需要局域网访问数据库，可改为 `0.0.0.0`，但不建议长期开放。
- `SPRING_SQL_INIT_MODE=always` 会在每次后端启动时执行 `schema.sql`。当前 SQL 基本按幂等方式编写，生产稳定后也可以改成 `never`，避免启动时反复检查结构和种子数据。

## 七、常用运维命令

进入 NAS 部署目录：

```bash
cd /volume1/Projects/GAK-App
```

启动：

```bash
./scripts/up.sh
```

停止：

```bash
./scripts/down.sh
```

查看日志：

```bash
./scripts/logs.sh
```

查看容器状态：

```bash
docker compose --env-file .env -f docker-compose.yml ps
```

只看后端日志：

```bash
docker logs -f --tail=200 gak-app
```

只看前端 Nginx 日志：

```bash
docker logs -f --tail=200 gak-web
```

## 八、升级发布

推荐直接执行一键升级脚本：

```bash
cd /Users/millerchu/workspace/gak/GnomishArmyKnife
./deploy-greennas.sh
```

这个脚本会使用当前绿联 NAS 的默认配置：

- `REMOTE_DOCKER_CMD="sudo docker"`
- `NAS_USER=millerchu`
- `NAS_HOST=greennas`
- `NAS_SCP_DIR=/Projects/GAK-App`
- `NAS_SSH_DIR=/volume1/Projects/GAK-App`
- `SSH_PORT=22`

如需临时覆盖镜像版本或 NAS 参数，可以在命令前加环境变量：

```bash
IMAGE_TAG=1.0.1 ./deploy-greennas.sh
```

如果只想上传镜像和部署文件，不自动重启容器，可以执行上传脚本：

```bash
cd /Users/millerchu/workspace/gak/GnomishArmyKnife
./deploy/nas/scripts/package-and-upload.sh millerchu@greennas /Projects/GAK-App /volume1/Projects/GAK-App 22
```

上传后需要手动在 NAS 上重启：

```bash
cd /volume1/Projects/GAK-App
DOCKER_CMD="sudo docker" ./scripts/up.sh
```

`./data/postgres` 和 `./data/app` 是持久化目录，不会因为镜像升级丢失。

## 九、数据持久化与备份

Compose 会在部署目录下创建：

```text
data/
  postgres/          # PostgreSQL 数据
  app/
    app-icons/       # 应用图标上传文件
    data-migrations/ # 数据迁移导出包
    health-records/  # 健康档案附件
  redis/             # Redis 数据，启用后出现
```

建议定期备份整个目录：

```bash
cd /volume1/Projects
tar -czf gak-backup-$(date +%Y%m%d_%H%M%S).tar.gz GAK-App/.env GAK-App/data
```

恢复时：

1. 停止容器：`./scripts/down.sh`
2. 解压备份覆盖 `.env` 和 `data`
3. 启动容器：`./scripts/up.sh`

## 十、启用 Redis

当前后端还没有强依赖 Redis，所以 Compose 中把 Redis 放在 profile 里，默认不启动。

需要 Redis 时：

1. 修改 `.env`：

```env
ENABLE_REDIS=true
REDIS_PASSWORD=换成强密码
```

2. 启动：

```bash
./scripts/up.sh
```

3. 后续如果后端代码接入 Redis，建议新增环境变量：

```env
SPRING_DATA_REDIS_HOST=redis
SPRING_DATA_REDIS_PORT=6379
SPRING_DATA_REDIS_PASSWORD=你的Redis密码
```

并在 `docker-compose.yml` 的 `gak-app.environment` 中同步添加。

## 十一、绿联 NAS Web 界面部署提示

如果你更习惯用 NAS 的图形界面：

1. 先使用本地脚本把镜像和部署包上传到 NAS。
2. 在 NAS 终端执行 `docker load -i gak-images-1.0.0.tar`。
3. 在容器管理界面创建 Compose/项目。
4. 项目目录选择 `/volume1/Projects/GAK-App`。
5. Compose 内容使用 `docker-compose.yml`。
6. 环境变量使用 `.env` 中的内容。
7. 启动项目后，访问 `http://NAS_IP:18080`。

图形界面的字段名称可能随绿联系统版本变化；如果找不到 Compose 项目入口，直接使用本文命令行方式最稳定。

## 十二、排错

端口被占用：

```bash
docker compose --env-file .env -f docker-compose.yml ps
```

如果 `gak-web` 起不来，改 `.env` 里的 `WEB_PORT` 后重启。

如果 `gak-postgres` 提示 `127.0.0.1:25432` 或其他 PostgreSQL 端口被占用，改 `.env` 中的 `POSTGRES_PORT`，例如：

```env
POSTGRES_PORT=25432
```

这个端口只影响从 NAS 主机访问数据库，不影响后端容器连接 PostgreSQL。

数据库密码改错：

```bash
docker logs -f --tail=200 gak-app
```

如果看到数据库认证失败，检查 `.env` 中的 `POSTGRES_USER`、`POSTGRES_PASSWORD`，并确认已有数据库数据目录是否用的是旧密码。已有数据目录不会因为 `.env` 改密码自动重置。

Docker socket 权限不足：

```text
permission denied while trying to connect to the Docker daemon socket
```

短期处理方式是在命令前加 `sudo`：

```bash
REMOTE_DOCKER_CMD="sudo docker" ./deploy/nas/scripts/package-and-upload.sh admin@NAS_IP /Projects/GAK-App /volume1/Projects/GAK-App 22
DOCKER_CMD="sudo docker" ./scripts/up.sh
```

长期处理方式是在 NAS 上把部署用户加入 `docker` 组，然后重新登录 SSH：

```bash
sudo usermod -aG docker 你的NAS用户名
```

前端页面打开但接口失败：

```bash
docker logs -f --tail=200 gak-web
docker logs -f --tail=200 gak-app
```

重点确认 `gak-web` 和 `gak-app` 都在运行，且前端访问地址是 `http://NAS_IP:WEB_PORT`，不要直接打开后端端口。

重新初始化数据库：

```bash
./scripts/down.sh
mv data/postgres data/postgres.bak.$(date +%Y%m%d_%H%M%S)
./scripts/up.sh
```

这会生成一个全新的数据库目录。旧目录只是改名保留，没有删除。
