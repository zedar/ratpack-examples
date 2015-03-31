package r.p.handling;

import r.p.exec.Action;
import ratpack.handling.Context;
import ratpack.handling.Handler;

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;

public class ExecHandler implements Handler {
  @Override
  public void handle(Context ctx) throws Exception {
    ctx.getResponse().getHeaders()
      .add("Cache-Control", "no-cache, no-store, must-revalidate")
      .add("Pragma", "no-cache")
      .add("Expires", "0");

    try {
      Iterable<Action> actions = new LinkedList<>(Arrays.asList(new Action("foo"), new Action("bar"), new Action("buzz")));

      Iterator<Action> iterator = actions.iterator();

    } catch (Exception ex) {
      ctx.error(ex);
    }
  }

}
