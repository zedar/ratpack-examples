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

import com.google.common.reflect.TypeToken;
import ratpack.exec.ExecControl;
import ratpack.handling.Context;
import ratpack.handling.Handler;

import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
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
    SortedMap<String, HealthCheck.Result> hcheckResults = new TreeMap<String, HealthCheck.Result>();
    if (!name.equals(DEFAULT_NAME)) {
      Optional<HealthCheck> hcheck = context.first(TypeToken.of(HealthCheck.class), hc -> hc.getName().equals(name));
      if (hcheck.isPresent()) {
        execAndCollectResult(hcheck.get(), hcheckResults, context.getExecution().getControl());
      }
      else {
        context.clientError(404);
      }
    }
    else {
      context.getAll(HealthCheck.class).forEach(hc -> {
        execAndCollectResult(hc, hcheckResults, context.getExecution().getControl());
      });
    }
    //context.render();
  }

  private HealthCheck execAndCollectResult(HealthCheck hc, SortedMap<String, HealthCheck.Result> results, ExecControl execControl) {
    if (hc == null || execControl == null) {
      return null;
    }
    try {
      hc.check(execControl).then(r -> {
        results.put(hc.getName(), r);
      });
    }
    catch (Exception ex) {
      results.put(hc.getName(), HealthCheck.Result.unhealthy(ex));
    }
    return hc;
  }
}
