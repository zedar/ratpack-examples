package r.kryo;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.pool.KryoPool;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.util.CharsetUtil;
import ratpack.registry.Registry;
import ratpack.session.clientside.ValueSerializer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.nio.CharBuffer;
import java.util.Base64;
import java.util.Objects;

public class KryoValueSerializer implements ValueSerializer {
  private static final Base64.Encoder ENCODER = Base64.getUrlEncoder();
  private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

  @Override
  public ByteBuf serialize(Registry registry, ByteBufAllocator bufAllocator, Object value) throws Exception {
    Objects.requireNonNull(value);
    KryoPool kryoPool = registry.get(KryoPool.class);
    Kryo kryo = kryoPool.borrow();
    try {
      ByteArrayOutputStream stream = new ByteArrayOutputStream();
      Output output = new Output(stream);
      kryo.writeClassAndObject(output, value);
      output.close();
      byte[] bytes = stream.toByteArray();
      String encoded = ENCODER.encodeToString(bytes);
      return ByteBufUtil.encodeString(bufAllocator, CharBuffer.wrap(encoded), CharsetUtil.UTF_8);
    } catch (Exception ex) {
      throw ex;
    } finally {
      kryoPool.release(kryo);
    }
  }

  @Override
  public Object deserialize(Registry registry, String value) throws Exception {
    if (value == null || value.isEmpty()) {
      return null;
    }
    KryoPool kryoPool = registry.get(KryoPool.class);
    Kryo kryo = kryoPool.borrow();
    try {
      byte[] bytes = DECODER.decode(value);
      ByteArrayInputStream stream = new ByteArrayInputStream(bytes);
      Input input = new Input(stream);
      Object obj = kryo.readClassAndObject(input);
      input.close();
      return obj;
    } catch (Exception ex) {
      throw ex;
    } finally {
      kryoPool.release(kryo);
    }
  }
}
