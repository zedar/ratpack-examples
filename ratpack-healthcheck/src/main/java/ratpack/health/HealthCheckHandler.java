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
import ratpack.exec.ExecControl;
import ratpack.exec.Promise;
import ratpack.handling.Context;
import ratpack.handling.Handler;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

/**
 * Handler that runs and renders health checks executing in non-blocking mode
 */
public class HealthCheckHandler implements Handler {

  public static final String DEFAULT_NAME = "undefined";

  /**
   * If defined, run only health check with the given name
   */
  private final String name;

  public HealthCheckHandler() {
    this(DEFAULT_NAME);
  }

  public HealthCheckHandler(String healthCheckName) {
    this.name = healthCheckName;
  }

  @Override
  public void handle(Context context) throws Exception {
    System.out.println("HEALTHCHECK HANDLER: " + name);
    SortedMap<String, HealthCheck.Result> hcheckResults = new TreeMap<String, HealthCheck.Result>();
    if (!name.equals(DEFAULT_NAME)) {
      Optional<HealthCheck> hcheck = context.first(TypeToken.of(HealthCheck.class), hc -> hc.getName().equals(name));
      if (hcheck.isPresent()) {
        try {
          Promise<HealthCheck.Result> promise = hcheck.get().check(context.getExecution().getControl());
          promise.then(r -> {
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
        context.promise(f -> {
          promises.forEach((name, p) -> {
            p.then(r -> {
              System.out.println("HEALTH CHECK: " + name);
              hcheckResults.put(name, r);
            });
          });
          f.success(hcheckResults);
        }).then(results -> {
          context.render(new HealthCheckResults(ImmutableSortedMap.<String, HealthCheck.Result>copyOfSorted(hcheckResults)));
        });
      }
    }
  }
}
