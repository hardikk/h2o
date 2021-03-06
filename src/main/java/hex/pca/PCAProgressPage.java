package hex.pca;

import water.*;
import water.api.*;
import water.api.RequestBuilders.Response;

import com.google.gson.JsonObject;

public class PCAProgressPage extends Progress2 {
  /** Return {@link Response} for finished job. */
  @Override protected Response jobDone(final Job job, final Key dst) {
    JsonObject args = new JsonObject();
    args.addProperty("model_key", job.dest().toString());
    return PCAModelView.redirect(this, job.dest());
  }

  public static Response redirect(Request req, Key jobkey, Key dest) {
    return Response.redirect(req, "PCAProgressPage", JOB_KEY, jobkey, DEST_KEY, dest );
  }

  @Override public boolean toHTML( StringBuilder sb ) {
    Job jjob = Job.findJob(job_key);
    Value v = DKV.get(jjob.dest());
    if(v != null){
      PCAModel m = v.get();
      m.generateHTML("PCA Model", sb);
    } else
      sb.append("<b>No model yet.</b>");
    return true;
  }
}