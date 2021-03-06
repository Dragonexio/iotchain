package jbok.app.helper

import jbok.core.models.Account
import jbok.evm.solidity.ABIDescription.ParameterType
import jbok.evm.solidity.{AddressType, BoolType, BytesNType, BytesType, IntType, SolidityParser, StringType, UIntType}

import scala.util.matching.Regex

object InputValidator {
  val number   = "0123456789"
  val alpha    = new Regex("[a-z]+")
  val hex      = "0123456789abcdef"
  val ipv4Item = "([01]?\\d\\d?|2[0-4]\\d|25[0-5])"
  val ipv4     = new Regex(s"^$ipv4Item\\.$ipv4Item\\.$ipv4Item\\.$ipv4Item" + "$")

  private def getHexValue(data: String): String = data match {
    case d if d.startsWith("0x") => d.substring(2)
    case _                       => data
  }

  def isValidAddress(address: String): Boolean = {
    val value = getHexValue(address)
    value.length == 40 && value.forall(hex.contains(_))
  }

  def isValidNumber(n: String): Boolean = n.length > 0 && n.forall(number.contains(_))

  def isValidValue(value: String, account: Option[Account]): Boolean =
    value.forall(number.contains(_)) && account.forall(_.balance.toN >= BigInt(value))

  def isValidData(data: String): Boolean = {
    val value = getHexValue(data)
    value.length > 0 && value.length % 2 == 0 && value.forall(hex.contains(_))
  }

  def isValidIPv4(data: String): Boolean =
    ipv4.pattern.matcher(data).matches

  def isValidPort(data: String): Boolean =
    data.nonEmpty && data.forall(number.contains(_)) && data.length <= 5 && data.toInt > 1000 && data.toInt < 65535

  def isValidCode(data: String): Boolean = SolidityParser.parseSource(data).isSuccess

  def isValidBool(data: String): Boolean = data == "true" || data == "false"

  def isValidBytes(data: String, length: Option[Int] = None): Boolean = {
    val value = getHexValue(data)

    val dataValid = data.forall(hex.contains(_))
    dataValid && length.map(value.length == _ * 2).getOrElse(value.length % 2 == 0)
  }

  def isValidString(data: String): Boolean =
    data.length >= 2 && data.startsWith("\"") && data.endsWith("\"")

  def getValidator(parameterType: ParameterType): Option[String => Boolean] =
    if (parameterType.arrayList.isEmpty) {
      parameterType.solidityType match {
        case AddressType()            => Some(isValidAddress)
        case UIntType(_) | IntType(_) => Some(isValidNumber)
        case StringType()             => Some(isValidString)
        case BoolType()               => Some(isValidBool)
        case BytesType() =>
          def isValidBytesNone(data: String) = isValidString(data) && isValidBytes(data.substring(1, data.length - 1))

          Some(isValidBytesNone)
        case BytesNType(n) =>
          def isValidBytesN(data: String) = isValidString(data) && isValidBytes(data.substring(1, data.length - 1), Some(n))

          Some(isValidBytesN)
        case _ => None
      }
    } else { None }

  def getInputPrefixAndSuffix(parameterType: ParameterType): (String, String) = {
    val fix   = "\""
    val empty = ""
    if (parameterType.arrayList.isEmpty) {
      parameterType.solidityType match {
        case AddressType() => fix   -> fix
        case _             => empty -> empty
      }
    } else { empty -> empty }
  }
}
