
//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** @author  John Miller
 *  @version 2.0
 *  @date    Sun Feb 13 16:22:21 EST 2022
 *  @see     LICENSE (MIT style license file).
 *
 *  @note    Model: NeuralNet_XL for Time Series
 */

package scalation
package modeling
package forecasting
package neuralforecasting

// FIX - recode to follow the NeuralNet_3L4TS pattern

import scala.math.max

import scalation.mathstat._

import ActivationFun._
import neuralnet.{NeuralNet_XL, Optimizer}

import MakeMatrix4TS._

//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `NeuralNet_XL4TS` object supports X-layer regression-like neural networks 
 *  for Time Series data.  Given a response vector y, a predictor matrix x is built
 *  that consists of lagged y vectors.
 *      y_t = f2 (b dot f(a dot x))
 *  where x = [y_{t-1}, y_{t-2}, ... y_{t-lags}].
 */
object NeuralNet_XL4TS:

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Create a `NeuralNet_XL` object from a response vector.  The input/data matrix
     *  x is formed from the lagged y vectors as columns in matrix x.
     *  @param y       the original un-expanded output/response vector
     *  @param lags    the maximum lag included (inclusive)
     *  @param h       the forecasting horizon (1, 2, ... h)
     *  @param nz      the number of nodes in hidden layer (-1 => use default formula)
     *  @param hparam  the hyper-parameters (use Optimizer.hp for default)
     *  @param f       the array of activation function family for layers k->k+1
     */
    def apply (y: VectorD, lags: Int, h: Int, nz: Int = -1,
               hparam: HyperParameter = Optimizer.hp,
               f: Array [AFF] = Array (f_eLU, f_eLU, f_tanh)): NeuralNet_XL =
        val hh  = 6 
        val bakcast = false
        val xy  = ARX.buildMatrix (null, y, hparam, bakcast)
        val yy  = makeMatrix4Y (y, hh, bakcast)

        val mod = NeuralNet_XL.rescale (xy, yy, null, null, hparam, f)
        mod.modelName = s"NeuralNet_XL4TS_$lags"
        mod
    end apply

    //:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Create an `NeuralNet_XL` object from a response vector.  The input/data matrix
     *  x is formed from the lagged y vectors as columns in matrix x.
     *  In addition, lagged exogenous variables are added.
     *  ARX_D.buildMatrix4TS (xe: MatrixD, y: VectorD, p: Int, q: Int, spec: Int, lwave: Double,
     *  @param y       the original un-expanded output/response vector
     *  @param lags    the maximum lag included (inclusive)
     *  @parax ex      the input matrix for exogenous variables (one per column)
     *  @param h       the forecasting horizon (1, 2, ... h)
     *  @param nz      the number of nodes in hidden layer (-1 => use default formula)
     *  @param hparam  the hyper-parameters (use Optimizer.hp for default)
     *  @param f       the array of activation function family for layers k->k+1
     *  @param elag1   the minimum exo lag included (inclusive)
     *  @param elag2   the maximum exo lag included (inclusive)
     */
    def exo (y: VectorD, lags: Int, ex: MatrixD, h: Int, nz: Int = -1,
             hparam: HyperParameter = Optimizer.hp,
             f: Array [AFF] = Array (f_eLU, f_eLU, f_tanh))
            (elag1: Int = max (1, lags / 5), elag2: Int = max (1, lags)): NeuralNet_XL =
        val hh  = 6 
        val bakcast = false
        val xy  = ARX.buildMatrix (ex, y, hparam, bakcast)
        val yy  = makeMatrix4Y (y, hh, bakcast)

        println (s"exo: xy.dims = ${xy.dims}, yy.dim = ${yy.dim}")
//      println (s"exo: xy = $xy \n yy = $yy")

        val mod = NeuralNet_XL.rescale (xy, yy, null, null, hparam, f)
        mod.modelName = s"NeuralNet_XL4TS_$lags"
        mod
    end exo

end NeuralNet_XL4TS


//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `neuralNet_XL4TSTest` main function tests the `NeuralNet_XL4TS` class.
 *  This test is used to CHECK that the buildMatrix4TS function is working correctly.
 *  May get NaN for some maximum lags (p) due to multi-collinearity.
 *  > runMain scalation.modeling.forecasting.neuralforecasting.neuralNet_XL4TSTest
 */
@main def neuralNet_XL4TSTest (): Unit =

    val m = 30
    val y = VectorD.range (1, m)                                       // used to CHECK the buildMatrix4TS function
    val h = 3                                                          // the forecasting horizon

    for p <- 5 to 5 do                                                 // autoregressive hyper-parameter p
        banner (s"Test: NeuralNet_XL4TS with $p lags")
        val mod = NeuralNet_XL4TS (y, p, h)                            // create model for time series data
        mod.trainNtest2 ()()                                           // train the model on full dataset
        println (mod.summary)

        val yy = mod.getYY
        val yp = mod.predict (mod.getX)
        for j <- yp.indices2 do
            new Plot (null, yy(?, j), yp(?, j), s"yy_$j vs. yp_$j for ${mod.modelName} with $p lags", lines = true)
        end for
    end for

end neuralNet_XL4TSTest


//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `neuralNet_XL4TSTest2` main function tests the `NeuralNet_XL4TS` class on real data:
 *  Forecasting lake levels.
 *  @see cran.r-project.org/web/packages/fpp/fpp.pdf
 *  > runMain scalation.modeling.forecasting.neuralforecasting.neuralNet_XL4TSTest2
 */
@main def neuralNet_XL4TSTest2 (): Unit =

    import Example_LakeLevels.y
    val h = 3                                                          // the forecasting horizon

    for p <- 1 to 10 do                                                // autoregressive hyper-parameter p
        banner (s"Test: NeuralNet_XL4TS with $p lags")
        val mod = NeuralNet_XL4TS (y, p, h)                            // create model for time series data
        mod.trainNtest2 ()()                                           // train the model on full dataset
        println (mod.summary)

        banner ("Predictions/Forecasts")                               // direct forecasting technique
        val yy = mod.getYY
        val yf = mod.predict (mod.getX)
        for k <- yf.indices2 do
            new Plot (null, yy(?, k), yf(?, k), s"yy_$k vs. yf_$k for ${mod.modelName} with $p lags", lines = true)
        end for
        println (s"yf = $yf")
        println (s"yf.dims = ${yf.dims}")
    end for

end neuralNet_XL4TSTest2

import Example_Covid.{loadData, response}

//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `neuralNet_XL4TSTest3` main function tests the `NeuralNet_XL4TS` class on real data:
 *  Forecasts COVID-19 Weekly Data using endogenous variable only.
 *  Does In-Sample Testing (In_ST).
 *  Determines the terms to include in the model using Feature Selection.
 *  > runMain scalation.modeling.forecasting.neuralforecasting.neuralNet_XL4TSTest3
 */
@main def neuralNet_XL4TSTest3 (): Unit =

    val LAGS = 10                                                               // number of lags
    val h    = 6                                                                // forecasting horizon

    val (x, y) = loadData (Array ("new_cases", "hosp_patients", "icu_patients"), response)

    println (s"x.dims = ${x.dims}, y.dim = ${y.dim}")

//  val f_ : Array [AFF] = Array (f_eLU, f_eLU, f_tanh)
    val f_ : Array [AFF] = Array (f_id, f_eLU, f_tanh)
    Optimizer.hp ("eta") = 0.25

    banner ("In-ST Test: NeuralNet_XL4TS on COVID-19 Weekly Data")
    val mod = NeuralNet_XL4TS (y, LAGS, h, f = f_)                              // create model for time series data

//  val (x_, y_, xx, yy) = NeuralNet_XL4TS.split_TnT (mod.getX, mod.getYY)
//  val (yp, qof) = mod.trainNtest2 (x_, y_)(xx, yy)                            // train on (x_, y_) and test on (xx, yy)

    val (yp, qof) = mod.trainNtest2 ()()                                        // train on full and test on full
    val yy = y(LAGS until y.dim)
    new Plot (null, yy, yp(?, 0), s"${mod.modelName}, yy vs. yp", lines = true)

/*
    banner (s"Feature Selection Technique: Stepwise")
    val (cols, rSq) = mod.stepwiseSelAll (cross = false)                         // R^2, R^2 bar, sMAPE, NA
    val k = cols.size
    println (s"k = $k, n = ${mod.getX.dim2}")
    new PlotM (null, rSq.transpose, Array ("R^2", "R^2 bar", "sMAPE", "NA"),
               s"R^2 vs n for NeuralNet_XL4TS with tech", lines = true)
//  println (mod.summary ())

    banner ("Feature Importance")
    println (s"tech: rSq = $rSq")
//  val imp = mod.importance (cols.toArray, rSq)
//  for (c, r) <- imp do println (s"col = $c, \t ${header(c)}, \t importance = $r")
*/

end neuralNet_XL4TSTest3

//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `neuralNet_XL4TSTest4` main function tests the `NeuralNet_XL4TS` class on real data:
 *  Forecasts COVID-19 Weekly Data using endogenous and exogenous variables.
 *  Does In-Sample Testing (In-ST).
 *  Determines the terms to include in the model using Feature Selection.
 *  > runMain scalation.modeling.forecasting.neuralforecasting.neuralNet_XL4TSTest4
 */
@main def neuralNet_XL4TSTest4 (): Unit =

    val LAGS = 10                                                               // number of lags
    val h    = 6                                                                // forecasting horizon

    val (_x, _y) = loadData (Array ("new_cases", "hosp_patients", "icu_patients"), response)
//  val (x, y)   = (_x, _y)                                                     // full
    val (x, y)   = (_x(0 until 116), _y(0 until 116))                           // clip the flat end

    println (s"x.dims = ${x.dims}, y.dim = ${y.dim}")

//  val f_ : Array [AFF] = Array (f_eLU, f_eLU, f_tanh)
    val f_ : Array [AFF] = Array (f_id, f_eLU, f_tanh)
    Optimizer.hp ("eta") = 0.07
    
    banner ("In-ST: Test NeuralNet_XL4TS on COVID-19 Weekly Data")
    val mod = NeuralNet_XL4TS.exo (y, LAGS, x, h, f = f_)(1, LAGS+1)            // create model for time series data

//  val (x_, y_, xx, yy) = NeuralNet_XL4TS.split_TnT (mod.getX, mod.getYY)
//  val (yp, qof) = mod.trainNtest2 (x_, y_)(xx, yy)                            // train on (x_, y_) and test on (xx, yy)

    val (yp, qof) = mod.trainNtest2 ()()                                        // train on full and test on full
    val yy = y(LAGS until y.dim)
    new Plot (null, yy, yp(?, 0), s"${mod.modelName}, yy vs. yp", lines = true)

/*
    banner (s"Feature Selection Technique: Stepwise")
    val (cols, rSq) = mod.stepwiseSelAll (cross = false)                        // R^2, R^2 bar, sMAPE, NA
    val k = cols.size
    println (s"k = $k, n = ${mod.getX.dim2}")
    new PlotM (null, rSq.transpose, Array ("R^2", "R^2 bar", "sMAPE", "NA"),
               s"R^2 vs n for NeuralNet_XL4TS with tech", lines = true)
//  println (mod.summary ())

    banner ("Feature Importance")
    println (s"tech: rSq = $rSq")
//  val imp = mod.importance (cols.toArray, rSq)
//  for (c, r) <- imp do println (s"col = $c, \t ${header(c)}, \t importance = $r")
*/

end neuralNet_XL4TSTest4


//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `neuralNet_XL4TSTest4` main function tests the `NeuralNet_XL4TS` class on real data:
 *  Forecasts COVID-19 Weekly Data using endogenous and exogenous variables.
 *  Does Train-n-Test Split (TnT) Testing.
 *  Determines the terms to include in the model using Feature Selection.
 *  > runMain scalation.modeling.forecasting.neuralforecasting.neuralNet_XL4TSTest4
 */
@main def neuralNet_XL4TSTest5 (): Unit =

    val LAGS = 10                                                               // number of lags
    val h    = 6                                                                // forecasting horizon

    val (_x, _y) = loadData (Array ("new_cases", "hosp_patients", "icu_patients"), response)
//  val (x, y)   = (_x, _y)                                                     // full
    val (x, y)   = (_x(0 until 116), _y(0 until 116))                           // clip the flat end

    println (s"x.dims = ${x.dims}, y.dim = ${y.dim}")

//  val f_ : Array [AFF] = Array (f_eLU, f_eLU, f_tanh)
    val f_ : Array [AFF] = Array (f_id, f_eLU, f_tanh)
    Optimizer.hp ("eta") = 0.2
   
    banner ("TnT Test: NeuralNet_XL4TS on COVID-19 Weekly Data")
    val mod = NeuralNet_XL4TS.exo (y, LAGS, x, h, f = f_)(1, LAGS+1)            // create model for time series data

//  val (x_, y_, xx, yy) = ARX_D.split_TnT (mod.getX, mod.getYY)
//  val (yp, qof) = mod.trainNtest2 (x_, y_)(xx, yy)                            // train on (x_, y_) and test on (xx, yy)
//  new Plot (null, yy(?, 0), yp(?, 0), s"${mod.modelName}, yy vs. yp", lines = true)

    mod.trainNtest2 ()()                                                        // train on (x_, y_) and test on (xx, yy)
//  mod.rollValidate ()    // FIX
/*
    banner (s"Feature Selection Technique: Stepwise")
    val (cols, rSq) = mod.stepwiseSelAll (cross = false)                        // R^2, R^2 bar, sMAPE, NA
    val k = cols.size
    println (s"k = $k, n = ${mod.getX.dim2}")
    new PlotM (null, rSq.transpose, Array ("R^2", "R^2 bar", "sMAPE", "NA"),
               s"R^2 vs n for NeuralNet_XL4TS with tech", lines = true)
//  println (mod.summary ())

    banner ("Feature Importance")
    println (s"tech: rSq = $rSq")
//  val imp = mod.importance (cols.toArray, rSq)
//  for (c, r) <- imp do println (s"col = $c, \t ${header(c)}, \t importance = $r")
*/

end neuralNet_XL4TSTest5

