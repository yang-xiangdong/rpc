package cn.uestc.ew.rpc.client;

import cn.uestc.ew.rpc.common.bean.RpcRequest;
import cn.uestc.ew.rpc.common.bean.RpcResponse;
import cn.uestc.ew.rpc.common.util.codec.RpcDecoder;
import cn.uestc.ew.rpc.common.util.codec.RpcEncoder;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RPC 客户端，用于 RPC 服务代理发起远程调用时，处理 Netty 相关的
 * 网络调用逻辑
 */
public class RpcClient extends SimpleChannelInboundHandler<RpcResponse> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RpcClient.class);

    /**
     * 远程服务地址
     */
    private final String host;

    /**
     * 远程服务端口
     */
    private final int port;

    /**
     * 本次请求远程服务收到的响应体
     */
    private RpcResponse response;

    public RpcClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, RpcResponse response) {
        this.response = response;
    }

    /**
     * 向远程服务发起请求，基于 Netty NIO 实现的网络通信
     *
     * @param request RPC 请求体
     * @return RPC 响应体
     * @throws Exception 网络通信过程中可能出现的任何异常
     */
    public RpcResponse send(RpcRequest request) throws Exception {
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            // 1. 创建并初始化 Netty 客户端 Bootstrap 对象
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group);
            bootstrap.channel(NioSocketChannel.class);
            bootstrap.handler(new ChannelInitializer<SocketChannel>() {
                @Override
                public void initChannel(SocketChannel channel) {
                    ChannelPipeline pipeline = channel.pipeline();
                    pipeline.addLast(new RpcEncoder<RpcRequest>(RpcRequest.class)); // 编码 RPC 请求
                    pipeline.addLast(new RpcDecoder(RpcResponse.class));            // 解码 RPC 响应
                    pipeline.addLast(RpcClient.this);       // 处理 RPC 响应
                }
            });
            bootstrap.option(ChannelOption.TCP_NODELAY, true);
            // 2. 连接 RPC 服务器
            ChannelFuture future = bootstrap.connect(host, port).sync();
            // 3. 写入 RPC 请求数据并关闭连接
            Channel channel = future.channel();
            channel.writeAndFlush(request).sync();
            channel.closeFuture().sync();
            // 4. 返回 RPC 响应对象
            return response;
        } finally {
            group.shutdownGracefully();
        }
    }
}