package cn.uestc.ew.rpc.server;

import cn.uestc.ew.rpc.common.bean.RpcRequest;
import cn.uestc.ew.rpc.common.bean.RpcResponse;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import net.sf.cglib.reflect.FastClass;
import net.sf.cglib.reflect.FastMethod;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * RPC 服务端处理器，规定了服务端收到远程调用请求时的处理逻辑，
 * 即对于 {@link RpcServer} 收到的调用请求，由当前类负责处
 * 理，也就是执行真正的服务调用并返回原始结果。
 */
public class RpcServerHandler extends SimpleChannelInboundHandler<RpcRequest> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RpcServerHandler.class);

    /**
     * 当前服务器中，服务名称（服务类名称-服务版本号）与服务类（Service Bean）的映射
     */
    private final Map<String, Object> handlerMap;

    public RpcServerHandler(Map<String, Object> handlerMap) {
        this.handlerMap = handlerMap;
    }

    /**
     * Netty 提供的触发接口，在客户端的输入到达后，经过一系列的前置处理，
     * 取得 {@link RpcRequest} 并调用当前方法，执行处理逻辑
     */
    @Override
    public void channelRead0(final ChannelHandlerContext ctx, RpcRequest request) {
        // 1. 创建并初始化 RPC 响应对象
        RpcResponse response = new RpcResponse();
        response.setRequestId(request.getRequestId());
        try {
            // 2. 服务端执行本地服务调用
            Object result = handle(request);
            // 3. 返回调用结果
            response.setResult(result);
        } catch (Exception e) {
            LOGGER.error("Error occurred during local procedure call", e);
            response.setException(e);
        }
        // 4. 写入 RPC 响应对象并自动关闭连接
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    /**
     * 自定义服务端本地过程调用逻辑，处理流程如下：
     * <ol>
     *     <li>获取提供服务的 Bean 对象</li>
     *     <li>反射调用其指定方法（RpcRequest 指定）</li>
     * </ol>
     *
     * @param request 客户端调用信息，包括调用者身份、目标服务及参数等
     * @return 服务端执行本地调用后的原始返回结果
     * @throws Exception 反射调用时可能出现的任何异常
     */
    private Object handle(RpcRequest request) throws Exception {
        // 获取服务对象
        String serviceName = request.getInterfaceName();
        String serviceVersion = request.getServiceVersion();
        if (StringUtils.isNotEmpty(serviceVersion)) {
            serviceName += "-" + serviceVersion;
        }
        Object serviceBean = handlerMap.get(serviceName);
        if (serviceBean == null) {
            throw new RuntimeException(String.format("Can not find service bean by service name: %s", serviceName));
        }
        // 获取反射调用所需的参数
        Class<?> serviceClass = serviceBean.getClass();
        String methodName = request.getMethodName();
        Class<?>[] parameterTypes = request.getParameterTypes();
        Object[] parameters = request.getParameters();
        // 使用 CGLib 执行反射调用
        FastClass serviceFastClass = FastClass.create(serviceClass);
        FastMethod serviceFastMethod = serviceFastClass.getMethod(methodName, parameterTypes);
        return serviceFastMethod.invoke(serviceBean, parameters);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOGGER.error("Server exception caught", cause);
        ctx.close();
    }
}