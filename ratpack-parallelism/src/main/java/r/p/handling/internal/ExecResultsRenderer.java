package r.p.handling.internal;

import r.p.exec.Action;
import r.p.handling.ExecResults;
import ratpack.handling.Context;
import ratpack.render.RendererSupport;

import java.util.Map;

import static ratpack.jackson.Jackson.json;

public class ExecResultsRenderer extends RendererSupport<ExecResults> {
  @Override
  public void render(Context context, ExecResults execResults) throws Exception {
    context.render(json(execResults.getResults()));
  }
}
