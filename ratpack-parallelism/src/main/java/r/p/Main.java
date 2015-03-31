package r.p;

import ratpack.health.HealthCheck;
import ratpack.health.HealthCheckHandler;
import ratpack.server.RatpackServer;

public class Main {
  public static void main(String... args) throws Exception {
    RatpackServer.start(server -> server
      .registryOf(registrySpec -> registrySpec
        .add(HealthCheck.of("eventLoopSize", execControl -> execControl
          .promiseOf(HealthCheck.Result.healthy()))))
      .handlers(chain -> chain
        .get("health-checks", new HealthCheckHandler())
        .get(ctx -> ctx.render("Hi!"))));
  }
}
