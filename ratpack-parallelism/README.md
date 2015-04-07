Ratpack and parallelism
-----------------------------

## Requirements:

* [ratpack](http://ratpack.io) version 0.9.15

## Executions patterns

### Parallel
Execute actions in parallel (without informing each other), collect the results and render the results as *JSON* string.
Blocking actions should be called as ```execControl.blocking()```, that is performed on separate thread pool and do not

    $ ./gradlew run
    $ curl -v -X GET http://localhost:5050/parallel

The ```X-Response-Time``` header provides handler execution time.

### Fan-out and fan-in

Execute actions in parallel (independently), collect the results, apply post processing action and render result as *JSON* output.


    $ ./gradlew run
    $ curl -v -X GET http://localhost:5050/fanoutfanin

The ```X-Response-Time``` header provides handler execution time.

**Note:**

> Ratpack executes actions as promises on an IO event loop that is by default limited to ```Runtime.getRuntime().availableProcessors() * 2```
number of threads. Promises executed in parallel that exceed event loop's number of thread are waiting in event loop.
All blocking operations should be called as ```execControl.blocking()```, that are performed on separate thread pool and do not block
main event loop.

