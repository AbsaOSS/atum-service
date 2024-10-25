package za.co.absa.atum.reader.server

import cats.Monad
import io.circe.Decoder

abstract class GenericServerConnection[F[_] : Monad](val serverUrl: String) {
  def query[R: Decoder](endpointUri: String): F[R]
}
