/**
 * 小程序运行时配置。
 *
 * 部署时唯一需要改的地方：
 * - API_BASE：oc-app 的公网 HTTPS 地址，必须以 /api/v1 结尾，并在支付宝小程序后台
 *   「服务器域名」白名单中登记该域名。
 * - ACCESS_CODE：与服务端注册码保持一致。服务端开启 DEV_VERIFY_CODE 时填该固定验证码；
 *   开启 REGISTER_INVITE_CODE 时填邀请码；两者都关闭时留空，改用算术验证码自动注册。
 *
 * 说明：小程序不持有任何模型密钥，所有 AI 调用都由后端完成。
 * APPID（2021007126674072）写入 mini.project.json；开发者工具导入时如未识别，手动关联同一 APPID 即可。
 */
module.exports = {
  API_BASE: 'https://api.offercoach.example.com/api/v1',
  ACCESS_CODE: '',
  REQUEST_TIMEOUT: 30000,
  DEFAULT_QUESTION_COUNT: 5,
  MAX_ANSWER_LENGTH: 2000
};
