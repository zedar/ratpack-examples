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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.reflect.TypeToken;
import r.p.exec.Action;
import r.p.exec.ActionResults;
import r.p.exec.TypedAction;
import r.p.exec.internal.LongBlockingIOAction;
import r.p.pattern.Pattern;
import ratpack.exec.ExecControl;
import ratpack.exec.Promise;
import ratpack.handling.Context;
import ratpack.handling.Handler;

import java.io.IOException;
import java.util.*;

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

    try {
      Iterable<Action> actions = new LinkedList<>(Arrays.asList(
        new LongBlockingIOAction("foo"),
        new LongBlockingIOAction("bar"),
        Action.of("buzz", execControl -> execControl
          .promise(fulfiller -> {
            throw new IOException("CONTROLLED EXCEPTION");
          })),
        new LongBlockingIOAction("quzz"),
        new LongBlockingIOAction("foo_1"),
        new LongBlockingIOAction("foo_2"),
        new LongBlockingIOAction("foo_3"),
        new LongBlockingIOAction("foo_4"),
        new LongBlockingIOAction("foo_5"),
        new LongBlockingIOAction("foo_6")
      ));
      TypedAction<ActionResults, ActionResults> mergeResults = TypedAction.of("merge", (execControl, actionResults) ->
        execControl.promise(fulfiller -> {
          final int[] counters = {0, 0};
          actionResults.getResults().forEach((name, result) -> {
            if (result.getCode() != null && "0".equals(result.getCode())) {
              counters[0]++;
            } else if (result.getCode() != null && !"0".equals(result.getCode())) {
              counters[1]++;
            }
          });
          StringBuilder strB = new StringBuilder();
          strB.append("Succeeded: ").append(counters[0]).append(" Failed: ").append(counters[1]);
          fulfiller.success(new ActionResults(ImmutableMap.<String, Action.Result>of("COUNTED", Action.Result.error("0", strB.toString()))));
        })
      );
      ctx.render(execute(ctx, patternName, actions, mergeResults));
    } catch (Exception ex) {
      ctx.error(ex);
    }
  }

  private Promise<ActionResults> execute(Context ctx, String patternName, Iterable<? extends Action> actions, TypedAction<ActionResults, ActionResults> postAction) throws Exception {
    Optional<Pattern> first = ctx.first(PATTERN_TYPE_TOKEN, pattern -> pattern.getName().equals(patternName) ? pattern : null);
    if (!first.isPresent()) {
      return ctx.promiseOf(new ActionResults(ImmutableMap.<String, Action.Result>of("ERROR", Action.Result.error("404", "No such pattern exception"))));
    } else {
      return first.get().apply(ctx, actions, postAction);
    }
  }


}
