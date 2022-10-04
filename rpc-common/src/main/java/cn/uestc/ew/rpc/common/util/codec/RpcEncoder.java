package cn.uestc.ew.rpc.common.util.codec;

import cn.uestc.ew.rpc.common.util.SerializationUtils;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * 自定义编码器，基于 Netty 实现
 *
 * @param <T> 当前编码器对象所处理的类型
 */
public class RpcEncoder extends MessageToByteEncoder {

    /**
     * 当前编码器对象所处理的类型
     */
    private final Class<?> genericClass;

    public RpcEncoder(Class<?> genericClass) {
        this.genericClass = genericClass;
    }

    /**
     * 自定义编码方法
     *
     * @param ctx   the {@link ChannelHandlerContext} which this {@link MessageToByteEncoder} belongs to
     * @param in    the message to encode
     * @param out   the {@link ByteBuf} into which the encoded message will be written
     */
    @Override
    public void encode(ChannelHandlerContext ctx, Object in, ByteBuf out) {
        if (genericClass.isInstance(in)) {
            byte[] data = SerializationUtils.serialize(in);
            out.writeInt(data.length);
            out.writeBytes(data);
        }
    }
}