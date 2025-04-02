//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** @author  John Miller, Leela Venkata Sai Vukkurthi
 *  @version 2.0
 *  @date    Mon Dec  2 22:55:42 EST 2024
 *  @see     LICENSE (MIT style license file).
 ^
 *  @note    Model: Recurrent Neural Network (RNN) for Multivariate Time Series
 *
 *  Standard RNN (GRU/LSTM) Formats for Multivariate Time Series Data.
 *  Keras: 3D format expected by GRU/LSTM is [samples, timesteps, features].
 *  => indexing [timestamp t, lags k, variable j]
 */

package scalation
package modeling
package forecasting
package neuralforecasting

import scalation.mathstat.{MatrixD, Plot, VectorD}
import scalation.random.{NormalMat, NormalVec_c}
import ActivationFun.{softmax_, tanh_}
import MatrixD.outer

//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `RNN` class implements Recurrent Neural Network (RNN) via Back Propagation Through
 * Time (BPTT).  At each time point x_t, there is a vector representing several variables
 * or the encoding of a word.  Intended to work for guessing the next work in a sentence
 * or for multi-horizon forecasting.
 * Time series: (x_t: t = 0, 1, ..., n_seq-1) where n_seq is the number of time points/words
 *
 * @param x      the input sequence/time series
 * @param y      the output sequence/time series
 * @param fname  the feature/variable names
 * @param n_mem  the size for hidden state (h) (dimensionality of memory)
 */
class RNN (x: MatrixD, y: MatrixD, fname: Array[String] = null, n_mem: Int = 8)
      extends FitM:

    private val CLASSIF = false // whether to apply classification (e.g., guess next word) or forecast a value
    private val max_epochs = 55 // maximum number of iterations
    private val eta = 0.27 // the learning rate (use 0.25 for rNNTest)
    private val batch_size = 64 // batch size for mini-batch training
    private val truncation_length = 63 // Truncate the back-propagation through time
    private val β = 0 // Momentum hyper parameter
    private val threshold = 5.0 // Threshold for gradient clipping

    private val n_seq = x.dim // e.g., 20, number of words in a sentence (including start and end symbol)
    private val n_var = x.dim2 // e.g., 64, number of variables or distinct words (vocabulary size)\

    // since we will only use one sentence for training,
    // this is also the total steps during training.
//   private val _1 = VectorD.one (n_mem)                              // vector of all ones for e.g., 1 - z

    // initialize parameters (weights and biases)
    private val rmg1 = NormalMat(n_mem, n_var, 0.0, math.sqrt(2.0 / (n_mem + n_var)))
    private val rmg2 = NormalMat(n_mem, n_mem, 0.0, math.sqrt(2.0 / (n_mem + n_mem)))
//  private val rmg3 = NormalMat(n_var, n_mem, 0.0, math.sqrt(2.0 / (n_var + n_mem)))  // Original initialization for V matrix and b_y

    private val rmg4 = NormalMat(y.dim2, n_mem, 0.0, math.sqrt(2.0 / (y.dim2 + n_mem)))

    private val rvg1 = NormalVec_c(n_mem, 0.0, 0.01)                   // random (Normal) vector generators
//  private val rvg3 = NormalVec_c (n_var, 0.0, 0.01)

    private val U   = rmg1.gen                                         // parameters for computing the hidden state
    private val W   = rmg2.gen
    private val b_h = new VectorD(n_mem)                               // bias vector for hidden state

    // decoder for generating output
    private val V   = rmg4.gen                                          // decoder weight matrix
    private val b_y = new VectorD (y.dim2)                              // decoder bias vector: Original initialization for b_y is n_var

    private val h_m1 = rvg1.gen                                         // hidden state @ t = -1 (m1 means minus 1)
    private val h    = new MatrixD (n_seq, n_mem)                       // hidden state h
    private val yp   = new MatrixD (n_seq, y.dim2)                      // predicted output: yp.dim2 is originally n_var
    private val L    = new VectorD (n_seq)                              // store loss function values

    // the partial derivative of weights and biases
    private var db_y: VectorD = new VectorD(b_y.dim)
    private val db_h: VectorD = new VectorD(b_h.dim)
    private val dV = new MatrixD(V.dim, V.dim2)
    private val dW = new MatrixD(W.dim, W.dim2)
    private val dU = new MatrixD(U.dim, U.dim2)

    // initialize velocity parameters for momentum
    private val vU   = new MatrixD(U.dim, U.dim2)
    private val vW   = new MatrixD(W.dim, W.dim2)
    private val vb_h = new VectorD(b_h.dim)
    private val vV   = new MatrixD(V.dim, V.dim2)
    private val vb_y = new VectorD(b_y.dim)

    // parameter grouping for easy access
    private case class ParamGroup (var param: MatrixD, var velocity: MatrixD, var grad: MatrixD) // For matrices

    private case class ParamGroupVector (var param: VectorD, var velocity: VectorD, var grad: VectorD) // For vectors

    private val matrixParams = List (ParamGroup(U, vU, dU),
                                     ParamGroup(W, vW, dW),
                                     ParamGroup(V, vV, dV))

    private val vectorParams = List (ParamGroupVector(b_h, vb_h, db_h),
                                     ParamGroupVector(b_y, vb_y, db_y))

    if fname != null then println (s"RNN: fname = $fname")

    private val L_epoch = new VectorD (max_epochs)                      // store loss function values for each epoch

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Train the RNN using batch gradient descent. IN PROGRESS
     */
    def train(): Unit =
        for it <- 1 to max_epochs do

            val n_batches = math.ceil (n_seq / batch_size).toInt
            for i <- 0 to n_batches do
                val batch_start = i * batch_size
                val batch_end   = math.min (n_seq - 1, (i + 1) * batch_size - 1)
                println (s"batch_start = $batch_start, batch_end = $batch_end")

                forward (batch_start, batch_end)      // forward propagate: get intermediate and output results

                backward (batch_start, batch_end)     // back propagate: calculate gradients (partial derivatives)

                clip_gradients(threshold)            // clip gradients to avoid exploding gradients

                update_params (batch_end - batch_start + 1, leaky = true)  // update parameters (weights and biases)
            end for

            val mse = L.sum / n_seq                                     // mean squared error
            println (s"train: for epoch $it: loss function L = $L")
            banner (s"train: for epoch $it: sum of loss function L.sum = ${L.sum}")
            banner (s"train: for epoch $it: mean squared error = $mse")
            L_epoch(it - 1) = L.sum
        end for
    end train

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Test the RNN predictions.
     *  @param original_extremes
     */
    def test (original_extremes: (Double, Double) = (1.0, 1.0)): Unit =
        new Plot(null, y(?, 0), yp(?, 0), "Plot of y vs yp for RNN", lines = true)

        for col <- 0 until y.dim2 do
            val y_unscaled  = unscaleV (original_extremes, (-2.0, 2.0))(y(?, col))
            val yp_unscaled = unscaleV (original_extremes, (-2.0, 2.0))(yp(?, col))
            banner ("smape value = " + smapeF(y_unscaled, yp_unscaled))    // calculate the Symmetric Mean Absolute Percentage Error
            banner ("mae value   = " + Fit.mae(y_unscaled, yp_unscaled))   // calculate the Mean Absolute Error
        end for

        new Plot (VectorD.range(0, max_epochs), L_epoch, null, "Plot of Loss Function vs Epoch", lines = true)

        banner ("minimum loss epoch = " + L_epoch.argmin())
        banner ("minimum loss value = " + L_epoch.min())
    end test

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Forward propagate calculates yp, loss and intermediate variables for each step.
     *  @param batch_start
     *  @param batch_end
     */
    def forward (batch_start: Int, batch_end: Int): Unit =

        for t <- batch_start to batch_end do

            val h_pre = if t == 0 then h_m1 else h(t - 1)                  // get previous hidden state
            h(t) = tanh_(U * x(t) + W * h_pre + b_h)                       // compute hidden state
            if CLASSIF then
                yp(t) = softmax_(V * h(t) + b_y)                           // activation: softmax for classification
                L(t) = (-y(t) * log_(yp(t))).sum                           // cross-entropy loss function
            else
                yp(t) = V * h(t) + b_y                                     // activation: id for forecasting
                L(t) = ((y(t) - yp(t)).normSq) / 2.0                       // SSE loss function
        end for
    end forward

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Backward propagate to calculate gradients using chain rules in O(n_seq) time.
     *  FIX - add option of using sse loss function and fix affected partial derivatives
     *  @param batch_start
     *  @param batch_end
     */
    def backward (batch_start: Int, batch_end: Int): Unit =
        import ActivationFun.tanhD

        // Reset gradients before accumulating
        matrixParams.foreach (_.grad.setAll(0))                            // Reset matrix gradients
        vectorParams.foreach (_.grad.set(0))                               // Reset vector gradients

        val truncated_start = math.max (0, batch_end - truncation_length)  // Determine the starting point for truncation

        val e = yp - y                                                     // error matrix

        db_y = e.sumV                                                      // vector of row sums

        println ("debug: e.dims = " + e.dims)
        println ("debug: h.dims = " + h.dims)
        println ("dv.dims = " + V.dims)

        for t <- truncated_start until n_seq do dV += outer(e(t), h(t))    // outer vector product
        val dh_T = V.𝐓 * e(n_seq - 1)                                      // partial w.r.t. h_T
        val dh = new MatrixD (n_seq, n_mem)                                // partial w.r.t. h_t
        dh(n_seq - 1) = dh_T                                               // set last row

        for t <- batch_end - 2 to truncated_start by -1 do
            dh(t) = ((tanhD(h(t + 1))) *~: W) * dh(t + 1) + (V.𝐓 * e(t))   // partial w.r.t. h_t

        for t <- truncated_start until batch_end do
            val h_pre = if t == 0 then h_m1 else h(t - 1)                  // get previous hidden state
            dU += outer(dh(t), x(t))
            dW += outer(dh(t), h_pre)
            db_h += tanhD(h(t)) * dh(t)
        end for

        matrixParams.head.grad += dU
        matrixParams(1).grad += dW
        matrixParams(2).grad += dV
        vectorParams.head.grad += db_h
        vectorParams(1).grad += db_y
    end backward

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Backward propagate to calculate gradients using chain rules in O(n_seq) time.
     *  FIX - add option of using sse loss function and fix affected partial derivatives
     *  @oaram threshold
     */
    private def clip_gradients (threshold: Double): Unit =
        for group <- matrixParams do
            val norm = group.grad.normF                                    // calculate the norm of the gradient
            if norm > threshold then
                group.grad *= (threshold / norm)                           // scale the gradient

        for group <- vectorParams do
            val norm = group.grad.norm                                     // calculate the norm of the gradient
            if norm > threshold then
                group.grad *= (threshold / norm)                           // scale the gradient
    end clip_gradients

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Based on the calculated partial derivatives, update the parameters (weights
     *  and biases).
     ^  @pram batch_size
     ^  @pram leaky
     */
    def update_params (batch_size: Int, leaky: Boolean = true): Unit =
        for group <- matrixParams do
            group.velocity *= β
            group.velocity += group.grad * (if leaky then 1 else (1 - β))
            group.param    -= group.velocity * eta / batch_size

        // Check and log gradients for vectors
        for group <- vectorParams do
            group.velocity *= β
            group.velocity += group.grad * (if leaky then 1 else (1 - β))
            group.param    -= group.velocity * eta / batch_size
    end update_params

end RNN


//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `RNN` companion object provides factory methods.
 */
object RNN:

    import ActivationFun._

    //::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
    /** Create a `RNN` with automatic rescaling from a data matrix and response matrix.
     * @param x      the input/data matrix
     * @param y      the output/response matrix
     * @param fname  the feature/variable names
     * @param n_mem  the size of the hidden state (dimensionality of memory)
     */
    def rescale(x: MatrixD, y: MatrixD, fname: Array[String] = null, n_mem: Int = 4): RNN =
        val x_s = rescaleX (x, f_sigmoid)
        val y_s = rescaleY (y, f_sigmoid)._1

//      println (s" scaled: x = $x_s \n scaled y = $y_s")
        new RNN (x_s, y_s, fname, n_mem)
    end rescale

end RNN


//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `rNNTest` main function tests the `RNN` class on randomly generated
 *  sequence data meant to represent encoded words
 *  > runMain scalation.modeling.forecasting.neuralforecasting.rNNTest
 */
@main def rNNTest (): Unit =

    val n_seq = 8
    val n_var = 5

    val (x_t, y_t) = genSequenceData (n_seq, n_var)

    println (s"x_t = $x_t")
    println (s"y_t = $y_t")

    banner ("Create a Recurrent Neural Network (RNN)")
    val mod = new RNN (x_t, y_t)
    mod.train ()
    mod.test ()

end rNNTest


//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `rNNTest2` main function tests the `RNN` class on sequence data read as words
 *  in a file that encoded and pass into `RNN`
 *  > runMain scalation.modeling.forecasting.neuralforecasting.rNNTest2
 */
@main def rNNTest2 (): Unit =

    println("read words from a text file")

//  FIX - find example text

end rNNTest2


//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** The `rNNTest3` main function tests the `RNN` class on sequence/time series data
 *  corresponding to the lake level dataset using multiple lags.
 *  > runMain scalation.modeling.forecasting.neuralforecasting.rNNTest3
 */
@main def rNNTest3 (): Unit =

    import Example_LakeLevels.y
    import MakeMatrix4TS._
    val hh  = 2                                                       // forecasting horizon - FIX - currently lags == hh
    hp("p") = 2                                                       // number of lags to include

    val y_s = scaleV (extreme (y), (-2.0, 2.0))(y)                    // rescale y to active domain of sigmoid, tanh

    val x  = ARY.buildMatrix (y_s, hp)                                // column for each lag
    val yy = makeMatrix4Y (y_s, hh)

    println (s"x.dims = ${x.dims}, yy.dims = ${yy.dims}")

    banner ("Create a Recurrent Neural Network Unit (RNN)")
    val mod = new RNN (x, yy)                                          // call constructor
    mod.train ()                                                       // train the model
    mod.test ()                                                        // test the model

end rNNTest3

 
//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
@main def rNNTest4 (): Unit =

    import MakeMatrix4TS._
    val hh  = 2                                                       // forecasting horizon - FIX - currently lags == hh
    hp("p") = 2                                                       // number of lags to include

    var y = Example_Covid.loadData_y ("new_deaths")

    y = y(0 until 116)

    val original_extremes = extreme(y)

    println ("original_extremes.type = " + original_extremes.getClass)

    val y_s = scaleV (extreme (y), (-2.0, 2.0))(y)                    // rescale y to active domain of sigmoid, tanh

    val x  = ARY.buildMatrix (y_s, hp)                                // column for each lag
    val yy = makeMatrix4Y (y_s, hh)


    println (s"x.dims = ${x.dims}, yy.dims = ${yy.dims}")

    banner ("Create a Recurrent Neural Network Unit (RNN)")
    val mod = new RNN (x, yy)                                         // call constructor
    mod.train ()                                                      // train the model
    mod.test (original_extremes)

    print ("y(116) = " + y(115))

end rNNTest4

// GRU, using Trait for Code reusability


