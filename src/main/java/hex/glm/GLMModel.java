package hex.glm;

import hex.FrameTask.DataInfo;
import hex.glm.GLMParams.CaseMode;
import hex.glm.GLMParams.Family;
import hex.glm.GLMValidation.GLMXValidation;

import java.text.DecimalFormat;
import java.util.HashMap;

import water.*;
import water.H2O.H2OCountedCompleter;
import water.api.DocGen;
import water.api.Request.API;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.util.RString;

public class GLMModel extends Model implements Comparable<GLMModel> {
  static final int API_WEAVER = 1; // This file has auto-gen'd doc & json fields
  static public DocGen.FieldDoc[] DOC_FIELDS; // Initialized from Auto-Gen code.

  @API(help="mean of response in the training dataset")
  final double     ymu;

  @API(help="predicate applied to the response column to turn it into 0/1")
  final CaseMode  _caseMode;

  @API(help="value used to co compare agains using case-predicate to turn the response into 0/1")
  final double _caseVal;

  @API(help="offsets of categorical columns into the beta vector. The last value is the offset of the first numerical column.")
  final int    []  catOffsets;
  @API(help="warnings")
  final String []  warnings;
  @API(help="Decision threshold.")
  final double     threshold;
  @API(help="glm params")
  final GLMParams  glm;
  @API(help="beta epsilon - stop iterating when beta diff is below this threshold.")
  final double     beta_eps;
  @API(help="regularization parameter driving proportion of L1/L2 penalty.")
  final double     alpha;

  @API(help="column names including expanded categorical values")
  public String [] coefficients_names;

  @API(help="index of lambda giving best results")
  int best_lambda_idx;

  public double auc(){
    if(glm.family == Family.binomial && submodels != null && submodels[best_lambda_idx].validation != null)
      return submodels[best_lambda_idx].validation.auc;
    return -1;
  }
  public double aic(){
    if(submodels != null && submodels[best_lambda_idx].validation != null)
      return submodels[best_lambda_idx].validation.aic;
    return Double.MAX_VALUE;
  }
  public double devExplained(){
    if(submodels == null || submodels[best_lambda_idx].validation == null)
      return 0;
    GLMValidation val = submodels[best_lambda_idx].validation;
    return 1.0 - val.residual_deviance/val.null_deviance;
  }


  @Override
  public int compareTo(GLMModel m){
//    assert m._dataKey.equals(_dataKey);
    assert m.glm.family == glm.family;
    assert m.glm.link == glm.link;
    switch(glm.family){
      case binomial: // compare by AUC, higher is better
        return (int)(1e6*(m.auc()-auc()));
      case gamma: // compare by percentage of explained deviance, higher is better
        return (int)(100*(m.devExplained()-devExplained()));
      default: // compare by AICs by default, lower is better
        return (int)(100*(aic()- m.aic()));
    }
  }
  @API(help="Overall run time")
  long run_time;
  @API(help="computation started at")
  long start_time;

  static class Submodel extends Iced {
    static final int API_WEAVER = 1; // This file has auto-gen'd doc & json fields
    static public DocGen.FieldDoc[] DOC_FIELDS; // Initialized from Auto-Gen code.
    @API(help="regularization param giving the strength of the applied regularization. high values drive coeffficients to zero.")
    final double     lambda;
    @API(help="number of iterations computed.")
    final int        iteration;
    @API(help="running time of the algo in ms.")
    final long       run_time;
    @API(help="Validation")
    GLMValidation validation;
    @API(help="Beta vector containing model coefficients.") double []  beta;
    @API(help="Beta vector containing normalized coefficients (coefficients obtained on normalized data).") double []  norm_beta;

    final int rank;
    final int [] idxs;

    public Submodel(double lambda, double [] beta, double [] norm_beta, long run_time, int iteration){
      this.lambda = lambda;
      this.beta = beta;
      this.norm_beta = norm_beta;
      this.run_time = run_time;
      this.iteration = iteration;
      int r = 0;
      if(beta != null){
        final double [] b = norm_beta != null?norm_beta:beta;
        // grab the indeces of non-zero coefficients
        for(double d:beta)if(d != 0)++r;
        idxs = new int[r];
        int ii = 0;
        for(int i = 0; i < b.length; ++i)if(b[i] != 0)idxs[ii++] = i;
        // now sort them
        for(int i = 1; i < r; ++i){
          for(int j = 1; j < r-i;++j){
            if(Math.abs(b[idxs[j-1]]) < Math.abs(b[idxs[j]])){
              int jj = idxs[j];
              idxs[j] = idxs[j-1];
              idxs[j-1] = jj;
            }
          }
        }
      } else idxs = null;
      rank = r;
    }
  }

  @API(help = "models computed for particular lambda values")
  Submodel [] submodels;

  public GLMModel(Key selfKey, Frame fr, DataInfo dinfo, GLMParams glm, double beta_eps, double alpha, double [] lambda, double ymu,  CaseMode caseMode, double caseVal ) {
    super(selfKey,null,fr);
    this.ymu = ymu;
    this.glm = glm;
    threshold = 0.5;
    this.catOffsets = dinfo._catOffsets;
    this.warnings = null;
    this.alpha = alpha;
    this.beta_eps = beta_eps;
    _caseVal = caseVal;
    _caseMode = caseMode;
    submodels = new Submodel[lambda.length];
    for(int i = 0; i < submodels.length; ++i)
      submodels[i] = new Submodel(lambda[i], null, null, 0, 0);
    run_time = 0;
    start_time = System.currentTimeMillis();
    coefficients_names = coefNames(dinfo.fullN()+1);
  }
  public void setLambdaSubmodel(int lambdaIdx,double lambda, double [] beta, double [] norm_beta, int iteration){
    submodels[lambdaIdx] = new Submodel(lambda, beta, norm_beta, run_time, iteration);
    run_time = (System.currentTimeMillis()-start_time);
  }
//
//  public void setBeta(int lambdaIdx, double [] beta, double [] norm_beta){
//    Submodel sm = submodels[lambdaIdx];
//    sm.beta = beta;
//    sm.norm_beta = norm_beta;
//  }

  public double lambda(){
    if(submodels == null)return Double.NaN;
    return submodels[best_lambda_idx].lambda;
  }
  public double lambdaMax(){
    return submodels[0].lambda;
  }
  public double lambdaMin(){
    return submodels[submodels.length-1].lambda;
  }
  public GLMValidation validation(){
    return submodels[best_lambda_idx].validation;
  }
  public int iteration(){return submodels[best_lambda_idx].iteration;}
  public double [] beta(){return submodels[best_lambda_idx].beta;}
  public double [] beta(int i){return submodels[i].beta;}
  public double [] norm_beta(){return submodels[best_lambda_idx].norm_beta;}
  public double [] norm_beta(int i){return submodels[i].norm_beta;}
  @Override protected float[] score0(double[] data, float[] preds) {
    return score0(data,preds,best_lambda_idx);
  }
  protected float[] score0(double[] data, float[] preds, int lambdaIdx) {
    double eta = 0.0;
    final double [] b = beta(lambdaIdx);
    for(int i = 0; i < catOffsets.length-1; ++i) if(data[i] != 0)
      eta += b[catOffsets[i] + (int)(data[i]-1)];
    final int noff = catOffsets[catOffsets.length-1] - catOffsets.length + 1;
    for(int i = catOffsets.length-1; i < data.length; ++i)
      eta += b[noff+i]*data[i];
    eta += b[b.length-1]; // add intercept
    double mu = glm.linkInv(eta);
    preds[0] = (float)mu;
    if(glm.family == Family.binomial){ // threshold
      if(preds.length > 1)preds[1] = preds[0];
      preds[0] = preds[0] >= threshold?1:0;
    }
    return preds;
  }
  public final int ncoefs() {return beta().length;}

  public static class GLMValidationTask<T extends GLMValidationTask<T>> extends MRTask2<T> {
    protected final GLMModel _model;
    protected GLMValidation _res;
    public int _lambdaIdx;
    public boolean _improved;
    public static Key makeKey(){return Key.make("__GLMValidation_" + Key.make().toString());}
    public GLMValidationTask(GLMModel model, int lambdaIdx){this(model,lambdaIdx,null);}
    public GLMValidationTask(GLMModel model, int lambdaIdx, H2OCountedCompleter completer){super(completer); _lambdaIdx = lambdaIdx; _model = model;}
    @Override public void map(Chunk [] chunks){
      _res = new GLMValidation(null,_model.ymu,_model.glm,_model.rank(_lambdaIdx));
      final int nrows = chunks[0]._len;
      double [] row   = MemoryManager.malloc8d(_model._names.length);
      float  [] preds = MemoryManager.malloc4f(_model.glm.family == Family.binomial?2:1);
      OUTER:
      for(int i = 0; i < nrows; ++i){
        if(chunks[chunks.length-1].isNA0(i))continue;
        for(int j = 0; j < chunks.length-1; ++j){
          if(chunks[j].isNA0(i))continue OUTER;
          row[j] = chunks[j].at0(i);
        }
        _model.score0(row, preds,_lambdaIdx);
        double response = chunks[chunks.length-1].at0(i);
        if(_model._caseMode != CaseMode.none)
          response = _model._caseMode.isCase(response, _model._caseVal)?1:0;
        _res.add(response, _model.glm.family == Family.binomial?preds[1]:preds[0]);
      }
      _res.avg_err /= _res.nobs;
    }
    @Override public void reduce(GLMValidationTask gval){_res.add(gval._res);}
    @Override public void postGlobal(){
      _res.finalize_AIC_AUC();
      _improved = _model.setAndTestValidation(_lambdaIdx, _res);
      UKV.put(_model._selfKey,_model);
    }
  }
  // use general score to reduce number of possible different code paths
  public static class GLMXValidationTask extends GLMValidationTask<GLMXValidationTask>{
    protected final GLMModel [] _xmodels;
    protected GLMValidation [] _xvals;
    public static Key makeKey(){return Key.make("__GLMValidation_" + Key.make().toString());}
    public GLMXValidationTask(GLMModel mainModel,int lambdaIdx, GLMModel [] xmodels){this(mainModel,lambdaIdx,xmodels,null);}
    public GLMXValidationTask(GLMModel mainModel,int lambdaIdx, GLMModel [] xmodels, H2OCountedCompleter completer){super(mainModel, lambdaIdx, completer); _xmodels = xmodels;}
    @Override public void map(Chunk [] chunks){
      _xvals = new GLMValidation[_xmodels.length];
      for(int i = 0; i < _xmodels.length; ++i)
        _xvals[i] = new GLMValidation(null,_xmodels[i].ymu,_xmodels[i].glm,_xmodels[i].rank());
      final int nrows = chunks[0]._len;
      double [] row   = MemoryManager.malloc8d(_model._names.length);
      float  [] preds = MemoryManager.malloc4f(_model.glm.family == Family.binomial?2:1);
      OUTER:
      for(int i = 0; i < nrows; ++i){
        if(chunks[chunks.length-1].isNA0(i))continue;
        for(int j = 0; j < chunks.length-1; ++j){
          if(chunks[j].isNA0(i))continue OUTER;
          row[j] = chunks[j].at0(i);
        }
        final int mid = i % _xmodels.length;
        final GLMModel model = _xmodels[mid];
        final GLMValidation val = _xvals[mid];
        model.score0(row, preds);
        double response = chunks[chunks.length-1].at80(i);
        if(model._caseMode != CaseMode.none)
          response = model._caseMode.isCase(response, model._caseVal)?1:0;
        val.add(response, model.glm.family == Family.binomial?preds[1]:preds[0]);
      }
      for(GLMValidation val:_xvals)
        if(val.nobs > 0)val.avg_err = val.avg_err/val.nobs;
    }
    @Override public void reduce(GLMXValidationTask gval){
      for(int i = 0; i < _xvals.length; ++i)
        _xvals[i].add(gval._xvals[i]);}
    @Override public void postGlobal(){
      for(int i = 0; i < _xmodels.length; ++i){
        _xvals[i].finalize_AIC_AUC();
        _xmodels[i].setValidation(0, _xvals[i]);
        Futures fs = new Futures();
        DKV.put(_xmodels[i]._selfKey, _xmodels[i],fs);
        _res = new GLMXValidation(_model, _xmodels,_lambdaIdx);
        _improved = _model.setAndTestValidation(_lambdaIdx, _res);
        DKV.put(_model._selfKey, _model);
        fs.blockForPending();
      }
    }
  }

  @Override
  public String toString(){
    final double [] beta = beta(), norm_beta = norm_beta();
    StringBuilder sb = new StringBuilder("GLM Model (key=" + _selfKey + " , trained on " + _dataKey + ", family = " + glm.family + ", link = " + glm.link + ", #iterations = " + iteration() + "):\n");
    final int cats = catOffsets.length-1;
    int k = 0;
    for(int i = 0; i < cats; ++i)
      for(int j = 1; j < _domains[i].length; ++j)
        sb.append(_names[i] + "." + _domains[i][j] + ": " + beta[k++] + "\n");
    final int nums = beta.length-k-1;
    for(int i = 0; i < nums; ++i)
      sb.append(_names[cats+i] + ": " + beta[k+i] + "\n");
    sb.append("Intercept: " + beta[beta.length-1] + "\n");
    return sb.toString();
  }
  public int rank() {return rank(best_lambda_idx);}
  public int rank(int lambdaIdx) {return submodels[lambdaIdx].rank;}
  @Override public void delete(){super.delete();}

  public void  setValidation(int lambdaIdx,GLMValidation val ){
    submodels[lambdaIdx].validation = val;
  }
  public boolean  setAndTestValidation(int lambdaIdx,GLMValidation val ){
    submodels[lambdaIdx].validation = val;
    if(lambdaIdx == 0 || rank(lambdaIdx) == 1)
      return true;
    double diff = submodels[best_lambda_idx].validation.residual_deviance - val.residual_deviance;
    if(diff/val.null_deviance >= 0.01)
      best_lambda_idx = lambdaIdx;
    return (diff >= 0);
  }

  /**
   * get beta coefficients in a map indexed by name
   * @return
   */
  public HashMap<String,Double> coefficients(){
    HashMap<String, Double> res = new HashMap<String, Double>();
    final double [] b = beta();
    if(b != null) for(int i = 0; i < b.length; ++i)res.put(coefficients_names[i],b[i]);
    return res;
  }
  private String [] coefNames(int n){
    final int cats = catOffsets.length-1;
    int k = 0;
    String [] res = new String[n];
    for(int i = 0; i < cats; ++i)
      for(int j = 1; j < _domains[i].length; ++j)
        res[k++] = _names[i] + "." + _domains[i][j];
    final int nums = n-k-1;
    for(int i = 0; i < nums; ++i)
      res[k+i] = _names[cats+i];
    assert k + nums == res.length-1;
    res[k+nums] = "Intercept";
    return res;
  }
}
