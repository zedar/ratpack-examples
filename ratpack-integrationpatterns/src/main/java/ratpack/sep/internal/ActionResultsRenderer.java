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

package ratpack.sep.internal;

import ratpack.sep.ActionResults;
import ratpack.handling.Context;
import ratpack.render.RendererSupport;

import static ratpack.jackson.Jackson.json;

/**
 * Serializes {@link ActionResults} to {@code JSON} and sends it to the handler {@code response}.
 * <p>
 * This renderer requires {@code ratpack.dependency(jackson)}
 */
public class ActionResultsRenderer extends RendererSupport<ActionResults> {
  /**
   * Renders {@link ActionResults} as {@code JSON} string.
   *
   * @param context handler context
   * @param actionResults a collection of the action results
   * @throws Exception any
   */
  @Override
  public void render(Context context, ActionResults actionResults) throws Exception {
    context.render(json(actionResults.getResults()));
  }
}
