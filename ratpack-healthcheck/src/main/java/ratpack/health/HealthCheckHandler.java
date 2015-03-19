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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.reflect.TypeToken;
import ratpack.exec.Promise;
import ratpack.handling.Context;
import ratpack.handling.Handler;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;

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

 * public class Example {
 *   public static class FooHealthCheck implements HealthCheck {
 *     public String getName() { return "foo"; }
 *     public Promise<HealthCheck.Result> check(ExecControl execControl) throws Exception {
 *       return execControl.promise(f -> {
 *         f.success(HealthCheck.Result.healthy());
 *       });
 *     }
 *   }
 *
 *   public static void main(String... args) throws Exception {
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
 *
 *     EmbeddedApp.of(s -> s
 *       .registryOf(r -> r
 *         .add(new HealthCheckResultsRenderer())
 *       )
 *       .registry(Guice.registry(b -> b
 *         .bind(FooHealthCheck.class)
 *         .bindInstance(HealthCheck.class, HealthCheck.of("bar", execControl -> {
 *           return execControl.promise(f -> {
 *             f.success(HealthCheck.Result.unhealthy("FAILED"));
 *           });
 *         }))
 *       ))
 *       .handler(r -> {
 *         return new HealthCheckHandler("bar");
 *       })
 *       ).test(httpClient -> {
 *         assertEquals("bar : FAILED", httpClient.getText());
 *       });
 *     }
 *   }
 * }</pre>
 *
 * @see ratpack.health.HealthCheck
 * @see ratpack.health.HealthCheckResults
 * @see ratpack.health.HealthCheckResultsRenderer
 */
public class HealthCheckHandler implements Handler {

  public static final String DEFAULT_NAME_TOKEN = "DEFAULT";
  public static final int DEFAULT_CONCURRENCY_LEVEL = 0;
  /**
   * If defined, run only health check with the given name
   */
  private final String name;

  /**
   * Define number of health checks (as promises) to be run in parallel.
   * 0 - infinite potential parallelism
   * 1 - serial, promises run one by one in sequence
   * 2 - max 2 at a time promises run in parallel
   * 3 - max 3 at a time promises run in parallel
   * ...
   */
  private final int concurrencyLevel;

  public HealthCheckHandler() {
    this(DEFAULT_NAME_TOKEN, DEFAULT_CONCURRENCY_LEVEL);
  }

  public HealthCheckHandler(String healthCheckName) {
    this(healthCheckName, DEFAULT_CONCURRENCY_LEVEL);
  }

  public HealthCheckHandler(int concurrencyLevel) {
    this(DEFAULT_NAME_TOKEN, concurrencyLevel);
  }

  public HealthCheckHandler(String healthCheckName, int concurrencyLevel) {
    this.name = healthCheckName;
    this.concurrencyLevel = concurrencyLevel;
  }

  @Override
  public void handle(Context context) throws Exception {
    SortedMap<String, HealthCheck.Result> hcheckResults = new ConcurrentSkipListMap<>();
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
      SortedMap<String, Promise<HealthCheck.Result>> promises = new ConcurrentSkipListMap<>();
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
        AtomicInteger countDown = new AtomicInteger(promises.size());
        AtomicInteger queuedPromises = new AtomicInteger(promises.size());

        context.promise(f -> {
          Map<String, Promise<HealthCheck.Result>> toExecPromises = new ConcurrentHashMap<String, Promise<HealthCheck.Result>>();
          promises.forEach((name, p) -> {
            boolean execParallel = false;
            if (concurrencyLevel <= 0) {
              // execute every health check in parallel
              execParallel = true;
              queuedPromises.decrementAndGet();
            } else if (concurrencyLevel == 1) {
              execParallel = false;
              queuedPromises.decrementAndGet();
            } else {
              System.out.println("==> PROMISE IN QUEUE: " + name);
              toExecPromises.put(name, p);
              if (queuedPromises.decrementAndGet() > 0 && toExecPromises.size() < concurrencyLevel) {
                return;
              }
              System.out.println("==> EXECUTE QUEUE: ");
              execParallel = true;
            }

            if (toExecPromises.size() > 0) {
              context.promiseOf(ImmutableMap.<String, Promise<HealthCheck.Result>>copyOf(toExecPromises)).then(map -> {
                AtomicInteger countDown2 = new AtomicInteger(map.size());
                context.promise(f2 -> {
                  map.forEach((name2, p2) -> {
                    context.exec().onComplete(execution2 -> {
                      System.out.println("==> PROMISE THEN: " + name2);
                      int i = countDown.decrementAndGet();
                      if (countDown2.decrementAndGet() == 0 || i == 0) {
                        f2.success(i == 0 ? Boolean.TRUE : Boolean.FALSE);
                      }
                    }).onError(throwable -> {
                      System.out.println("==> PROMISE onERROR: " + name2);
                      hcheckResults.put(name2, HealthCheck.Result.unhealthy(throwable));
                      int i = countDown.decrementAndGet();
                      if (countDown2.decrementAndGet() == 0 || i == 0) {
                        f2.success(i == 0 ? Boolean.TRUE : Boolean.FALSE);
                      }
                    }).start(execution2 -> {
                      System.out.println("==> EXECUTING PROMISE: " + name2);
                      p2.then(r -> {
                        hcheckResults.put(name2, r);
                      });
                    });
                  });
                }).then(finish -> {
                  if (finish == Boolean.TRUE) {
                    f.success(hcheckResults);
                  }
                });
              });

              toExecPromises.clear();

            } else {
              if (execParallel) {
                context.exec().onComplete(execution -> {
                  System.out.println("==> PROMISE THEN: " + name);
                  if (countDown.decrementAndGet() == 0) {
                    f.success(hcheckResults);
                  }
                }).onError(throwable -> {
                  System.out.println("==> PROMISE onERROR: " + name);
                  hcheckResults.put(name, HealthCheck.Result.unhealthy(throwable));
                  if (countDown.decrementAndGet() == 0) {
                    f.success(hcheckResults);
                  }
                }).start(execution -> {
                  System.out.println("==> EXECUTING PROMISE: " + name);
                  p.then(r -> {
                    hcheckResults.put(name, r);
                  });
                });
              } else {
                context.promise(f2 -> {
                  context.exec().onComplete(execution2 -> {
                    System.out.println("==> PROMISE THEN: " + name);
                    int i = countDown.decrementAndGet();
                    f2.success(i == 0 ? Boolean.TRUE : Boolean.FALSE);
                  }).onError(throwable -> {
                    System.out.println("==> PROMISE onERROR: " + name);
                    hcheckResults.put(name, HealthCheck.Result.unhealthy(throwable));
                    int i = countDown.decrementAndGet();
                    f2.success(i == 0 ? Boolean.TRUE : Boolean.FALSE);
                  }).start(execution -> {
                    System.out.println("==> EXECUTING PROMISE: " + name);
                    p.then(r -> {
                      hcheckResults.put(name, r);
                    });
                  });
                }).then(finish -> {
                  if (finish == Boolean.TRUE) {
                    f.success(hcheckResults);
                  }
                });
//                System.out.println("==> EXECUTING PROMISE: " + name);
//                p.onError(throwable -> {
//                  System.out.println("==> PROMISE onERROR: " + name);
//                  hcheckResults.put(name, HealthCheck.Result.unhealthy(throwable));
//                  if (countDown.decrementAndGet() == 0) {
//                    System.out.println("==> PROMISE onERROR FINISHED: " + name);
//                    f.success(hcheckResults);
//                  }
//                  System.out.println("==> PROMISE onERROR NOT FINISHED: " + name);
//                }).then(r -> {
//                  System.out.println("==> PROMISE THEN: " + name);
//                  hcheckResults.put(name, r);
//                  if (countDown.decrementAndGet() == 0) {
//                    f.success(hcheckResults);
//                  }
//                });
              }
            }
          });
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
