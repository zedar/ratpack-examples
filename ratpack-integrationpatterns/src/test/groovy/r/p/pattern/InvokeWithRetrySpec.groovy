/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package r.p.pattern

import r.p.exec.ActionResults
import ratpack.exec.ExecResult
import ratpack.registry.Registries
import ratpack.registry.Registry
import ratpack.test.exec.ExecHarness
import spock.lang.AutoCleanup
import spock.lang.Specification

class InvokeWithRetrySpec extends Specification {
  @AutoCleanup
  ExecHarness harness = ExecHarness.harness()
  InvokeWithRetry pattern
  Registry registry

  def setup() {
    registry = Registries.just(new InvokeWithRetry(1))
    pattern = registry.get(InvokeWithRetry.class)
  }

  def "pattern name is defined"() {
    when:
    String name = pattern.getName()

    then:
    InvokeWithRetry.PATTERN_NAME == name
    name == "invokewithretry"
  }

  def "no action generates empty result"() {
    when:
    ExecResult<ActionResults> result = harness.yield { execControl ->
      pattern.apply(execControl, registry, InvokeWithRetry.Params.of(null))
    }

    then:
    ActionResults actionResults = result.getValue()
    actionResults.results.size() == 0
  }

  def "successful action does not retry"() {

  }

  def "failed action retries default number of times"() {

  }

  def "failed action retries custom number of times"() {

  }

  def "successful action after 2 retries stops execution"() {

  }

  def "failed action with async retries returns immediatelly"() {

  }

  def "async invoke returns results asynchronously"() {

  }
}
