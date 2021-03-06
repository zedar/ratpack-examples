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

package health;

import ratpack.exec.ExecControl;
import ratpack.exec.Promise;
import ratpack.health.HealthCheck;

public class FooHealthCheck implements HealthCheck {
  @Override
  public String getName() {
    return "foo";
  }

  @Override
  public Promise<Result> check(ExecControl execControl) throws Exception {
    return execControl.promise(f -> {
      Thread.sleep(3000);
      f.success(Result.healthy());
    });
  }
}
