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

import ratpack.exec.ExecControl;
import ratpack.exec.Promise;
import ratpack.func.Function;

/**
 *  Non-blocking health checks to verify that application components are working as expected.
 *  <p>
 *  Interface is based on <a href="https://dropwizard.github.io/metrics/3.1.0/apidocs/">Coda Hale's HealthCheck</a>.
 *  Instead of {@code HeathCheck.Result} it returns {@code Promise<HealthCheck.Result>}.
 *  In <strong>Ratpack</strong> all health checks have a name.
 *
 *  @see ratpack.health.HealthCheckHandler
 */
public interface HealthCheck {
  /**
   * The result of {@link HealthCheck} being run.
   * <p>
   * Result can be healthy with optional message or unhealthy with either error message or thrown exception
   */
  class Result {
    private static final Result HEALTHY = new Result(true, null, null);

    private final boolean healthy;
    private final String message;
    private final Throwable error;

    public Result(boolean healthy, String message, Throwable error) {
      this.healthy = healthy;
      this.message = message;
      this.error = error;
    }

    /**
     * @return {@code true} if application component is healthy
     */
    public boolean isHealthy() {
      return healthy;
    }

    /**
     * @return additional message either for healthy or unhealthy application component
     */
    public String getMessage() {
      return message;
    }

    /**
     * @return exception thrown by unhealthy application component
     */
    public Throwable getError() {
      return error;
    }

    /**
     * @return a healthy {@link ratpack.health.HealthCheck.Result} with no additional message
     */
    public static Result healthy() {
      return HEALTHY;
    }

    /**
     * Return healthy result with additional message.
     * @param message additional message for healthy result
     * @return a healthy {@link ratpack.health.HealthCheck.Result} with additional message
     */
    public static Result healthy(String message) {
      return new Result(true, message, null);
    }

    /**
     * Return healthy result with additional and formatted message.
     * @param message additional message with {@link java.lang.String#format(String, Object...)} formatting
     * @param args arguments for message formatting
     * @return a healthy {@link ratpack.health.HealthCheck.Result} with additional and formatted message
     */
    public static Result healthy(String message, Object... args) {
      return healthy(String.format(message, args));
    }

    /**
     * @param message message describing how the check failed
     * @return an unhealthy {@link ratpack.health.HealthCheck.Result} with failure message
     */
    public static Result unhealthy(String message) {
      return new Result(false, message, null);
    }

    /**
     * @param message failure message with {@link java.lang.String#format(String, Object...)} formatting
     * @param args arguments for message formatting
     * @return an unhealthy {@link ratpack.health.HealthCheck.Result} with failure and formatted message
     */
    public static Result unhealthy(String message, Object... args) {
      return unhealthy(String.format(message, args));
    }

    /**
     * @param error an exception thrown during health check
     * @return an unhealthy {@link ratpack.health.HealthCheck.Result} with error message and thrown exception
     */
    public static Result unhealthy(Throwable error) {
      return new Result(false, error.getMessage(), error);
    }
  }

  /**
   * Every health check in <strong>Ratpack</strong> is named.
   *
   * @return health check name
   */
  String getName();

  /**
   * Perform check check of application component in non-blocking execution. Health check can block or do async operations.
   * {@code Promise} is non-blocking.
   * @param execControl current {@link ratpack.exec.ExecControl}
   * @return {@link ratpack.exec.Promise} of {@link ratpack.health.HealthCheck.Result}
   * @throws Exception
   */
  Promise<HealthCheck.Result> check(ExecControl execControl) throws Exception;

  /**
   * Create named health check with implementation given as lambda {@code func} parameter.
   * @param name a name of health check
   * @param func a health check implementation
   * @return named health check implementation
   */
  static HealthCheck of(String name, Function<? super ExecControl, ? extends Promise<HealthCheck.Result>> func) {
    return new HealthCheck() {
      @Override
      public String getName() {
        return name;
      }

      @Override
      public Promise<Result> check(ExecControl execControl) throws Exception {
        return func.apply(execControl);
      }
    };
  }
}
