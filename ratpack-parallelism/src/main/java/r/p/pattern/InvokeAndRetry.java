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
import ratpack.exec.ExecControl;
import ratpack.exec.Fulfiller;
import ratpack.exec.Promise;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lets action to execute and retry number of times.
 */
public class InvokeAndRetry implements Pattern {

  private static final Logger log = LoggerFactory.getLogger(InvokeAndRetry.class);

  /**
   * The name of the pattern that indicates pattern to execute in handler
   *
   * Value: {@value}
   */
  public static final String PATTERN_NAME = "invokeandretry";

  /**
   * The name of the pattern
   *
   * @return the name of the pattern
   */
  @Override
  public String getName() { return PATTERN_NAME; }


  @Override
  public Promise<ActionResults> apply(ExecControl execControl, Iterable<? extends Action> actions) throws Exception {
    return null;
  }

  public Promise<ActionResults> apply(ExecControl execControl, Action action) throws Exception {
    if (action == null) {
      return execControl.promiseOf(new ActionResults(ImmutableMap.<String, Action.Result>of()));
    }

    return execControl.<Map<String, Action.Result>>promise( fulfiller -> {
      AtomicInteger repeatCounter = new AtomicInteger(3);
      Map<String, Action.Result> results = Maps.newConcurrentMap();
      execAction(execControl, fulfiller, action, results, repeatCounter);
    })
      .map(ImmutableMap::copyOf)
      .map(ActionResults::new);
  }

  private void execAction(ExecControl execControl, Fulfiller fulfiller, Action action, Map<String, Action.Result> results, AtomicInteger repeatCounter) {
    execControl.exec().start( execution -> {
      applyInternal(execution, action)
        .then(result -> {
          System.out.println("EXECUTED: " + repeatCounter.get());
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
