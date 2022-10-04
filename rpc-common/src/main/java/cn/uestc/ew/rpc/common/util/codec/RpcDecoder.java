package cn.uestc.ew.rpc.common.util.codec;

import cn.uestc.ew.rpc.common.util.SerializationUtils;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

/**
 * 自定义解码器，基于 Netty 实现
 */
public class RpcDecoder extends ByteToMessageDecoder {

    /**
     * 当前解码器对象所处理的类型
     */
    private final Class<?> genericClass;

    public RpcDecoder(Class<?> genericClass) {
        this.genericClass = genericClass;
    }


    /**
     * 自定义解码方法
     *
     * @param ctx   the {@link ChannelHandlerContext} which this {@link ByteToMessageDecoder} belongs to
     * @param in    the {@link ByteBuf} from which to read data
     * @param out   the {@link List} to which decoded messages should be added
     */
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {

        if (in.readableBytes() < 4) {
            return;
        }
        in.markReaderIndex();
        int dataLength = in.readInt();  // 跳过前四个标识数据长度的字节
        if (in.readableBytes() < dataLength) {
            in.resetReaderIndex();
            return;
        }
        // 反序列化剩余数据
        byte[] data = new byte[dataLength];
        in.readBytes(data);
        out.add(SerializationUtils.deserialize(data, genericClass));
    }
}
