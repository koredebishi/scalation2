
//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** @author  John Miller
 *  @version 2.0
 *  @date    Sun Jun 30 13:27:00 EDT 2024
 *  @see     LICENSE (MIT style license file).
 *
 *  @note    Model: Auto-Regressive on lagged y (SARY) using OLS
 *
 *  @see `scalation.modeling.Regression`
 *  @see `scalation.modeling.forecasting.ARX` when exogenous variable are needed
 */

package scalation
package modeling
package forecasting

import scalation.mathstat._

import MakeMatrix4TS._

//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `SARY` class provides basic time series analysis capabilities for SARY models.
 *  SARY models utilize multiple linear regression based on lagged values of y.
 *  Given time series data stored in vector y, its next value y_t = combination of last
 *  p values of y.
 *
 *      y_t = b dot x_t + e_t
 *
 *  where y_t is the value of y at time t and e_t is the residual/error term.
 *  @param x        the data/input matrix (lagged columns of y) @see `SARY.apply`
 *  @param y        the response/output vector (time series data) 
 *  @param hh       the maximum forecasting horizon (h = 1 to hh)
 *  @param fname    the feature/variable names
 *  @param tRng     the time range, if relevant (time index may suffice)
 *  @param hparam   the hyper-parameters (defaults to `MakeMatrix4TS.hp`)
 *  @param bakcast  whether a backcasted value is prepended to the time series (defaults to false)
 */
class SARY (x: MatrixD, y: VectorD, hh: Int, fname: Array [String],
           tRng: Range = null, hparam: HyperParameter = hp,
           bakcast: Boolean = false)                                    // backcast value used only `MakeMatrix4TS`
      extends ARY (x, y, hh, fname, tRng, hparam, bakcast):             // no automatic backcasting, @see `SARY.apply`

    private val debug = debugf ("SARY", true)                           // debug function
    private val sp    = hparam("sp").toInt                              // the seasonal period
    private val ps    = hparam("ps").toInt                              // use the last ps seasonal values (ps seasonal lags)

    modelName = s"SARY($p, $ps @ $sp)"

    debug ("init", s"$modelName with additional term spec = $spec")
    debug ("init", s"[ x | y ] = ${x :^+ y}")

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Forge a new vector from the first spec values of x, the last p-h+1 values
     *  of x (past values) and recent values 1 to h-1 from the forecasts.
     *  @param xx  the t-th row of the input matrix (lagged actual values)
     *  @param yy  the t-th row of the forecast matrix (forecasted future values)
     *  @param h   the forecasting horizon, number of steps ahead to produce forecasts
     */
    override def forge (xx: VectorD, yy: VectorD, h: Int): VectorD =
        val n_endo   = spec + ps + p                                    // number of trend + seasonal + endogenous values
        val x_trend  = xx(0 until spec)                                 // get trend values
        val xs_act   = xx(spec until spec + ps)                         // get actual seasonally lagged y-values
        val x_act    = xx(n_endo-(p+1-h) until n_endo)                  // get actual lagged y-values (endogenous)
        val nyy      = p - x_act.dim                                    // number of forecasted values needed
//      println (s"forge: h = $h, n_nedo = $n_endo, [ ${x_trend.dim}, ${x_act.dim} ], nyy = $nyy")
        val x_fcast  = yy(h-nyy until h)                                // get forecasted y-values
//      val xs_fcast = getYS (yy, p, sp, ps, xs_act.dim)                // FIX get forecasted seasonal y-values
        x_trend ++ xs_act ++ x_act ++ x_fcast                           // FIX assumes all actual seasonal values are used
    end forge

// Issue: if p >= sp then some seasonal values are redundant, so would not be in built matrix
// Issue: if h >= sp then some seasonal values would be future values (data leakage)

end SARY


//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `SARY` companion object provides factory methods for the `SARY` class.
 */
object SARY:

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Create an `SARY` object by making/building an input matrix x and then calling the
     *  `SARY` constructor.
     *  @param y        the response vector (time series data)
     *  @param hh       the maximum forecasting horizon (h = 1 to hh)
     *  @param fname_   the feature/variable names
     *  @param tRng     the time range, if relevant (time index may suffice)
     *  @param hparam   the hyper-parameters
     *  @param bakcast  whether a backcasted value is prepended to the time series (defaults to false)
     */
    def apply (y: VectorD, hh: Int, fname_ : Array [String] = null,
               tRng: Range = null, hparam: HyperParameter = hp,
               bakcast: Boolean = false): SARY =
        val p     = hparam("p").toInt                                   // use the last p values
        val sp    = hparam("sp").toInt                                  // the seasonal period (time units until repetitive behavior) 
        val ps    = hparam("ps").toInt                                  // use the last ps seasonal values
        val spec  = hparam("spec").toInt                                // 0 - none, 1 - constant, 2 - linear, 3 -quadratic, 4 - sin, 5 = cos
        val lwave = hparam("lwave").toDouble                            // wavelength (distance between peaks)
        val xt    = makeMatrix4T (y, spec, lwave, bakcast)              // trend terms
        val xs    = makeMatrix4S (y, p, sp, ps, bakcast)                // seasonal lags terms
        val xl    = makeMatrix4L (y, p, bakcast)                        // regular lag terms
        val start = if xs.dim2 == ps then 1 else 2                      // first seasonal lag to use (not subsumed)
        val fname = if fname_ == null then formNames (spec, p, false, sp, start, ps)
                    else fname_
        new SARY (xt ++^ xs ++^ xl, y, hh, fname, tRng, hparam, bakcast)
    end apply

end SARY

import Example_Covid.loadData_y
import Example_LakeLevels.y

//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `RYTest` main function tests the `SARY` class on real data:
 *  Forecasting Lake Levels using In-Sample Testing (In-ST).
 *  Test forecasts (h = 1 to hh steps ahead forecasts).
 *  @see cran.r-project.org/web/packages/fpp/fpp.pdf
 *  > runMain scalation.modeling.forecasting.sARYTest
 */
@main def sARYTest (): Unit =

    val hh = 3                                                          // maximum forecasting horizon

    val mod = SARY (y, hh)                                               // create model for time series data
    banner (s"In-ST Forecasts: ${mod.modelName} on LakeLevels Dataset")
    mod.trainNtest_x ()()                                               // train and test on full dataset

    mod.forecastAll ()                                                  // forecast h-steps ahead (h = 1 to hh) for all y
    Forecaster.evalForecasts (mod, mod.getYb, hh)
    println (s"Final In-ST Forecast Matrix yf = ${mod.getYf}")

end sARYTest


//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `sARYTest2` main function tests the `SARY` class on real data:
 *  Forecasting Lake Levels using Train-n-Test Split (TnT) with Rolling Validation.
 *  Test forecasts (h = 1 to hh steps ahead forecasts).
 *  @see cran.r-project.org/web/packages/fpp/fpp.pdf
 *  > runMain scalation.modeling.forecasting.sARYTest2
 */
@main def sARYTest2 (): Unit =

    val hh = 3                                                          // maximum forecasting horizon

    val mod = SARY (y, hh)                                               // create model for time series data
    banner (s"TnT Forecasts: ${mod.modelName} on LakeLevels Dataset")
    mod.trainNtest_x ()()                                               // train and test on full dataset

    mod.rollValidate ()                                                 // TnT with Rolling Validation
    println (s"Final TnT Forecast Matrix yf = ${mod.getYf}")

end sARYTest2


//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `sARYTest3` main function tests the `SARY` class on real data:
 *  Forecasting COVID-19 using In-Sample Testing (In-ST).
 *  Test forecasts (h = 1 to hh steps ahead forecasts).
 *  > runMain scalation.modeling.forecasting.sARYTest3
 */
@main def sARYTest3 (): Unit =

    val yy = loadData_y ()
//  val y  = yy                                                         // full
    val y  = yy(0 until 116)                                            // clip the flat end
    val hh = 6                                                          // maximum forecasting horizon
    hp("lwave") = 20                                                    // wavelength (distance between peaks) 

    for p <- 2 to 5; s <- 1 to 2 do                                     // number of lags; trend
        hp("p")    = p                                                  // endo lags
        hp("spec") = s                                                  // trend specification: 0, 1, 2, 3, 5
        val mod = SARY (y, hh)                                           // create model for time series data
        banner (s"In-ST Forecasts: ${mod.modelName} on COVID-19 Dataset")
        mod.trainNtest_x ()()                                           // train and test on full dataset
        println (mod.summary ())                                        // statistical summary of fit

//      mod.setSkip (p)                                                 // full AR-formula available when t >= p
        mod.forecastAll ()                                              // forecast h-steps ahead (h = 1 to hh) for all y
        mod.diagnoseAll (y, mod.getYf)
//      Forecaster.evalForecasts (mod, mod.getYb, hh)
//      println (s"Final In-ST Forecast Matrix yf = ${mod.getYf}")
//      println (s"Final In-ST Forecast Matrix yf = ${mod.getYf.shiftDiag}")
    end for

end sARYTest3


//:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `sARYTest4` main function tests the `SARY` class on real data:
 *  Forecasting COVID-19 using Train-n-Test Split (TnT) with Rolling Validation.
 *  Test forecasts (h = 1 to hh steps ahead forecasts).
 *  > runMain scalation.modeling.forecasting.sARYTest4
 */
@main def sARYTest4 (): Unit =

    val yy = loadData_y ()
//  val y  = yy                                                         // full
    val y  = yy(0 until 116)                                            // clip the flat end
    val hh = 6                                                          // maximum forecasting horizon
    hp("lwave") =     20                                                // wavelength (distance between peaks) 

    for p <- 1 to 10; s <- 1 to 5 do                                    // number of lags; trend
        hp("p")     = p                                                 // endo lags
        hp("spec")  = s                                                 // trend specification: 0, 1, 2, 3, 5
        val mod = SARY (y, hh)                                           // create model for time series data
        banner (s"TnT Forecasts: ${mod.modelName} on COVID-19 Dataset")
        mod.trainNtest_x ()()                                           // use customized trainNtest_x

        mod.setSkip (0)
        mod.rollValidate ()                                             // TnT with Rolling Validation
        println (s"After Roll TnT Forecast Matrix yf = ${mod.getYf}")
        mod.diagnoseAll (y, mod.getYf, Forecaster.teRng (y.dim), 0)     // only diagnose on the testing set
//      println (s"Final TnT Forecast Matrix yf = ${mod.getYf}")
    end for

end sARYTest4


//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `sARYTest5` main function tests the `SARY` class on real data:
 *  Forecasting COVID-19 using In-Sample Testing (In-ST).
 *  Test forecasts (h = 1 to hh steps ahead forecasts).
 *  This version performs feature selection.
 *  > runMain scalation.modeling.forecasting.sARYTest5
 */
@main def sARYTest5 (): Unit =

    val yy = loadData_y ()
//  val y  = yy                                                         // full
    val y  = yy(0 until 116)                                            // clip the flat end
    val hh = 6                                                          // maximum forecasting horizon
    hp("p")     = 10                                                    // endo lags
    hp("spec")  = 5                                                     // trend specification: 0, 1, 2, 3, 5
    hp("lwave") = 20                                                    // wavelength (distance between peaks)

    val mod = SARY (y, hh)                                               // create model for time series data
    banner (s"In-ST Forecasts: ${mod.modelName} on COVID-19 Dataset")
    mod.trainNtest_x ()()                                               // train and test on full dataset
    println (mod.summary ())                                            // statistical summary of fit

    mod.forecastAll ()                                                  // forecast h-steps ahead (h = 1 to hh) for all y
    Forecaster.evalForecasts (mod, mod.getYb, hh)
    println (s"Final In-ST Forecast Matrix yf = ${mod.getYf}")

    banner ("Feature Selection Technique: Forward")
    val (cols, rSq) = mod.forwardSelAll ()                              // R^2, R^2 bar, sMAPE, R^2 cv
//  val (cols, rSq) = mod.backwardElimAll ()                            // R^2, R^2 bar, sMAPE, R^2 cv
    val k = cols.size
    println (s"k = $k")
    new PlotM (null, rSq.transpose, Array ("R^2", "R^2 bar", "sMAPE", "R^2 cv"),
               s"R^2 vs n for ${mod.modelName}", lines = true)
    println (s"rSq = $rSq")

end sARYTest5


//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `sARYTest6` main function tests the `SARY` class on real data:
 *  Forecasting COVID-19 using In-Sample Testing (In-ST).
 *  Test forecasts (h = 1 to hh steps ahead forecasts).
 *  > runMain scalation.modeling.forecasting.sARYTest6
 * 
@main def sARYTest6 (): Unit =

    val yy = loadData_y ()
//  val y  = yy                                                         // full
    val y  = yy(0 until 116)                                            // clip the flat end
    val hh = 6                                                          // maximum forecasting horizon
    hp("lwave") = 20                                                    // wavelength (distance between peaks)

    for p <- 1 to 5; s <- 1 to 1 do                                     // number of lags; trend
        hp("p")    = p                                                  // endo lags
        hp("spec") = s                                                  // trend specification: 0, 1, 2, 3, 5
        val mod = SARY.quadratic (y, hh)                                 // create model for time series data
        banner (s"In-ST Forecasts: ${mod.modelName} on COVID-19 Dataset")
        mod.trainNtest_x ()()                                           // train and test on full dataset
        println (mod.summary ())                                        // statistical summary of fit

//      mod.setSkip (p)                                                 // full AR-formula available when t >= p
        mod.forecastAll ()                                              // forecast h-steps ahead (h = 1 to hh) for all y
        mod.diagnoseAll (y, mod.getYf)
        Forecaster.evalForecasts (mod, mod.getYb, hh)
//      println (s"Final In-ST Forecast Matrix yf = ${mod.getYf}")
//      println (s"Final In-ST Forecast Matrix yf = ${mod.getYf.shiftDiag}")
    end for

end sARYTest6
 */

