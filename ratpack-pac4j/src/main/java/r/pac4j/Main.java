package r.pac4j;

import com.google.common.collect.ImmutableMap;
import com.google.common.reflect.ImmutableTypeToInstanceMap;
import org.pac4j.http.client.FormClient;
import org.pac4j.http.credentials.SimpleTestUsernamePasswordAuthenticator;
import org.pac4j.http.profile.UsernameProfileCreator;
import ratpack.groovy.template.MarkupTemplateModule;
import ratpack.guice.Guice;
import ratpack.pac4j.Pac4jModule;
import ratpack.server.RatpackServer;
import ratpack.server.ServerConfig;
import ratpack.session.SessionModule;
import ratpack.session.clientside.ClientSideSessionsModule;
import ratpack.session.clientside.serializer.JavaValueSerializer;
import ratpack.session.store.MapSessionsModule;

import java.nio.file.Path;
import java.time.Duration;

import static ratpack.groovy.Groovy.groovyMarkupTemplate;

public class Main {
  public static void main(String... args) throws Exception {
    RatpackServer.start(server -> server
        .serverConfig(ServerConfig.findBaseDir("application.properties"))
        .registry(Guice.registry(b -> b
            // --- Add cookie session with java serialization of values of session entries
            .module(ClientSideSessionsModule.class, config -> {
              config.setSecretKey("aaaaaaaaaaaaaaaa");
              // required to share the same session between app instances (in cluster)
              config.setSecretToken("bbbbbb");
              // IMPORTANT: JavaValueSerializer is set up as default. And it works very well with pac4j UserProfile class serialization.
            })
            // --- Add server side sessions with in-memory storage (works with one server instance only)
            //.add(SessionModule.class)
            //.add(new MapSessionsModule(10, 5))
            .module(new Pac4jModule<>(
              new FormClient("/login", new SimpleTestUsernamePasswordAuthenticator(), new UsernameProfileCreator()),
              new PathAuthorizer()
            ))
            .module(MarkupTemplateModule.class)
        ))
        .handlers(chain -> chain
            .get(ctx -> {
              ctx.redirect("admin");
            })
            .get("admin", ctx -> {
              ctx.render("admin page ACCESSED");
            })
            .get("login", ctx -> {
              ctx.render(groovyMarkupTemplate("login.gtpl", model -> model
                  .put("title", "Login")
                  .put("action", "/pac4j-callback")
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
