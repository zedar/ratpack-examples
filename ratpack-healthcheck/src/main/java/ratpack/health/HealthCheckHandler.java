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

package ratpack.health;

import com.google.common.collect.ImmutableSortedMap;
import com.google.common.reflect.TypeToken;
import ratpack.exec.Promise;
import ratpack.handling.Context;
import ratpack.handling.Handler;

import java.util.*;
import java.util.concurrent.CountDownLatch;

/**
 * Handler that runs and renders health checks executing in non-blocking mode
 * <p>
 * This handler queries {@code context} for either all or {@link ratpack.health.HealthCheck} with given token.
 * Then handler gets {@link Promise<HealthCheck.Result>} for every health check to execute.
 * Promises are executed in parallel and independently.
 * <p>
 * This handler should be bound to an application path, and most likely only for the GET method.
 * <pre class="java-chain-dsl">
 * import ratpack.health.HealthCheckHandler;
 *
 * chain instanceof ratpack.handling.Chain;
 * chain.get("health-checks/:name?", new HealthCheckHandler());
 * </pre>
 * <p>
 * The handler can render the result of all of the health checks or an individual health check, depending on the presence of a path token.
 * The path token provides the name of the health check to render.
 * If the path token is not present, all health checks will be rendered.
 * The token name to use can be provided as the construction argument to this handler.
 * The default token name is {@value #DEFAULT_NAME_TOKEN} and is used if the no-arg constructor is used.
 * <p>
 * If the token is present, the health check whose name is the value of the token will be rendered.
 * If no health check exists by that name, the client will receive a 404.
 * <p>
 * When a single health check is selected (by presence of the path token)
 * the {@link ratpack.health.HealthCheckResults} with one {@link ratpack.health.HealthCheck.Result}
 * is {@link Context#render(Object) rendered}.
 * When rendering all health checks a {@link ratpack.health.HealthCheckResults} is {@link Context#render(Object) rendered}.
 * <p>
 * The default {@link ratpack.health.HealthCheckResultsRenderer} is added to base registry. It renders in plain text.
 * If you wish to change the output, to JSON for example, you can register your own renderer for {@link ratpack.health.HealthCheckResults}.
 * <pre class="java">{@code
 * import ratpack.exec.ExecControl;
 * import ratpack.exec.Promise;
 * import ratpack.guice.Guice;
 * import ratpack.health.HealthCheck;
 * import ratpack.health.HealthCheckHandler;
 * import ratpack.health.HealthCheckResultsRenderer;
 * import ratpack.test.embed.EmbeddedApp;

 * import static org.junit.Assert.*;

 * public class JavaDocTests {
 *   public static class FooHealthCheck implements HealthCheck {
 *     public String getName() { return "foo"; }
 *     public Promise<HealthCheck.Result> check(ExecControl execControl) throws Exception {
 *       return execControl.promise(f -> {
 *         f.success(HealthCheck.Result.healthy());
 *       });
 *     }
 *   }
 *
 *   public static void mainTest(String... args) throws Exception {
 *     EmbeddedApp.of(s -> s
 *       .registryOf(r -> r
 *         .add(new HealthCheckResultsRenderer())
 *       )
 *       .registry(Guice.registry(b -> b
 *         .bind(FooHealthCheck.class)
 *       ))
 *       .handler(HealthCheckHandler.class)
 *     ).test(httpClient -> {
 *       assertEquals("foo : HEALTHY", httpClient.getText());
 *     });
 *   }
 * }
 * }</pre>
 */
public class HealthCheckHandler implements Handler {

  public static final String DEFAULT_NAME_TOKEN = "DEFAULT";

  /**
   * If defined, run only health check with the given name
   */
  private final String name;

  public HealthCheckHandler() {
    this(DEFAULT_NAME_TOKEN);
  }

  public HealthCheckHandler(String healthCheckName) {
    this.name = healthCheckName;
  }

  @Override
  public void handle(Context context) throws Exception {
    System.out.println("HEALTHCHECK HANDLER: " + name);
    SortedMap<String, HealthCheck.Result> hcheckResults = new TreeMap<String, HealthCheck.Result>();
    if (!name.equals(DEFAULT_NAME_TOKEN)) {
      Optional<HealthCheck> hcheck = context.first(TypeToken.of(HealthCheck.class), hc -> hc.getName().equals(name));
      if (hcheck.isPresent()) {
        try {
          Promise<HealthCheck.Result> promise = hcheck.get().check(context.getExecution().getControl());
          promise.onError(throwable -> {
            hcheckResults.put(hcheck.get().getName(), HealthCheck.Result.unhealthy(throwable));
            context.render(new HealthCheckResults(ImmutableSortedMap.<String, HealthCheck.Result>copyOfSorted(hcheckResults)));
          }).then(r -> {
            hcheckResults.put(hcheck.get().getName(), r);
            context.render(new HealthCheckResults(ImmutableSortedMap.<String, HealthCheck.Result>copyOfSorted(hcheckResults)));
          });
        }
        catch (Exception ex) {
          hcheckResults.put(hcheck.get().getName(), HealthCheck.Result.unhealthy(ex));
          context.render(new HealthCheckResults(ImmutableSortedMap.<String, HealthCheck.Result>copyOfSorted(hcheckResults)));
        }
      }
      else {
        context.clientError(404);
      }
    }
    else {
      SortedMap<String, Promise<HealthCheck.Result>> promises = new TreeMap<>();
      context.getAll(HealthCheck.class).forEach(hcheck -> {
        try {
          Promise<HealthCheck.Result> promise = hcheck.check(context.getExecution().getControl());
          promises.put(hcheck.getName(), promise);
        }
        catch (Exception ex) {
          hcheckResults.put(hcheck.getName(), HealthCheck.Result.unhealthy(ex));
        }
      });

      if (promises.size() == 0) {
        context.render(new HealthCheckResults(ImmutableSortedMap.<String, HealthCheck.Result>copyOfSorted(hcheckResults)));
      }
      else {
        // Execute promises in parallel
        CountDownLatch latch = new CountDownLatch(promises.size());
        context.promise(f -> {
          promises.forEach((name, p) -> {
            context.exec().onComplete(execution -> {
              System.out.println("HEALTH CHECK: " + name + " COUNTDOWN");
              latch.countDown();
            }).onError(throwable -> {
              System.out.println("HEALTH CHECK: " + name + " EXCEPTION AND COUNTDOWN");
              hcheckResults.put(name, HealthCheck.Result.unhealthy(throwable));
              latch.countDown();
            }).start(execution -> {
              System.out.println("==> HEALTH CHECK: " + name + " STARTED");
              p.then(r -> {
                System.out.println("HEALTH CHECK: " + name + " FINISHED");
                hcheckResults.put(name, r);
              });
            });
          });
          latch.await();
          f.success(hcheckResults);
        }).then(results -> {
          context.render(new HealthCheckResults(ImmutableSortedMap.<String, HealthCheck.Result>copyOfSorted(hcheckResults)));
        });
      }
    }
  }

  private static class HealthCheckPromise {
    public final String name;
    public final Promise<HealthCheck.Result> promise;

    public HealthCheckPromise(String name, final Promise<HealthCheck.Result> promise) {
      this.name = name;
      this.promise = promise;
    }
  }
}
