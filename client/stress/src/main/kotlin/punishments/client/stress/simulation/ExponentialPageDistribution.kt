package punishments.client.stress.simulation

import kotlin.math.ln
import kotlin.random.Random

class ExponentialPageDistribution(
    private val maxPage: Int,
    private val lambda: Double
) {
    init {
        require(maxPage >= 0) { "maxPage must be non-negative" }
        require(lambda > 0.0) { "lambda must be positive" }
    }

    fun pick(random: Random = Random.Default): Int = pick(random.nextDouble())

    fun pick(sample: Double): Int {
        if (maxPage == 0) {
            return 0
        }
        val boundedSample = sample.coerceIn(0.0, 0.999999999999)
        val raw = -ln(1.0 - boundedSample) / lambda
        return raw.toInt().coerceIn(0, maxPage)
    }
}
