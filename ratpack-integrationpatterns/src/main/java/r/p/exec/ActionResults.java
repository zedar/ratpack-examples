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

import com.google.common.collect.ImmutableMap;
import r.p.exec.Action;

/**
 * A value type representing the result of running multiple actions.
 *
 * @see r.p.handling.ExecHandler
 */
public class ActionResults {
  private final ImmutableMap<String, Action.Result> results;

  /**
   * Constructor
   *
   * @param results immutable map of action name to its result
   */
  public ActionResults(ImmutableMap<String, Action.Result> results) {
    this.results = results;
  }

  /**
   * The results.
   *
   * @return the results
   */
  public ImmutableMap<String, Action.Result> getResults() { return results; }
}