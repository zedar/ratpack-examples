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

import com.google.common.reflect.TypeToken;
import ratpack.sep.Action;
import r.p.exec.internal.LongBlockingIOAction;
import ratpack.sep.exec.Parallel;
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
      Iterable<Action<String,String>> actions = new LinkedList<>(Arrays.asList(
        new LongBlockingIOAction("foo", "data"),
        new LongBlockingIOAction("bar", "data"),
        Action.<String,String>of("buzz", "data", (execControl, data) -> execControl
          .promise(fulfiller -> {
            throw new IOException("CONTROLLED EXCEPTION");
          })),
        new LongBlockingIOAction("quzz", "data"),
        new LongBlockingIOAction("foo_1", "data"),
        new LongBlockingIOAction("foo_2", "data"),
        new LongBlockingIOAction("foo_3", "data"),
        new LongBlockingIOAction("foo_4", "data"),
        new LongBlockingIOAction("foo_5", "data"),
        new LongBlockingIOAction("foo_6", "data")
      ));

      Parallel<String,String> pattern = new Parallel<>();
      ctx.render(pattern.apply(ctx, ctx, actions));
    } catch (Exception ex) {
      ctx.clientError(404);
    }
  }
}
