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

package r.p.exec;

import ratpack.api.Nullable;
import ratpack.exec.ExecControl;
import ratpack.exec.Promise;
import ratpack.func.Function;

/**
 * Executes any action. Could be blocking or non-blocking action.
 * <p>
 * Actions are typically used for external services invocation or internal computation logic execution.
 * The results exposed by actions can be reported via HTTP  by a {@link r.p.handling.ExecHandler}.
 * <p>
 * The actual execution is implemented by the {@link #exec(ExecControl)} method, that returns a promise for a {@link r.p.exec.Action.Result}.
 * <p>
 * The actions are typically executed by the particular {@link r.p.pattern.Pattern} or combination of patterns.
 *
 * @see r.p.handling.ExecHandler
 * @see r.p.pattern.Pattern
 */
public interface Action {
  /**
   * The <b>unique</b> name of the action.
   * <p>
   * Each action within application must have a unique name.
   *
   * @return the name of the action
   */
  String getName();

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
  Promise<Result> exec(ExecControl execControl) throws Exception;

  /**
   * Factory for action implementation.
   *
   * @param name a name of the action
   * @param func an action implementation
   * @return a named action implementation
   */
  public static Action of(String name, Function<? super ExecControl, ? extends Promise<Result>> func) {
    return new Action() {
      @Override
      public String getName() { return name; }

      @Override
      public Promise<Result> exec(ExecControl execControl) throws Exception {
        return func.apply(execControl);
      }
    };
  }

  /**
   * The result of an action execution.
   * <p>
   * Instances can be create by one of the static methods.
   */
  public static class Result {
    private static final Result NO_ERROR = new Result("0", null);

    private final String code;
    private final String message;

    private Result(String code, String message) {
      this.code = code;
      this.message = message;
    }

    /**
     * An error code. If <b>0</b> no error was reported.
     *
     * @return <b>0<b/> if no error or another string otherwise
     */
    public String getCode() { return code; }

    /**
     * Any message provided as part of action execution, may be {@code null}.
     * <p>
     * Message may be provided with error or successful result.
     *
     * @return any message provided as part of action execution.
     */
    @Nullable
    public String getMessage() { return message; }

    /**
     * Creates a successful result, with no message.
     *
     * @return a successful result, with no message.
     */
    public static Result success() { return NO_ERROR; }

    /**
     * Creates successful result, with the given message.
     *
     * @param message a message to accompany the result
     * @return a successful result, with the given message
     */
    public static Result success(String message) { return new Result("0", message); }

    /**
     * Creates an error result, with the given message.
     *
     * @param code an error code
     * @param message a message to accompany the result
     * @return an failed result, with the given message
     */
    public static Result error(String code, String message) { return new Result(code, message); }

    /**
     * Creates an error result, with the given exception.
     * <p>
     * The message of the given exception will be used as the message of the result
     * @param error an exception thrown during action exception
     * @return an failed result, with the given error
     */
    public static Result error(Throwable error) { return new Result("100", error.getMessage()); }
  }
}
