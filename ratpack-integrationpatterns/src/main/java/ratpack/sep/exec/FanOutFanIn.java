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
import ratpack.sep.*;
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
 * @see Pattern
 * @see Action
 * @see TypedAction
 * @see PatternsModule
 */
public class FanOutFanIn<T,O,U> {

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
  public String getName() { return PATTERN_NAME; }

  /**
   * Executes actions and applies post processing action.
   * <p>
   * This pattern requires execution of post/fan-in action.
   *
   * @param execControl an execution control
   * @param registry the server registry
   * @return a promise for results
   * @throws Exception any
   */
  public Promise<ActionResults<U>> apply(ExecControl execControl,
                                         Registry registry,
                                         Iterable<Action<T,O>> actions,
                                         TypedAction<ActionResults<O>, ActionResults<U>> postAction) throws Exception {
    Objects.requireNonNull(postAction);
    return apply(execControl, registry, actions)
      .flatMap(results -> postAction
        .exec(execControl, results)
        .mapError(throwable -> new ActionResults<>(ImmutableMap.of(postAction.getName(), ActionResult.error(throwable)))));
  }

  private Promise<ActionResults<O>> apply(ExecControl execControl, Registry registry, Iterable<Action<T,O>> actions) throws Exception {
    Parallel<T,O> parallel = new Parallel<>();
    return parallel.apply(execControl, registry, actions);
  }
}
