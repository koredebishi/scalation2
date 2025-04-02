
//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** @author  John Miller
 *  @version 2.0
 *  @date    Sun Jun 30 13:27:00 EDT 2024
 *  @see     LICENSE (MIT style license file).
 *
 *  @note    Model: Weighted Moving Average (not the same as MA in ARMA)
 */

package scalation
package modeling
package forecasting

import scalation.mathstat._

import Forecaster.rdot
import Example_Covid.loadData_y
import Example_LakeLevels.y

//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `WeightedMovingAverage` class provides basic time series analysis capabilities for
 *  WeightedMovingAverage models.  WeightedMovingAverage models are often used for forecasting.
 *  Given time series data stored in vector y, its next value y_t = weighted mean of last q values.
 *
 *      y_t = weighted-mean (y_t-1, ..., y_t-q) + e_t
 *
 *  where y_t is the value of y at time t and e_t is the residual/error term.
 *  @param y        the response vector (time series data) 
 *  @param hh       the maximum forecasting horizon (h = 1 to hh)
 *  @param tRng     the time range, if relevant (time index may suffice)
 *  @param hparam   the hyper-parameters (defaults to SimpleMovingAverage.hp)
 *  @param bakcast  whether a backcasted value is prepended to the time series (defaults to false)
 */
class WeightedMovingAverage (y: VectorD, hh: Int, tRng: Range = null,
                             hparam: HyperParameter = SimpleMovingAverage.hp,
                             bakcast: Boolean = false)
      extends Forecaster (y, hh, tRng, hparam, bakcast):

    private val flaw = flawf ("WeightedMovingAverage")                  // flaw function
    private val q    = hparam("q").toInt                                // take mean of last q values
    private val u    = hparam("u").toDouble                             // u = 0 => flat, 1 => linear weights

    b         = WeightedMovingAverage.weights (q, u)                    // combination of linear/flat weights
    modelName = s"WeightedMovingAverage($q)"

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Predict a value for y_t using the 1-step ahead forecast.
     *
     *      y_t = f (y_t-1, ...) = weighted mean of last q values    (weighted moving average model)
     *
     *  @param t   the time point being predicted
     *  @param y_  the actual values to use in making predictions (mean (inclusive, exclusice))
     */
    override def predict (t: Int, y_ : VectorD): Double = rdot (b, y_, t-1)

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Produce a vector of size hh, h = 1 to hh-steps ahead forecasts for the model,
     *  i.e., forecast the following time points:  t+1, ..., t+h.
     *  Intended to work with rolling validation (analog of predict method).
     *  @param t   the time point from which to make forecasts
     *  @param y_  the actual values to use in making predictions
     */
    override def forecast (t: Int, y_ : VectorD = yb): VectorD =
        val yh = new VectorD (hh)                                       // hold forecasts for each horizon
        for h <- 1 to hh do
            val pred = rdot (b, yf, t, h-1)                             // slide in prior forecasted values
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
    override def forecastAt (h: Int, y_ : VectorD = yb): VectorD =
        if h < 2 then flaw ("forecastAt", s"horizon h = $h must be at least 2")

        for t <- y_.indices do                                          // make forecasts over all time points for horizon h
            yf(t, h) = rdot (b, yf, t, h-1)                             // record in forecast matrix
        yf(?, h)                                                        // return the h-step ahead forecast vector
    end forecastAt

end WeightedMovingAverage


//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `WeightedMovingAverage` companion object provides factory methods for the
 *  `WeightedMovingAverage` class.
 */
object WeightedMovingAverage:

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Create a `WeightedMovingAverage` object.
     *  @param y       the response vector (time series data)
     *  @param hh      the maximum forecasting horizon (h = 1 to hh)
     *  @param tRng    the time range, if relevant (time index may suffice)
     *  @param hparam  the hyper-parameters
     */
    def apply (y: VectorD, hh: Int, tRng: Range = null,
               hparam: HyperParameter = SimpleMovingAverage.hp): WeightedMovingAverage =
        new WeightedMovingAverage (y, hh, tRng, hparam)
    end apply

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Calculate the weight vector used for computing the weighted average.
     *  param q  the number weights to compute
     *  param u  factor indicating how much to have linear vs. flat weights
     */
    def weights (q: Int, u: Double): VectorD =
        val ww = VectorD.range (1, q+1)
        val w1 = ww / ww.sum                                            // linear weights
        val w2 = VectorD.one (q) / q                                    // flat weights
        w1 * u + w2 * (1 - u)                                           // combination of weights
    end weights

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Backcast to predict the value prior to the start (or offset i) of the time series.
     *  @param y  the response vector (time series data), a prefix suffices
     *  @param i  the index offset (defaults to 0)
     */
    def backcast (y_ : VectorD, i: Int = 0): Double =
        val q  = SimpleMovingAverage.hp ("q").toInt
        val u  = SimpleMovingAverage.hp ("u").toInt
        val yy = y_(i until q+i).reverse                                // first q (offset by i) values reversed
        val b  = weights (q, u)                                         // coefficients/weights
        b dot yy                                                        // weighted average
    end backcast

end WeightedMovingAverage


//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `weightedMovingAverageTest` main function tests the `WeightedMovingAverage` class on real data:
 *  Forecasting Lake Levels using In-Sample Testing (In-ST).
 *  Test forecasts (h = 1 to hh steps ahead forecasts).
 *  @see cran.r-project.org/web/packages/fpp/fpp.pdf
 *  > runMain scalation.modeling.forecasting.weightedMovingAverageTest
 */
@main def weightedMovingAverageTest (): Unit =

    val hh = 3                                                            // maximum forecasting horizon

    val mod = new WeightedMovingAverage (y, hh)                           // create model for time series data
    banner (s"In-ST Forecasts: ${mod.modelName} on LakeLevels Dataset")
    mod.trainNtest ()()                                                   // train and test on full dataset

    mod.forecastAll ()                                                    // forecast h-steps ahead (h = 1 to hh) for all y
    mod.diagnoseAll (y, mod.getYf)
    println (s"Final In-ST Forecast Matrix yf = ${mod.getYf}")

end weightedMovingAverageTest


//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `weightedMovingAverageTest2` main function tests the `WeightedMovingAverage` class on real data:
 *  Forecasting Lake Levels using Train-n-Test Split (TnT) with Rolling Validation.
 *  Test forecasts (h = 1 to hh steps ahead forecasts).
 *  @see cran.r-project.org/web/packages/fpp/fpp.pdf
 *  > runMain scalation.modeling.forecasting.weightedMovingAverageTest2
 */
@main def weightedMovingAverageTest2 (): Unit =

    val hh = 3                                                            // maximum forecasting horizon

    val mod = new WeightedMovingAverage (y, hh)                           // create model for time series data
    banner (s"TnT Forecasts: ${mod.modelName} on LakeLevels Dataset")
    mod.trainNtest ()()                                                   // train and test on full dataset

    mod.rollValidate ()                                                   // TnT with Rolling Validation
    println (s"Final TnT Forecast Matrix yf = ${mod.getYf}")

end weightedMovingAverageTest2


//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `weightedMovingAverageTest3` main function tests the `WeightedMovingAverage` class on real data:
 *  Forecasting COVID-19 using In-Sample Testing (In-ST).
 *  Test forecasts (h = 1 to hh steps ahead forecasts).
 *  > runMain scalation.modeling.forecasting.weightedMovingAverageTest3
 */
@main def weightedMovingAverageTest3 (): Unit =

    val yy = loadData_y ()
//  val y  = yy                                                           // full
    val y  = yy(0 until 116)                                              // clip the flat end
    val hh = 6                                                            // maximum forecasting horizon

    val mod = new WeightedMovingAverage (y, hh)                           // create model for time series data
    banner (s"In-ST Forecasts: ${mod.modelName} on COVID-19 Dataset")
    mod.trainNtest ()()                                                   // train and test on full dataset

    mod.forecastAll ()                                                    // forecast h-steps ahead (h = 1 to hh) for all y
    mod.diagnoseAll (y, mod.getYf)
    println (s"Final In-ST Forecast Matrix yf = ${mod.getYf}")

end weightedMovingAverageTest3


//:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `weightedMovingAverageTest4` main function tests the `WeightedMovingAverage` class on real data:
 *  Forecasting COVID-19 using Train-n-Test Split (TnT) with Rolling Validation.
 *  Test forecasts (h = 1 to hh steps ahead forecasts).
 *  > runMain scalation.modeling.forecasting.weightedMovingAverageTest4
 */
@main def weightedMovingAverageTest4 (): Unit =

    val yy = loadData_y ()
//  val y  = yy                                                           // full
    val y  = yy(0 until 116)                                              // clip the flat end
    val hh = 6                                                            // maximum forecasting horizon

    val mod = new WeightedMovingAverage (y, hh)                           // create model for time series data
    banner (s"TnT Forecasts: ${mod.modelName} on COVID-19 Dataset")
    mod.trainNtest ()()

    mod.rollValidate ()                                                   // TnT with Rolling Validation
    println (s"Final TnT Forecast Matrix yf = ${mod.getYf}")

end weightedMovingAverageTest4

