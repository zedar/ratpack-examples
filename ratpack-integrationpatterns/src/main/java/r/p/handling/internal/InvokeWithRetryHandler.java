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
import r.p.exec.ActionResult;
import r.p.exec.ActionResults;
import r.p.pattern.InvokeWithRetry;
import r.p.pattern.PatternsModule;
import ratpack.handling.Context;
import ratpack.handling.Handler;
import ratpack.util.MultiValueMap;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A handler that shows how <b>InvokeAndRetry</b> pattern works.
 */
public class InvokeWithRetryHandler implements Handler {
  private static final TypeToken<PatternsModule.Config> PATTERN_CONFIG_TYPE_TOKEN = TypeToken.of(PatternsModule.Config.class);

  @Override
  public void handle(Context ctx) throws Exception {
    try {
      AtomicInteger execCounter = new AtomicInteger(0);
      Action<String,String> action = Action.<String, String>of("foo", "data", execControl -> execControl
        .promise( fulfiller -> {
          if (execCounter.incrementAndGet() <= 10) {
            throw new  IOException("FAILED EXECUTION");
          }
          fulfiller.success(ActionResult.success());
        })
      );

      // check if retries have to be executed asynchronously
      boolean asyncRetry = false;
      MultiValueMap<String, String> queryAttrs = ctx.getRequest().getQueryParams();
      System.out.println("QUERY: " + queryAttrs.toString());
      if ("async".equals(queryAttrs.get("retrymode"))) {
        asyncRetry = true;
      }
      //InvokeWithRetry pattern = ctx.get(PATTERN_TYPE_TOKEN);
      PatternsModule.Config patternsConfig = ctx.get(PATTERN_CONFIG_TYPE_TOKEN);
      Objects.requireNonNull(patternsConfig);
      InvokeWithRetry<String,String> pattern = new InvokeWithRetry<>(patternsConfig.getDefaultRetryCount());

      // check if action should be executed asynchronously (in background)
      boolean asyncAction = false;
      if ("async".equals(queryAttrs.get("mode"))) {
        asyncAction = true;
      }

      if (asyncAction) {
        boolean finalAsyncRetry = asyncRetry;
        ctx.exec().start(execution -> pattern.apply(execution, ctx, action, 5, finalAsyncRetry)
          .then(actionResults -> {
            // TODO: log and store results for the future
          }));
        ctx.render(ctx.promiseOf(new ActionResults<>(ImmutableMap.of(action.getName(), ActionResult.success("EXECUTING IN BACKGROUND")))));
      } else {
        ctx.render(pattern.apply(ctx, ctx, action, 5, asyncRetry));
      }
    } catch (Exception ex) {
      ctx.clientError(404);
    }
  }
}
