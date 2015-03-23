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
import ratpack.exec.Fulfiller;
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
   * 0 - infinite potential parallelism - up to the event loop number of threads
   * 1 - serial, promises run one by one in sequence
   * 2 - max 2 at a time promises run in parallel
   * 3 - max 3 at a time promises run in parallel
   * ...
   */
  private final int concurrencyLevel;

  /**
   * Default constructor with {@code concurrencyLevel} set to 0 (infinite potential parallelism) and
   * undefined health check name.
   */
  public HealthCheckHandler() {
    this(DEFAULT_NAME_TOKEN, DEFAULT_CONCURRENCY_LEVEL);
  }

  /**
   * Health check of the given name will be executed. If not registered HTTP 404 error is reported.
   * @param healthCheckName health check name
   */
  public HealthCheckHandler(String healthCheckName) {
    this(healthCheckName, DEFAULT_CONCURRENCY_LEVEL);
  }

  /**
   * Execute all registered health check with the given {@link ratpack.health.HealthCheckHandler#concurrencyLevel}.
   *
   * @param concurrencyLevel
   */
  public HealthCheckHandler(int concurrencyLevel) {
    this(DEFAULT_NAME_TOKEN, concurrencyLevel);
  }

  /**
   * Execute health check of the given name (if different than {@link ratpack.health.HealthCheckHandler#DEFAULT_NAME_TOKEN} and
   * given {@code concurrencyLevel}.
   *
   * @param healthCheckName
   * @param concurrencyLevel
   */
  protected HealthCheckHandler(String healthCheckName, int concurrencyLevel) {
    this.name = healthCheckName;
    this.concurrencyLevel = concurrencyLevel;
  }

  /**
   * Run either individual health check by name or all health checks.
   * The {@link ratpack.health.HealthCheckHandler#concurrencyLevel} determines parallelism in health check execution.
   *
   * @param context request context and exec control as well
   * @throws Exception
   */
  @Override
  public void handle(Context context) throws Exception {
    if (!name.equals(DEFAULT_NAME_TOKEN)) {
      handleByName(context, name);
    }
    else {
      handleAll(context);
    }
  }

  /**
   * Run health check with the given name. If it is not registered HTTP 404 is send to the client
   * All exceptions from health check are wrapped in unhealthy result with error=Exception.
   * @param context request context
   * @param name health check name
   * @throws Exception
   */
  private void handleByName(Context context, String name) throws Exception {
    if (name == null || "".equals(name) || DEFAULT_NAME_TOKEN.equals(name)) {
      context.clientError(404);
      return;
    }
    SortedMap<String, HealthCheck.Result> hcheckResults = new ConcurrentSkipListMap<>();
    Optional<HealthCheck> hcheck = context.first(TypeToken.of(HealthCheck.class), hc -> hc.getName().equals(name));
    if (!hcheck.isPresent()) {
      context.clientError(404);
      return;
    }
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

  /**
   * Run all health checks. The concurrencyLevel determines how many health checks could be run in parallel.
   * If concurrencyLevel>1 and there is more health checks than concurrencyLevel they are grouped in chanks of size
   * equal to concurrencyLevel.
   * concurrencyLevel equal to 0 runs all health checks in parallel.
   * IMPORTANT: the real concurrency level depends on the event loop size. Event loop sets the higher limit of concurrency.
   * If event loop size is 8 ({@link ratpack.exec.internal.DefaultExecController#DefaultExecController}), the 9th health check will wait for the first.
   *
   * @param context request context and exec control as well
   * @throws Exception
   */
  private void handleAll(Context context) throws Exception {
    SortedMap<String, HealthCheck.Result> hcheckResults = new ConcurrentSkipListMap<>();

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
      return;
    }

    context.promise(f -> {
      // execute promises in parallel based on the concurrencyLevel
      // count finished health checks. If all are done, render results
      AtomicInteger executedPromisesCountDown = new AtomicInteger(promises.size());
      // count health checks waiting for execution.
      AtomicInteger toExecPromisesCountDown = new AtomicInteger(promises.size());
      // collect health checks to execute in the next run. Used when concurrencyLevel > 1
      Map<String, Promise<HealthCheck.Result>> toExecPromises = new ConcurrentHashMap<>();

      promises.forEach((name, p) -> {
        boolean execParallel = false;
        if (concurrencyLevel <= 0) {
          // execute all health checks in parallel
          execParallel = true;
        } else if (concurrencyLevel == 1) {
          // execute promise by promise, in sequence
          execParallel = false;
        } else {
          // collect promises into group of concurrencyLevel size or into group of last promises to execute
          toExecPromises.put(name, p);
          if (toExecPromisesCountDown.decrementAndGet() > 0 && toExecPromises.size() < concurrencyLevel) {
            return;
          }
          // execute promises in group in parallel
          execParallel = true;
        }

        if (toExecPromises.size() > 0) {
          // promise of immutable map of promises to execute, controls construction and their parallel execution while allows
          // safe freeing of toExecPromises map.
          context.promiseOf(ImmutableMap.<String, Promise<HealthCheck.Result>>copyOf(toExecPromises)).then(map -> {
            // control finalization of parent promise
            AtomicInteger groupOfPromisesCountDown = new AtomicInteger(map.size());
            context.promise(f2 -> {
              map.forEach((name2, p2) -> {
                // execute promise p2 and check end condition: either last promise in group or last promise globally
                execPromiseWithEndCondition(context, name2, p2, f2, hcheckResults, groupOfPromisesCountDown, executedPromisesCountDown);
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
            // execute promise and if last promise globally, return health check results
            execPromiseWithEndResult(context, name, p, f, hcheckResults, executedPromisesCountDown);
          } else {
            context.promise(f2 -> {
              // execute promise p and check end condition: if last promise globally
              execPromiseWithEndCondition(context, name, p, f2, hcheckResults, null, executedPromisesCountDown);
            }).then(finish -> {
              if (finish == Boolean.TRUE) {
                f.success(hcheckResults);
              }
            });
          }
        }
      });
    }).then(results -> {
      context.render(new HealthCheckResults(ImmutableSortedMap.<String, HealthCheck.Result>copyOfSorted(hcheckResults)));
    });
  }

  /**
   * Execute promise and if last ({@code executedPromisesCountDown} is 0) return sorted map of health check results.
   * @param context execution context
   * @param name health check name
   * @param promise health check promise with calculation to be run
   * @param fulfiller fulfiller of an asynchronous promise
   * @param hcheckResults sorted map of health check results
   * @param executedPromisesCountDown counter of executed promises (counts down)
   */
  private void execPromiseWithEndResult(
          Context context,
          String name,
          Promise<HealthCheck.Result> promise,
          Fulfiller<Object> fulfiller,
          SortedMap<String, HealthCheck.Result> hcheckResults,
          AtomicInteger executedPromisesCountDown) {

    context.exec().onComplete(execution -> {
      if (executedPromisesCountDown.decrementAndGet() == 0) {
        fulfiller.success(hcheckResults);
      }
    }).onError(throwable -> {
      hcheckResults.put(name, HealthCheck.Result.unhealthy(throwable));
      if (executedPromisesCountDown.decrementAndGet() == 0) {
        fulfiller.success(hcheckResults);
      }
    }).start(execution -> {
      promise.then(r -> {
        hcheckResults.put(name, r);
      });
    });
  }

  /**
   * Execute promise and check end condition. Report Boolean as result.
   * End condition: finish outer promise if {@code groupOfPromisesCountDown} is given and its decremented value is 0 or
   * decremented {@code executedPromisesCountDown} is 0, return Boolean.TRUE if there is nothing to left to do, so if
   * {@code executedPromisesCountDown} is 0.
   * @param context execution context
   * @param name health check name
   * @param promise health check promise with calculation to be run
   * @param fulfiller fulfiller of an asynchronous promise
   * @param hcheckResults sorted map of health check results
   * @param groupOfPromisesCountDown counter of executed promises in the given group (counts down)
   * @param executedPromisesCountDown counter of executed promises (counts down)
   */
  private void execPromiseWithEndCondition(
          Context context,
          String name,
          Promise<HealthCheck.Result> promise,
          Fulfiller<Object> fulfiller,
          SortedMap<String, HealthCheck.Result> hcheckResults,
          AtomicInteger groupOfPromisesCountDown, AtomicInteger executedPromisesCountDown) {

    context.exec().onComplete(execution -> {
      int i = executedPromisesCountDown != null ? executedPromisesCountDown.decrementAndGet() : 0;
      if (groupOfPromisesCountDown != null) {
        if (groupOfPromisesCountDown.decrementAndGet() == 0 || i == 0) {
          fulfiller.success(i == 0 ? Boolean.TRUE : Boolean.FALSE);
        }
      }
      else {
        fulfiller.success(i == 0 ? Boolean.TRUE : Boolean.FALSE);
      }
    }).onError(throwable -> {
      hcheckResults.put(name, HealthCheck.Result.unhealthy(throwable));
      int i = executedPromisesCountDown != null ? executedPromisesCountDown.decrementAndGet() : 0;
      if (groupOfPromisesCountDown != null) {
        if (groupOfPromisesCountDown.decrementAndGet() == 0 || i == 0) {
          fulfiller.success(i == 0 ? Boolean.TRUE : Boolean.FALSE);
        }
      }
      else {
        fulfiller.success(i == 0 ? Boolean.TRUE : Boolean.FALSE);
      }
    }).start(execution -> {
      promise.then(r -> {
        hcheckResults.put(name, r);
      });
    });
  }
}
