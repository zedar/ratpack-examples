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

package health;

import ratpack.exec.ExecControl;
import ratpack.exec.Promise;
import ratpack.guice.Guice;
import ratpack.health.HealthCheck;
import ratpack.health.HealthCheckHandler;
import ratpack.health.HealthCheckResultsRenderer;
import ratpack.test.embed.EmbeddedApp;

import javax.swing.*;

import static org.junit.Assert.*;

public class JavaDocTests {
  public static class FooHealthCheck implements HealthCheck {
    public String getName() { return "foo"; }
    public Promise<HealthCheck.Result> check(ExecControl execControl) throws Exception {
      return execControl.promise(f -> {
        f.success(HealthCheck.Result.healthy());
      });
    }
  }

  public static void mainTest(String... args) throws Exception {
    EmbeddedApp.of(s -> s
      .registryOf(r -> r
        .add(new HealthCheckResultsRenderer())
      )
      .registry(Guice.registry(b -> b
        .bind(FooHealthCheck.class)
      ))
      .handler(HealthCheckHandler.class)
    ).test(httpClient -> {
      assertEquals("foo : HEALTHY", httpClient.getText());
    });

    EmbeddedApp.of(s -> s
      .registryOf(r -> r
                      .add(new HealthCheckResultsRenderer())
      )
      .registry(Guice.registry(b -> b
        .bind(FooHealthCheck.class)
        .bindInstance(HealthCheck.class, HealthCheck.of("bar", execControl -> {
          return execControl.promise(f -> {
            f.success(HealthCheck.Result.unhealthy("FAILED"));
          });
        }))
      ))
      .handler(r -> {
        return new HealthCheckHandler("bar");
      })
    ).test(httpClient -> {
      assertEquals("bar : FAILED", httpClient.getText());
    });
  }
}
