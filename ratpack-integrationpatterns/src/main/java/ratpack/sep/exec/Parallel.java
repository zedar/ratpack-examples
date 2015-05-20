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
import ratpack.sep.Action;
import ratpack.sep.ActionResult;
import ratpack.sep.ActionResults;
import ratpack.exec.ExecControl;
import ratpack.exec.Promise;
import ratpack.registry.Registry;
import ratpack.sep.PatternsModule;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lets actions to execute in parallel.
 * <p>
 * Actions execute independently as {@code promises}. They are not notified about each other.
 *
 * @see Pattern
 * @see Action
 * @see PatternsModule
 */
public class Parallel<T,O> {

  /**
   * The name of the pattern that indicates pattern to execute in handler.
   *
   * Value: {@value}
   */
  public static final String PATTERN_NAME = "parallel";

  /**
   * The name of the pattern
   *
   * @return the name of the pattern
   */
  public String getName() { return PATTERN_NAME; }

  /**
   * Executes {@code Params.actions} in parallel.
   * <p>
   * The result of each {@code action} execution is added to {@link ActionResults}.
   * <p>
   * if {@code action} throws an exception it is equivalent to error result.
   *
   * @param execControl an execution control
   * @param registry the server registry
   * @return a promise for the results
   * @throws Exception any
   */
  public Promise<ActionResults<O>> apply(ExecControl execControl, Registry registry, Iterable<Action<T,O>> actions) throws Exception {
    Iterator<Action<T,O>> iterator = actions.iterator();
    if (!iterator.hasNext()) {
      return execControl.promiseOf(new ActionResults<>(ImmutableMap.of()));
    }

    return execControl.<Map<String, ActionResult<O>>>promise(fulfiller -> {
      AtomicInteger counter = new AtomicInteger();
      Map<String, ActionResult<O>> results = Maps.newConcurrentMap();
      while (iterator.hasNext()) {
        counter.incrementAndGet();
        Action<T,O> action = iterator.next();
        if (action == null || action.getName() == null) {
          int i = counter.decrementAndGet();
          results.put("ACTION_NULL_IDX_" + i, ActionResult.error(new NullPointerException()));
          if (i == 0 && !iterator.hasNext()) {
            fulfiller.success(results);
            return;
          }
          continue;
        }
        execControl.exec().start(execution ->
            apply(execution, action)
              .defer(Runnable::run)
              .wiretap(result -> {

              })
              .then(result -> {
                results.put(action.getName(), result);
                if (counter.decrementAndGet() == 0 && !iterator.hasNext()) {
                  fulfiller.success(results);
                }
              })
        );
      }
    }).map(ImmutableMap::copyOf)
      .map(ActionResults<O>::new);
  }

  private Promise<ActionResult<O>> apply(ExecControl execControl, Action<T,O> action) {
    try {
      return action.exec(execControl).mapError(ActionResult::error);
    } catch (Exception ex) {
      return execControl.promiseOf(ActionResult.error(ex));
    }
  }
}
