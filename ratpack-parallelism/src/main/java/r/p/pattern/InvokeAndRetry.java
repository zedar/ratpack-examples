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
 */
public class InvokeAndRetry implements Pattern<InvokeAndRetry.Params, ActionResults> {

  /**
   * Parameters necessary for pattern execution.
   *
   * Instances can be created by the static method {@link #of}
   */
  public static class Params {
    private final Action action;
    private final Integer retryCount;

    private Params(Action action, Integer retryCount) {
      this.action = action;
      this.retryCount = retryCount;
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
     * Creates pattern parameters
     *
     * @param action an action to execute
     * @param retryCount a number of retries in case of failure
     * @return a structure of pattern parameters
     */
    public static Params of(final Action action, final Integer retryCount) {
      return new Params(action, retryCount);
    }
  }

  private static final Logger log = LoggerFactory.getLogger(InvokeAndRetry.class);

  private final int defaultRetryCount;

  /**
   * The name of the pattern that indicates pattern to execute in handler
   *
   * Value: {@value}
   */
  public static final String PATTERN_NAME = "invokeandretry";

  /**
   * Constructor
   *
   * @param defaultRetryCount the default retry count for the failed action. Can be overridden on {@code apply} method level.
   */
  public InvokeAndRetry(int defaultRetryCount) {
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
   * {@link r.p.pattern.InvokeAndRetry.Params#retryCount}.
   *
   * @param execControl an execution control
   * @param registry the server registry
   * @param params a structure of actions to be executed. Each pattern could have their own configuration of actions
   * @return
   * @throws Exception
   */
  @Override
  public Promise<ActionResults> apply(ExecControl execControl, Registry registry, Params params) throws Exception {
    return apply(execControl, params.getAction(), params.getRetryCount());
  }

  private Promise<ActionResults> apply(ExecControl execControl, Action action, Integer retryCount) throws Exception {
    if (action == null) {
      return execControl.promiseOf(new ActionResults(ImmutableMap.<String, Action.Result>of()));
    }

    return execControl.<Map<String, Action.Result>>promise( fulfiller -> {
      AtomicInteger repeatCounter = new AtomicInteger(retryCount != null ? retryCount.intValue() : defaultRetryCount);
      Map<String, Action.Result> results = Maps.newConcurrentMap();
      execAction(execControl, fulfiller, action, results, repeatCounter);
    })
      .map(ImmutableMap::copyOf)
      .map(ActionResults::new);
  }

  private void execAction(ExecControl execControl, Fulfiller<Map<String, Action.Result>> fulfiller, Action action, Map<String, Action.Result> results, AtomicInteger repeatCounter) {
    execControl.exec().start( execution -> {
      applyInternal(execution, action)
        .then(result -> {
          log.debug("APPLY retry counter: {}", repeatCounter.get());
          results.put(action.getName(), result);
          if ("0".equals(result.getCode())) {
            fulfiller.success(results);
          } else {
            if (repeatCounter.decrementAndGet() == 0) {
              fulfiller.success(results);
            } else {
              execAction(execControl, fulfiller, action, results, repeatCounter);
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
