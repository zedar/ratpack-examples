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

package r.p.handling;

import r.p.handling.internal.FanOutFanInHandler;
import r.p.handling.internal.InvokeWithRetryHandler;
import r.p.handling.internal.ParallelHandler;
import ratpack.sep.exec.FanOutFanIn;
import ratpack.sep.exec.InvokeWithRetry;
import ratpack.sep.exec.Parallel;
import ratpack.handling.Context;
import ratpack.handling.Handler;
import ratpack.sep.Action;
import ratpack.sep.TypedAction;
import ratpack.sep.exec.Pattern;

/**
 * A handler that executes {@link Action actions} or {@link TypedAction typed actions} and renders their results.
 * <p>
 * The handler obtains pattern for actions execution from the context's registry.
 *
 * @see Action
 * @see TypedAction
 * @see Pattern
 */
public class ExecHandler implements Handler {
  private final Handler fanOutFanInHandler = new FanOutFanInHandler();
  private final Handler parallelHandler = new ParallelHandler();
  private final Handler invokeAndRetryHandler = new InvokeWithRetryHandler();

  /**
   * The default path token name that indicates the pattern to be used for actions execution.
   *
   * Value: {@value}
   */
  public static final String DEFAULT_NAME_TOKEN = "name";

  /**
   * Runs actions with the given {@code pattern}
   *
   * @param ctx the request context
   * @throws Exception any
   */
  @Override
  public void handle(Context ctx) throws Exception {
    ctx.getResponse().getHeaders()
      .add("Cache-Control", "no-cache, no-store, must-revalidate")
      .add("Pragma", "no-cache")
      .add("Expires", "0");

    String patternName = ctx.getPathTokens().get(DEFAULT_NAME_TOKEN);
    if (patternName == null || "".equals(patternName)) {
      ctx.clientError(404);
      return;
    }

    if (FanOutFanIn.PATTERN_NAME.equals(patternName)) {
      ctx.insert(fanOutFanInHandler);
    } else if (Parallel.PATTERN_NAME.equals(patternName)) {
      ctx.insert(parallelHandler);
    } else if (InvokeWithRetry.PATTERN_NAME.equals(patternName)) {
      ctx.insert(invokeAndRetryHandler);
    } else {
      ctx.next();
    }
  }
}
