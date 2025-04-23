
//:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** @author  John Miller
 *  @version 2.0
 *  @date    Mon Apr 29 16:31:34 EDT 2024
 *  @see     LICENSE (MIT style license file).
 *
 *  @note    Example Time Series Data: Influenza-Like Illness (ILI) Weekly Data
 *
 *  @see www.medrxiv.org/content/10.1101/2022.10.27.22281617v1.full
 *       arxiv.org/pdf/2001.08317v1
 */

package scalation
package modeling
package forecasting

import scala.math._

import scalation.mathstat._

//:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `Example_ILI` object provides a convenient way to load ILI weekly data.
 */
object Example_ILI:

    import scala.collection.mutable.HashMap

    val fileName = "national_illness.csv"

    val header = Array ("%WEIGHTED ILI",                // percent per state weighted by population
                        "%UNWEIGHTED ILI",              // aggregated without weighting
                        "AGE 0-4",                      // count in age group 0 to 4
                        "AGE 5-24",                     // count in age group 5 to 24
                        "ILITOTAL",                     // total ILI count
                        "NUM. OF PROVIDERS",            // number of clinics that report
                        "OT")                           // number of patient visits

    val response = "ILITOTAL"                           // main response/output variable

    //:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Load the ILI weekly data into a matrix for the exogenous variables x
     *  and a vector for the response/endogenous variable y.
     *  @param x_strs  the column names for the exogenous variables x
     *  @param y_str   the column name for the endogenous variable y
     *  @param trim    the number of initial rows to trim away (e.g., they are all 0)
     */
    def loadData (x_strs: Array [String], y_str: String, trim: Int = 0): (MatrixD, VectorD) =
        val col = HashMap [String, Int] ()
        for i <- header.indices do col += header(i) -> i

        val data = MatrixD.load (fileName, 1+trim, 1)      // skip first row (header) + trim first column
        val x_cols = for s <- x_strs yield col(s)
        (data(?, x_cols), data(?, col(y_str)))
    end loadData

    //:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Load the ILI weekly data into a vector for the response/endogenous variable y.
     *  @param y_str  the column name for the endogenous variable y
     *  @param trim   the number of initial rows to trim away (e.g., they are all 0)
     */
    def loadData_y (y_str: String, trim: Int = 0): VectorD =
        val col = HashMap [String, Int] ()
        for i <- header.indices do col += header(i) -> i

        val data = MatrixD.load (fileName, 1+trim, 1)      // skip first row (header) + trim first column
        data(?, col(y_str))
    end loadData_y

    //:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Load the ILI weekly data into a matrix for the variables y.
     *  @param y_str  the column names for the variables y (e.g., used in a VAR model)
     *  @param trim   the number of initial rows to trim away (e.g., they are all 0)
     */
    def loadData_yy (y_strs: Array [String], trim: Int = 0): MatrixD =
        val col = HashMap [String, Int] ()
        for i <- header.indices do col += header(i) -> i

        val data = MatrixD.load (fileName, 1+trim, 1)      // skip first row (header) + trim first column
        val y_cols = for s <- y_strs yield col(s)
        data(?, y_cols)
    end loadData_yy

end Example_ILI

import Example_ILI._

//:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `example_ILITest` main function test the `Example_ILI` object.
 *  Plots the response column.
 *  > runMain scalation.modeling.forecasting.example_ILITest
 */
@main def example_ILITest (): Unit =

    val y = Example_ILI.loadData_y (response)

    banner (s"Plot the response = $response column for the ILI dataset (${y.dim} points")
    new Plot (null, y, null, s"y ($response)", lines = true)

end example_ILITest


//:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `example_ILITest2` main function test the `Example_ILI` object.
 *  This performs Exploratory Data Analysis (EDA) to find relationships
 *  between contemporaneous variables.
 *  > runMain scalation.modeling.forecasting.example_ILITest2
 */
@main def example_ILITest2 (): Unit =

    import scala.collection.mutable.Set

    val (x, y) = loadData (header, response)

    new Plot (null, y, null, "y ($response)", lines = true)

    for j <- x.indices2 do
        banner (s"EDA for response = $response vs. ${header(j)}")
        var xj  = x(?, j)                                               // get column j
        xj = scaleV (extreme (xj), (0.0, 2.0))(xj)                      // rescale vector xj to [0, 2]
        val xxj = MatrixD.fromVector (xj)
//      val mod = SymbolicRegression.quadratic (xxj, y)
//      val mod = SymbolicRegression.rescale (xxj, y, null, Set (1.0, 2.0, 3.0), cross = false)
        val mod = SymbolicRegression (xxj, y, null, Set (0.5, 1.0, 2.0, 3.0), cross = false)
        mod.trainNtest ()()
        val yp = mod.predict (mod.getX)
        println (mod.summary ())
        new Plot (xj, y, yp, s"y, yp ($response) vs. x_$j (${header(j)})")
    end for

end example_ILITest2


//:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `example_ILITest3` main function tests the `Example_ILI` object.
 *  Uses In-Sample Testing (In-ST), i.e., train and test on the same data.
 *  Runs several baseline models for horizons 1 to 6, see sMAPE metrics below:
 *
 *  72.3771,    72.3020,    72.2361,    72.1766,    72.1235,    72.0677  Null
 *  63.3460,    63.3241,    63.3049,    63.2781,    63.2447,    63.2163  Trend
 *  14.8647,    21.3947,    30.4372,    37.5090,    44.7307,    51.0374  SMA
 *  16.3572,    21.1826,    31.5004,    37.5687,    45.4430,    51.2477  WMA
 *  10.7057,    19.2054,    27.4321,    35.0421,    42.2938,    48.9523  SES
 *  10.9952,    19.5427,    27.7590,    35.3626,    42.5756,    49.2132  RW
 *  10.4080,    18.8430,    27.0731,    34.6972,    41.9847,    48.6709  RWS
 *  13.0155,    22.9537,    31.6664,    39.0945,    45.5909,   	51.2390  AR(1)
 *
 *  > runMain scalation.modeling.forecasting.example_ILITest3
 */
@main def example_ILITest3 (): Unit =

    val y  = Example_ILI.loadData_y (response)
    val hh = 12                                                           // max forecasting horizon

    new Plot (null, y, null, s"y ($response)", lines = true)

    new NullModel (y, hh).inSampleTest ()
    new TrendModel (y, hh).inSampleTest ()
    new SimpleMovingAverage (y, hh).inSampleTest ()
    new WeightedMovingAverage (y, hh).inSampleTest ()
    new SimpleExpSmoothing (y, hh).inSampleTest ()
    new RandomWalk (y, hh).inSampleTest ()
    new RandomWalkS (y, hh).inSampleTest ()
    new AR (y, hh).inSampleTest ()

end example_ILITest3


//:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `example_CovidTest5` main function tests the `Example_ILI` object.
 *  Uses In-Sample Testing (In-ST), i.e., train and test on the same data.
 *  Runs Auto-Regressive AR(p) models for several p values and horizons 1 to 6,
 *  see sMAPE metrics below:
 *
 *  13.0155,    22.9537,    31.6664,    39.0945,    45.5909,    51.2390  AR(1)
 *  12.2138,    22.3784,    31.4101,    38.9114,    45.5691,    50.9743  AR(2)
 *  12.1428,    22.6277,    32.1664,    39.8123,    46.2555,    51.4107  AR(3)
 *  12.1593,    22.6250,    32.1326,    39.7545,    46.1988,    51.3480  AR(4)
 *  12.2947,    22.7086,    31.6933,    38.7545,    44.9812,    50.1115  AR(5)
 *  13.1465,    23.9406,    33.3826,    40.4398,    46.4482,    51.2484  AR(6)
 *  13.1506,    23.9510,    33.3948,    40.4620,    46.4715,    51.2688  AR(7)
 *  12.8000,    23.7886,    33.2327,    40.0353,    45.6288,    50.3420  AR(8)
 *  12.8755,    23.7681,    33.0763,    39.8769,    45.5340,    50.3367  AR(9)
 *  12.8060,    23.7307,    33.2322,    40.0946,    45.7090,    50.5064  AR(10)
 *
 *  > runMain scalation.modeling.forecasting.example_ILITest5
 */
@main def example_ILITest5 (): Unit =

    val y  = Example_ILI.loadData_y (response)
    val hh = 12                                                         // max forecasting horizon
    val hp = AR.hp                                                      // hyper-parameters for AR family of models

    new Plot (null, y, null, s"y ($response)", lines = true)

    for p <- 1 to 10 do                                                 // AR hyper-parameter settings
        hp("p") = p
        new AR (y, hh).inSampleTest ()                                  // create and test an AR model
    end for

end example_ILITest5


//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `example_ILITest6` main function test the `Example_ILI` object.
 *  This test compares the `ARMA` model for several values of p and q.
 *  > runMain scalation.modeling.forecasting.example_ILITest6
 */
@main def example_ILITest6 (): Unit =

    import AR.hp

    val y  = Example_ILI.loadData_y (response)
    val hh = 12                                                         // maximum forecasting horizon

    for p <- 1 to 8; q <- 0 to 3 do
        hp("p") = p; hp("q") = q                                        // set p (AR) and q (MA) hyper-parameters
        val mod = new ARMA (y, hh)                                      // create model for time series data
        banner (s"Test: ${mod.modelName} on ILI Dataset")
        mod.trainNtest ()()                                             // train and test the model on full dataset

//      mod.setSkip (p)                                                 // full ARY-formula available when t >= p
        mod.forecastAll ()                                              // forecast h-steps ahead (h = 1 to hh) for all y
        mod.diagnoseAll (y, mod.getYf, showYf = false)                  // model diagnostics for all horizons
    end for

end example_ILITest6


//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `example_ILITest7` main function test the `Example_ILI` object.
 *  This test compares the `ARY` model for several values of p.
 *  > runMain scalation.modeling.forecasting.example_ILITest7
 */
@main def example_ILITest7 (): Unit =

    import MakeMatrix4TS.hp

    val y  = Example_ILI.loadData_y (response)
    val hh = 12                                                         // maximum forecasting horizon
    hp("spec") = 1                                                      // trend specification

    for p <- 1 to 26 do
        hp("p") = p                                                     // set p (ARY) hyper-parameter
        val mod = ARY (y, hh)                                           // create model for time series data
        banner (s"Test: ${mod.modelName} on ILI Dataset")
        mod.trainNtest_x ()()                                           // train and test the model on full dataset

//      mod.setSkip (p)                                                 // full ARY-formula available when t >= p
        mod.forecastAll ()                                              // forecast h-steps ahead (h = 1 to hh) for all y
        mod.diagnoseAll (y, mod.getYf, showYf = false)                  // model diagnostics for all horizons
    end for

end example_ILITest7


//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `example_ILITest8` main function test the `Example_ILI` object.
 *  This test compares the `ARY_D` model for several values of p.
 *  > runMain scalation.modeling.forecasting.example_ILITest8
 */
@main def example_ILITest8 (): Unit =

    import MakeMatrix4TS.hp

    val y  = Example_ILI.loadData_y (response)
    val hh = 12                                                         // maximum forecasting horizon
    hp("spec") = 1                                                      // trend specification

    for p <- 1 to 26 do
        hp("p") = p                                                     // set p (ARY_D) hyper-parameter
        val mod = ARY_D (y, hh)                                         // create model for time series data
        banner (s"Test: ${mod.modelName} on ILI Dataset")
        mod.trainNtest_x ()()                                           // train and test the model on full dataset

//      mod.setSkip (p)                                                 // full ARY-formula available when t >= p
        mod.forecastAll ()                                              // forecast h-steps ahead (h = 1 to hh) for all y
        mod.diagnoseAll (y, mod.getYf, showYf = false)                  // model diagnostics for all horizons
    end for

end example_ILITest8


//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `example_ILITest9` main function test the `Example_ILI` object.
 *  This test compares the `ARIMA` model for several values of p and q.
 *  > runMain scalation.modeling.forecasting.example_ILITest9
 */
@main def example_ILITest9 (): Unit =

    import AR.hp

    val y  = Example_ILI.loadData_y (response)
    val hh = 12                                                         // maximum forecasting horizon

    for p <- 1 to 8; q <- 0 to 3 do
        hp("p") = p; hp("q") = q                                        // set p (AR) and q (MA) hyper-parameters
        val mod = new ARIMA (y, hh)                                     // create model for time series data
        banner (s"Test: ${mod.modelName} on ILI Dataset")
        mod.trainNtest ()()                                             // train and test the model on full dataset

//      mod.setSkip (p)                                                 // full ARY-formula available when t >= p
        mod.forecastAll ()                                              // forecast h-steps ahead (h = 1 to hh) for all y
        mod.diagnoseAll (y, mod.getYf, showYf = false)                  // model diagnostics for all horizons
    end for

end example_ILITest9

//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `example_ILITest10` main function test the `Example_ILI` object.
 *  This test compares the `ARX_Symb` and `ARX_Symb_D` models for several values of p and q.
 *  > runMain scalation.modeling.forecasting.example_ILITest10
 */
@main def example_ILITest10 (): Unit =

    import MakeMatrix4TS.hp

    val exo_vars = Array ("%WEIGHTED ILI", "%UNWEIGHTED ILI")
    val (xe, y)  = loadData (exo_vars, response)
    println (s"xe.dims = ${xe.dims}, y.dim = ${y.dim}")

    val hh = 12                                                          // maximum forecasting horizon
    val p  = 10
    val q  = 10
    val pp = 1.5
    hp("p")     = p                                                     // endo lags
    hp("q")     = q                                                     // exo lags
    hp("spec")  = 1                                                     // trend specification: 0, 1, 2, 3, 5
    hp("lwave") = 20                                                    // wavelength (distance between peaks)
    hp("cross") = 1
    hp("lambda") = 1.0

    val ff = Array [Transform] (powForm (VectorD (pp)))
    val gg = Array [Transform] ()

    val mod = ARX_Symb (xe, y, hh, fEndo = ff, fExo = gg)               // create model for time series data
    banner (s"In-ST Forecasts: ${mod.modelName} on COVID-19 Dataset")
    mod.trainNtest_x ()()                                               // train and test on full dataset

    mod.setSkip(0)
    mod.rollValidate (rc = 2)                                           // TnT with Rolling Validation
    mod.diagnoseAll (y, mod.getYf, Forecaster.teRng(y.dim), 0)

    banner ("Feature Selection Technique: stepwise")
    val (cols, rSq) = mod.stepwiseSelAll ()                             // R^2, R^2 bar, sMAPE, R^2 cv
//  val (cols, rSq) = mod.backwardElimAll ()                            // R^2, R^2 bar, sMAPE, R^2 cv
    val k = cols.size
    println (s"k = $k")
    new PlotM (null, rSq.transpose, Array ("R^2", "R^2 bar", "sMAPE", "R^2 cv"),
               s"R^2 vs n for ${mod.modelName}", lines = true)
    println (s"rSq = $rSq")

    val modBest = mod.getBest.mod
    val x_fs = modBest.getX

    val yy_D  = MakeMatrix4TS.makeMatrix4Y (y, hh, false)               // FIX - switch to usiing apply method in next line)
    val mod_D = new ARX_Symb_D (x_fs, yy_D, hh, n_exo = 1, null)
    mod_D.trainNtest_x ()()

    mod_D.setSkip (0)
    mod_D.rollValidate (rc = 2)                                         // TnT with Rolling Validation
    mod_D.diagnoseAll (y, mod_D.getYf, Forecaster.teRng (y.dim), 0)     // only diagnose on the testing set

end example_ILITest10

//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `example_ILITest10` main function test the `Example_ILI` object.
 *  This test compares the several models for several values of p and q.
 *  > runMain scalation.modeling.forecasting.example_ILITest11
 */
@main def example_ILITest11 (): Unit =

    import MakeMatrix4TS.hp

    //    val exo_vars = Array ("%WEIGHTED ILI", "%UNWEIGHTED ILI")
    val exo_vars = Array("OT")

    val (xe, y)  = loadData (exo_vars, response)
    println (s"xe.dims = ${xe.dims}, y.dim = ${y.dim}")

    val hh = 6                                                          // maximum forecasting horizon
    val p  = 6
    val q  = 6
    hp("p")     = p                                                     // endo lags
    hp("q")     = q                                                     // exo lags
    hp("spec")  = 1                                                     // trend specification: 0, 1, 2, 3, 5
    hp("lwave") = 20                                                    // wavelength (distance between peaks)
    hp("cross") = 0
    hp("lambda") = 1.0


    banner("RandomWalkS")
    val mod1 = RandomWalkS(y, hh) // create model for time series data
    banner(s"In-ST Forecasts: ${mod1.modelName} on ILI Dataset")
    mod1.trainNtest()() // train and test on full dataset
    mod1.forecastAll() // forecast h-steps ahead (h = 1 to hh) for all y
    mod1.diagnoseAll(mod1.getY, mod1.getYf)

    println("rollValidate")
    mod1.setSkip(0)
    mod1.rollValidate() // TnT with Rolling Validation
    mod1.diagnoseAll(mod1.getY, mod1.getYf, Forecaster.teRng(y.dim), 0)


    banner("AR")
    val mod2 = AR(y, hh) // create model for time series data
    banner(s"In-ST Forecasts: ${mod2.modelName} on ILI Dataset")
    mod2.trainNtest()() // train and test on full dataset
    mod2.forecastAll() // forecast h-steps ahead (h = 1 to hh) for all y
    mod2.diagnoseAll(mod2.getY, mod2.getYf)

    println("rollValidate")
    mod2.setSkip(0)
    mod2.rollValidate() // TnT with Rolling Validation
    mod2.diagnoseAll(mod2.getY, mod2.getYf, Forecaster.teRng(y.dim), 0)


    banner("ARX")
    val mod3 = ARX(xe, y, hh) // create model for time series data
    banner(s"In-ST Forecasts: ${mod3.modelName} on ILI Dataset")
    mod3.trainNtest_x()() // train and test on full dataset
    mod3.forecastAll() // forecast h-steps ahead (h = 1 to hh) for all y
    mod3.diagnoseAll(mod3.getY, mod3.getYf)

    println("rollValidate")
    mod3.setSkip(0)
    mod3.rollValidate() // TnT with Rolling Validation
    mod3.diagnoseAll(mod3.getY, mod3.getYf, Forecaster.teRng(y.dim), 0)


    banner("ARX_D")
    val mod4 = ARX_D(xe, y, hh) // create model for time series data
    banner(s"In-ST Forecasts: ${mod4.modelName} on ILI Dataset")
    mod4.trainNtest_x()() // train and test on full dataset
    mod4.forecastAll() // forecast h-steps ahead (h = 1 to hh) for all y
    mod4.diagnoseAll(mod4.getY, mod4.getYf)

    println("rollValidate")
    mod4.setSkip(0)
    mod4.rollValidate() // TnT with Rolling Validation
    mod4.diagnoseAll(mod4.getY, mod4.getYf, Forecaster.teRng(y.dim), 0)


    banner("ARX_Quad")
    val mod5 = ARX_Quad(xe, y, hh) // create model for time series data
    banner(s"In-ST Forecasts: ${mod5.modelName} on ILI Dataset")
    mod5.trainNtest_x()() // train and test on full dataset
    mod5.forecastAll() // forecast h-steps ahead (h = 1 to hh) for all y
    mod5.diagnoseAll(mod5.getY, mod5.getYf)

    println("rollValidate")
    mod5.setSkip(0)
    mod5.rollValidate() // TnT with Rolling Validation
    mod5.diagnoseAll(mod5.getY, mod5.getYf, Forecaster.teRng(y.dim), 0)


    banner("ARX_Quad_D")
    val mod6 = ARX_Quad_D(xe, y, hh) // create model for time series data
    banner(s"In-ST Forecasts: ${mod6.modelName} on ILI Dataset")
    mod6.trainNtest_x()() // train and test on full dataset
    mod6.forecastAll() // forecast h-steps ahead (h = 1 to hh) for all y
    mod6.diagnoseAll(mod6.getY, mod6.getYf)

    println("rollValidate")
    mod6.setSkip(0)
    mod6.rollValidate() // TnT with Rolling Validation
    mod6.diagnoseAll(mod6.getY, mod6.getYf, Forecaster.teRng(y.dim), 0)

end example_ILITest11