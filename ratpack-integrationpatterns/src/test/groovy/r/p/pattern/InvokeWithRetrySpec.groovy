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

import com.google.common.collect.ImmutableMap
import r.p.exec.Action
import r.p.exec.ActionResults
import ratpack.exec.ExecResult
import ratpack.registry.Registries
import ratpack.registry.Registry
import ratpack.test.exec.ExecHarness
import spock.lang.AutoCleanup
import spock.lang.IgnoreRest
import spock.lang.Specification

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

class InvokeWithRetrySpec extends Specification {
  private static final int DEFAULT_RETRY_COUNT = 3

  @AutoCleanup
  ExecHarness harness = ExecHarness.harness()
  InvokeWithRetry pattern
  Registry registry

  def setup() {
    registry = Registries.just(new InvokeWithRetry(DEFAULT_RETRY_COUNT))
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
    given:
    AtomicInteger counter = new AtomicInteger()
    Action action = Action.of("foo") { execControl -> execControl.promise{ fulfiller ->
      counter.incrementAndGet()
      fulfiller.success(Action.Result.success())
    }}

    when:
    ExecResult<ActionResults> result = harness.yield { execControl ->
      pattern.apply(execControl, registry, InvokeWithRetry.Params.of(action))
    }

    then:
    ActionResults actionResults = result.getValue()
    actionResults.results.size() == 1
    with(actionResults.results["foo"]) {
      code == "0"
    }
    counter.get() == 1
  }

  def "failed action retries default number of times"() {
    given:
    AtomicInteger counter = new AtomicInteger()
    Action action = Action.of("foo") { execControl -> execControl.promise{ fulfiller ->
      int current = counter.incrementAndGet()
      fulfiller.error(new IOException("Failure $current"))
    }}

    when:
    ExecResult<ActionResults> result = harness.yield { execControl ->
      pattern.apply(execControl, registry, InvokeWithRetry.Params.of(action))
    }

    then:
    ActionResults actionResults = result.getValue()
    actionResults.results.size() == 1
    counter.get() == (DEFAULT_RETRY_COUNT + 1)
    with(actionResults.results["foo"]) {
      code != "0"
    }
  }

  def "failed action retries custom number of times"() {
    given:
    AtomicInteger counter = new AtomicInteger()
    Action action = Action.of("foo") { execControl -> execControl.promise{ fulfiller ->
      int current = counter.incrementAndGet()
      fulfiller.error(new IOException("Failure $current"))
    }}

    when:
    ExecResult<ActionResults> result = harness.yield { execControl ->
      pattern.apply(execControl, registry, InvokeWithRetry.Params.of(action, DEFAULT_RETRY_COUNT + 3))
    }

    then:
    ActionResults actionResults = result.getValue()
    actionResults.results.size() == 1
    counter.get() == DEFAULT_RETRY_COUNT + 3 + 1
    with(actionResults.results["foo"]) {
      code != "0"
    }
  }

  def "successful action after 2 retries stops execution"() {
    given:
    AtomicInteger counter = new AtomicInteger()
    Action action = Action.of("foo") { execControl -> execControl.promise{ fulfiller ->
      int current = counter.incrementAndGet()
      if (current >= 3) {
        fulfiller.success(Action.Result.success())
      } else {
        fulfiller.error(new IOException("Failure $current"))
      }
    }}

    when:
    ExecResult<ActionResults> result = harness.yield { execControl ->
      pattern.apply(execControl, registry, InvokeWithRetry.Params.of(action, DEFAULT_RETRY_COUNT + 10))
    }

    then:
    ActionResults actionResults = result.getValue()
    actionResults.results.size() == 1
    counter.get() == 3
    with(actionResults.results["foo"]) {
      code == "0"
    }
  }

  def "failed action with async retries returns immediatelly"() {
    given:
    AtomicInteger counter = new AtomicInteger()
    Action action = Action.of("foo") { execControl -> execControl.promise{ fulfiller ->
      int current = counter.incrementAndGet()
      if (current > 2) {
        fulfiller.success(Action.Result.success())
      } else {
        fulfiller.error(new IOException("$current"))
      }
    }}

    when:
    ExecResult<ActionResults> result = harness.yield { execControl ->
      pattern.apply(execControl, registry, InvokeWithRetry.Params.of(action, DEFAULT_RETRY_COUNT+10, true /*async retries*/))
    }

    then:
    ActionResults actionResults = result.getValue()
    actionResults.results.size() == 1
    with(actionResults.results["foo"]) {
      code != "0"
      message == "1"
    }
    counter.get() == 3
  }

  def "async invoke returns results asynchronously"() {
    given:
    AtomicInteger counter = new AtomicInteger()
    Action action = Action.of("foo") { execControl -> execControl.promise{ fulfiller ->
      int current = counter.incrementAndGet()
      if (current > 2) {
        fulfiller.success(Action.Result.success())
      } else {
        fulfiller.error(new IOException("$current"))
      }
    }}
    CountDownLatch releaser1 = new CountDownLatch(1)
    CountDownLatch releaser2 = new CountDownLatch(1)

    when:
    ActionResults finalActionResults
    ExecResult<ActionResults> result = harness.yield { execControl ->
      execControl.exec().start { execution ->
        releaser1.await()
        pattern.apply(execution, registry, InvokeWithRetry.Params.of(action, DEFAULT_RETRY_COUNT+10))
          .then { actionResults ->
            finalActionResults = actionResults
            releaser2.countDown()
          }
      }
      execControl.promiseOf(new ActionResults(ImmutableMap.of("foo", Action.Result.success("EXECUTING"))))
    }

    then:
    ActionResults actionResults = result.getValue()
    actionResults.results.size() == 1
    with(actionResults.results["foo"]) {
      code == "0"
      message == "EXECUTING"
    }
    releaser1.countDown()

    releaser2.await()
    finalActionResults != null
    finalActionResults.results.size() == 1
    with(finalActionResults.results["foo"]) {
      code == "0"
    }
    counter.get() == 3
  }
}
