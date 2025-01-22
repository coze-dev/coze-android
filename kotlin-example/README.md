# coze-kotlin

一个基于火山引擎 RTC 技术的 Android 实时语音演示项目，使用 Kotlin 语言开发。

## 快速入门

详细的快速入门指南请参考：[Coze Android（Kotlin） 实时语音快速入门](https://bytedance.larkoffice.com/docx/P5x8dC8qPoUl2Vx1cAUcHAvSnXc)

快速开始步骤：

1. 环境准备

   - 确保满足环境要求
   - 安装必要的开发工具

2. 项目配置

   - 克隆项目并安装依赖
   - 配置 API 信息

3. 运行项目
   - 使用 Android Studio 打开项目
   - 在真机上进行调试

详细步骤请参考上述快速入门文档。

## 功能特性

- 实时音视频通话
- 基于 火山引擎 RTC 的实时语音对话
- 扣子 OpenAPI 接口集成

## 环境要求

- Android 7.0+
- Android Studio 2024+
- Kotlin 1.9+

## 使用说明

1. 启动应用后，确保已正确配置
2. 按照界面提示进行实时对话操作
3. 可以通过界面控制音视频开关等功能

## 注意事项

- 请在 [strings.xml](./src/main/res/values/strings.xml) 中配置以下参数:
  - `coze_access_token`: Coze 平台的访问令牌
  - `base_url`: Coze API 的基础 URL
  - `bot_id`: 机器人 ID
  - `voice_id`: 语音 ID
- 请确保在使用前正确配置 [strings.xml](./src/main/res/values/strings.xml) 文件
- 务必使用真机调试
- 确保设备已授权摄像头和麦克风权限

## 贡献指南

欢迎提交 Issue 和 Pull Request 来帮助改进项目。

## 许可证

本项目基于 MIT 许可证开源。

## 联系方式

如有问题，请通过 Issue 与我们联系。
