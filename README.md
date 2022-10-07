# RPC Simple Framework ( With Samples )

## 1. 项目结构

### 1.1 Framework Modules

框架模块是去除样例程序的完整可发布的组件单元，发布形式为 JAR包，外部项目可通过引入此 JAR 的依赖，使用此框架。 包含模块：

- `rpc-common` ：提供基础的 JavaBean、工具类等
- `rpc-client` ：提供给客户端使用的依赖，如网络操作等
- `rpc-server` ：提供给服务端使用的依赖，如网络操作等
- `rpc-registry` ：提供服务注册和发现相关的功能


### 1.2 Sample Server

服务器模块，提供一个简单的服务端样例，发布形式为独立的 WEB 应用，即需要单独启动。包含模块：

- `sample-rpc-api` ：由服务端对外发布的可用服务接口列表（无实现）
- `sample-rpc-server` ：样例服务端的主程序，注册服务，接收客户端请求并处理


### 1.3 Sample Client

客户端模块，提供一个简单的客户端样例，发布形式为独立的 WEB 应用，即需要单独启动。包含模块：

- `sample-rpc-api` ：**（引用）** 服务端发布的可用服务接口列表
- `sample-rpc-client` ：样例客户端的主程序，发现服务并发起调用


### 1.4 模块依赖图

#### 1.4.1 整体视图
<div style="text-align:center">
<img alt="结构_模块依赖图" src="./doc/uml_png/arch_module_dependency.png">
</div>

#### 1.4.2 模块间依赖
<div style="text-align:center">
<img alt="结构_模块内部依赖图" src="./doc/uml_png/arch_module_dependency_inner.png">
</div>


## 2. 快速启动

### 2.1 引用依赖

对于只引用依赖，在自己客户端和服务端项目中使用，而不需要运行样例程序的，可直接删除项目内包含 `sample` 的一级文件夹。


### 2.2 测试样例程序

1. 启动服务端样例
2. 启动客户端样例
3. 触发客户端接口，让其进行远程调用