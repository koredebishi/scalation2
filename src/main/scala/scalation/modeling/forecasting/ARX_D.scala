
//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** @author  John Miller
 *  @version 2.0
 *  @date    Sun Jun 30 13:27:00 EDT 2024
 *  @see     LICENSE (MIT style license file).
 *
 *  @note    Model: Auto-Regressive on lagged y and xe (ARX_D) using OLS - Direct Forecasting
 */

package scalation
package modeling
package forecasting

import scalation.mathstat._
import scalation.modeling.neuralnet.{RegressionMV => REGRESSION}

import Example_Covid.{loadData, response}
import MakeMatrix4TS._

//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `ARX_D` class provides basic time series analysis capabilities for
 *  ARX_D models.  ARX_D models are often used for forecasting.
 *  `ARX_D` uses DIRECT (as opposed to RECURSIVE) multi-horizon forecasting.
 *  Given time series data stored in vector y, its next value y_t = combination of last p values.
 *
 *      y_t = b dot x_t + e_t
 *
 *  where y_t is the value of y at time t and e_t is the residual/error term.
 *  @param x        the data/input matrix (lagged columns of y) @see `ARX_D.apply`
 *  @param y        the response/output matrix (column per horizon) (time series data) 
 *  @param hh       the maximum forecasting horizon (h = 1 to hh)
 *  @param n_exo    the number of exogenous variables
 *  @param fname    the feature/variable names
 *  @param tRng     the time range, if relevant (time index may suffice)
 *  @param hparam   the hyper-parameters (defaults to `MakeMatrix4TS.hp`)
 *  @param bakcast  whether a backcasted value is prepended to the time series (defaults to false)
 *  @param tForms   the map of transformations applied
 */
class ARX_D (x: MatrixD, y: MatrixD, hh: Int, n_exo: Int, fname: Array [String],
             tRng: Range = null, hparam: HyperParameter = hp,
             bakcast: Boolean = false,
             tForms: TransformMap = Map ("tForm_y" -> null))
      extends Forecaster_D (x, y, hh, tRng, hparam, bakcast):

    private val debug   = debugf ("ARX_D", true)                        // debug function
    protected val p     = hparam("p").toInt                             // use the last p endogenous values (p lags)
    protected val q     = hparam("q").toInt                             // use the last q exogenous values (q lags)
    protected val spec  = hparam("spec").toInt                          // trend terms: 0 - none, 1 - constant, 2 - linear, 3 - quadratic
                                                                        //              4 - sine, 5 cosine
    protected val nneg  = hparam("nneg").toInt == 1                     // 0 => unrestricted, 1 => predictions must be non-negative
    protected val reg   = new REGRESSION (x, y, fname, hparam)          // delegate training to multi-variate regression

    modelName = s"ARX_D($p, $q, $n_exo)"
    yForm = tForms("tForm_y").asInstanceOf [Transform]

    debug ("init", s"$modelName with $n_exo exogenous variables and additional term spec = $spec")
//  debug ("init", s"[ x | y ] = ${x ++^ y}")

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Train/fit an `ARX_D` model to the times-series data in vector y_.
     *  Estimate the coefficient mattrix bb for a p-th order Auto-Regressive ARX_D(p) model.
     *  Uses OLS Matrix Fatorization to determine the coefficients, i.e., the bb matrix.
     *  @param x_  the data/input matrix (e.g., full x)
     *  @param y_  the training/full response vector (e.g., full y)
     */
    def train_x (x_ : MatrixD, y_ : MatrixD): Unit =
        debug ("train_x", s"$modelName, x_.dim = ${x_.dim}, y_.dim = ${y_.dim}")
        reg.train (x_, y_)                                              // train the multi-variate regression model
        bb = reg.parameter                                              // coefficients from regression
    end train_x

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Produce a QoF summary for a model with diagnostics for each predictor 'x_j'
     *  and the overall Quality of Fit (QoF).
     *  @param x_      the testing/full data/input matrix
     *  @param fname_  the array of feature/variable names
     *  @param b_      the parameters/coefficients for the model
     *  @param vifs    the Variance Inflation Factors (VIFs)
     */
    override def summary (x_ : MatrixD = getX, fname_ : Array [String] = reg.getFname,
                          b_ : VectorD = b, vifs: VectorD = reg.vif ()): String =
        super.summary (x_, fname_, b_, vifs)                             // summary from `Fit`
    end summary

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Predict a value for y_t using the 1-step ahead forecast.
     *
     *      y_t = b_0 + b_1 y_t-1 + b_2 y_t-2 + ... + b_p y_t-p = b dot x_t
     *
     *  @param t   the time point being predicted
     *  @param y_  the actual values to use in making predictions (ignored)
     */
    def predict (t: Int, y_ : MatrixD): VectorD =
        val yp = rectify (reg.predict (x(t)), nneg)
//      if t < y_.dim then
//          debug ("predict", s"@t = $t, x(t) = ${x(t)}, yp = $yp vs. y_ = ${y_(t)}")
        yp
    end predict

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Produce a vector of size hh, h = 1 to hh-steps ahead forecasts for the model,
     *  i.e., forecast the following time points:  t+1, ..., t+h.
     *  Intended to work with rolling validation (analog of predict method).
     *  @param t   the time point from which to make forecasts
     *  @param y_  the actual values to use in making predictions
     */
    override def forecast (t: Int, y_ : VectorD): VectorD =
        val pred = predict (t, MatrixD (y_).transpose)
        for h <- 1 to hh do yf(t, h) = pred(h-1)
        pred                                                         // yh is pred
    end forecast

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Forecast values for all y_.dim time points all horizons h (h-steps ahead).
     *  Assign into FORECAST MATRIX and return the forecast matrix.
     *  @param y_  the matrix of actual response values
     */
    override def forecastAll (y_ : MatrixD): MatrixD =
        for t <- y_.indices do
            val pred = predict (t, y_)
            for h <- 1 to hh do yf(t, h) = pred(h-1)
        yf
    end forecastAll

end ARX_D


//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `ARX_D` companion object provides factory methods for the `ARX_D` class.
 */
object ARX_D extends MakeMatrix4TS:

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Create an `ARX_D` object by building an input matrix x and then calling the constructor.
     *  @param xe       the matrix of exogenous variable values
     *  @param y        the response vector (time series data)
     *  @param hh       the maximum forecasting horizon (h = 1 to hh)
     *  @param fname_   the feature/variable names
     *  @param tRng     the time range, if relevant (time index may suffice)
     *  @param hparam   the hyper-parameters (defaults for `MakeMatrix4TS.hp`)
     *  @param fEndo    the array of functions used to transform endogenous variables
     *  @param fExo     the array of functions used to transform exogenous variables
     *  @param bakcast  whether a backcasted value is prepended to the time series (defaults to false)
     */
    def apply (xe: MatrixD, y: VectorD, hh: Int, fname_ : Array [String] = null,
               tRng: Range = null, hparam: HyperParameter = hp,
               fEndo: Array [Transform] = null, fExo: Array [Transform] = null,
               bakcast: Boolean = false): ARX_D =

        val xy    = ARX.buildMatrix (xe, y, hparam, bakcast)
        val yy    = makeMatrix4Y (y, hh, bakcast)
        val fname = if fname_ == null then ARX.formNames (xe.dim2, hparam) else fname_
        new ARX_D (xy, yy, hh, xe.dim2, fname, tRng, hparam, bakcast)
    end apply

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Create an `ARX_D` object by building an input matrix xy and then calling the
     *  `ARX_D` constructor.  Also rescale the input data.
     *  @param xe       the matrix of exogenous variable values
     *  @param y        the endogenous/response vector (main time series data)
     *  @param hh       the maximum forecasting horizon (h = 1 to hh)
     *  @param tRng     the time range, if relevant (time index may suffice)
     *  @param hparam   the hyper-parameters
     *  @param fEndo    the array of functions used to transform endogenous variables
     *  @param fExo     the array of functions used to transform exogenous variables
     *  @param bakcast  whether a backcasted value is prepended to the time series (defaults to false)
     *  @param tForm    the z-transform (rescale to standard normal)
     */
    def rescale (xe: MatrixD, y: VectorD, hh: Int, fname_ : Array [String] = null,
                 tRng: Range = null, hparam: HyperParameter = hp,
                 fEndo: Array [Transform] = null, fExo: Array [Transform] = null,
                 bakcast: Boolean = false,
                 tForm: VectorD | MatrixD => Transform = x => zForm(x)): ARX_D =

        val tForm_y = tForm(y)
        if tForm_y.getClass.getSimpleName == "zForm" then hparam("nneg") = 0
        val y_scl   = tForm_y.f(y)
        val tForms: TransformMap = Map ("tForm_y" -> tForm_y)

        val xy    = ARX.buildMatrix (xe, y_scl, hparam, bakcast)
        val yy    = makeMatrix4Y (y_scl, hh, bakcast)
        val fname = if fname_ == null then formNames (xe.dim2, hparam) else fname_
        new ARX_D (xy, yy, hh, xe.dim2, fname, tRng, hparam, bakcast, tForms)
    end rescale

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Form an array of names for the features included in the model.
     *  @param n_exo  the number of exogenous variable
     *  @param hp_    the hyper-parameters
     *  @param n_fEn  the number of functions used to map endogenous variables
     *  @param n_fEx  the number of functions used to map exogenous variables
     */
    def formNames (n_exo: Int, hp_ : HyperParameter, n_fEn: Int, n_fEx: Int): Array [String] =
        ARX.formNames (n_exo, hp_, n_fEn, n_fEx)
    end formNames

end ARX_D


//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `aRX_DTest` main function tests the `ARX_D` class on real data:
 *  Forecasting Lake Levels using In-Sample Testing (In-ST).
 *  Test forecasts (h = 1 to hh steps ahead forecasts).
 *  @see cran.r-project.org/web/packages/fpp/fpp.pdf
 *  > runMain scalation.modeling.forecasting.aRX_DTest
 *
@main def aRX_DTest (): Unit =

    val hh = 3                                                          // maximum forecasting horizon

    val mod = ARX_D (y, hh)                                             // create model for time series data
    banner (s"In-ST Forecasts: ${mod.modelName} on LakeLevels Dataset")
    mod.trainNtest_x ()()                                               // train and test on full dataset

    mod.forecastAll ()                                                  // forecast h-steps ahead (h = 1 to hh) for all y
    println (s"Final In-ST Forecast Matrix yf = ${mod.getYf}")

end aRX_DTest
 */


//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `aRX_DTest2` main function tests the `ARX_D` class on real data:
 *  Forecasting Lake Levels using Train-n-Test Split (TnT) with Rolling Validation.
 *  Test forecasts (h = 1 to hh steps ahead forecasts).
 *  @see cran.r-project.org/web/packages/fpp/fpp.pdf
 *  > runMain scalation.modeling.forecasting.aRX_DTest2
 *
@main def aRX_DTest2 (): Unit =

    val hh = 3                                                          // maximum forecasting horizon

    val mod = ARX_D (y, hh)                                             // create model for time series data
    banner (s"TnT Forecasts: ${mod.modelName} on LakeLevels Dataset")
    mod.trainNtest_x ()()                                               // train and test on full dataset

    mod.rollValidate ()                                                 // TnT with Rolling Validation
    println (s"Final TnT Forecast Matrix yf = ${mod.getYf}")

end aRX_DTest2
 */


//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `aRX_DTest3` main function tests the `ARX_D` class on real data:
 *  Forecasting COVID-19 using In-Sample Testing (In-ST).
 *  Test forecasts (h = 1 to hh steps ahead forecasts).
 *  > runMain scalation.modeling.forecasting.aRX_DTest3
 */
@main def aRX_DTest3 (): Unit =

    val exo_vars  = Array ("icu_patients")
//  val exo_vars  = Array ("icu_patients", "hosp_patients", "new_tests", "people_vaccinated")
    val (xxe, yy) = loadData (exo_vars, response)
    println (s"xxe.dims = ${xxe.dims}, yy.dim = ${yy.dim}")

//  val xe = xxe                                                        // full
    val xe = xxe(0 until 116)                                           // clip the flat end
//  val y  = yy                                                         // full
    val y  = yy(0 until 116)                                            // clip the flat end
    val hh = 6                                                          // maximum forecasting horizon
    hp("lwave") = 20                                                    // wavelength (distance between peaks)

    for p <- 6 to 6; q <- 4 to 4; s <- 1 to 1 do                        // number of lags (endo, exo); trend
        hp("p")    = p                                                  // mumber of endo lags
        hp("q")    = q                                                  // mumber of exo lags
        hp("spec") = s                                                  // trend specification: 0, 1, 2, 3, 5
        val mod = ARX_D (xe, y, hh)                                     // create model for time series data
//        val mod = ARX_D.rescale (xe, y, hh)
        mod.inSampleTest ()                                             // In-sample Testing
        println (mod.summary ())                                        // statistical summary of fit  FIX - crashes
    end for

end aRX_DTest3


//:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `aRX_DTest4` main function tests the `ARX_D` class on real data:
 *  Forecasting COVID-19 using Train-n-Test Split (TnT) with Rolling Validation.
 *  Test forecasts (h = 1 to hh steps ahead forecasts).
 *  > runMain scalation.modeling.forecasting.aRX_DTest4
 */
@main def aRX_DTest4 (): Unit =

    val exo_vars  = Array ("icu_patients")
//  val exo_vars  = Array ("icu_patients", "hosp_patients", "new_tests", "people_vaccinated")
    val (xxe, yy) = loadData (exo_vars, response)
    println (s"xxe.dims = ${xxe.dims}, yy.dim = ${yy.dim}")

//  val xe = xxe                                                        // full
    val xe = xxe(0 until 116)                                           // clip the flat end
//  val y  = yy                                                         // full
    val y  = yy(0 until 116)                                            // clip the flat end
    val hh = 6                                                          // maximum forecasting horizon
    hp("lwave") = 20                                                    // wavelength (distance between peaks)

    for p <- 6 to 6; q <- 4 to 4; s <- 1 to 1 do                        // number of lags (endo, exo); trend
        hp("p")    = p                                                  // number of endo lags
        hp("q")    = q                                                  // number of exo lags
        hp("spec") = s                                                  // trend specification: 0, 1, 2, 3, 5
        val mod = ARX_D (xe, y, hh)                                     // create model for time series data
//        val mod = ARX_D.rescale (xe, y, hh)
        banner (s"TnT Forecasts: ${mod.modelName} on COVID-19 Dataset")
        mod.trainNtest_x ()()                                           // use customized trainNtest_x

        mod.setSkip (0)
        mod.rollValidate ()
//      println (s"After Roll TnT Forecast Matrix yf = ${mod.getYf}")
        mod.diagnoseAll (mod.getY, mod.getYf, Forecaster.teRng (y.dim))    // only diagnose on the testing set
//      println (s"Final TnT Forecast Matrix yf = ${mod.getYf}")
    end for

end aRX_DTest4

