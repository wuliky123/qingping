# 本地打包失败记录（2026-11-08，沙箱环境限制，非代码问题）

## 尝试 1：系统 Gradle 生成 Wrapper
命令：`/tmp/gradle-8.7/bin/gradle wrapper --gradle-version 8.7 --no-daemon`
结果：失败。关键行：
```
FAILURE: Build failed with an exception.
* What went wrong:
The first result from the daemon was empty. The daemon process may have died or a non-daemon process is reusing the same port.
* Exception is:
org.gradle.launcher.daemon.client.DaemonInitialConnectException: The first result from the daemon was empty.
```
原因：Gradle 守护进程走本机回环 TCP，被沙箱拦截（2026-11-06 单别跑打包时同样失败过 20 次）。

## 尝试 2：Gradle Wrapper 打包
命令：`./gradlew assembleDebug --no-daemon`
结果：失败（exit=1）。关键行：
```
Downloading https://services.gradle.org/distributions/gradle-8.7-bin.zip
Exception in thread "main" javax.net.ssl.SSLException: (internal_error)
  Unexpected error: java.security.InvalidAlgorithmParameterException:
  the trustAnchors parameter must be non-empty
    at org.gradle.wrapper.Install.forceFetch(SourceFile:2)
    at org.gradle.wrapper.GradleWrapperMain.main(SourceFile:67)
```
原因：本机 JDK 的 CA 证书库（cacerts）为空，Wrapper 下载 Gradle 发行版时 SSL 握手失败。
Gradle 发行版本身已用 curl 下好（/tmp/gradle-8.7-bin.zip，134,184,980 字节），但按任务要求不再重试。

## 结论
本地两次尝试均因沙箱环境限制失败，没有产出 APK，也没有伪造 APK。
备用路线：把工程推到 GitHub，用 `.github/workflows/android.yml` 打 debug APK（见 README 方法 A）。
