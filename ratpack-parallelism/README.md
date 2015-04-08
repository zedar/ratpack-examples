Ratpack and parallelism
-----------------------------

## Requirements:

* [ratpack](http://ratpack.io) version 0.9.15

## Executions patterns

### Parallel
Execute actions in parallel (without informing each other), collect the results and render the results as *JSON* string.
Blocking actions should be called as ```execControl.blocking()```, that is performed on separate thread pool and do not
block main event loop.

Example execution:

    $ ./gradlew run
    $ curl -v -X GET http://localhost:5050/api/parallel

The ```X-Response-Time``` header provides handler execution time.

### Fan-out and fan-in

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

### Invoke with Retry
Execute action and if it fails (thrown exception) retry it number of times.

Example execution

    $ ./gradlew run
    $ curl -v -X GET http://localhost:5050/api/invokewithretry

Default retry count can be set as ```PatternsModule``` configuration parameter ```defaultRetryCount```. Example configuration:

    RatpackServer.start(server -> server
      .registry(Guice.registry(b -> b
        .add(PatternsModule.class, config -> {
          config.setDefaultRetryCount(3);
        })
      ))
    );

Default retry count can be overridden as parameter to pattern's ```apply``` method.

    pattern.apply(ctx, ctx, InvokeAndRetry.Params.of(action, Integer.valueOf(5)))

#### Retries executed asynchronously
Retries can be executed asynchronously. It means that action is executed and if fails all subsequent retries are executed
in separated execution. Result of first action execution is returned to the caller while retries are executed asynchronously.

Example execution:

    $ ./gradlew run
    $ curl -v -X GET http://localhost:5050/api/invokewithretry?retrymode=async

Asynchronous retry mode can be declared as ```asyncRetry``` parameter of ```InvokeWithRetry.Params```.

    pattern.apply(ctx, ctx, InvokeAndRetry.Params.of(action, Integer.valueOf(5), true /*asyncRetry*/))

### Async Invoke with Retry
Thanks to [ratpack](http://ratpack.io) action can be executed completely asynchronously, in background.

Example execution:

    $ ./gradlew run
    $ curl -v -X GET http://localhost:5050/api/invokewithretry?mode=async

**Note:**

In order to pass more than one query parameter to *curl* command put quotes around the url

    $ curl -v -X GET "http://localhost:5050/api/invokewithretry?mode=async&retrymode=async"

Caller can get immediate response with information that execution started in background.
The following code from [InvokeWithRetryHandler]() demonstrates how to implement such asynchronous execution:

    InvokeWithRetry pattern = ctx.get(PATTERN_TYPE_TOKEN);
    // 1: start new execution
    ctx.exec().start( execution -> {
      // 3: when then is called pattern starts to execute action with potential retries
      pattern.apply(execution, ctx, InvokeWithRetry.Params.of(action, Integer.valueOf(5)))
        .then( actionResults -> {
           // 4: get final results
        });
    });
    // 2: return response with information about action to be run in background
    ctx.render(ctx.promiseOf(new ActionResults(ImmutableMap.<String, Action.Result>of(action.getName(), Action.Result.success("EXECUTING IN BACKGROUND")))));

