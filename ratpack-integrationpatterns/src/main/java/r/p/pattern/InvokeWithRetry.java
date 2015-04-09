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

package r.p.pattern;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import r.p.exec.Action;
import r.p.exec.ActionResults;
import ratpack.api.Nullable;
import ratpack.exec.ExecControl;
import ratpack.exec.Fulfiller;
import ratpack.exec.Promise;
import ratpack.registry.Registry;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lets action to execute and in case of failure retry given number of times.
 * <p>
 * Retry can be done synchronously, while still non blocking or asynchronously.
 * <p>
 * Asynchronous retry means, that action is executed and if fails, all subsequent retries are executed in separate execution.
 * Result is immediately returned to the caller.
 * Asynchronous retry usually requires correlation id but this should be implemented by custom actions.
 */
public class InvokeWithRetry implements Pattern<InvokeWithRetry.Params, ActionResults> {

  /**
   * Parameters necessary for pattern execution.
   *
   * Instances can be created by the static method {@link #of}
   */
  public static class Params {
    private final Action action;
    private final Integer retryCount;
    private final boolean asyncRetry;

    private Params(Action action, Integer retryCount, boolean asyncRetry) {
      this.action = action;
      this.retryCount = retryCount;
      this.asyncRetry = asyncRetry;
    }

    /**
     * Action to execute.
     *
     * @return the action to execute
     */
    public Action getAction() {
      return action;
    }

    /**
     * Maximum number of retries, may be {@code null}
     * <p>
     * If it is not provided default retry count is used defined in {@link r.p.pattern.PatternsModule.Config#defaultRetryCount}
     *
     * @return the retry count for the given action
     */
    @Nullable
    public Integer getRetryCount() {
      return retryCount;
    }

    /**
     * Are action retries executed synchronously or asynchronously?
     *
     * @return {@code true} if retries are executed asynchronously.
     */
    public boolean isAsyncRetry() { return asyncRetry; }

    /**
     * Creates pattern parameters with default retry count and synchronous, while non-blocking retries.
     * @param action an action to execute
     * @return the structure of pattern parameters
     */
    public static Params of(final Action action) {
      return new Params(action, null, false);
    }

    /**
     * Creates pattern parameters. Retries are executed synchronously, while still they are non-blocking.
     *
     * @param action an action to execute
     * @param retryCount the number of retries in case of failure
     * @return the structure of pattern parameters
     */
    public static Params of(final Action action, Integer retryCount) {
      return new Params(action, retryCount, false);
    }

    /**
     * Creates pattern parameters.
     *
     * @param action an action to execute
     * @param retryCount the number of retries in case of failure
     * @param asyncRetry execute retries synchronously or asynchronously
     * @return the structure of pattern parameters
     */
    public static Params of(final Action action, Integer retryCount, boolean asyncRetry) {
      return new Params(action, retryCount, asyncRetry);
    }
  }

  private static final Logger log = LoggerFactory.getLogger(InvokeWithRetry.class);

  private final int defaultRetryCount;

  /**
   * The name of the pattern that indicates pattern to execute in handler
   *
   * Value: {@value}
   */
  public static final String PATTERN_NAME = "invokewithretry";

  /**
   * Constructor
   *
   * @param defaultRetryCount the default retry count for the failed action. Can be overridden on {@code apply} method level.
   */
  public InvokeWithRetry(int defaultRetryCount) {
    this.defaultRetryCount = defaultRetryCount;
  }

  /**
   * The name of the pattern
   *
   * @return the name of the pattern
   */
  @Override
  public String getName() { return PATTERN_NAME; }

  /**
   * Executes {@code action} and if it fails retries its execution given number of times.
   * <p>
   * Default retry could be set as {@link r.p.pattern.PatternsModule.Config#defaultRetryCount} but could be overridden as
   * {@link InvokeWithRetry.Params#retryCount}.
   *
   * @param execControl an execution control
   * @param registry the server registry
   * @param params a structure of actions to be executed. Each pattern could have their own configuration of actions
   * @return
   * @throws Exception
   */
  @Override
  public Promise<ActionResults> apply(ExecControl execControl, Registry registry, Params params) throws Exception {
    if (params.getAction() == null) {
      return execControl.promiseOf(new ActionResults(ImmutableMap.<String, Action.Result>of()));
    }
    int retryCount = params.getRetryCount() != null ? params.getRetryCount().intValue() : defaultRetryCount;
    if (params.isAsyncRetry()) {
      return applyAsync(execControl, params.getAction(), retryCount);
    } else {
      return apply(execControl, params.getAction(), retryCount);
    }
  }

  private Promise<ActionResults> apply(ExecControl execControl, Action action, int retryCount) throws Exception {
    return execControl.<Map<String, Action.Result>>promise( fulfiller -> {
      AtomicInteger repeatCounter = new AtomicInteger(retryCount + 1);
      Map<String, Action.Result> results = Maps.newConcurrentMap();
      applyWithRetry(execControl, fulfiller, action, results, repeatCounter);
    })
      .map(ImmutableMap::copyOf)
      .map(ActionResults::new);
  }

  private Promise<ActionResults> applyAsync(ExecControl execControl, Action action, int retryCount) throws Exception {
    return execControl.<Map<String, Action.Result>>promise( fulfiller -> {
      AtomicInteger repeatCounter = new AtomicInteger(1);
      Map<String, Action.Result> results = Maps.newConcurrentMap();
      applyWithRetry(execControl, fulfiller, action, results, repeatCounter);
    })
      .map(ImmutableMap::copyOf)
      .map(ActionResults::new)
      .wiretap( result -> {
        ActionResults actionResults = result.getValue();
        Action.Result actionResult = actionResults.getResults().get(action.getName());
        if (actionResult != null && !"0".equals(actionResult.getCode())) {
          // execute retries asynchronously
          apply(execControl, action, retryCount)
            .defer(runnable -> {
              runnable.run();
            })
            .then(retryActionResults -> {
              // TODO: add logging and some special callback
            });
        }
      });
  }

  private void applyWithRetry(ExecControl execControl, Fulfiller<Map<String, Action.Result>> fulfiller, Action action, Map<String, Action.Result> results, AtomicInteger repeatCounter) {
    execControl.exec().start( execution -> {
      applyInternal(execution, action)
        .then(result -> {
          log.debug("APPLY retry from: {}", repeatCounter.get());
          results.put(action.getName(), result);
          if ("0".equals(result.getCode())) {
            fulfiller.success(results);
          } else {
            if (repeatCounter.decrementAndGet() == 0) {
              fulfiller.success(results);
            } else {
              applyWithRetry(execControl, fulfiller, action, results, repeatCounter);
            }
          }
        });
    });
  }

  private Promise<Action.Result> applyInternal(ExecControl execControl, Action action) {
    try {
      return action.exec(execControl).mapError(Action.Result::error);
    } catch (Exception ex) {
      return execControl.promiseOf(Action.Result.error(ex));
    }
  }
}
