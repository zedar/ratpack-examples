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
            config.setSecretKey("aaaaaaaaaaaaaaaa");
            //config.setSessionName("my-session-value");
            //config.setSecretToken("cookiesession");
            //config.setMacAlgorithm("HmacMD5");
          })
        ))
        .handlers(chain -> chain
          .get(ctx -> {
            SessionStorage sessionStorage = ctx.getRequest().get(SessionStorage.class);
            ctx.render(sessionStorage.getOrDefault("value", "NOT SET"));
          })
          .get("s", ctx -> {
            SessionStorage sessionStorage = ctx.getRequest().get(SessionStorage.class);
            StringBuilder stringBuilder = new StringBuilder();
            sessionStorage.forEach((attr, value) -> {
              stringBuilder.append("ATTR: ")
                .append(attr)
                .append(" VALUE: ")
                .append(value)
                .append(" || ");
            });
            ctx.render(stringBuilder.toString());
          })
          .get("m/set/:attr", ctx -> {
            // set large session size
            SessionStorage sessionStorage = ctx.getRequest().get(SessionStorage.class);
            String attr = ctx.getPathTokens().get("attr");
            String value = "";
            for (int i = 0; i < 1024; i++) {
              value += "ab";
            }
            sessionStorage.put(attr, value);
            ctx.render(value);
          })
          .get("m/get/:attr", ctx -> {
            SessionStorage sessionStorage = ctx.getRequest().get(SessionStorage.class);
            String attr = ctx.getPathTokens().get("attr");
            ctx.render(sessionStorage.get(attr));
          })
          .get("s/set/:attr/:value", ctx -> {
            SessionStorage sessionStorage = ctx.getRequest().get(SessionStorage.class);
            String attr = ctx.getPathTokens().get("attr");
            String value = ctx.getPathTokens().get("value");
            if (attr == null || "".equals(attr) || value == null || "".equals(value)) {
              ctx.render("ACTION IGNORED FOR: " + attr);
            } else {
              sessionStorage.put(attr, value);
              ctx.render("ATTR " + attr + " SET TO: " + sessionStorage.get(attr));
            }
          })
          .get("s/get/:attr", ctx -> {
            SessionStorage sessionStorage = ctx.getRequest().get(SessionStorage.class);
            String attr = ctx.getPathTokens().get("attr");
            if (attr == null || "".equals(attr)) {
              ctx.render("ATTR NOT FOUND: " + attr);
            } else {
              ctx.render("ATTR " + attr + " VALUE: " + sessionStorage.get(attr));
            }
          })
          .get("s/clear/:attr", ctx -> {
            SessionStorage sessionStorage = ctx.getRequest().get(SessionStorage.class);
            String attr = ctx.getPathTokens().get("attr");
            if (attr != null && !"".equals(attr)) {
              sessionStorage.remove(attr);
              ctx.render("REMOVED ATTR: " + attr);
            } else {
              ctx.render("ATTR NOT FOUND: " + attr);
            }
          })
          .get("set/:attr/:value", ctx -> {
            SessionStorage sessionStorage = ctx.getRequest().get(SessionStorage.class);
            String attr = ctx.getPathTokens().get("attr");
            String value = ctx.getPathTokens().get("value");
            if (attr == null || "".equals(attr)) {
              ctx.render("No attr defined");
              return;
            }
            sessionStorage.forEach((k, v) -> {
              System.out.println("SESSION KEY: " + k + " VALUE: " + v);
            });
            if (value == null || "".equals(value)) {
              sessionStorage.remove(attr);
              ctx.render("Attr: " + attr + " REMOVED");
            } else {
              if ("populate".equals(value)) {
                for (int i = 0; i < 1; i++) {
                  sessionStorage.put("attr" + i, "valueeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee" + i);
                }
                ctx.render("Attributes populated");
              } else {
                String prevValue = (String) sessionStorage.getOrDefault(attr, "NOT SET");
                sessionStorage.put(attr, value + "1234567890");
                ctx.render("Attr " + attr + " SET TO: " + value + " FROM: " + prevValue);
              }
            }
          })
        )
    );
  }
}
