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

package r.p.pattern;

import com.google.inject.Singleton;
import ratpack.guice.ConfigurableModule;

/**
 * An extension module, that provides integration patterns for actions execution.
 * <p>
 * Available patterns:
 * <ul>
 *   <li><b>Fan-out/Fan-in</b> - execute actions in parallel and apply post processing action to results</></li>
 * </ul>
 *
 * <p>
 * Configuration options:
 * <ul>
 *   <li>None</li>
 * </ul>
 */
public class PatternsModule extends ConfigurableModule<PatternsModule.Config> {
  @Override
  protected void configure() {
    bind(FanOutFanIn.class).in(Singleton.class);
    bind(Parallel.class).in(Singleton.class);
  }

  /**
   * The configuration object for {@link r.p.pattern.PatternsModule}
   */
  public static class Config {
  }
}
