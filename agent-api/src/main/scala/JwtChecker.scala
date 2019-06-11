package htrc.agent

import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.security.InvalidAlgorithmParameterException
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.NoSuchAlgorithmException
import java.security.PublicKey
import java.security.interfaces.RSAPublicKey
import java.security.cert.Certificate
import java.security.cert.CertificateException
import scala.util.Try
import com.auth0.jwt.JWT
import com.auth0.jwt.interfaces.DecodedJWT
import com.auth0.jwt.algorithms.Algorithm
import akka.event.Logging

class JwtChecker {
  def verify(token: String): Try[DecodedJWT] = {
    Try(throw new Exception("Unable to verify JWT"))
  }
}

class HtrcJwtChecker(alg: Algorithm, issuer: String, ignoreExpiry: Boolean)
  extends JwtChecker {
  val SECONDS_IN_A_YEAR = 366 * 24 * 60 * 60
  // leeway is defined in seconds
  val leeway = if (ignoreExpiry) (10 * SECONDS_IN_A_YEAR) else 10
  val jwtVerifier =
    JWT.require(alg).withIssuer(issuer).acceptExpiresAt(leeway).build()

  override def verify(token: String): Try[DecodedJWT] = {
    Try(jwtVerifier.verify(token))
  }
}

object JwtChecker {
  val log = Logging(HtrcSystem.system, getClass)

  def apply(): JwtChecker = {
    // JwtChecker handles only the RSA256 algorithm
    if (! (HtrcConfig.jwtVerificationAlg == "RSASHA256" ||
           HtrcConfig.jwtVerificationAlg == "RSA256")) {
      log.error("Unsupported JWT verification algorithm " +
        HtrcConfig.jwtVerificationAlg +
        "; expected JWT verification algorithms: RSASHA256, RSA256")
      new JwtChecker
    } else {
      try {
        val publicKey = getPubKey(HtrcConfig.jwtPublicKeyPath,
          HtrcConfig.jwtPublicKeyPass, HtrcConfig.jwtPublicKeyAlias)
        val alg = Algorithm.RSA256(publicKey.asInstanceOf[RSAPublicKey], null)
        new HtrcJwtChecker(alg, HtrcConfig.jwtIssuer, HtrcConfig.jwtIgnoreExpiry)
      }
      catch {
        case e: Exception =>
          log.error("Error while loading public key for JWT verification: "
            + e)
          new JwtChecker
      }
    }
  }

  def getPubKey(keystorePath: String, keystorePass: String,
    pubkeyAlias: String): PublicKey = {
    val keystore = KeyStore.getInstance(KeyStore.getDefaultType())
    keystore.load(readKeyStore(keystorePath), keystorePass.toCharArray())

    val cert = keystore.getCertificate(pubkeyAlias)

    cert.getPublicKey()

    /*
    catch {
      case e @ (_: KeyStoreException |
          _: CertificateException |
          _: IOException |
          _: NoSuchAlgorithmException ) =>
        throw new RuntimeException("Error while loading public key, ", e)
    }
     */
   }

  def readKeyStore(path: String): InputStream = {
    // val classLoader = SecurityUtils.class.getClassLoader()
    // val keystoreResource = classLoader.getResource(path)
    // if (keystoreResource != null) {
    //     return classLoader.getResource(path).openStream()
    // }

    return new FileInputStream(path)
  }
}
