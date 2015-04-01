package r.p.handling.internal;

import r.p.handling.ExecResults;
import ratpack.handling.Context;
import ratpack.render.RendererSupport;

public class ExecResultsRenderer extends RendererSupport<ExecResults> {
  @Override
  public void render(Context context, ExecResults execResults) throws Exception {
    context.getResponse().send("");
  }
}
