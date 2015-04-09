package r.cs;

import ratpack.guice.Guice;
import ratpack.server.RatpackServer;
import ratpack.session.clientside.ClientSideSessionsModule;
import ratpack.session.store.SessionStorage;

public class Main {
  public static void main(String... args) throws Exception {
    RatpackServer.start(server -> server
        .registry(Guice.registry(b -> b
          .add(ClientSideSessionsModule.class, config -> {
            config.setSessionName("my-session");
            config.setSecretToken("cookiesession");
          })
        ))
        .handlers(chain -> chain
          .get(ctx -> {
            SessionStorage sessionStorage = ctx.getRequest().get(SessionStorage.class);
            ctx.render(sessionStorage.getOrDefault("value", "NOT SET"));
          })
          .get("set/:value", ctx -> {
            SessionStorage sessionStorage = ctx.getRequest().get(SessionStorage.class);
            String value = ctx.getPathTokens().get("value");
            if (value == null || "".equals(value)) {
              ctx.render(sessionStorage.getOrDefault("value", "NOT SET"));
              return;
            } else {
              String prevValue = (String)sessionStorage.getOrDefault("value", "NOT SET");
              sessionStorage.put("value", value);
              ctx.render("VALUE SETO TO: " + value + " FROM: " + prevValue);
            }
          })
        )
    );
  }
}
