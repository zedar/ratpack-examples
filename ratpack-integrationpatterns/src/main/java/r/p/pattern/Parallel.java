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
import r.p.exec.ActionResult;
import r.p.exec.ActionResults;
import ratpack.exec.ExecControl;
import ratpack.exec.Promise;
import ratpack.registry.Registry;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lets actions to execute in parallel.
 * <p>
 * Actions execute independently as {@code promises}. They are not notified about each other.
 *
 * @see r.p.pattern.Pattern
 * @see r.p.exec.Action
 * @see r.p.pattern.PatternsModule
 */
public class Parallel implements Pattern<Parallel.Params, ActionResults> {

  /**
   * Parameters necessary for pattern execution.
   *
   * Instances can be created by static method {@link #of}
   */
  public static class Params {
    private final Iterable<? extends Action> actions;

    private Params(final Iterable<? extends Action> actions) {
      this.actions = actions;
    }

    /**
     * The collection of actions to execute.
     *
     * @return the collection of actions to execute
     */
    public Iterable<? extends Action> getActions() {
      return actions;
    }

    /**
     * Creates the structure of parameters for pattern execustion
     *
     * @param actions collection of actions to execute in parallel
     * @return the structure of parameters for pattern execution
     */
    public static Params of(final Iterable<? extends Action> actions) {
      return new Params(actions);
    }
  }

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
  @Override
  public String getName() { return PATTERN_NAME; }

  /**
   * Executes {@code Params.actions} in parallel.
   * <p>
   * The result of each {@code action} execution is added to {@link r.p.exec.ActionResults}.
   * <p>
   * if {@code action} throws an exception it is equivalent to error result.
   *
   * @param execControl an execution control
   * @param registry the server registry
   * @param params params with collection of actions to execute
   * @return a promise for the results
   * @throws Exception any
   */
  @Override
  public Promise<ActionResults<ActionResults>> apply(ExecControl execControl, Registry registry, Params params) throws Exception {
    return apply(execControl, params.getActions());
  }

  private Promise<ActionResults> apply(ExecControl execControl, Iterable<? extends Action> actions) throws Exception {
    Iterator<? extends Action> iterator = actions.iterator();
    if (!iterator.hasNext()) {
      return execControl.promiseOf(new ActionResults(ImmutableMap.<String, ActionResult>of()));
    }

    return execControl.<Map<String, ActionResult>>promise(fulfiller -> {
      AtomicInteger counter = new AtomicInteger();
      Map<String, ActionResult> results = Maps.newConcurrentMap();
      while (iterator.hasNext()) {
        counter.incrementAndGet();
        Action action = iterator.next();
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
      .map(ActionResults::new);
  }

  private Promise<ActionResult> apply(ExecControl execControl, Action action) {
    try {
      return action.exec(execControl).mapError(ActionResult::error);
    } catch (Exception ex) {
      return execControl.promiseOf(ActionResult.error(ex));
    }
  }
}
