# GnomishArmyKnife Backend

GnomishArmyKnife 后端工程，基于 Java 21、Spring Boot 3.4.x 和 Maven 多模块构建。

## 技术版本

| 组件 | 版本 / 说明 |
| --- | --- |
| JDK | 21 |
| Spring Boot | 3.4.1 |
| Maven Wrapper | 3.9.12 |
| MyBatis Plus | 3.5.9 |
| MyBatis Spring | 3.0.3 |

JDK 版本以根目录 `pom.xml` 中的 `java.version` 为准：

```xml
<java.version>21</java.version>
```

## 本地 JDK 说明

当前 GAK 工作区为了避免和电脑上的 JDK 8 环境冲突，把 JDK 21 安装在工作区内部：

```text
E:\AIWorkspace\GAK\tools\jdk-21
```

不要为了运行本后端工程强行修改系统级 `JAVA_HOME`。在 GAK 工作区中启动或验证后端时，优先使用工作区根目录脚本：

```powershell
cd E:\AIWorkspace\GAK
.\scripts\use-jdk21.ps1
.\scripts\backend-dev.ps1 -version
```

启动后端：

```powershell
cd E:\AIWorkspace\GAK
.\scripts\backend-dev.ps1
```

编译后端启动模块及依赖：

```powershell
cd E:\AIWorkspace\GAK
.\scripts\backend-dev.ps1 -pl gak-start -am -DskipTests compile
```

上述脚本只在当前 PowerShell 进程中临时设置：

```powershell
JAVA_HOME=E:\AIWorkspace\GAK\tools\jdk-21
```

因此不会影响系统默认 JDK 8，也不会影响其他项目。

## 单独使用后端仓库

如果只克隆了本后端仓库，而没有使用 GAK 顶层工作区，请自行安装 JDK 21，并在当前终端设置 `JAVA_HOME` 后再运行 Maven：

```powershell
$env:JAVA_HOME = "C:\Path\To\JDK21"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd -version
.\mvnw.cmd -pl gak-start -am -DskipTests compile
```

确认 Maven 输出中的 Java version 为 21 后再启动或编译。

## 项目结构

```text
.
|-- gak-common
|-- gak-framework
|-- gak-modules
|-- gak-start
|-- pom.xml
`-- mvnw.cmd
```

- `gak-start`: 后端启动模块。
- `gak-common`: 通用基础代码。
- `gak-framework`: 响应包装、异常处理、字典支持等框架能力。
- `gak-modules`: 业务模块集合。

## 常用命令

在 GAK 顶层工作区中：

```powershell
.\scripts\backend-dev.ps1 -version
.\scripts\backend-dev.ps1 -pl gak-start -am -DskipTests compile
.\scripts\backend-dev.ps1 test
```

在后端仓库目录中，且当前终端已经设置好 JDK 21：

```powershell
.\mvnw.cmd -version
.\mvnw.cmd -pl gak-start -am -DskipTests compile
.\mvnw.cmd test
```

