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

package r.p.pattern

import r.p.exec.Action
import r.p.exec.ActionResult
import r.p.exec.ActionResults
import ratpack.exec.ExecResult
import ratpack.registry.Registries
import ratpack.registry.Registry
import ratpack.test.exec.ExecHarness
import spock.lang.AutoCleanup
import spock.lang.IgnoreRest
import spock.lang.Specification

import java.util.concurrent.CountDownLatch

class ParallelSpec extends Specification {
  @AutoCleanup
  ExecHarness harness = ExecHarness.harness()
  Parallel pattern
  Registry registry

  def setup() {
    registry = Registries.empty()
    pattern = new Parallel()
  }

  def "pattern name is defined"() {
    when:
    String name = pattern.name

    then:
    name == Parallel.PATTERN_NAME
    name == "parallel"
  }

  def "empty list of actions generate empty results"() {
    given:
    def actions = []

    when:
    ExecResult<ActionResults<String>> result = harness.yield { execControl ->
      pattern.apply(execControl, registry, actions)
    }

    then:
    ActionResults actionResults = result.getValue()
    actionResults
    actionResults.results.size() == 0
  }

  def "one action generates one response"() {
    given:
    def actions = [
      Action.of("foo", "data") { execControl -> execControl.promise { fulfiller -> fulfiller.success(ActionResult.success())}}
    ]

    when:
    ExecResult<ActionResults<String>> result = harness.yield { execControl ->
      pattern.apply(execControl, registry, actions)
    }

    then:
    ActionResults<String> actionResults = result.getValue()
    actionResults.results.size() == 1
    with(actionResults.results.get("foo")) {
      code == "0"
    }
  }

  def "successful action and failed action generates two responses"() {
    given:
    def actions = [
      Action.of("foo", "data") { execControl -> execControl.promise { fulfiller -> fulfiller.success(ActionResult.success())}},
      Action.of("bar", "data") { execControl -> throw new IOException("bar exception") }
    ]

    when:
    ExecResult<ActionResults<String>> result = harness.yield { execControl ->
      pattern.apply(execControl, registry, actions)
    }

    then:
    ActionResults<String> actionResults = result.getValue()
    actionResults.results.size() == 2
    with(actionResults.results.get("foo")) {
      code == "0"
    }
    with(actionResults.results.get("bar")) {
      code == "100"
      message == "bar exception"
    }
  }

  def "10 blocking actions execute in parallel"() {
    given:
    def actions = []
    CountDownLatch releaser = new CountDownLatch(1)

    for (int i = 0; i < 10; i++) {
      actions.add(Action.of("foo_$i", "data") { execControl ->
        execControl.blocking {
          releaser.await()
          ActionResult.success("SUCCESS")
        }
      })
    }

    when:
    releaser.countDown()

    ExecResult<ActionResults<String>> result = harness.yield { execControl ->
      pattern.apply(execControl, registry, actions)
    }

    then:
    ActionResults<String> actionResults = result.getValue()
    actionResults.results.size() == 10

    for (int i = 0; i < 10; i++) {
      with (actionResults.results["foo_$i"]) {
        code == "0"
        message == "SUCCESS"
      }
    }
  }
}
