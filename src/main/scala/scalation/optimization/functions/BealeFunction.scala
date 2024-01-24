
//::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
/** @author  André Filipe Caldas Laranjeira
 *  @version 2.0
 *  @note    Mon Jan 22 15:21:01 EST 2024
 *  @see     LICENSE (MIT style license file).
 *------------------------------------------------------------------------------
 *  Object to represent the Beale function for tests and benchmarks performed on
 *  function optimization and gradient descent classes.
 */

// Package definition.
package scalation
package optimization
package functions

// Project imports.
import scalation.mathstat.VectorD

// Object.
object BealeFunction extends BenchmarkFunction:
    // Public fields.
    val functionMinimum: VectorD = VectorD(3, 0.5)

    // Public methods.
    def objectiveFunction(x: VectorD): Double = (1.5 - x(0) + x(0) * x(1)) ~^ 2 + (2.25 - x(0) + x(0) * (x(1) ~^ 2)) ~^ 2 + (2.625 - x(0) + x(0) * (x(1) ~^ 3)) ~^ 2

    // Overridden public methods.
    override def gradientFunction(x: VectorD): VectorD = VectorD(2 * (1.5 - x(0) + x(0) * x(1)) * (-1 + x(1)) +
        2 * (2.25 - x(0) + x(0) * (x(1) ~^ 2)) * (-1 + (x(1) ~^ 2)) +
        2 * (2.625 - x(0) + x(0) * (x(1) ~^ 3)) * (-1 + (x(1) ~^ 3)),
        2 * (1.5 - x(0) + x(0) * x(1)) * x(0) +
            2 * (2.25 - x(0) + x(0) * (x(1) ~^ 2)) * (2 * x(0) * x(1)) +
            2 * (2.625 - x(0) + x(0) * (x(1) ~^ 3)) * (3 * x(0) * (x(1) ~^ 2)))
end BealeFunction
