package r.p;

import r.p.handling.ExecHandler;
import r.p.handling.internal.ExecResultsRenderer;
import ratpack.guice.Guice;
import ratpack.handling.ResponseTimer;
import ratpack.health.HealthCheck;
import ratpack.health.HealthCheckHandler;
import ratpack.jackson.JacksonModule;
import ratpack.server.RatpackServer;

public class Main {
  public static void main(String... args) throws Exception {
    RatpackServer.start(server -> server
      .registry(Guice.registry(b -> b
            .add(JacksonModule.class, c -> c.prettyPrint(true))
            .bindInstance(HealthCheck.of("eventLoopSize", execControl -> execControl
              .promiseOf(HealthCheck.Result.healthy())))
            .bindInstance(ExecResultsRenderer.class, new ExecResultsRenderer())
            .bindInstance(ResponseTimer.decorator())
        )
      )
      .handlers(chain -> chain
          .get("health-checks", new HealthCheckHandler())
          .get(ctx -> ctx.render("Hi!"))
          .get("fanout", new ExecHandler())
      )
    );
  }
}
