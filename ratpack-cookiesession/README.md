Cookie Session Values
-----------------------------

## Prerequisites

* [ratpack](http:ratpack.io) - version 0.9.15

## Helpers

**curl and cookie management**

Curl has ability to store and send cookie. Option *-b filename* reads cookies from the file while option
*-c filename* saves cookies to the file.

    $ ./gradlew run
    $ curl -v -b cookie.txt -c cookie.txt -X GET http://localhost:5050/set/foo
    $ curl -v -b cookie.txt -c cookie.txt -X GET http://localhost:5050/set/bar