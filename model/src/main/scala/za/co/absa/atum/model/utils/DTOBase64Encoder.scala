package za.co.absa.atum.model.utils

import io.circe.Encoder
import io.circe.syntax.EncoderOps

import java.util.Base64

object DTOBase64Encoder {

  def encodeDTO[T](input: T, encoder: Encoder[T]): String = {
    Base64.getUrlEncoder.encodeToString(
      input.asJson(encoder).noSpaces.getBytes("UTF-8")
    )
  }

}
