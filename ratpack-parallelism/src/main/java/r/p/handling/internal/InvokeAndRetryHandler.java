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
import r.p.exec.Action;
import r.p.pattern.InvokeAndRetry;
import ratpack.handling.Context;
import ratpack.handling.Handler;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A handler that shows how <b>InvokeAndRetry</b> pattern works.
 */
public class InvokeAndRetryHandler implements Handler {
  private static final TypeToken<InvokeAndRetry> PATTERN_TYPE_TOKEN = TypeToken.of(InvokeAndRetry.class);

  @Override
  public void handle(Context ctx) throws Exception {
    try {
      AtomicInteger execCounter = new AtomicInteger(0);
      Action action = Action.of("foo", execControl -> execControl
        .promise( fulfiller -> {
          if (execCounter.incrementAndGet() <= 10) {
            throw new  IOException("FAILED EXECUTION");
          }
          fulfiller.success(Action.Result.success());
        })
      );

      InvokeAndRetry pattern = ctx.get(PATTERN_TYPE_TOKEN);
      ctx.render(pattern.apply(ctx, action, Integer.valueOf(5)));
    } catch (Exception ex) {
      ctx.clientError(404);
    }
  }
}
