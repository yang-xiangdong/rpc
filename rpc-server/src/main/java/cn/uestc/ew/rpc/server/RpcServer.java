package cn.uestc.ew.rpc.server;

import cn.uestc.ew.rpc.common.bean.RpcRequest;
import cn.uestc.ew.rpc.common.bean.RpcResponse;
import cn.uestc.ew.rpc.common.util.codec.RpcDecoder;
import cn.uestc.ew.rpc.common.util.codec.RpcEncoder;
import cn.uestc.ew.rpc.registry.ServiceRegistry;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.HashMap;
import java.util.Map;

/**
 * RPC 服务端，启动服务端监听，基于 Netty NIO 实现
 */
public class RpcServer implements ApplicationContextAware, InitializingBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(RpcServer.class);

    /**
     * 服务端地址
     */
    private final String serviceAddress;

    /**
     * 服务注册工具
     */
    private ServiceRegistry serviceRegistry;

    /**
     * 存放 服务名 与 服务对象 之间的映射关系
     */
    private final Map<String, Object> handlerMap = new HashMap<>();

    public RpcServer(String serviceAddress) {
        this.serviceAddress = serviceAddress;
    }

    public RpcServer(String serviceAddress, ServiceRegistry serviceRegistry) {
        this.serviceAddress = serviceAddress;
        this.serviceRegistry = serviceRegistry;
    }

    /**
     * 应用启动时，提供 Spring 提供的注解扫描，自动收集所有 RPC 服务者，
     * 将其注册到 {@link RpcServerHandler} 的 {@code handleMap}
     *
     * @param ctx Spring 上下文
     */
    @Override
    public void setApplicationContext(ApplicationContext ctx) {

        // 1. 扫描带有 RpcService 注解的类
        Map<String, Object> serviceBeanMap = ctx.getBeansWithAnnotation(RpcService.class);
        if (MapUtils.isEmpty(serviceBeanMap)) return;

        // 2. 初始化 HandlerMap 对象
        for (Object serviceBean : serviceBeanMap.values()) {
            RpcService rpcService = serviceBean.getClass().getAnnotation(RpcService.class);
            String serviceName = rpcService.value().getName();
            String serviceVersion = rpcService.version();
            if (StringUtils.isNotEmpty(serviceVersion)) {
                serviceName += "-" + serviceVersion;
            }
            handlerMap.put(serviceName, serviceBean);
        }
    }

    /**
     * 服务端设置前置流程，如 Pipeline，注册服务到注册中心，启动监听等。
     * 基于 Netty NIO 实现网络通信
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            // 1. 创建并初始化 Netty 服务端 Bootstrap 对象
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup);
            bootstrap.channel(NioServerSocketChannel.class);
            bootstrap.childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                public void initChannel(SocketChannel channel) {
                    ChannelPipeline pipeline = channel.pipeline();
                    pipeline.addLast(new RpcDecoder(RpcRequest.class));     // 解码 RPC 请求
                    pipeline.addLast(new RpcEncoder<>(RpcResponse.class));  // 编码 RPC 响应
                    pipeline.addLast(new RpcServerHandler(handlerMap));     // 处理 RPC 请求
                }
            });
            bootstrap.option(ChannelOption.SO_BACKLOG, 1024);
            bootstrap.childOption(ChannelOption.SO_KEEPALIVE, true);
            // 2. 获取 RPC 服务器的 IP 地址与端口号并启动监听
            String[] addressArray = StringUtils.split(serviceAddress, ":");
            String ip = addressArray[0];
            int port = Integer.parseInt(addressArray[1]);
            ChannelFuture future = bootstrap.bind(ip, port).sync();
            // 3. 注册 RPC 服务到注册中心
            if (serviceRegistry != null) {
                for (String interfaceName : handlerMap.keySet()) {
                    serviceRegistry.register(interfaceName, serviceAddress);
                    LOGGER.debug("Register service: name={}, address={}", interfaceName, serviceAddress);
                }
            }
            LOGGER.debug("Server started on port {}", port);
            // 4. 关闭 RPC 服务器（阻塞）
            future.channel().closeFuture().sync();
        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }
}