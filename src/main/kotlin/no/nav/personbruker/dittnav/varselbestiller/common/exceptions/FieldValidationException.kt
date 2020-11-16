package no.nav.personbruker.dittnav.varselbestiller.common.exceptions

open class FieldValidationException (message: String, cause: Throwable?) : AbstractPersonbrukerException(message, cause) {
    constructor(message: String) : this(message, null)
}