Ratpack and parallelism
-----------------------------

## Requirements:

* [ratpack](http://ratpack.io) version 0.9.15

## Executions patterns

### Fan out and in

Execute actions in parallel (independently), collect the results and render them as *JSON* output.

    $ ./gradlew run
    $ curl -v -X GET http://localhost:5050/fanout

The ```X-Response-Time``` header provides handler execution time.

**Note**

> Ratpack executes actions as promises on an IO event loop that is by default limited to ```Runtime.getRuntime().availableProcessors() * 2```
number of threads. Promises executed in parallel that exceed event loop's number of thread are waiting in event loop.
All blocking operations should be called as ```execControl.blocking()```, that are performed on seperate thread pool and do not block
main event loop.

