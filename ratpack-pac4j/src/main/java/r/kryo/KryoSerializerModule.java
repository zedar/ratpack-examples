package r.kryo;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.pool.KryoFactory;
import com.esotericsoftware.kryo.pool.KryoPool;
import com.google.common.reflect.TypeToken;
import com.google.inject.Provides;
import ratpack.guice.ConfigurableModule;

import javax.inject.Singleton;

public class KryoSerializerModule extends ConfigurableModule<KryoSerializerModule.Config> {

  @Override
  protected void configure() {

  }

  @Provides
  @Singleton
  KryoPool provideKryoPool() {
    KryoFactory kryoFactory = new KryoFactory() {
      @Override
      public Kryo create() {
        Kryo kryo = new Kryo();
        // configuration comes here
        return kryo;
      }
    };
    // build pool with soft references
    KryoPool kryoPool = new KryoPool.Builder(kryoFactory).softReferences().build();
    return kryoPool;
  }

  public static class Config {
    /**
     * Used to get kryo serializer configuration
     */
    public static final TypeToken<Config> KRYO_SERIALIZER_CONFIG = TypeToken.of(Config.class);
  }
}
