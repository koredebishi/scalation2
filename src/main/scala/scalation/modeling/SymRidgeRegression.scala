
//:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** @author  John Miller
 *  @version 2.0
 *  @date    Thu Dec 23 13:54:30 EST 2021
 *  @see     LICENSE (MIT style license file).
 *
 *  @title   Model: Symbolic Ridge Regression, including Quadratic and Cubic Ridge Regression
 */

package scalation
package modeling

import scala.collection.mutable.Set

import scalation.mathstat._

//:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `SymRidgeRegression` object supports symbolic ridge regression that allows
 *  variables/columns to be raised to various powers, e.g., x^2, x^3, x^.5.
 *  Note, x~^p is a column-wise power function (each column raised to p-th power).
 *  IMPORTANT:  must not include INTERCEPT (column of ones) in initial data matrix),
 *  i.e., DO NOT include a column of ones in x (will cause singularity in expanded matrix).
 *  Method signatures are the as same as for `SymbolicRegression`, except there is
 *  NO intercept ARGUMENT.
 */
object SymRidgeRegression:

    //:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Create a `RidgeRegression` object from a data matrix and a response vector.
     *  Partial support for "Symbolic Ridge Regression" as matrix x can be raised
     *  to several powers (e.g., x^1 and x^2).  Note, x^1 is automatically included.
     *  NOTE, Ridge Regression will NOT have an INTERCEPT column.
     *  @see `SymbolicRegression.buildMatrix`
     *  @param x       the initial data/input m-by-n matrix (before expansion)
     *                     must not include an intercept column of all ones
     *  @param y       the response/output m-vector
     *  @param fname   the feature/variable names (defaults to null)
     *  @param powers  the set of powers to raise matrix x to (defaults to null)
     *  @param cross   whether to include 2-way cross/interaction terms x_i x_j (defaults to true)
     *  @param cross3  whether to include 3-way cross/interaction terms x_i x_j x_k (defaults to false)
     *  @param hparam  the hyper-parameters (defaults to RidgeRegression.hp)
     *  @param terms   custom terms to add into the model, e.g., Array ((0, 1.0), (1, -2.0))
     *                     adds x0 x1^(-2)
     */
    def apply (x: MatrixD, y: VectorD, fname: Array [String] = null,
               powers: Set [Double] = null, cross: Boolean = true, cross3: Boolean = false,
               hparam: HyperParameter = RidgeRegression.hp,
               terms: Array [Xj2p]*): RidgeRegression =
        val fname_ = if fname != null then fname
                     else x.indices2.map ("x" + _).toArray                // default feature/variable names

        val (xx, f_name) = SymbolicRegression.buildMatrix (x, fname_, powers,
                                                           false, cross, cross3, terms :_*)
//      val mod       = new RidgeRegression (xx, y, f_name, hparam)       // user must center
        val mod       = RidgeRegression.center (xx, y, f_name, hparam)    // automatically centers the data
        mod.modelName = "SymRidgeRegression" + (if cross then "X" else "") +
                                               (if cross3 then "XX" else "")
        mod
    end apply

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Create a `SymRidgeRegression` object from a data matrix and a response vector.
     *  This method provides data rescaling.
     *  NOTE, Ridge Regression will NOT have an INTERCEPT column.
     *  @param x       the data/input m-by-n matrix
     *                     (augment with a first column of ones to include intercept in model)
     *  @param y       the response/output m-vector
     *  @param fname   the feature/variable names (defaults to null)
     *  @param powers  the set of powers to raise matrix x to (defualts to null)
     *  @param cross   whether to include 2-way cross/interaction terms x_i x_j (defaults to true)
     *  @param cross3  whether to include 3-way cross/interaction terms x_i x_j x_k (defaults to false)
     *  @param hparam  the hyper-parameters (defaults to Regression.hp)
     *  @param terms   custom terms to add into the model, e.g., Array ((0, 1.0), (1, -2.0))
     *                     adds x0 x1^(-2)
     */
    def rescale (x: MatrixD, y: VectorD, fname: Array [String] = null,
                 powers: Set [Double] = null, cross: Boolean = true, cross3: Boolean = false,
                 hparam: HyperParameter = RidgeRegression.hp,
                 terms: Array [Xj2p]*): RidgeRegression =
        val xn = normalize ((x.mean, x.stdev)) (x)
        println (s"rescale: xn = $xn")
        apply (xn, y, fname, powers, cross, cross3, hparam, terms :_*)
    end rescale

    //:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Create a `RidgeRegression` object that uses multiple regression to fit a quadratic
     *  surface to the data.  For example in 2D, the quadratic regression equation is
     *      y  =  b dot x + e  =  [b_0, ... b_k] dot [x_0, x_0^2, x_1, x_1^2] + e
     *  NOTE, Ridge Regression will NOT have an INTERCEPT column.
     *  @param x       the initial data/input m-by-n matrix (before quadratic term expansion)
     *                     must not include an intercept column of all ones
     *  @param y       the response/output m-vector
     *  @param fname   the feature/variable names (defaults to null)
     *  @param cross   whether to include cross terms x_i * x_j (defaults to false)
     *  @param hparam  the hyper-parameters (defaults to RidgeRegression.hp)
     */
    def quadratic (x: MatrixD, y: VectorD, fname: Array [String] = null,
                   cross: Boolean = false,
                   hparam: HyperParameter = RidgeRegression.hp): RidgeRegression =
        val mod       = apply (x, y, fname, Set (1, 2), cross, false, hparam)
        mod.modelName = "SymRidgeRegression.quadratic" + (if cross then "X" else "")
        mod
    end quadratic

    //:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Create a `RidgeRegression` object that uses multiple regression to fit a cubic
     *  surface to the data.  For example in 2D, the cubic regression equation is
     *      y  =  b dot x + e  =  [b_0, ... b_k] dot [x_0, x_0^2, x_0^3,
     *                                                x_1, x_1^2, x_1^3,
     *                                                x_0*x_1, x_0^2*x_1, x_0*x_1^2] + e
     *  NOTE, Ridge Regression will NOT have an INTERCEPT column.
     *  @param x       the initial data/input m-by-n matrix (before quadratic term expansion)
     *                     must not include an intercept column of all ones
     *  @param y       the response/output m-vector
     *  @param fname   the feature/variable names (defaults to null)
     *  @param cross   whether to include 2-way cross/interaction terms x_i x_j (defaults to false)
     *  @param cross3  whether to include 3-way cross/interaction terms x_i x_j x_k (defaults to false)
     *  @param hparam  the hyper-parameters (defaults to RidgeRegression.hp)
     */
    def cubic (x: MatrixD, y: VectorD, fname: Array [String] = null,
               cross: Boolean = false, cross3: Boolean = false,
               hparam: HyperParameter = RidgeRegression.hp): RidgeRegression =
        val mod       = apply (x, y, fname, Set (1, 2, 3), cross, cross3, hparam)
        mod.modelName = "SymRidgeRegression.cubic" + (if cross then "X" else "") +
                                                     (if cross3 then "X" else "")
        mod
    end cubic

end SymRidgeRegression

import Example_AutoMPG._

//:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `symRidgeRegressionTest` main function tests the `SymRidgeRegression`
 *  object using the AutoMPG dataset.  Assumes no missing values.
 *  It tests custom "Symbolic Ridge Regression", with powers specified in "Set (...)" and
 *  applies forward selection, backward elimination, or stepwise regression.
 *  > runMain scalation.modeling.symRidgeRegressionTest
 */
@main def symRidgeRegressionTest (): Unit =

//  println (s"x = $x")
//  println (s"y = $y")

    banner ("AutoMPG Symbolic Ridge Regression")
    val mod = SymRidgeRegression (x, y, x_fname, Set (-2, -1, 0.5, 2))    // add cross-terms and given powers
    mod.trainNtest ()()                                                   // train and test the model
    println (mod.summary ())                                              // parameter/coefficient statistics

    for tech <- SelectionTech.values do 
        banner (s"Feature Selection Technique: $tech")
        val (cols, rSq) = mod.selectFeatures (tech)                       // R^2, R^2 bar, R^2 cv
        val k = cols.size
        println (s"k = $k, n = ${x.dim2}")
        new PlotM (null, rSq.transpose, Array ("R^2", "R^2 bar", "R^2 cv"),
                   s"R^2 vs n for Symbolic Ridge Regression with $tech", lines = true)
        println (s"$tech: rSq = $rSq")
    end for

end symRidgeRegressionTest


//:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `symRidgeRegressionTest2` main function tests the `SymRidgeRegression`
 *  object using the AutoMPG dataset.  Assumes no missing values.
 *  It tests "Quadratic Ridge Regression" (with cross = false) and
 *  applies forward selection, backward elimination, or stepwise regression.
 *  > runMain scalation.modeling.symRidgeRegressionTest2
 */
@main def symRidgeRegressionTest2 (): Unit =

//  println (s"x = $x")
//  println (s"y = $y")

    banner ("AutoMPG Quadratic Ridge Regression")
    val mod = SymRidgeRegression.quadratic (x, y, x_fname)                // add x^2 terms
    mod.trainNtest ()()                                                   // train and test the model
    println (mod.summary ())                                              // parameter/coefficient statistics

    for tech <- SelectionTech.values do
        banner (s"Feature Selection Technique: $tech")
        val (cols, rSq) = mod.selectFeatures (tech)                       // R^2, R^2 bar, R^2 cv
        val k = cols.size
        println (s"k = $k, n = ${x.dim2}")
        new PlotM (null, rSq.transpose, Array ("R^2", "R^2 bar", "R^2 cv"),
                   s"R^2 vs n for Quadratic Ridge Regression with $tech", lines = true)
        println (s"$tech: rSq = $rSq")
    end for

end symRidgeRegressionTest2


//:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `symRidgeRegressionTest3` main function tests the `SymRidgeRegression`
 *  object using the AutoMPG dataset.  Assumes no missing values.
 *  It tests "Quadratic X Ridge Regression" (with cross = true) and
 *  applies forward selection, backward elimination, or stepwise regression.
 *  > runMain scalation.modeling.symRidgeRegressionTest3
 */
@main def symRidgeRegressionTest3 (): Unit =

//  println (s"x = $x")
//  println (s"y = $y")

    banner ("AutoMPG Quadratic X Ridge Regression")
    val mod = SymRidgeRegression.quadratic (x, y, x_fname, true)          // add cross-terms and x^2 terms
    mod.trainNtest ()()                                                   // train and test the model
    println (mod.summary ())                                              // parameter/coefficient statistics

    for tech <- SelectionTech.values do 
        banner (s"Feature Selection Technique: $tech")
        val (cols, rSq) = mod.selectFeatures (tech)                       // R^2, R^2 bar, R^2 cv
        val k = cols.size
        println (s"k = $k, n = ${x.dim2}")
        new PlotM (null, rSq.transpose, Array ("R^2", "R^2 bar", "R^2 cv"),
                   s"R^2 vs n for Quadratic X Ridge Regression with $tech", lines = true)
        println (s"$tech: rSq = $rSq")
    end for

end symRidgeRegressionTest3


//:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `symRidgeRegressionTest4` main function tests the `SymRidgeRegression`
 *  object using the AutoMPG dataset.  Assumes no missing values.
 *  It tests "Cubic Ridge Regression" (with cross = false) and
 *  applies forward selection, backward elimination, or stepwise regression.
 *  > runMain scalation.modeling.symRidgeRegressionTest4
 */
@main def symRidgeRegressionTest4 (): Unit =

//  println (s"x = $x")
//  println (s"y = $y")

    banner ("AutoMPG Cubic Ridge Regression")
    val mod = SymRidgeRegression.cubic (x, y, x_fname)                    // add x^2 and x^3 terms
    mod.trainNtest ()()                                                   // train and test the model
    println (mod.summary ())                                              // parameter/coefficient statistics

    for tech <- SelectionTech.values do 
        banner (s"Feature Selection Technique: $tech")
        val (cols, rSq) = mod.selectFeatures (tech)                       // R^2, R^2 bar, R^2 cv
        val k = cols.size
        println (s"k = $k, n = ${x.dim2}")
        new PlotM (null, rSq.transpose, Array ("R^2", "R^2 bar", "R^2 cv"),
                   s"R^2 vs n for Cubic Ridge Regression with $tech", lines = true)
        println (s"$tech: rSq = $rSq")
    end for

end symRidgeRegressionTest4


//:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `symRidgeRegressionTest5` main function tests the `SymRidgeRegression`
 *  object using the AutoMPG dataset.  Assumes no missing values.
 *  It tests "Cubic X Ridge Regression" (with cross = true) and
 *  applies forward selection, backward elimination, or stepwise regression.
 *  > runMain scalation.modeling.symRidgeRegressionTest5
 */
@main def symRidgeRegressionTest5 (): Unit =

//  println (s"x = $x")
//  println (s"y = $y")

    banner ("AutoMPG Cubic X Ridge Regression")
    val mod = SymRidgeRegression.cubic (x, y, x_fname, true)              // add cross-terms, x^2 and x^3 terms
    mod.trainNtest ()()                                                   // train and test the model
    println (mod.summary ())                                              // parameter/coefficient statistics

    for tech <- SelectionTech.values do 
        banner (s"Feature Selection Technique: $tech")
        val (cols, rSq) = mod.selectFeatures (tech)                       // R^2, R^2 bar, R^2 cv
        val k = cols.size
        println (s"k = $k, n = ${x.dim2}")
        new PlotM (null, rSq.transpose, Array ("R^2", "R^2 bar", "R^2 cv"),
                   s"R^2 vs n for Cubic X Ridge Regression with $tech", lines = true)
        println (s"$tech: rSq = $rSq")
    end for

end symRidgeRegressionTest5


//:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `symRidgeRegressionTest6` main function tests the `SymRidgeRegression`
 *  object using the AutoMPG dataset.  Assumes no missing values.
 *  It tests "Cubic XX Ridge Regression" (with cross, cross3 = true) and
 *  applies forward selection, backward elimination, or stepwise regression.
 *  WARNING: setting cross3 = true can lead to an explotion of terms.
 *  > runMain scalation.modeling.symRidgeRegressionTest6
 */
@main def symRidgeRegressionTest6 (): Unit =

//  println (s"x = $x")
//  println (s"y = $y")

    banner ("AutoMPG Cubic XX Ridge Regression")
    val mod = SymRidgeRegression.cubic (x, y, x_fname,                  // add x^2 and x^3 terms
                                        true, true)                     // add cross and cross3 terms
    mod.trainNtest ()()                                                 // train and test the model
    println (mod.summary ())                                            // parameter/coefficient statistics

    for tech <- SelectionTech.values do
        banner (s"Feature Selection Technique: $tech")
        val (cols, rSq) = mod.selectFeatures (tech)                     // R^2, R^2 bar, R^2 cv
        val k = cols.size
        println (s"k = $k, n = ${x.dim2}")
        new PlotM (null, rSq.transpose, Array ("R^2", "R^2 bar", "R^2 cv"),
                   s"R^2 vs n for Cubic XX Ridge Regression with $tech", lines = true)
        println (s"$tech: rSq = $rSq")
    end for

end symRidgeRegressionTest6


//:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `symRidgeRegressionTest7` main function tests the `SymRidgeRegression`
 *  object using the AutoMPG dataset.  Assumes no missing values.
 *  It tests custom "Symbolic Ridge Regression", with powers specified in "Set (...)" and
 *  applies forward selection, backward elimination, or stepwise regression.
 *  This test case performs data rescaling.
 *  > runMain scalation.modeling.symRidgeRegressionTest7
 */
@main def symRidgeRegressionTest7 (): Unit =

    val RESCALE = true

//  println (s"x = $x")
//  println (s"y = $y")

    banner ("AutoMPG Symbolic Ridge Regression")
    val mod =
    if RESCALE then
        SymRidgeRegression.rescale (x, y, x_fname, Set (-2, -1, 2))      // add cross-terms and given powers & rescale (0.5 -> NaN)
    else
        SymRidgeRegression (x, y, x_fname, Set (-2, -1, 0.5, 2))         // add cross-terms and given powers
    mod.trainNtest ()()                                                  // train and test the model
    println (mod.summary ())                                             // parameter/coefficient statistics

    for tech <- SelectionTech.values do
        banner (s"Feature Selection Technique: $tech")
        val (cols, rSq) = mod.selectFeatures (tech)                      // R^2, R^2 bar, R^2 cv
        val k = cols.size
        println (s"k = $k, n = ${x.dim2}")
        new PlotM (null, rSq.transpose, Array ("R^2", "R^2 bar", "R^2 cv"),
                   s"R^2 vs n for Symbolic Ridge Regression with $tech", lines = true)
        println (s"$tech: rSq = $rSq")
    end for

end symRidgeRegressionTest7


//:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `symRidgeRegressionTest8` main function tests the `SymRidgeRegression`
 *  object using a simulated gravity dataset.
 *  It tests custom "Symbolic Regression", with a custom term: x0 x1^(-2)
 *  FIX - acquire a real gravity dataset
 *  > runMain scalation.modeling.symRidgeRegressionTest8
 */
@main def symRidgeRegressionTest8 (): Unit =

    import random.{Uniform, Normal}

    val noise = Normal (0, 10)                                           // random noise
    val rad   = Uniform (6370, 7000)                                     // distance from the center of the Earth in km
    val mas   = Uniform (50, 150)                                        // mass of person

    val m1 = 5.97219E24                                                  // mass of Earth in kg
    val G  = 6.67408E-11                                                 // gravitational constant in m^3 kg^-1 s^-2

    val xy = new MatrixD (100, 3)                                        // simulated gravity data
    for i <- xy.indices do
        val m2 = mas.gen                                                 // unit of kilogram (kg)
        val r  = 1000 * rad.gen                                          // unit of meter (m)
        xy(i, 0) = m2                                                    // mass of person
        xy(i, 1) = r                                                     // radius/distance
        xy(i, 2) = G * m1 * m2 / r~^2 + noise.gen                        // force of gravity
    end for

    val fname = Array ("mass2", "radius")

    println (s"xy = $xy")
    val (x, y) = (xy.not (?, 2), xy(?, 2))

    banner ("Newton's Universal Gravity Symbolic Ridge Regression")
    val mod = SymRidgeRegression (x, y, fname, null, false, false,
              terms = Array ((0, 1.0), (1, -2.0)))                       // add one custom term

    mod.trainNtest ()()                                                  // train and test the model
    println (mod.summary ())                                             // parameter/coefficient statistics
    println (s"b =~ GM = ${G * m1}")                                     // Gravitational Constant * Mass of the Earth

end symRidgeRegressionTest8


//:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `symRidgeRegressionTest9` main function tests the `SymRidgeRegression`
 *  object using a simple dataset to compare Ridge Regression, Quadratic Ridge Regression
 *  and Cubic Ridge Regression.
 *  > runMain scalation.modeling.symRidgeRegressionTest9
 */
@main def symRidgeRegressionTest9 (): Unit =

    val x  = VectorD (1, 2, 3, 4, 5)
    val y  = VectorD (1, 3, 3, 5, 4)

    val mu_x = x.mean                                                      // center the data
    val mu_y = y.mean
    val y_c  = y - mu_y

    val xx = MatrixD (x).transpose
    val fname = Array ("x")

    banner ("Ridge Regression")
    var mod = RidgeRegression.center (xx, y, fname)
    mod.trainNtest ()()
    println (mod.summary ())
    var yp  = mod.predict (mod.getX)
    var yp2 = yp + mu_y
    new Plot (null, y_c, yp, s"${mod.modelName} y_c vs yp", lines = true)
    new Plot (null, y,  yp2, s"${mod.modelName} y vs yp2", lines = true)

    banner ("Quadratic Ridge Regression")
    mod = SymRidgeRegression.quadratic (xx, y, fname)
    mod.trainNtest ()()
    println (mod.summary ())
    yp  = mod.predict (mod.getX)
    yp2 = yp + mu_y
    new Plot (null, y_c, yp, s"${mod.modelName} y_c vs yp", lines = true)
    new Plot (null, y,  yp2, s"${mod.modelName} y vs yp2", lines = true)

    banner ("Cubic Ridge Regression")
    mod = SymRidgeRegression.cubic (xx, y, fname)
    mod.trainNtest ()()
    println (mod.summary ())
    yp  = mod.predict (mod.getX)
    yp2 = yp + mu_y
    new Plot (null, y_c, yp, s"${mod.modelName} y_c vs yp", lines = true)
    new Plot (null, y,  yp2, s"${mod.modelName} y vs yp2", lines = true)

end symRidgeRegressionTest9

