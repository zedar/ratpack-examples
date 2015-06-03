package r.pac4j;

import com.google.common.collect.ImmutableMap;
import com.google.common.reflect.ImmutableTypeToInstanceMap;
import org.pac4j.http.client.FormClient;
import org.pac4j.http.credentials.SimpleTestUsernamePasswordAuthenticator;
import org.pac4j.http.profile.UsernameProfileCreator;
import r.kryo.KryoSerializerModule;
import r.kryo.KryoValueSerializer;
import ratpack.groovy.template.MarkupTemplateModule;
import ratpack.guice.Guice;
import ratpack.pac4j.RatpackPac4j;
import ratpack.server.RatpackServer;
import ratpack.server.ServerConfig;
import ratpack.session.SessionModule;
import ratpack.session.clientside.ClientSideSessionModule;

import java.time.Duration;

import static ratpack.groovy.Groovy.groovyMarkupTemplate;

public class Main {
  public static void main(String... args) throws Exception {
    RatpackServer.start(server -> server
      .serverConfig(ServerConfig.findBaseDir("application.properties"))
        .registry(Guice.registry(b -> b
          .module(MarkupTemplateModule.class)
          .module(SessionModule.class)
          .module(ClientSideSessionModule.class, config -> {
            config.setSecretKey("aaaaaaaaaaaaaaaa");
            // required to share the same session between app instances (in cluster)
            config.setSecretToken("bbbbbb");
          })
        ))
        .handlers(chain -> chain
          .handler(RatpackPac4j.callback(new FormClient("/login", new SimpleTestUsernamePasswordAuthenticator(), new UsernameProfileCreator())))
          .get(ctx -> {
            ctx.redirect("admin");
          })
          .prefix("admin", p -> p
            .handler(RatpackPac4j.auth(FormClient.class))
            .get(ctx -> {
              ctx.render("admin page ACCESSED");
            })
          )
          .get("login", ctx -> {
            ctx.render(groovyMarkupTemplate("login.gtpl", model -> model
              .put("title", "Login")
              .put("action", "/auth-callback")
              .put("method", "get")
              .put("buttonText", "Login")
              .put("error", ctx.getRequest().getQueryParams().getOrDefault("error", ""))
            ));
          })
          .assets("public")
        )
    );
  }
}
