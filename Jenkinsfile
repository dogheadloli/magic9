// GitLab + Jenkins + Docker Compose（无 K8s）
// Jenkins Credentials（ID 需一致）：
//   docker-registry  — 镜像仓库 Username/Password
//   deploy-ssh-key   — 部署机 SSH Username with private key
// 任务或全局环境变量：
//   DOCKER_IMAGE / DEPLOY_HOST / DEPLOY_USER / DEPLOY_PATH

pipeline {
  agent any

  options {
    timestamps()
    disableConcurrentBuilds()
    buildDiscarder(logRotator(numToKeepStr: '20'))
  }

  environment {
    DOCKER_IMAGE = "${env.DOCKER_IMAGE ?: 'registry.example.com/stock-monitor'}"
    IMAGE_TAG    = "${env.GIT_COMMIT ? env.GIT_COMMIT.take(8) : env.BUILD_NUMBER}"
    DEPLOY_HOST  = "${env.DEPLOY_HOST ?: 'your-server.example.com'}"
    DEPLOY_USER  = "${env.DEPLOY_USER ?: 'deploy'}"
    DEPLOY_PATH  = "${env.DEPLOY_PATH ?: '/opt/stock'}"
  }

  stages {
    stage('Checkout') {
      steps {
        checkout scm
      }
    }

    stage('Build image') {
      steps {
        script {
          def skipTests = (env.SKIP_TESTS == 'true') ? 'true' : 'false'
          sh """
            docker build \\
              --build-arg SKIP_TESTS=${skipTests} \\
              -t ${DOCKER_IMAGE}:${IMAGE_TAG} \\
              -t ${DOCKER_IMAGE}:latest \\
              .
          """
        }
      }
    }

    stage('Push image') {
      when {
        anyOf {
          branch 'main'
          branch 'master'
        }
      }
      steps {
        withCredentials([usernamePassword(
          credentialsId: 'docker-registry',
          usernameVariable: 'REG_USER',
          passwordVariable: 'REG_PASS'
        )]) {
          sh """
            REG_HOST=\$(echo "${DOCKER_IMAGE}" | cut -d/ -f1)
            echo "\$REG_PASS" | docker login "\$REG_HOST" -u "\$REG_USER" --password-stdin
            docker push ${DOCKER_IMAGE}:${IMAGE_TAG}
            docker push ${DOCKER_IMAGE}:latest
          """
        }
      }
    }

    stage('Deploy') {
      when {
        anyOf {
          branch 'main'
          branch 'master'
        }
      }
      steps {
        sshagent(credentials: ['deploy-ssh-key']) {
          sh """
            ssh -o StrictHostKeyChecking=accept-new ${DEPLOY_USER}@${DEPLOY_HOST} '
              set -e
              cd ${DEPLOY_PATH}
              test -f docker-compose.yml || { echo "缺少 docker-compose.yml"; exit 1; }
              test -f .env || { echo "缺少 .env，请从 .env.example 初始化"; exit 1; }
              grep -q "^DOCKER_IMAGE=" .env && sed -i.bak "s|^DOCKER_IMAGE=.*|DOCKER_IMAGE=${DOCKER_IMAGE}|" .env || echo "DOCKER_IMAGE=${DOCKER_IMAGE}" >> .env
              grep -q "^IMAGE_TAG=" .env && sed -i.bak "s|^IMAGE_TAG=.*|IMAGE_TAG=${IMAGE_TAG}|" .env || echo "IMAGE_TAG=${IMAGE_TAG}" >> .env
              docker compose pull app
              docker compose up -d app
              docker compose ps
            '
          """
        }
      }
    }
  }

  post {
    success {
      echo "OK  image=${DOCKER_IMAGE}:${IMAGE_TAG}"
    }
    failure {
      echo "FAILED — 构建/部署日志见本页；容器运行日志用 Portainer 或 docker compose logs"
    }
  }
}
