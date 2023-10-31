package za.co.absa.atum.model.utils

object OptionImplicits {
  implicit class OptionEnhancements[T](val option: Option[T]) extends AnyVal {
    /**
      * Gets the `option` value or throws the provided exception
      *
      * @param exception the exception to throw in case the `option` is None
      * @return
      */
    def getOrThrow(exception: => Throwable): T = {
      option.getOrElse(throw exception)
    }
  }
}
