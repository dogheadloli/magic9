# 服务器初始化（只需做一次）

在部署机执行：

```bash
# 1. 安装 Docker Engine + Compose 插件

# 2. 目录
sudo mkdir -p /opt/stock
sudo chown "$USER:$USER" /opt/stock
cd /opt/stock

# 3. 放入 compose（从仓库拷贝或 git clone 后只保留部署文件）
#    至少需要：docker-compose.yml
cp /path/to/repo/docker-compose.yml .
cp /path/to/repo/.env.example .env
vi .env   # 填写 DB_PASS / MAIL_* / WECOM_KEY / DEEPSEEK_* / DOCKER_IMAGE

# 4. 登录镜像仓库
docker login registry.example.com

# 5. 首次启动（IMAGE_TAG 先用 latest，或等 Jenkins 首次推送后再 pull）
docker compose pull || true
docker compose up -d

# 6.（可选）安装 Portainer 看日志，勿对公网裸奔 9443
docker volume create portainer_data
docker run -d -p 9443:9443 --name portainer --restart=unless-stopped \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v portainer_data:/data \
  portainer/portainer-ce:latest
```

## Jenkins 凭据与任务

| Credentials ID | 类型 | 用途 |
|----------------|------|------|
| `docker-registry` | Username/Password | 推送镜像 |
| `deploy-ssh-key` | SSH Username with private key | 登录部署机执行 compose |

任务类型：Pipeline from SCM → 指向本 GitLab 仓库 → Script Path: `Jenkinsfile`。

在任务或 Jenkins 全局环境中设置：

- `DOCKER_IMAGE`：如 `registry.example.com/stock-monitor`
- `DEPLOY_HOST`：服务器 IP/域名
- `DEPLOY_USER`：SSH 用户
- `DEPLOY_PATH`：默认 `/opt/stock`

GitLab → Jenkins：安装 GitLab 插件，配置 Webhook，push `main`/`master` 触发构建。

## 回滚

```bash
cd /opt/stock
# 将 .env 中 IMAGE_TAG 改成已知好的 commit 短 SHA
docker compose pull app && docker compose up -d app
```

或：`./deploy/update.sh <旧TAG>`（需把脚本也放到服务器）。
