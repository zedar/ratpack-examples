Example: [ratpack](http://ratpack.io/), [PAC4J](http://www.pac4j.org) and cookie session.
-----------------------------

The user session stored in cookies enables PAC4J to be stateless.
Every user's request may be directed to any of the ratpack servers in the cluster.
User once logged in on one server is logged in on the other servers. Up to session expiration.
This even works when server restarts.

The cookie based session allows simplified scaling and fault tolerance.

Run example with the following command

  $ ./gradlew run

In the browser go to `http://localhost:5050/auth`.
If user is not authenticated the `http://localhost:5050/login` page should be visible.
Enter the same login and password (there is very simple user manager used in this example).

Kill the server. Run it again. Refresh `http://localhost:5050/auth`.
If session is not expired the `auth` page should be visible without the `login`.

