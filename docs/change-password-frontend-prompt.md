# 修改密码前端提示词

请基于现有系统实现一个“修改密码”页面或弹窗，技术栈沿用当前项目已有方案，不要擅自切换框架。

接口信息：
- 请求方式：`POST`
- 接口路径：`/auth/change-password`
- 接口说明：用户通过“用户名 + 原密码 + 新密码”完成密码修改；当前后端没有真实登录态绑定，所以本次页面按这个模式实现
- 请求体 JSON：

```json
{
  "username": "alice",
  "oldEncryptedPassword": "Base64_RSA_Encrypted_OldPassword",
  "newEncryptedPassword": "Base64_RSA_Encrypted_NewPassword"
}
```

配套接口：
- 获取 RSA 公钥：`GET /auth/password-public-key`
- 返回示例：

```json
{
  "publicKey": "Base64EncodedPublicKey"
}
```

交互要求：
- 表单包含 4 个字段：用户名、原密码、新密码、确认新密码
- 用户名默认可回填当前登录用户名；如果页面拿不到登录用户，也允许手动输入
- 原密码、新密码、确认新密码都使用密码框
- 前端校验：
  - 所有字段必填
  - 新密码长度至少 8 位
  - 新密码不能与原密码相同
  - 确认新密码必须和新密码一致
- 提交前先调用 `/auth/password-public-key` 获取公钥，使用 RSA 公钥加密原密码和新密码，再调用 `/auth/change-password`
- 不要把明文密码写入日志、localStorage、sessionStorage 或 URL
- 提交中按钮置灰并显示加载状态，防止重复提交
- 成功提示使用“密码修改成功，请使用新密码重新登录”
- 失败时展示后端返回的 `message`

错误码兼容：
- `AUTH_INVALID`：用户名或原密码错误
- `PASSWORD_UNCHANGED`：新密码不能与原密码相同
- `PASSWORD_DECRYPT_FAILED`：密码解密失败，请稍后重试

UI 要求：
- 风格简洁、正式，适合后台管理系统
- 优先做成卡片式表单，桌面端居中展示，移动端自适应
- 表单项之间留白明确，错误提示贴近字段展示
- 提交按钮文案使用“确认修改”
- 成功后清空密码字段

请直接产出：
1. 页面/组件代码
2. RSA 加密调用封装
3. 接口请求封装
4. 表单校验逻辑
5. 成功和失败反馈处理

如果当前项目已经有请求封装、表单组件、消息提示组件，请优先复用，不要重复造轮子。
