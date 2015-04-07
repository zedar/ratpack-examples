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
import r.p.exec.TypedAction;
import ratpack.exec.ExecControl;
import ratpack.exec.Promise;

import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
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
public class FanOutFanIn extends Parallel {

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
   * This pattern requires execution of post action.
   *
   * @param execControl an execution control
   * @param actions a collection of actions to execute
   * @param postAction an action to be applied for all actions results
   * @return a promise for results
   * @throws Exception any
   */
  @Override
  public Promise<ActionResults> apply(ExecControl execControl, Iterable<? extends Action> actions, TypedAction<ActionResults, ActionResults> postAction) throws Exception {
    Objects.requireNonNull(postAction, "Action for Fan-in is required");
    return super.apply(execControl, actions, postAction);
  }
}
