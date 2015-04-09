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
import r.p.exec.Action;
import r.p.exec.ActionResult;
import r.p.exec.ActionResults;
import r.p.exec.TypedAction;
import ratpack.exec.ExecControl;
import ratpack.exec.Promise;
import ratpack.registry.Registry;

import java.util.Objects;

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
public class FanOutFanIn implements Pattern<FanOutFanIn.Params, ActionResults> {

  /**
   * Parameters necessary for pattern execution.
   *
   * Instances can be created by static method {@link #of}
   */
  public static class Params {
    private final Iterable<? extends Action> actions;
    private final TypedAction<ActionResults, ActionResults> thenAction;

    private Params(final Iterable<? extends Action> actions, TypedAction<ActionResults, ActionResults> thenAction) {
      this.actions = actions;
      this.thenAction = thenAction;
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
     * An action to apply to results of executed {@link #actions}.
     *
     * @return an action to apply when {@link #actions} are finished
     */
    public TypedAction<ActionResults, ActionResults> getThenAction() {
      return thenAction;
    }

    /**
     * Creates structure of parameters for pattern execution
     *
     * @param actions a collection of actions to execute
     * @param fanInAction an action to apply when {@code actions} are executed
     * @return the structure of parameters for pattern execution
     */
    public static Params of(final Iterable<? extends Action> actions, TypedAction<ActionResults, ActionResults> fanInAction) {
      return new Params(actions, fanInAction);
    }
  }

  /**
   * The name of the pattern that indicates pattern to execute in handler.
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
   * Executes actions and applies post processing action.
   * <p>
   * This pattern requires execution of post/fan-in action.
   *
   * @param execControl an execution control
   * @param registry the server registry
   * @param params a structure of actions to be executed. Collections of actions to be executed in parallel and post/fan-in action.
   * @return a promise for results
   * @throws Exception any
   */
  @Override
  public Promise<ActionResults> apply(ExecControl execControl, Registry registry, Params params) throws Exception {
    Objects.requireNonNull(params.getThenAction(), "Action for Fan-in is required");
    return apply(execControl, registry, params.getActions(), params.getThenAction());
  }

  private Promise<ActionResults> apply(ExecControl execControl, Registry registry, Iterable<? extends Action> actions, TypedAction<ActionResults, ActionResults> postAction) throws Exception {
    if (postAction != null) {
      return apply(execControl, registry, actions)
        .flatMap(results -> postAction
          .exec(execControl, results)
          .mapError(throwable -> new ActionResults(ImmutableMap.<String, ActionResult>of(postAction.getName(), ActionResult.error(throwable)))));
    } else {
      return apply(execControl, registry, actions);
    }
  }

  private Promise<ActionResults> apply(ExecControl execControl, Registry registry, Iterable<? extends Action> actions) throws Exception {
    Parallel parallel = registry.get(Parallel.class);
    return parallel.apply(execControl, registry, Parallel.Params.of(actions));
  }
}
