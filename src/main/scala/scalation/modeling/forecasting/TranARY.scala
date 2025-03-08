
//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** @author  John Miller
 *  @version 2.0
 *  @date    Sun Jun 30 13:27:00 EDT 2024
 *  @see     LICENSE (MIT style license file).
 *
 *  @note    Model: Transformed Auto-Regressive on lagged y (ARY) using OLS
 *
 *  @see     `scalation.modeling.TranRegression`
 */

package scalation
package modeling
package forecasting

import scala.math._

import scalation.mathstat._
import scalation.modeling.{Regression => REGRESSION}
//import scalation.modeling.{RidgeRegression => REGRESSION}
//import scalation.modeling.{LassoRegression => REGRESSION}

import MakeMatrix4TS._
import Example_Covid.loadData_y
import Example_LakeLevels.y

//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `TranARY` class provides basic time series analysis capabilities for
 *  TranARY models.  TranARY models are often used for forecasting.
 *  Given time series data stored in vector y, its next value y_t = combination of last p values.
 *
 *      tran (y_t) = b dot x_t + e_t
 *
 *  where y_t is the value of y at time t and e_t is the residual/error term.
 *  @see `TranARY.apply` for applying transformations (tran, itran)
 *  @param x        the data/input matrix (lagged columns of y) @see `ARY.apply`
 *  @param y        the response/output vector (time series data) 
 *  @param hh       the maximum forecasting horizon (h = 1 to hh)
 *  @param tRng     the time range, if relevant (time index may suffice)
 *  @param hparam   the hyper-parameters (defaults to `MakeMatrix4TS.hp`)
 *  @param itran    the inverse transformation to return to the original scale (defaults to expm1)
 *  @param bakcast  whether a backcasted value is prepended to the time series (defaults to false)
 */
class TranARY (x: MatrixD, y: VectorD, hh: Int, tRng: Range = null,
               hparam: HyperParameter = hp, val itran: FunctionS2S = expm1,
               bakcast: Boolean = false)
      extends Forecaster (y, hh, tRng, hparam, bakcast):                // no automatic backcasting, @see `ARY.apply`

    private val debug = debugf ("TranARY", true)                        // debug function
    private val flaw  = flawf ("TranARY")                               // flaw function
    private val p     = hparam("p").toInt                               // use the last p values (p lags)
    private val spec  = hparam("spec").toInt                            // additional terms: 0 => none, 1 => constant, 2 => linear
    private val reg   = new REGRESSION (x, y, null, hparam)             // delegate training to regression

    modelName = s"TranARY($p)"

    debug ("init", s"$modelName with additional term spec = $spec")
    debug ("init", s"[ x | y ] = ${x :^+ y}")

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Get the data/input matrix built from lagged y values.
     */
    override def getX: MatrixD = x

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Train/fit an `TranARY` model to the times-series data in vector y_.
     *  Estimate the coefficient vector b for a p-th order Auto-Regressive TranARY(p) model.
     *  Uses OLS Matrix Fatorization to determine the coefficients, i.e., the b (φ) vector.
     *  @param x_  the data/input matrix (e.g., full x)
     *  @param y_  the training/full response vector (e.g., full y)
     */
    override def train (x_ : MatrixD, y_ : VectorD): Unit =
        debug ("train", s"$modelName, x_.dims = ${x_.dims}, y_.dim = ${y_.dim}")
        reg.train (x_, y_)                                              // train the regression model
        b = reg.parameter                                               // coefficients from regression
    end train

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Train and test the forecasting model y_ = f(y-past) + e and report its QoF
     *  and plot its predictions.  Return the predictions and QoF.
     *  NOTE: must use `trainNtest_x` when an x matrix is used, such as in `TranARY`.
     *  @param x_  the training/full data/input matrix (defaults to full x)
     *  @param y_  the training/full response/output vector (defaults to full y)
     *  @param xx  the testing/full data/input matrix (defaults to full x)
     *  @param yy  the testing/full response/output vector (defaults to full y)
     */
    def trainNtest_x (x_ : MatrixD = x, y_ : VectorD = y)(xx: MatrixD = x, yy: VectorD = y): (VectorD, VectorD) =
        train (x_, y_)                                                  // train the model on training set
        val (yp, qof) = test (xx, yy)                                   // test the model on testing set
        println (report (qof))                                          // report on Quality of Fit (QoF)
        (yp, qof)
    end trainNtest_x

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Test FORECASTS of a forecasting model y_ = f(lags (y_)) + e
     *  and RETURN (1) aligned actual values, (2) its forecasts and (3) QoF vector.
     *  Testing may be in-sample (on the training set) or out-of-sample (on the testing set)
     *  as determined by the parameters passed in.  Note: must call train and forecastAll
     *  before testF.
     *  @param h   the forecasting horizon, number of steps ahead to produce forecasts
     *  @param y_  the testing/full response/output vector
     */
    override def testF (h: Int, y_ : VectorD): (VectorD, VectorD, VectorD) =
        val h_  = h - 1
        val yy  = y_(h_ until y_.dim)                                   // align the actual values
        val yfh = yf(?, h)(0 until y_.dim-h_)                           // column h of the forecast matrix
        println (s"yy.dim = ${yy.dim}, yfh.dim = ${yfh.dim}")
//      Forecaster.differ (yy, yfh)                                     // uncomment for debugging
        assert (yy.dim == yfh.dim)                                      // make sure the vector sizes agree

        new Plot (null, yy, yfh, s"testF: yy, yfh vs. t for $modelName @h = $h", lines = true)
        mod_resetDF (yy.dim)                                            // reset the degrees of freedom
        (yy, yfh, diagnose (yy, yfh))                                   // return actual, forecasted and QoF vectors
    end testF

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Models need to provide a means for updating the Degrees of Freedom (DF).
     *  @param size  the size of dataset (full, train, or test)
     */
    override def mod_resetDF (size: Int): Unit =
        val dfm = max (1, parameter.size - 1)                           // degrees of freedom for model
        debug ("mod_resetDF", s"dfm = $dfm, df = ${size-dfm}")
        resetDF (dfm, size - dfm)
    end mod_resetDF

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Predict a value for y_t using the 1-step ahead forecast.
     *
     *      y_t = b_0 + b_1 y_t-1 + b_2 y_t-2 + ... + b_p y_t-p = b dot x_t
     *
     *  FIX - parameter order is in conflict with AR models.
     *  @param t   the time point being predicted
     *  @param y_  the actual values to use in making predictions (ignored)
     */
    override def predict (t: Int, y_ : VectorD): Double =
        val yp = reg.predict (x(t))
//      debug ("predict", s"@t = $t, b = $b dot x(t) = ${x(t)} = yp = $yp vs. y_ = ${y_(t)}")
        yp
    end predict

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Produce a vector of size hh, h = 1 to hh-steps ahead forecasts for the model,
     *  i.e., forecast the following time points:  t+1, ..., t+h.
     *  Intended to work with rolling validation (analog of predict method).
     *  @param t   the time point from which to make forecasts
     *  @param y_  the actual values to use in making predictions
     */
    override def forecast (t: Int, y_ : VectorD = y): VectorD =
        val yh = new VectorD (hh)                                       // hold forecasts for each horizon
        for h <- 1 to hh do
            val xy   = forge (x(min (t+1, x.dim-1)), yf(t), h)          // FIX - why t+1
            val pred = reg.predict (xy)                                 // slide in prior forecasted values
//          debug ("forecast", s"h = $h, @t = $t, xy = $xy, yp = $pred, y_ = ${y_(t)}")
            yf(t, h) = pred                                             // record in forecast matrix
            yh(h-1)  = pred                                             // record forecasts for each horizon
        yh                                                              // return forecasts for all horizons
    end forecast

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Forecast values for all y_.dim time points at horizon h (h-steps ahead).
     *  Assign into FORECAST MATRIX and return the h-steps ahead forecast.
     *  Note, `predictAll` provides predictions for h = 1.
     *  @see `forecastAll` method in `Forecaster` trait.
     *  @param h   the forecasting horizon, number of steps ahead to produce forecasts
     *  @param y_  the actual values to use in making forecasts
     */
    override def forecastAt (h: Int, y_ : VectorD = y): VectorD =
        if h < 2 then flaw ("forecastAt", s"horizon h = $h must be at least 2")

        for t <- y_.indices do                                          // make forecasts over all time points for horizon h
            val xy = forge (x(t), yf(t), h)
            val pred = reg.predict (xy)
//          debug ("forecastAt", s"h = $h, @t = $t, xy = $xy, yp = $pred, y_ = ${y_(t)}")
            yf(t, h) = pred                                             // record in forecast matrix
        yf(?, h)                                                        // return the h-step ahead forecast vector
    end forecastAt

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Forge a new vector from the first spec values of x, the last p-h+1 values
     *  of x (past values) and values 1 to h-1 from the forecasts.
     *  @param xx  the t-th row of the input matrix (lagged actual values)
     *  @param yy  the t-th row of the forecast matrix (forecasted future values)
     */
     def forge (xx: VectorD, yy: VectorD, h: Int): VectorD =
         val pp  = p + 1
         var xy  = xx(0 until spec) ++ xx (xx.dim+h-pp until xx.dim)
         val nyy = pp - xy.dim
         if nyy > 0 then  xy = xy ++ yy(h-nyy until h)
         xy
    end forge

end TranARY


//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `TranARY` companion object provides factory methods for the `TranARY` class.
 */
object TranARY:

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Create an `TranARY` object by building an input matrix x and then calling the
     *  constructor.
     *  @see `scalation.modeling.TranRegression` for several options for (tran, itran) pairs
     *  @param y       the response vector (time series data)
     *  @param hh      the maximum forecasting horizon (h = 1 to hh)
     *  @param tRng    the time range, if relevant (time index may suffice)
     *  @param hparam  the hyper-parameters
     *  @param tran    the transformation function (defaults to log1p)
     *  @param itran   the inverse transformation function to rescale predictions to original y scale (defaults to expm1)
     *  @param bakcast  whether a backcasted value is prepended to the time series (defaults to false)
     */
    def apply (y: VectorD, hh: Int, tRng: Range = null, hparam: HyperParameter = hp,
               tran: FunctionS2S = log1p, itran: FunctionS2S = expm1,
               bakcast: Boolean = false): TranARY =
        val p     = hparam("p").toInt                                   // use the last p values
        val spec  = hparam("spec").toInt                                // trend terms to include
        val lwave = hparam("lwave").toDouble                            // wavelength (distance between peaks)
        val yt    = y.map (tran)                                        // y transformed
        val xt    = makeMatrix4T (yt, spec, lwave, bakcast)             // trend terms
        val xl    = makeMatrix4L (yt, p, bakcast)                       // regular lag terms
        new TranARY (xt ++^ xl, yt, hh, tRng, hparam, itran, bakcast)   // hook for user to transform back
    end apply

end TranARY


//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `tranARYTest` main function tests the `TranARY` class on real data:
 *  Forecasting Lake Levels using In-Sample Testing (In-ST).
 *  Test forecasts (h = 1 to hh steps ahead forecasts).
 *  @see cran.r-project.org/web/packages/fpp/fpp.pdf
 *  > runMain scalation.modeling.forecasting.traARYTest
 */
@main def tranARYTest (): Unit =

    val hh = 3                                                          // maximum forecasting horizon

    val mod = TranARY (y, hh)                                           // create model for time series data
    banner (s"In-ST Forecasts: ${mod.modelName} on LakeLevels Dataset")
    mod.trainNtest_x ()()                                               // train and test on full dataset

    mod.forecastAll ()                                                  // forecast h-steps ahead (h = 1 to hh) for all y
    Forecaster.evalForecasts (mod, mod.getYb, hh)
    println (s"Final In-ST Forecast Matrix yf = ${mod.getYf}")

end tranARYTest


//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `tranARYTest2` main function tests the `TranARY` class on real data:
 *  Forecasting Lake Levels using Train-n-Test Split (TnT) with Rolling Validation.
 *  Test forecasts (h = 1 to hh steps ahead forecasts).
 *  @see cran.r-project.org/web/packages/fpp/fpp.pdf
 *  > runMain scalation.modeling.forecasting.tranARYTest2
 */
@main def tranARYTest2 (): Unit =

    val hh = 3                                                          // maximum forecasting horizon

    val mod = TranARY (y, hh)                                           // create model for time series data
    banner (s"TnT Forecasts: ${mod.modelName} on LakeLevels Dataset")
    mod.trainNtest_x ()()                                               // train and test on full dataset

    mod.rollValidate ()                                                 // TnT with Rolling Validation
    println (s"After Roll TnT Forecast Matrix yf = ${mod.getYf}")
    mod.diagnoseAll (mod.getY, mod.getYf, Forecaster.teRng (y.dim), 0)       // only diagnose on the testing set - tran
    mod.diagnoseAll (y, mod.getYf.map_ (mod.itran), Forecaster.teRng (y.dim), 0)  // only diagnose on the testing set - orig
    println (s"Final TnT Forecast Matrix yf = ${mod.getYf}")

end tranARYTest2


//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `tranARYTest3` main function tests the `TranARY` class on real data:
 *  Forecasting COVID-19 using In-Sample Testing (In-ST).
 *  Test forecasts (h = 1 to hh steps ahead forecasts).
 *  > runMain scalation.modeling.forecasting.tranARYTest3
 */
@main def tranARYTest3 (): Unit =

    val yy = loadData_y ()
//  val y  = yy                                                         // full
    val y  = yy(0 until 116)                                            // clip the flat end
    val hh = 6                                                          // maximum forecasting horizon

    for p <- 1 to 5 do                                                  // number of lags
        hp("p") = p  
        val mod = TranARY (y, hh)                                       // create model for time series data
        banner (s"In-ST Forecasts: ${mod.modelName} on COVID-19 Dataset")
        mod.trainNtest_x ()()                                           // train and test on full dataset

        mod.forecastAll ()                                              // forecast h-steps ahead (h = 1 to hh) for all y
        Forecaster.evalForecasts (mod, mod.getYb, hh)
        println (s"Final In-ST Forecast Matrix yf = ${mod.getYf}")
    end for

end tranARYTest3


//:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `tranARYTest4` main function tests the `TranARY` class on real data:
 *  Forecasting COVID-19 using Train-n-Test Split (TnT) with Rolling Validation.
 *  Test forecasts (h = 1 to hh steps ahead forecasts).
 *  > runMain scalation.modeling.forecasting.tranARYTest4
 */
@main def tranARYTest4 (): Unit =

    val yy = loadData_y ()
//  val y  = yy                                                         // full
    val y  = yy(0 until 116)                                            // clip the flat end
    val hh = 6                                                          // maximum forecasting horizon

    for p <- 2 to 2 do                                                  // number of lags
        hp("p") = p
        val mod = TranARY (y, hh)                                       // create model for time series data
        banner (s"TnT Forecasts: ${mod.modelName} on COVID-19 Dataset")
        mod.trainNtest_x ()()                                           // use customized trainNtest_

        mod.setSkip (0)
        mod.rollValidate ()                                             // TnT with Rolling Validation
        println (s"After Roll TnT Forecast Matrix yf = ${mod.getYf}")
        mod.diagnoseAll (mod.getY, mod.getYf, Forecaster.teRng (y.dim), 0)       // only diagnose on the testing set - tran
        mod.diagnoseAll (y, mod.getYf.map_ (mod.itran), Forecaster.teRng (y.dim), 0)  // only diagnose on the testing set - orig
        println (s"Final TnT Forecast Matrix yf = ${mod.getYf}")
    end for

end tranARYTest4

