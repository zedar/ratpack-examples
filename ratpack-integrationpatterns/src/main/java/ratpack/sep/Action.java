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

package ratpack.sep;

import ratpack.exec.ExecControl;
import ratpack.exec.Promise;
import ratpack.func.Function;
import ratpack.sep.exec.Pattern;

/**
 * Executes any action. Could be blocking or non-blocking action.
 * <p>
 * Actions are typically used for external services invocation or internal computation logic execution.
 * The results exposed by actions can be reported via HTTP  by a {@link r.p.handling.ExecHandler}.
 * <p>
 * The actual execution is implemented by the {@link #exec(ExecControl)} method, that returns a promise for a {@link ActionResult}.
 * <p>
 * The actions are typically executed by the particular {@link Pattern} or combination of patterns.
 *
 * @see r.p.handling.ExecHandler
 * @see Pattern
 */
public interface Action<T,O> {
  /**
   * The <b>unique</b> name of the action.
   * <p>
   * Each action within application must have a unique name.
   *
   * @return the name of the action
   */
  String getName();

  /**
   * The data processed by the action
   *
   * @return the action data
   */
  T getData();

  /**
   * Executes the action, providing a promise for the result.
   * <p>
   * This method returns a promise to allow action execution to be asynchronous.
   * <p>
   * If this method throws an exception it is equivalent to error result.
   *
   * @param execControl an execution control
   * @return a promise for the result
   * @throws Exception any
   */
  Promise<ActionResult<O>> exec(ExecControl execControl) throws Exception;

  /**
   * Factory for action implementation.
   *
   * @param name a name of the action
   * @param func an action implementation
   * @return the named action implementation
   */
  public static <T,O> Action<T,O> of(String name, Function<? super ExecControl, Promise<ActionResult<O>>> func) {
    return new Action<T,O>() {
      @Override
      public String getName() { return name; }

      @Override
      public T getData() { return null; }

      @Override
      public Promise<ActionResult<O>> exec(ExecControl execControl) throws Exception {
        return func.apply(execControl);
      }
    };
  }

  /**
   * Factory for action implementation.
   *
   * @param name a name of the action
   * @param data a data associated to the action
   * @param func an action implementation
   * @return the named action implementation
   */
  public static <T,O> Action<T,O> of(String name, T data, Function<? super ExecControl, Promise<ActionResult<O>>> func) {
    return new Action<T,O>() {
      @Override
      public String getName() { return name; }

      @Override
      public T getData() { return data; }

      @Override
      public Promise<ActionResult<O>> exec(ExecControl execControl) throws Exception {
        return func.apply(execControl);
      }
    };
  }
}
