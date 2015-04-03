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
import r.p.exec.ActionResults;
import r.p.exec.TypedAction;
import ratpack.exec.ExecControl;
import ratpack.exec.Promise;

/**
 * Lets execution of number of actions with the given integration pattern.
 * <p>
 * Patterns are typically used for execution of blocking external (REST/Webservices) or internal actions in controlled way.
 * Sometimes is feasible to run actions in parallel, sometimes in sequence, sometimes with post or preprocessing.
 * Patterns return {@code Promise} so it is very easy to combine them sequence of non-blocking calls.
 */
public interface Pattern {
  /**
   * The <b>unique</b> name of the pattern.
   * <p>
   * Each pattern has to have unique name within an application.
   *
   * @return the name of pattern
   */
  String getName();

  /**
   * Executes {@code actions} in controlled by {@code pattern} way.
   * <p>
   * This method returns a promise to allow actions execution to be asynchronous and non-blocking.
   *
   * @param execControl an execution control
   * @param actions a collection of actions to execute
   * @return a promise for the results
   * @throws Exception any
   */
  Promise<ActionResults> apply(ExecControl execControl, Iterable<? extends Action> actions) throws Exception;

  /**
   * Executes {@code actions} with post processing.
   * <p>
   * This method returns a promise to allow actions execution to be non-blocking.
   * <p>
   * If {@code postAction} is {@code null} this method is equivalent to {@code apply(execControl, actions)}.
   * <p>
   * If {@code postAction} throws an exception it is catched and mapped to error result.
   *
   * @param execControl an execution control
   * @param actions a collection of actions to execute
   * @param postAction an action to be applied for all actions results
   * @return a promise for the results
   * @throws Exception any
   */
  default Promise<ActionResults> apply(ExecControl execControl, Iterable<? extends Action> actions, TypedAction<ActionResults, ActionResults> postAction) throws Exception {
    if (postAction != null) {
      return apply(execControl, actions)
        .flatMap(results -> postAction
          .exec(execControl, results)
          .mapError(throwable -> new ActionResults(ImmutableMap.<String, Action.Result>of(postAction.getName(), Action.Result.error(throwable)))));
    } else {
      return apply(execControl, actions);
    }
  }
}
