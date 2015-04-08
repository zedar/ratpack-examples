/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package r.p;

import r.p.handling.ExecHandler;
import r.p.handling.internal.ActionResultsRenderer;
import r.p.pattern.PatternsModule;
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
            .add(PatternsModule.class, config -> {
              config.setDefaultRetryCount(3);
            })
            .bindInstance(HealthCheck.of("eventLoopSize", execControl -> execControl
              .promiseOf(HealthCheck.Result.healthy())))
            .bindInstance(ActionResultsRenderer.class, new ActionResultsRenderer())
            .bindInstance(ResponseTimer.decorator())
        )
      )
      .handlers(chain -> chain
          .get("health-checks", new HealthCheckHandler())
          .get(ctx -> ctx.render("Hi!"))
          .get("api/:name", new ExecHandler())
      )
    );
  }
}
