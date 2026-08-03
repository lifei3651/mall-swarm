# SkyWalking 链路追踪配置

## 1. 安装 SkyWalking OAP Server
docker run -d --name skywalking-oap \
  -p 11800:11800 -p 12800:12800 \
  apache/skywalking-oap-server:9.7.0

## 2. 安装 SkyWalking UI
docker run -d --name skywalking-ui \
  -p 8088:8080 \
  -e SW_OAP_ADDRESS=http://skywalking-oap:12800 \
  apache/skywalking-ui:9.7.0

## 3. Java Agent 配置
在启动参数中添加：
-javaagent:/path/to/skywalking-agent.jar
-Dskywalking.agent.service_name=mall-admin
-Dskywalking.collector.backend_service=localhost:11800
