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
import ratpack.func.BiFunction;

/**
 * Executes an action of the type defined by {@code T} and returns a promise of type defined by {@code O}.
 *
 * @param <T> a type of value that is the action execution parameter
 * @param <O> a type of promised value that is an outcome of the action execution
 */
public interface TypedAction<T, O> {
  /**
   * The <b>unique</b> name of the action.
   *
   * @return the name of the action
   */
  String getName();

  /**
   * Executes the action, getting the object of type {@code T} and providing promise for the object of type {@code O}.
   * <p>
   * This method returns a promise to allow action execution to be asynchronous.
   * <p>
   * If this method throws an exception it is equivalent to error result.
   *
   * @param execControl an execution control
   * @param t an object of type {@code T}
   * @return an promise for the object of type {@code O}
   * @throws Exception any
   */
  Promise<O> exec(ExecControl execControl, T t) throws Exception;

  /**
   * Factory for action implementation.
   *
   * @param name a name of the action
   * @param func an action implementation
   * @param <T> a type of parameter for the action implementation
   * @param <O> a type of the promised output object from the action implementation
   * @return a named action implementation
   */
  public static <T, O> TypedAction<T, O> of(String name, BiFunction<? super ExecControl, T, Promise<O>> func) {
    return new TypedAction<T, O>() {
      @Override
      public String getName() { return name; }

      @Override
      public Promise<O> exec(ExecControl execControl, T t) throws Exception {
        return func.apply(execControl, t);
      }
    };
  }
}
