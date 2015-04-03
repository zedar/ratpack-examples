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
import r.p.exec.Action;
import r.p.exec.ActionResults;
import ratpack.exec.ExecControl;
import ratpack.exec.Promise;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lets actions to execute in parallel and once all are finished process results with post processing action.
 * <p>
 * Actions execute independently and asynchronously as {@code promises}. They are not notified about each other.
 * The post processing action execute as {@code promise} too, so it is non-blocking.
 *
 * @see r.p.pattern.Pattern
 * @see r.p.exec.Action
 * @see r.p.exec.TypedAction
 * @see r.p.pattern.PatternsModule
 */
public class FanOutFanIn implements Pattern {

  /**
   * The name of the pattern that indicates individual pattern to execute.
   *
   * Value: {@value}
   */
  public static final String PATTERN_NAME = "fanoutfanin";

  /**
   * The name of the pattern.
   *
   * @return the name of the pattern
   */
  @Override
  public String getName() { return PATTERN_NAME; }

  /**
   * Executes {@code actions} in parallel.
   * <p>
   * Result of each {@code action} execution is added to {@code ActionResults} as immutable map.
   * <p>
   * if {@code action} throws an exception it is equivalent to error result.
   *
   * @param execControl an execution control
   * @param actions a collection of actions to execute
   * @return a promise for the results
   * @throws Exception
   */
  @Override
  public Promise<ActionResults> apply(ExecControl execControl, Iterable<? extends Action> actions) throws Exception {
    Iterator<? extends Action> iterator = actions.iterator();
    if (!iterator.hasNext()) {
      return execControl.promiseOf(new ActionResults(ImmutableMap.<String, Action.Result>of()));
    }

    return execControl.<Map<String, Action.Result>>promise(fulfiller -> {
      AtomicInteger counter = new AtomicInteger();
      Map<String, Action.Result> results = Maps.newConcurrentMap();
      while (iterator.hasNext()) {
        counter.incrementAndGet();
        Action action = iterator.next();
        execControl.exec().start(execution ->
          apply(execution, action)
            .defer(runnable -> {
              runnable.run();
            })
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
      .map(ActionResults::new);
  }

  private Promise<Action.Result> apply(ExecControl execControl, Action action) {
    try {
      return action.exec(execControl).mapError(Action.Result::error);
    } catch (Exception ex) {
      return execControl.promiseOf(Action.Result.error(ex));
    }
  }
}
