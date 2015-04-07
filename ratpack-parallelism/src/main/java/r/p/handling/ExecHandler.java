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

import com.google.common.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import r.p.handling.internal.FanOutFanInHandler;
import r.p.handling.internal.ParallelHandler;
import r.p.pattern.FanOutFanIn;
import r.p.pattern.Parallel;
import r.p.pattern.Pattern;
import ratpack.handling.Context;
import ratpack.handling.Handler;

/**
 * A handler that executes {@link r.p.exec.Action actions} or {@link r.p.exec.TypedAction typed actions} and renders their results.
 * <p>
 * The handler obtains pattern for actions execution from the context's registry.
 *
 * @see r.p.exec.Action
 * @see r.p.exec.TypedAction
 * @see r.p.pattern.Pattern
 */
public class ExecHandler implements Handler {

  private static final Logger log = LoggerFactory.getLogger(ExecHandler.class);

  private final Handler fanOutFanInHandler = new FanOutFanInHandler();
  private final Handler parallelHandler = new ParallelHandler();

  /**
   * The default path token name that indicates the pattern to be used for actions execution.
   *
   * Value: {@value}
   */
  public static final String DEFAULT_NAME_TOKEN = "name";

  private static final TypeToken<Pattern> PATTERN_TYPE_TOKEN = TypeToken.of(Pattern.class);

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
    } else {
      ctx.next();
    }
  }
}
