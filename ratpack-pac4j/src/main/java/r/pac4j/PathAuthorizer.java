package r.pac4j;

import ratpack.handling.Context;
import ratpack.pac4j.AbstractAuthorizer;

public class PathAuthorizer extends AbstractAuthorizer {
  @Override
  public boolean isAuthenticationRequired(Context context) {
    return context.getRequest().getPath().startsWith("admin");
  }
}
