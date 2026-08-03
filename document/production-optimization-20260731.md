# 灵启商城生产优化说明（2026-07-31）

## 本轮已实现

- 商品图片按真实文件内容识别格式，拒绝伪造扩展名和非图片内容。
- JPEG/PNG上传时去除元数据，最长边自动限制为1920像素。
- 上传前检查像素总量，降低超大解码图片耗尽内存的风险。
- 图片采用内容摘要文件名，相同图片自动复用，并支持长期缓存。
- Nginx可直接读取本地商品图片，避免图片流量占用Java后端线程。
- 商城与后台的带哈希静态资源缓存365天，入口HTML保持不缓存。
- 下单、余额支付、余额转账、提现增加30秒防重复提交。
- 防重复键仅存储摘要，不再包含明文登录Token。
- 备份前检查剩余磁盘空间，清理中断产生的过期临时目录。
- 支持通过`OFFSITE_BACKUP_DIR`把校验后的备份复制到异地挂载目录。

## 生产可调参数

```text
SHOP_MEDIA_STORAGE_DIR=/opt/lingqimall/uploads/products
SHOP_MEDIA_MAX_DIMENSION=1920
SHOP_MEDIA_MAX_PIXELS=25000000
SHOP_MEDIA_JPEG_QUALITY=0.82
MIN_FREE_MB=2048
RETENTION_DAYS=14
OFFSITE_BACKUP_DIR=
OFFSITE_RETENTION_DAYS=30
```

`OFFSITE_BACKUP_DIR`留空时不会额外复制。该目录只有挂载到另一块云盘、NAS或远程文件系统时，才属于真正的异地备份。

## 后续需要外部资源后才能完成

- 阿里云OSS或其他兼容对象存储的Bucket、地域、访问域名和最小权限凭证。
- CDN加速域名及对应HTTPS证书/DNS配置。
- 短信、企业微信或其他报警通道的Webhook/账号配置。
- 微信支付、支付宝支付的正式商户资料和回调配置。
