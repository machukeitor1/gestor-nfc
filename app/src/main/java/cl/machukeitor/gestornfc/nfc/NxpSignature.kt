package cl.machukeitor.gestornfc.nfc

import java.math.BigInteger

/**
 * Verificación de la firma de originalidad de NXP (comando READ_SIG).
 * NXP firma el UID con ECDSA sobre la curva secp128r1, sin hash.
 * Java no trae esa curva, así que la aritmética se hace a mano.
 */
object NxpSignature {

    private val P = BigInteger("FFFFFFFDFFFFFFFFFFFFFFFFFFFFFFFF", 16)
    private val A = BigInteger("FFFFFFFDFFFFFFFFFFFFFFFFFFFFFFFC", 16)
    private val N = BigInteger("FFFFFFFE0000000075A30D1B9038A115", 16)
    private val G = Point(
        BigInteger("161FF7528B899B2D0C28607CA52C5B86", 16),
        BigInteger("CF5AC8395BAFEB13C02DA292DDED7A83", 16),
    )

    /** Clave pública de originalidad de NXP para NTAG21x / Ultralight EV1. */
    private val NTAG_PUBLIC_KEY = publicKey(
        "04494E1A386D3D3CFE3DC10E5DE68A499B1C202DB5B132393E89ED19FE5BE8BC61"
    )

    private data class Point(val x: BigInteger, val y: BigInteger)

    private fun publicKey(hex: String): Point {
        val b = hex.hexToBytes()
        return Point(BigInteger(1, b.copyOfRange(1, 17)), BigInteger(1, b.copyOfRange(17, 33)))
    }

    /** Devuelve true si la firma de 32 bytes corresponde al UID con la clave de NXP. */
    fun verify(uid: ByteArray, signature: ByteArray): Boolean {
        if (signature.size != 32 || signature.all { it == 0.toByte() }) return false
        val r = BigInteger(1, signature.copyOfRange(0, 16))
        val s = BigInteger(1, signature.copyOfRange(16, 32))
        if (r.signum() == 0 || r >= N || s.signum() == 0 || s >= N) return false
        val e = BigInteger(1, uid)
        val w = s.modInverse(N)
        val u1 = e.multiply(w).mod(N)
        val u2 = r.multiply(w).mod(N)
        val x = add(multiply(G, u1), multiply(NTAG_PUBLIC_KEY, u2)) ?: return false
        return x.x.mod(N) == r
    }

    private fun add(p1: Point?, p2: Point?): Point? {
        if (p1 == null) return p2
        if (p2 == null) return p1
        val lambda = if (p1.x == p2.x) {
            if ((p1.y + p2.y).mod(P).signum() == 0) return null
            p1.x.pow(2).multiply(BigInteger.valueOf(3)).add(A)
                .multiply(p1.y.shiftLeft(1).modInverse(P)).mod(P)
        } else {
            (p2.y - p1.y).multiply((p2.x - p1.x).modInverse(P)).mod(P)
        }
        val x3 = (lambda.pow(2) - p1.x - p2.x).mod(P)
        val y3 = (lambda.multiply(p1.x - x3) - p1.y).mod(P)
        return Point(x3, y3)
    }

    private fun multiply(p: Point, k: BigInteger): Point? {
        var result: Point? = null
        var addend: Point? = p
        for (i in 0 until k.bitLength()) {
            if (k.testBit(i)) result = add(result, addend)
            addend = add(addend, addend)
        }
        return result
    }
}
