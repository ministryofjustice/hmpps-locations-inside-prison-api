package uk.gov.justice.digital.hmpps.locationsinsideprison.dto

import java.util.*
import java.util.regex.Pattern

fun formatLocation(locationDescription: String): String {
  val description: String = locationDescription.capitalizeWords()
  val matcher = pattern.matcher(description)

  val stringBuilder = StringBuilder()
  while (matcher.find()) {
    val matched = matcher.group(1)
    matcher.appendReplacement(stringBuilder, matched.uppercase(Locale.getDefault()))
  }
  matcher.appendTail(stringBuilder)
  return stringBuilder.toString()
}

// \s on its own does not match a non-breaking space, which NOMIS names sometimes contain
private val WHITESPACE = Regex("[\\s\\p{Z}]+")

/**
 * Tidies a location name before it is saved: removes whitespace from the start and end and turns any run of
 * whitespace inside it into a single space. Returns null when nothing is left. A name with a leading space sorts
 * first and is missed by look-ups on the exact name (MAPA-422).
 */
fun String?.tidyLocalName(): String? = this?.replace(WHITESPACE, " ")?.trim()?.ifEmpty { null }

/**
 * List of abbreviations
 */
private val ABBREVIATIONS = listOf(
  "AAA",
  "ADTP",
  "AIC",
  "AM",
  "ATB",
  "BBV",
  "BHU",
  "BICS",
  "CAD",
  "CASU",
  "CES",
  "CGL",
  "CIT",
  "CSC",
  "CSCP",
  "CSU",
  "CTTLS",
  "CV",
  "DART",
  "DDU",
  "DHL",
  "DRU",
  "ETS",
  "ETSP",
  "ESOL",
  "FT",
  "GP",
  "GFSL",
  "HB\\d+",
  "HCC",
  "HDC",
  "HMP",
  "HMPYOI",
  "HR",
  "IAG",
  "ICT",
  "IDTS",
  "IMB",
  "IPD",
  "IPSO",
  "ISMS",
  "IT",
  "ITQ",
  "JAC",
  "LB\\d+",
  "LRC",
  "L&S",
  "MBU",
  "MCASU",
  "MDT",
  "MOD",
  "MPU",
  "NVQ",
  "NUJ",
  "OBP",
  "OMU",
  "OU",
  "PACT",
  "PASRO",
  "PCVL",
  "PE",
  "PICTA",
  "PIPE",
  "PM",
  "PT",
  "PTTLS",
  "RAM",
  "RAPT",
  "ROTL",
  "RSU",
  "SDP",
  "SIU",
  "SMS",
  "SOTP",
  "SPU",
  "STC",
  "TLC",
  "TSP",
  "TV",
  "UK",
  "VCC",
  "VDT",
  "VP",
  "VPU",
  "VTC",
  "WFC",
  "YPSMS",
  "YOI",
)

private val pattern: Pattern =
  Pattern.compile("\\b(" + ABBREVIATIONS.joinToString("|") + ")\\b", Pattern.CASE_INSENSITIVE)
