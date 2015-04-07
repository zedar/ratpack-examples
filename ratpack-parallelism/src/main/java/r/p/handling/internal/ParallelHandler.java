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

package r.p.handling.internal;

import com.google.common.collect.ImmutableMap;
import com.google.common.reflect.TypeToken;
import r.p.exec.Action;
import r.p.exec.ActionResults;
import r.p.exec.TypedAction;
import r.p.exec.internal.LongBlockingIOAction;
import r.p.pattern.FanOutFanIn;
import r.p.pattern.Parallel;
import ratpack.handling.Context;
import ratpack.handling.Handler;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;

/**
 * A handler that shows how <b>Parallel</b> pattern works.
 */
public class ParallelHandler implements Handler {
  private static final TypeToken<Parallel> PATTERN_TYPE_TOKEN = TypeToken.of(Parallel.class);

  @Override
  public void handle(Context ctx) throws Exception {
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

      Parallel pattern = ctx.get(PATTERN_TYPE_TOKEN);
      ctx.render(pattern.apply(ctx, actions));
    } catch (Exception ex) {
      ctx.clientError(404);
    }
  }
}
