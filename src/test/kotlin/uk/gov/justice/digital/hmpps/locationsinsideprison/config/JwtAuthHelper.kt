package uk.gov.justice.digital.hmpps.locationsinsideprison.config

import io.jsonwebtoken.Jwts
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpHeaders
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.locationsinsideprison.SYSTEM_USERNAME
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import java.time.Duration
import java.util.*

@Component
class JwtAuthHelper {
  private lateinit var keyPair: KeyPair

  init {
    val gen = KeyPairGenerator.getInstance("RSA")
    gen.initialize(2048)
    keyPair = gen.generateKeyPair()
  }

  @Bean
  fun jwtDecoder(): JwtDecoder = NimbusJwtDecoder.withPublicKey(keyPair.public as RSAPublicKey).build()

  fun setAuthorisation(
    user: String = SYSTEM_USERNAME,
    roles: List<String> = listOf(),
    scopes: List<String> = listOf(),
  ): (HttpHeaders) -> Unit {
    val token = createJwt(
      subject = user,
      scope = scopes,
      expiryTime = Duration.ofHours(1L),
      roles = roles,
    )
    return { it.set(HttpHeaders.AUTHORIZATION, "Bearer $token") }
  }

  internal fun createJwt(
    subject: String?,
    scope: List<String>? = listOf(),
    roles: List<String>? = listOf(),
    expiryTime: Duration = Duration.ofHours(1),
    jwtId: String = UUID.randomUUID().toString(),
  ): String =
    mutableMapOf<String, Any>()
      .also { subject?.let { subject -> it["user_name"] = subject } }
      .also { it["client_id"] = "locations-inside-prison-client" }
      .also { roles?.let { roles -> it["authorities"] = roles } }
      .also { scope?.let { scope -> it["scope"] = scope } }
      .let {
        Jwts.builder()
          .id(jwtId)
          .subject(subject)
          .claims(it.toMap())
          .expiration(Date(System.currentTimeMillis() + expiryTime.toMillis()))
          .signWith(keyPair.private, Jwts.SIG.RS256)
          .compact()
      }
}
