
//:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** @author  John Miller
 *  @version 2.0
 *  @date    Mon Sep  2 14:37:55 EDT 2024
 *  @see     LICENSE (MIT style license file).
 *
 *  @note    Model: Vector AutoRegressive (VAR)
 *
 *  @see     phdinds-aim.github.io/time_series_handbook/03_VectorAutoregressiveModels/03_VectorAutoregressiveMethods.html
 *           www.lem.sssup.it/phd/documents/Lesson17.pdf
 *           Parameter/coefficient estimation: Multi-variate Ordinary Least Squares (OLS) or
 *                                             Generalized Least Squares (GLS)
 */

package scalation
package modeling
package forecasting
package multivar

import scala.runtime.ScalaRunTime.stringOf

import scalation.mathstat._
import scalation.modeling.neuralnet.{RegressionMV => REGRESSION}
//import scalation.modeling.neuralnet.{RidgeRegressionMV => REGRESSION}

import MakeMatrix4TS.hp

//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `VAR` class provides multi-variate time series analysis capabilities for VAR models.
 *  VAR models are similar to `ARX` models, except that the exogenous variables are treated
 *  as endogenous variables and are themselves forecasted.  Potentially having more
 *  up-to-date forecasted values feeding into multi-horizon forecasting can improve
 *  accuracy, but may also lead to compounding of forecast errors.
 *  Given multi-variate time series data stored in matrix y, its next value y_t = combination
 *  of last p vector values of y.
 *
 *      y_t = b dot x_t + e_t
 *
 *  where y_t is the value of y at time t and e_t is the residual/error term.
 *  @param y        the response/output matrix (multi-variate time series data)
 *  @param x        the input lagged time series data
 *  @param hh       the maximum forecasting horizon (h = 1 to hh)
 *  @param fname    the feature/variable names
 *  @param tRng     the time range, if relevant (time index may suffice)
 *  @param hparam   the hyper-parameters (defaults to `MakeMatrix4TS.hp`)
 *  @param bakcast  whether a backcasted value is prepended to the time series (defaults to false)
 */
class VAR (x: MatrixD, y: MatrixD, hh: Int, fname: Array [String] = null,
           tRng: Range = null, hparam: HyperParameter = hp,
           bakcast: Boolean = false)                                      // backcasted values only used in `buildMatrix4TS`
//    extends Forecaster_D (x, y, hh, tRng, hparam, bakcast):             // no automatic backcasting, @see `VAR.apply`
      extends Diagnoser (dfm = hparam("p").toInt, df = y.dim - hparam("p").toInt)
         with ForecastTensor (y, hh, tRng)
         with Model:

    private val debug = debugf ("VAR", true)                              // debug function
    private val flaw  = flawf ("VAR")                                     // flaw function
    private val p     = hparam("p").toInt                                 // use the last p values for each variable
    private val spec  = hparam("spec").toInt                              // trend terms: 0 - none, 1 - constant, 2 - linear, 3 - quadratic
                                                                          //              4 - sine, 5 cosine
    private val nneg  = hparam("nneg").toInt == 1                         // 0 => unrestricted, 1 => predictions must be non-negative
    private val n     = y.dim2                                            // the number of variables
    private var bb: MatrixD = null                                        // matrix of parameter values
    private val yf    = makeForecastTensor (y, hh)                        // make the forecast tensor

    private val reg   = new REGRESSION (x, y, fname, hparam)              // delegate training to multi-variate regression

    modelName = s"VAR($p, $n) on ${stringOf(fname)}"

    debug ("init", s"$modelName with additional term spec = $spec")
    debug ("init", s"x = $x")

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Get the data/input matrix built from lagged y vector values.
     */
    def getX: MatrixD = x

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Return the used response vector y (first colum in matrix).
     */
    def getY: VectorD = y(?, 0)

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Return the used response matrix y.  Mainly for derived classes where y is
     *  transformed.
     */
    override def getYY: MatrixD = y

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Return the feature/variable names.
     */
    def getFname: Array [String] = fname

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Train/fit an `VAR` model to the times-series data in vector y_.
     *  Estimate the coefficient matrix bb for a p,q-th order VAR(p, q) model.
     *  Uses OLS Matrix Factorization to determine the coefficients, i.e., the bb matrix.
     *  @param x_  the data/input matrix (e.g., full x)
     *  @param y_  the training/full response matrix (e.g., full y)
     */
    def train (x_ : MatrixD, y_ : MatrixD): Unit =
        debug ("train", s"$modelName, x_.dim = ${x_.dim}, y_.dim = ${y_.dim}")
        reg.train (x_, y_)                                                // train the multi-variate regression model
        bb = reg.parameter                                                // coefficients from regression
        debug ("train", s"parameter matrix bb = $bb")
    end train

    def train (x_ : MatrixD, y_ : VectorD): Unit =
        throw new UnsupportedOperationException ("train (MatrixD, VectorD) use the alternative train")
    end train

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Train and test the forecasting model y_ = f(y-past) + e and report its QoF
     *  and plot its predictions.  Return the predictions and QoF.
     *  NOTE: must use `trainNtest_x` when an x matrix is used, such as in `ARY`.
     *  @param x_  the training/full data/input matrix (defaults to full x)
     *  @param y_  the training/full response/output vector (defaults to full y)
     *  @param xx  the testing/full data/input matrix (defaults to full x)
     *  @param yy  the testing/full response/output vector (defaults to full y)
     */
    def trainNtest_x (x_ : MatrixD = x, y_ : MatrixD = y)(xx: MatrixD = x, yy: MatrixD = y): (MatrixD, MatrixD) =
        train (x_, y_)                                                    // train the model on training set
        val (yp, qof) = test (xx, yy)                                     // test the model on testing set
        for j <- qof.indices do println (report (qof(j)))                 // report on Quality of Fit (QoF)
        (yp, qof)
    end trainNtest_x

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Test PREDICTIONS of a forecasting model y_ = f(lags (y_)) + e
     *  and return its predictions and  QoF vector.  Testing may be in-sample
     *  (on the training set) or out-of-sample (on the testing set) as determined
     *  by the parameters passed in.  Note, must call train before test.
     *  Must override to get Quality of Fit (QoF).
     *  @param x_  the data/input matrix (ignored, pass null)
     *  @param y_  the actual testing/full response/output matrix
     */
    def test (x_ : MatrixD, y_ : MatrixD): (MatrixD, MatrixD) =
        val yp = predictAll (y_)                                          // make all predictions
        val yy = if bakcast then y_(1 until y_.dim)                       // align the actual values
                 else y_
        println (s"yy.dim = ${yy.dim}, yp.dim = ${yp.dim}")
//      Forecaster.differ (yy, yfh)                                       // uncomment for debugging
        assert (yy.dim == yp.dim)                                         // make sure the vector sizes agree

        VAR.plotAll (yy, yp, s"test: $modelName")
        mod_resetDF (yy.dim)                                              // reset the degrees of freedom
        (yp, diagnose (yy, yp))                                           // return predicted and QoF vectors
    end test

    def test (x_ : MatrixD, y_ : VectorD): (VectorD, VectorD) =
        throw new UnsupportedOperationException ("test (MatrixD, VectorD) use the alternative test")
    end test

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Return the parameters.
     */
    def parameter: VectorD | MatrixD = bb

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Return the hyper-parameters.
     */
    def hparameter: HyperParameter = hparam

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Diagnose the quality of the model for each variable.
     *  @param yy  the matrix of actual values
     *  @param yp  the matrix of predicted values
     */
    def diagnose (yy: MatrixD, yp: MatrixD): MatrixD =
        MatrixD (for j <- yy.indices2 yield diagnose (yy(?, j), yp(?, j)))
    end diagnose

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Predict a value for y_t using the 1-step ahead forecast.
     *
     *      y_t = b_0 + b_1 y_t-1 + b_2 y_t-2 + ... + b_p y_t-p = b dot x_t
     *
     *  FIX - parameter order is in conflict with AR models.
     *  @param t   the time point being predicted
     *  @param y_  the actual values to use in making predictions (ignored)
     */
    def predict (t: Int, y_ : MatrixD): VectorD =
        val yp = rectify (reg.predict (x(t-1)), nneg)
        if t < y_.dim then
            debug ("predict", s"@t = $t, x(t-1) = ${x(t-1)}, yp = $yp vs. y_ = ${y_(t)}")
        yp
    end predict

    def predict (z: VectorD): Double | VectorD =
        throw new UnsupportedOperationException ("predict (VectorD) use the alternative predict")
    end predict

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Predict all values corresponding to the given time series vector y_.
     *  Update FORECAST TENSOR yf and return PREDICTION MATRIX yp as second (1) column
     *  of yf with last value removed.
     *  Note, yf(t, h, j) if the forecast to time t, horizon h, variable j
     *  @see `forecastAll` to forecast beyond horizon h = 1.
     *  @see `Forecaster.predictAll` for template implementation for vectors
     *  @param y_  the actual time series values to use in making predictions
     */
    def predictAll (y_ : MatrixD): MatrixD =
        if bakcast then
            for t <- 1 until y_.dim do yf(t-1, 1) = predict (t, y_)       // use model to make predictions
            yf(?, 1)(0 until y_.dim-1)                                    // return yp: first horizon only
        else
//          debug ("predictAll", s"y_.dim = ${y_.dim}, yf.dims = ${yf.dims}")
            for t <- 1 until yf.dim+1 do yf(t-1, 1) = predict (t, y_)     // skip t = 0
            yf(?, 1)
    end predictAll

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Forecast values for all y_.dim time points and all horizons (1 through hh-steps ahead).
     *  Record these in the FORECAST TENSOR yf, where
     *
     *      yf(t, h) = h-steps ahead forecast for y_t
     *
     *  @param y_  the actual values to use in making forecasts
     */
    def forecastAll (y_ : MatrixD): TensorD =
        for h <- 2 to hh do forecastAt (h, y_)                            // forecast k-steps into the future
        yf                                                                // return tensor of forecasted values
    end forecastAll

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Forecast values for all y_.dim time points at horizon h (h-steps ahead).
     *  Assign into FORECAST TENSOR and return the h-steps ahead forecast.
     *  Note, yf(t, h, j) if the forecast to time t, horizon h, variable j
     *  Note, `predictAll` provides predictions for h = 1.
     *  @see `forecastAll` method in `Forecaster` trait.
     *  @param h   the forecasting horizon, number of steps ahead to produce forecasts
     *  @param y_  the actual values to use in making forecasts
     */
    def forecastAt (h: Int, y_ : MatrixD = y): MatrixD =
        if h < 2 then flaw ("forecastAt", s"horizon h = $h must be at least 2")

        for t <- y_.indices do                                            // make forecasts over all time points for horizon h
            val xy   = forge (x(t), yf(t), h)                             // yf(t) = time t, all horizons, all variables
            val pred = rectify (reg.predict (xy), nneg)                   // slide in prior forecasted values
//          debug ("forecastAt", s"h = $h, @t = $t, xy = $xy, yp = $pred, y_ = ${y_(t)}")
            yf(t, h) = pred                                               // record in forecast matrix
        yf(?, h)                                                          // return the h-step ahead forecast vector
    end forecastAt

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Forge a new vector from the first spec values of x, the last p-h+1 values
     *  of x (past values), values 1 to h-1 from the forecasts, and available values
     *  from exogenous variables.
     *  @param xx  the t-th row of the input matrix (lagged actual values)
     *  @param yy  the t-th row of the forecast tensor (forecasted future values)
     *  @param h   the forecasting horizon, number of steps ahead to produce forecasts
     */
    def forge (xx: VectorD, yy: MatrixD, h: Int): VectorD =
        val xy = new VectorD (spec + n * p)
        xy(0 until spec) = xx(0 until spec)                               // get trend values

        var jend = spec + p                                               // ending j index
        for j <- 0 until n do                                             // for each variable
            val x_act   = xx(jend-(p+1-h) until jend)                     // get actual lagged y-values
            val nyy     = p - x_act.dim                                   // number of forecasted values needed
            val x_fcast = yy(h-nyy until h, j)                            // get forecasted y-values
            xy(jend-p until jend) = x_act ++ x_fcast
            jend += p
        end for
        xy
    end forge

    def crossValidate (k: Int, rando: Boolean): Array [Statistic] =
        throw new UnsupportedOperationException ("Use `rollValidate` instead of `crossValidate`")
    end crossValidate

end VAR


//:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `VAR` object supports regression for Multivariate Time Series data.
 *  Given a response matrix y, a predictor matrix x is built that consists of
 *  lagged y vectors.   Additional future response vectors are built for training.
 *      y_t = b dot x
 *  where x = [y_{t-1}, y_{t-2}, ... y_{t-lag}].
 */
object VAR:

//  private val debug = debugf ("VAR", true)                                   // debug function

    //:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Create a `VAR` object from a response matrix.  The input/data matrix
     *  x is formed from the lagged y vectors as columns in matrix x.
     *  @param y        the response/output matrix (multi-variate time series data)
     *  @param hh       the maximum forecasting horizon (h = 1 to hh)
     *  @param fname    the feature/variable names
     *  @param tRng     the time range, if relevant (time index may suffice)
     *  @param hparam   the hyper-parameters (defaults to `MakeMatrix4TS.hp`)
     */
    def apply (y: MatrixD, hh: Int, fname: Array [String] = null, tRng: Range = null,
               hparam: HyperParameter = hp, bakcast: Boolean = false): VAR =   // backcasted values only used in `buildMatrix4TS`
        val y_0   = y(?, 0)                                                    // the main endogenous variable (column zero)
        val yy    = y(?, 1 until y.dim2)                                       // the other endogenous variables (rest of the columns)
        val x     = ARX.buildMatrix (yy, y_0, hparam, bakcast)                 // add spec trend columns and p|q lags for each column of y
        new VAR (x, y, hh, fname, tRng, hparam)
    end apply

    //:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Split the x matrix and y matrix into training and testing sets.
     *  @param x      the x data/input matrix
     *  @param y      the y response/output matrix
     *  @param ratio  the ratio of the TESTING set to the full dataset (most common 70-30, 80-20)
     *
    def split_TnT (x: MatrixD, y: MatrixD, ratio: Double = 0.30): (MatrixD, MatrixD, MatrixD, MatrixD) =
        val n       = x.dim
        val tr_size = (n * (1.0 - ratio)).toInt
        println (s"VAR.split_TnT: tr_size = $tr_size, te_size = ${n - tr_size}")
        (x(0 until tr_size), y(0 until tr_size), x(tr_size until n), y(tr_size until n))
    end split_TnT
     */

    //:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Use rolling-validation to compute test Quality of Fit (QoF) measures
     *  by dividing the dataset into a TRAINING SET (tr) and a TESTING SET (te)
     *  as follows:  [ <-- tr_size --> | <-- te_size --> ]
     *  This version calls predict for one-step ahead out-of-sample forecasts.
     *  @see `RollingValidation`
     *  @param mod  the forecasting model being used (e.g., `VAR`)
     *  @param rc   the retraining cycle (number of forecasts until retraining occurs)
     *
    def rollValidate (mod: PredictorMV & Fit, rc: Int): Unit =
        val x       = mod.getX                                                 // get data/input matrix
        val y       = mod.getY                                                 // get response/output vector
        val te_size = RollingValidation.teSize (y.dim)                         // size of testing set
        val tr_size = y.dim - te_size                                          // size of initial training set
        debug ("rollValidate", s"train: tr_size = $tr_size; test: te_size = $te_size, rc = $rc")

        val yp = new MatrixD (te_size, y.dim2)                                 // y-predicted over testing set
        for i <- 0 until te_size do                                            // iterate through testing set
            val t = tr_size + i                                                // next time point to forecast
//          if i % rc == 0 then mod.train (x(0 until t), y(0 until t))         // retrain on sliding training set (growing set)
            if i % rc == 0 then mod.train (x(i until t), y(i until t))         // retrain on sliding training set (fixed size set)
            yp(i) = mod.predict (x(t-1))                                       // predict the next value
        end for

        val df = max (0, mod.parameter(0).dim - 1)                             // degrees of freedom for model
        mod.resetDF (df, te_size - df)                                         // reset degrees of freedom
        for k <- y.indices2 do
            val (t, yk) = RollingValidation.align (tr_size, y(?, k))           // align vectors
            val ypk = yp(?, k)
            banner (s"QoF for horizon ${k+1} with yk.dim = ${yk.dim}, ypk.dim = ${ypk.dim}")
            new Plot (t, yk, ypk, s"Plot yy, yp vs. t for horizon ${k+1}", lines = true)
            println (FitM.fitMap (mod.diagnose (yk, ypk), qoF_names))
        end for
    end rollValidate
     */

    //:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Plot actual vs. predicted values for all variables (columns of the matrices).
     *  @param y     the original un-expanded output/response matrix
     *  @param yp    the predicted values (one-step ahead forecasts) matrix
     *  @param name  the name of the model run to produce yp
     */
    def plotAll (y: MatrixD, yp: MatrixD, name: String): Unit =
        for j <- y.indices2  do
            new Plot (null, y(?, j).drop (1), yp(?, j), s"$name, y vs. yp @ var j = $j", lines = true)
    end plotAll

end VAR


//:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `vARTest` main function tests the `VAR` class.
 *  This test is used to CHECK that the `buildMatrix4TS` method (@see `apply`) is working correctly.
 *  May get NaN for some maximum lags (p) due to multi-collinearity.
 *  > runMain scalation.modeling.forecasting.multivar.vARTest
 */
@main def vARTest (): Unit =

    val m  = 30
    val z  = VectorD.range (1, m)
    val y  = MatrixD (z, -z + m)
    val hh = 3                                                                 // the forecasting horizon

    hp("q") = 2
    for p <- 5 to 5 do                                                         // autoregressive hyper-parameter p
        hp("p") = p
        banner (s"Test: VAR with $p lags")
        val mod = VAR (y, hh)                                                  // create model for time series data
        mod.trainNtest_x ()()                                                  // train the model on full dataset
        println (mod.summary)

//      val yy = mod.getY
//      val yp = mod.predict (mod.getX)
//      VAR.plotAll (yy, yp, mod.modelName)
//      for k <- yp.indices2 do
//          new Plot (null, yy(?, k), yp(?, k), s"yy_$k vs. yp_$k for ${mod.modelName} (h=${k+1}) with $p lags", lines = true)
    end for

end vARTest


//:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `vARTest2` main function tests the `VAR` class on real data:
 *  Forecasting Gas Furnace Data.  Performs In-Sample Testing.
 *  > runMain scalation.modeling.forecasting.multivar.vARTest2
 */
@main def vARTest2 (): Unit =

    import Example_GasFurnace._

    val hh   = 4                                                               // forecasting horizon
    val LAGS = 5                                                               // number of lags
    hp("p") = LAGS
    hp("q") = 2

    val y = Example_GasFurnace.loadData_yy (header)
    println (s"y.dims = ${y.dims}")

    banner ("Test In-Sample VAR on GasFurnace Data")
    val mod = VAR (y, hh, header)                                              // create model for time series data
    val (yp, qof) = mod.trainNtest_x ()()                                      // train on full and test on full
    println (mod.summary)
    val yy_ = y(1 until y.dim)                                                 // can't forecast first values at t = 0
    VAR.plotAll (yy_, yp, mod.modelName)

end vARTest2


//:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `vARTest3` main function tests the `VAR` class on real data:
 *  Forecasting COVID-19 Weekly Data.  Performs In-Sample Testing.
 *  Goal:  Find the variable that works best with "new_deaths"
 *  > runMain scalation.modeling.forecasting.multivar.vARTest3
 */
@main def vARTest3 (): Unit =

    val hh   = 6                                                               // maximum forecasting horizon
    val LAGS = 2                                                               // number of lags
    hp("p") = LAGS
    hp("q") = 2

    val vars = Array ("new_deaths", "icu_patients")
    val yy   = Example_Covid.loadData_yy (vars)
//  val y    = yy                                                              // full
    val y    = yy(0 until 116)                                                 // clip the flat end
    println (s"y.dims = ${y.dims}")

    for j <- vars.indices do
        new Plot (null, y(?, j), null, s"y_$j (${vars(j)}) vs. t", lines = true)

    banner ("Test In-Sample VAR on COVID-19 Weekly Data")
    val mod = VAR (y, hh)                                                      // create model for time series data
    val (yp, qof) = mod.trainNtest_x ()()                                      // train on full and test on full
//  println (mod.summary ())
//  val yy_ = y(1 until y.dim)                                                 // can't forecast first values at t = 0
//  VAR.plotAll (yy_, yp, mod.modelName)

/*
    banner (s"Feature Selection Technique: Stepwise")
    val (cols, rSq) = mod.stepRegressionAll (cross = false)                    // R^2, R^2 bar, sMAPE, NA
    val k = cols.size
    println (s"k = $k, n = ${mod.getX.dim2}")
    new PlotM (null, rSq.transpose, Array ("R^2", "R^2 bar", "sMAPE", "NA"),
               s"R^2 vs n for VAR with tech", lines = true)

    banner ("Feature Importance")
    println (s"Stepwise: rSq = $rSq")
*/
//  val imp = mod.importance (cols.toArray, rSq)
//  for (c, r) <- imp do println (s"col = $c, \t ${header(c)}, \t importance = $r")

end vARTest3


//:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `vARTest4` main function tests the `VAR` class on real data:
 *  Forecasting COVID-19 Weekly Data.  Performs In-Sample Testing.
 *  Goal:  Find the four variables that works best with "new_deaths"
 *  > runMain scalation.modeling.forecasting.multivar.vARTest4
 */
@main def vARTest4 (): Unit =

    val LAGS = 5                                                               // number of lags
    val hh   = 6                                                               // forecasting horizon

    val vars = Array ("new_deaths", "icu_patients", "hosp_patients", "new_tests", "people_vaccinated")
    val yy = Example_Covid.loadData_yy (vars)
    val iskip = yy(?, 0).indexWhere (_ >= 6.0)                                 // find day with at least 6 deaths
    println (s"iskip = $iskip is first day with at least 6 deaths")
    val y = yy(iskip until yy.dim)                                             // trim away the first iskip rows
    println (s"y.dims = ${y.dims}")

    banner ("Test In-Sample VAR on COVID-19 Weekly Data")
    hp("p") = LAGS
    hp("q") = 2
    val mod = VAR (y, hh)                                                      // create model for time series data - with exo
    val (yp, qof) = mod.trainNtest_x ()()                                      // train on full and test on full
//  println (mod.summary ())
//  val yy_ = y(1 until y.dim)                                                 // can't forecast first values at t = 0
//  VAR.plotAll (yy_, yp, mod.modelName)

//  val tech = SelectionTech.Forward                                           // pick one feature selection technique
//  val tech = SelectionTech.Backward
//  val tech = SelectionTech.Stepwise

/*
    banner (s"Feature Selection Technique: $tech")
    val (cols, rSq) = mod.selectFeatures (tech, cross = false)                 // R^2, R^2 bar, sMAPE, NA
    val k = cols.size
    println (s"k = $k, n = ${mod.getX.dim2}")
    new PlotM (null, rSq.transpose, Array ("R^2", "R^2 bar", "sMAPE", "NA"),
               s"R^2 vs n for VAR with tech", lines = true)

    banner ("Feature Importance")
    println (s"$tech: rSq = $rSq")
*/
//  val imp = mod.importance (cols.toArray, rSq)
//  for (c, r) <- imp do println (s"col = $c, \t ${header(c)}, \t importance = $r")

end vARTest4


//:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `vARTest5` main function tests the `VAR` class on real data:
 *  Forecasting COVID-19 Weekly Data. Does TnT Testing on endogenous and exogenous variables.
 *  Determine the terms to include in the model using Stepwise on In-Sample.
 *  > runMain scalation.modeling.forecasting.multivar.vARTest5
 */
@main def vARTest5 (): Unit =

    val LAGS = 5                                                               // number of lags
    val hh   = 6                                                               // forecasting horizon

    val vars = Array ("new_deaths", "icu_patients", "hosp_patients", "new_tests", "people_vaccinated")
    val yy = Example_Covid.loadData_yy (vars)
    val iskip = yy(?, 0).indexWhere (_ >= 6.0)                                 // find day with at least 6 deaths
    println (s"iskip = $iskip is first day with at least 6 deaths")
    val y = yy(iskip until yy.dim)                                             // trim away the first iskip rows
    println (s"y.dims = ${y.dims}")

    hp("p") = LAGS
    hp("q") = 2
    banner ("Test In-Sample VAR on COVID-19 Weekly Data")
    val mod = VAR (y, hh)                                                      // create model for time series data - with exo
    val (yp, qof) = mod.trainNtest_x ()()                                      // train on full and test on full
//  println (mod.summary ())
//  val yy_ = y(1 until y.dim)                                                 // can't forecast first values at t = 0
//  VAR.plotAll (yy_, yp, mod.modelName)

//  val tech = SelectionTech.Forward                                           // pick one feature selection technique
//  val tech = SelectionTech.Backward
//  val tech = SelectionTech.Stepwise

/*
    banner (s"Feature Selection Technique: $tech")
    val (cols, rSq) = mod.selectFeatures (tech, cross = false)                 // R^2, R^2 bar, sMAPE, NA
    val k = cols.size
    println (s"k = $k, n = ${mod.getX.dim2}")
    new PlotM (null, rSq.transpose, Array ("R^2", "R^2 bar", "sMAPE", "NA"),
               s"R^2 vs n for VAR with tech", lines = true)

    banner ("Feature Importance")
    println (s"$tech: rSq = $rSq")
*/
//  val imp = mod.importance (cols.toArray, rSq)
//  for (c, r) <- imp do println (s"col = $c, \t ${header(c)}, \t importance = $r")

/*
    banner ("Run TnT on Best model")
    val bmod = mod.getBest._3                                                  // get the best model from feature selection
    val (x_, y_, xtest, ytest) = VAR.split_TnT (bmod.getX, bmod.getY)
    val (yptest, qoftest) = bmod.trainNtest_x (x_, y_)(xtest, ytest)           // train on (x_, y_) and test on (xtest, ytest)
    new Plot (null, ytest(?, 0), yptest(?, 0), s"${mod.modelName}, ytest vs. yptest", lines = true)
*/

end vARTest5


//:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `vARTest6` main function tests the `VAR` class on real data:
 *  Forecasting COVID-19 Weekly Data.  Does Rolling Validation on variables.
 *  Determine the terms to include in the model using Stepwise on In-Sample.
 *  > runMain scalation.modeling.forecasting.multivar.vARTest6
 */
@main def vARTest6 (): Unit =

    val LAGS = 5                                                               // number of lags
    val h    = 6                                                               // forecasting horizon

    val vars = Array ("new_deaths", "icu_patients", "hosp_patients", "new_tests", "people_vaccinated")
    val yy = Example_Covid.loadData_yy (vars)
    val iskip = yy(?, 0).indexWhere (_ >= 6.0)                                 // find day with at least 6 deaths
    println (s"iskip = $iskip is first day with at least 6 deaths")
    val y = yy(iskip until yy.dim)                                             // trim away the first iskip rows
    println (s"y.dims = ${y.dims}")

    hp("p") = LAGS
    hp("q") = 2
    banner ("Test In-Sample VAR on COVID-19 Weekly Data")
    val mod = VAR (y, h)                                                       // create model for time series data - with exo
    val (yp, qof) = mod.trainNtest_x ()()                                      // train on full and test on full
//  println (mod.summary ())
//  val yy_ = y(1 until y.dim)                                                 // can't forecast first values at t = 0
//  VAR.plotAll (yy_, yp, mod.modelName)

//  val tech = SelectionTech.Forward                                           // pick one feature selection technique
//  val tech = SelectionTech.Backward
//  val tech = SelectionTech.Stepwise

/*
    banner (s"Feature Selection Technique: $tech")
    val (cols, rSq) = mod.selectFeatures (tech, cross = false)                 // R^2, R^2 bar, sMAPE, NA
    val k = cols.size
    println (s"k = $k, n = ${mod.getX.dim2}")
    new PlotM (null, rSq.transpose, Array ("R^2", "R^2 bar", "sMAPE", "NA"),
               s"R^2 vs n for VAR with tech", lines = true)

    banner ("Feature Importance")
    println (s"$tech: rSq = $rSq")
//  val imp = mod.importance (cols.toArray, rSq)
//  for (c, r) <- imp do println (s"col = $c, \t ${header(c)}, \t importance = $r")

    banner ("Run Rolling Validation on VAR Best model")
    val bmod = mod.getBest._3                                                  // get the best model from feature selection
    VAR.rollValidate (bmod, 1)
*/

end vARTest6

