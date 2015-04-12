Integration patterns implemented with *Ratpack Promises*
-----------------------------

## Requirements:

* [ratpack](http://ratpack.io) version 0.9.15

## Integration Patterns

### [Parallel](https://github.com/zedar/ratpack-examples/blob/master/ratpack-integrationpatterns/src/main/java/r/p/pattern/Parallel.java)
Execute actions in parallel (without informing each other), collect the results and render the results as *JSON* string.
Blocking actions should be called as ```execControl.blocking()```, that is performed on separate thread pool and do not
block main event loop.

Example execution:

    $ ./gradlew run
    $ curl -v -X GET http://localhost:5050/api/parallel

The ```X-Response-Time``` header provides handler execution time.

Example implementation creates list of actions, some blocking, some throwing exception and execute them in parallel.
Results of every action execution is returned as renderable (to JSON) ```ActionResults```.

````java
    public void handle(Context ctx) throws Exception {
      try {
        Iterable<Action<String,String>> actions = new LinkedList<>(Arrays.asList(
          new LongBlockingIOAction("foo", "data"),
          new LongBlockingIOAction("bar", "data"),
          Action.<String,String>of("buzz", "data", execControl -> execControl
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
````

### [Fan-out/fan-in](https://github.com/zedar/ratpack-examples/blob/master/ratpack-integrationpatterns/src/main/java/r/p/pattern/FanOutFanIn.java)

Execute actions in parallel (independently), collect the results, apply post processing action and render result as *JSON* output.

Example execution:

    $ ./gradlew run
    $ curl -v -X GET http://localhost:5050/api/fanoutfanin

The ```X-Response-Time``` header provides handler execution time.

**Note:**

> Ratpack executes actions as promises on an IO event loop that is by default limited to ```Runtime.getRuntime().availableProcessors() * 2```
number of threads. Promises executed in parallel that exceed event loop's number of thread are waiting in event loop.
All blocking operations should be called as ```execControl.blocking()```, that are performed on separate thread pool and do not block
main event loop.

Example implementation creates list of actions, some blocking, some throwing exception. Additionally ```TypedAction```
is create in order to merge results of actions. In fact ```mergeResults``` action calculates successful and failed actions.

````java
    public void handle(Context ctx) throws Exception {
      try {
        Iterable<Action<String,String>> actions = new LinkedList<>(Arrays.asList(
          new LongBlockingIOAction("foo", "data"),
          new LongBlockingIOAction("bar", "data"),
          Action.<String,String>of("buzz", "data", execControl -> execControl
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
        TypedAction<ActionResults<String>, ActionResults<String>> mergeResults = TypedAction.of("merge", (execControl, actionResults) ->
            execControl.promise(fulfiller -> {
              final int[] counters = {0, 0};
              actionResults.getResults().forEach((name, result) -> {
                if (result.getCode() != null && "0".equals(result.getCode())) {
                  counters[0]++;
                } else if (result.getCode() != null && !"0".equals(result.getCode())) {
                  counters[1]++;
                }
              });
              StringBuilder strB = new StringBuilder();
              strB.append("Succeeded: ").append(counters[0]).append(" Failed: ").append(counters[1]);
              fulfiller.success(new ActionResults<>(ImmutableMap.of("COUNTED", ActionResult.error("0", strB.toString()))));
            })
        );

        FanOutFanIn<String,String,String> pattern = new FanOutFanIn<>();
        ctx.render(pattern.apply(ctx, ctx, actions, mergeResults));
      } catch (Exception ex) {
        ctx.clientError(404);
      }
    }
````

### [Invoke with Retry](https://github.com/zedar/ratpack-examples/blob/master/ratpack-integrationpatterns/src/main/java/r/p/pattern/InvokeWithRetry.java)
Execute action and if it fails (thrown exception) retry it number of times.

Example execution

    $ ./gradlew run
    $ curl -v -X GET http://localhost:5050/api/invokewithretry

Default retry count can be set as ```PatternsModule``` configuration parameter ```defaultRetryCount```. Example configuration:

````java
    RatpackServer.start(server -> server
      .registry(Guice.registry(b -> b
        .add(PatternsModule.class, config -> {
          config.setDefaultRetryCount(3);
        })
      ))
    );
````

Default retry count can be overridden as parameter to pattern's ```apply``` method.

````java
    pattern.apply(ctx, ctx, action, 5))
````

Example implementation creates one action and declares **5** retries in case of failure.

````java
    public void handle(Context ctx) throws Exception {
      try {
        AtomicInteger execCounter = new AtomicInteger(0);
        Action<String,String> action = Action.<String,String>of("foo", execControl -> execControl
          .promise( fulfiller -> {
            if (execCounter.incrementAndGet() <= 10) {
              throw new  IOException("FAILED EXECUTION");
            }
            fulfiller.success(Action.Result.success());
          })
        );

        PatternsModule.Config patternsConfig = ctx.get(PATTERN_CONFIG_TYPE_TOKEN);
        Objects.requireNonNull(patternsConfig);
        InvokeWithRetry<String,String> pattern = new InvokeWithRetry<>(patternsConfig.getDefaultRetryCount());
        ctx.render(pattern.apply(ctx, ctx, action, 5)));
      } catch (Exception ex) {
        ctx.clientError(404);
      }
    }
````

#### Retries executed asynchronously
Retries can be executed asynchronously. It means that action is executed and if fails all subsequent retries are executed
in separated execution. Result of first action execution is returned to the caller while retries are executed asynchronously.

Example execution:

    $ ./gradlew run
    $ curl -v -X GET http://localhost:5050/api/invokewithretry?retrymode=async

Asynchronous retry mode can be declared as ```asyncRetry``` parameter of ```InvokeWithRetry.Params```.

````java
    pattern.apply(ctx, ctx, action, 5, true /*asyncRetry*/))
````

### [Async Invoke with Retry](https://github.com/zedar/ratpack-examples/blob/master/ratpack-integrationpatterns/src/main/java/r/p/handling/internal/InvokeWithRetryHandler.java#L67)
Thanks to [ratpack](http://ratpack.io) action can be executed completely asynchronously, in background.

Example execution:

    $ ./gradlew run
    $ curl -v -X GET http://localhost:5050/api/invokewithretry?mode=async

**Note:**

In order to pass more than one query parameter to *curl* command put quotes around the url

    $ curl -v -X GET "http://localhost:5050/api/invokewithretry?mode=async&retrymode=async"

Caller can get immediate response with information that execution started in background.
The following code from [InvokeWithRetryHandler](https://github.com/zedar/ratpack-examples/blob/master/ratpack-integrationpatterns/src/main/java/r/p/handling/internal/InvokeWithRetryHandler.java#L67) demonstrates how to implement such asynchronous execution:

````java
    InvokeWithRetry pattern = ctx.get(PATTERN_TYPE_TOKEN);
    // 1: start new execution
    ctx.exec().start( execution -> {
      // 3: when then is called pattern starts to execute action with potential retries
      pattern.apply(execution, ctx, action, 5)
        .then( actionResults -> {
           // 4: get final results
        });
    });
    // 2: return response with information about action to be run in background
    ctx.render(ctx.promiseOf(new ActionResults<>(ImmutableMap.of(action.getName(), ActionResult.success("EXECUTING IN BACKGROUND")))))
````
