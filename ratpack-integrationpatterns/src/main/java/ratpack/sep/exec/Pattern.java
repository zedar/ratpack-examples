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

import ratpack.exec.ExecControl;
import ratpack.exec.Promise;
import ratpack.registry.Registry;

/**
 * Lets execution of actions with the given integration pattern.
 * <p>
 * Patterns are typically used for execution of blocking external (REST/Webservices) or internal actions in controlled way.
 * Sometimes is feasible to run actions in parallel, sometimes in sequence, sometimes with post or preprocessing.
 * Patterns return {@code Promise} so it is very easy to combine them sequence of non-blocking calls.
 *
 * @param <T> the type of action to execute
 * @param <O> the output data from executed action
 */
public interface Pattern<T,O> {
  /**
   * The <b>unique</b> name of the pattern.
   * <p>
   * Each pattern has to have unique name within an application.
   *
   * @return the name of pattern
   */
  String getName();

  /**
   *
   * @param execControl an execution control
   * @param registry the server registry
   * @param params a structure of actions to be executed. Each pattern could have their own configuration of actions
   * @return a promise for result of type <b>O</b>
   * @throws Exception any
   */
  Promise<O> apply(ExecControl execControl, Registry registry, T params) throws Exception;
}
