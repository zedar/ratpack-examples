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

package ratpack.sep.exec;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ratpack.sep.Action;
import ratpack.sep.ActionResult;
import ratpack.sep.ActionResults;
import ratpack.exec.ExecControl;
import ratpack.exec.Fulfiller;
import ratpack.exec.Promise;
import ratpack.registry.Registry;
import ratpack.sep.PatternsModule;

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
public class InvokeWithRetry<T,O> {

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
  public String getName() { return PATTERN_NAME; }

  public Promise<ActionResults<O>> apply(ExecControl execControl, Registry registry, Action<T,O> action) throws Exception {
    return apply(execControl, registry, action, null, null);
  }

  public Promise<ActionResults<O>> apply(ExecControl execControl, Registry registry, Action<T,O> action, Integer actionRetryCount) throws Exception {
    return apply(execControl, registry, action, actionRetryCount, null);
  }

  /**
   * Executes {@code action} and if it fails retries its execution given number of times.
   * <p>
   * Default retry could be set as {@link PatternsModule.Config#defaultRetryCount} but could be overridden as
   * {@code actionRetryCount}
   *
   * @param execControl an execution control
   * @param registry the server registry
   * @return the promise for action results
   * @throws Exception
   */
  public Promise<ActionResults<O>> apply(ExecControl execControl,
                                         Registry registry,
                                         Action<T,O> action,
                                         Integer actionRetryCount,
                                         Boolean actionAsyncRetry) throws Exception {
    if (action == null) {
      return execControl.promiseOf(new ActionResults<O>(ImmutableMap.of()));
    }

    int retryCount = actionRetryCount != null ? actionRetryCount : defaultRetryCount;
    boolean asyncRetry = actionAsyncRetry != null ? actionAsyncRetry : false;

    if (asyncRetry) {
      return applyAsync(execControl, action, retryCount);
    } else {
      return apply(execControl, action, retryCount);
    }
  }

  public Promise<ActionResults<O>> apply(ExecControl execControl,
                                         Action<T,O> action,
                                         Integer retryCount) throws Exception {

    return execControl.<Map<String, ActionResult<O>>>promise( fulfiller -> {
      AtomicInteger repeatCounter = new AtomicInteger(retryCount + 1);
      Map<String, ActionResult<O>> results = Maps.newConcurrentMap();
      applyWithRetry(execControl, fulfiller, action, results, repeatCounter);
    })
      .map(ImmutableMap::copyOf)
      .map(ActionResults<O>::new);
  }

  private Promise<ActionResults<O>> applyAsync(ExecControl execControl, Action<T,O> action, int retryCount) throws Exception {
    return execControl.<Map<String, ActionResult<O>>>promise( fulfiller -> {
      AtomicInteger repeatCounter = new AtomicInteger(1);
      Map<String, ActionResult<O>> results = Maps.newConcurrentMap();
      applyWithRetry(execControl, fulfiller, action, results, repeatCounter);
    })
      .map(ImmutableMap::copyOf)
      .map(ActionResults<O>::new)
      .wiretap( result -> {
        ActionResults<O> actionResults = result.getValue();
        ActionResult<O> actionResult = actionResults.getResults().get(action.getName());
        if (actionResult != null && !"0".equals(actionResult.getCode())) {
          // execute retries asynchronously
          apply(execControl, action, retryCount)
            .defer(Runnable::run)
            .then(retryActionResults -> {
              // TODO: add logging and some special callback
            });
        }
      });
  }

  private void applyWithRetry(ExecControl execControl, Fulfiller<Map<String, ActionResult<O>>> fulfiller, Action<T,O> action, Map<String, ActionResult<O>> results, AtomicInteger repeatCounter) {
    execControl.exec().start( execution -> applyInternal(execution, action)
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
      }));
  }

  private Promise<ActionResult<O>> applyInternal(ExecControl execControl, Action<T,O> action) {
    try {
      return action.exec(execControl).mapError(ActionResult::error);
    } catch (Exception ex) {
      return execControl.promiseOf(ActionResult.error(ex));
    }
  }
}
