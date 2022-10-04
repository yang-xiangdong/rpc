package cn.uestc.ew.rpc.client;

import cn.uestc.ew.rpc.common.bean.RpcRequest;
import cn.uestc.ew.rpc.common.bean.RpcResponse;
import cn.uestc.ew.rpc.registry.ServiceDiscovery;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Objects;
import java.util.UUID;

/**
 * RPC 代理，也就是 RPC Stub。为客户端提供服务调用、编解码及远程调用结果返回，
 * 屏蔽了远程过程调用涉及的网络通信。
 */
public class RpcClientProxy {

    private static final Logger LOGGER = LoggerFactory.getLogger(RpcClientProxy.class);

    /**
     * 远程服务地址
     */
    private String serviceAddress;

    /**
     * 服务发现工具
     */
    private ServiceDiscovery serviceDiscovery;

    public RpcClientProxy(String serviceAddress) {
        this.serviceAddress = serviceAddress;
    }

    public RpcClientProxy(ServiceDiscovery serviceDiscovery) {
        this.serviceDiscovery = serviceDiscovery;
    }

    /**
     * 根据需要访问的 API 接口类，创建代理服务对象，使用默认版本号（""）。
     *
     * @see RpcClientProxy#create(Class, String)
     */
    public <T> T create(final Class<?> interfaceClass) {
        return create(interfaceClass, StringUtils.EMPTY);
    }

    /**
     * 根据需要访问的 API 接口类及服务版本号，创建代理服务对象。
     *
     * <p>此接口一般由服务方提供，并不是真正的服务对象，而是对外发布的一个
     * 函数标识符与真正的服务对象相一致的空的代理接口。</p>
     *
     * @param interfaceClass 需要访问的 API 接口类，用于获取服务相关的信息如地址、端口等
     * @param serviceVersion 需要访问的服务版本号
     * @return 目标服务代理接口
     * @param <T> 需要访问的 API 接口类
     */
    @SuppressWarnings("unchecked")
    public <T> T create(final Class<?> interfaceClass, final String serviceVersion) {
        // 创建动态代理对象
        return (T) Proxy.newProxyInstance(
                interfaceClass.getClassLoader(),
                new Class<?>[]{interfaceClass},
                new RpcInvocationHandler(interfaceClass, serviceVersion));
    }

    /**
     * RPC 服务代理处理器，用于规定代理对象如何处理服务调用及返回，每次发起
     * 服务调用，都会动态创建一个服务代理对象。而代理对象由于访问的服务不同，
     * 需要不同的代理处理器，来完成自己需要执行的操作。
     */
    private class RpcInvocationHandler implements InvocationHandler {

        /**
         * 本次请求需要访问的 API 接口类
         */
        private final Class<?> interfaceClass;

        /**
         * 本次请求需要访问的 API 版本号
         */
        private final String serviceVersion;

        RpcInvocationHandler (final Class<?> interfaceClass, final String serviceVersion) {
            this.interfaceClass = interfaceClass;
            this.serviceVersion = serviceVersion;
        }

        /**
         * 此方法规定了 RPC 服务代理类的执行逻辑
         *
         * <p>RPC 框架下，客户端代理接收客户发起的服务调用请求，为其发起远程调用，
         * 屏蔽远程通信面临的问题，其顺序处理流程如下：</p>
         * <ol>
         *     <li>根据客户端参数查找远程服务的地址</li>
         *     <li>创建请求体并发起网络请求</li>
         *     <li>接收并处理返回结果，将其返回给客户端</li>
         * </ol>
         *
         * @return RPC 服务代理类的执行结果，正常情况下应该是调用远程服务返回的内容，
         *         被正确解码后的结果
         * @throws Exception 代理类执行过程中可能出现的任何异常，如网络通信异常等
         */
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Exception {

            // 1. 创建 RPC 请求对象并设置请求属性
            RpcRequest request = new RpcRequest();
            request.setRequestId(UUID.randomUUID().toString());
            request.setInterfaceName(method.getDeclaringClass().getName());
            request.setMethodName(method.getName());
            request.setParameterTypes(method.getParameterTypes());
            request.setParameters(args);
            request.setServiceVersion(serviceVersion);

            // 2. 获取 RPC 服务地址
            if (serviceDiscovery != null) {
                String serviceName = interfaceClass.getName();
                if (StringUtils.isNotEmpty(serviceVersion)) {
                    serviceName += "-" + serviceVersion;
                }
                serviceAddress = serviceDiscovery.discover(serviceName);
                LOGGER.debug("Discover Service: name={}, address={}.", serviceName, serviceAddress);
            }
            if (StringUtils.isEmpty(serviceAddress)) {
                throw new RuntimeException("Server address is empty");
            }

            // 3. 从 RPC 服务地址中解析主机名与端口号
            String[] array = StringUtils.split(serviceAddress, ":");
            String host = array[0];
            int port = Integer.parseInt(array[1]);

            // 4. 创建 RPC 客户端对象并发送 RPC 请求
            RpcClient client = new RpcClient(host, port);
            long time = System.currentTimeMillis();
            RpcResponse response = client.send(request);
            LOGGER.debug("Time usage of remote process call: {}ms", System.currentTimeMillis() - time);
            if (response == null) {
                throw new RuntimeException("Response is null");
            }

            // 5. 返回 RPC 响应结果
            if (Objects.nonNull(response.getException())) {
                throw response.getException();
            }

            return response.getResult();
        }
    }
}