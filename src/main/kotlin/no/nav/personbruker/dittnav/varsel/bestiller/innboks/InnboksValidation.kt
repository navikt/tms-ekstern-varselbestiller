package no.nav.personbruker.dittnav.varsel.bestiller.innboks

import no.nav.brukernotifikasjon.schemas.Nokkel
import no.nav.personbruker.dittnav.varsel.bestiller.common.validation.validateMaxLength
import no.nav.personbruker.dittnav.varsel.bestiller.common.validation.validateNonNullFieldMaxLength
import no.nav.personbruker.dittnav.varsel.bestiller.common.validation.validateSikkerhetsnivaa

object InnboksValidation {

    fun validateEvent(externalNokkel: Nokkel, externalValue: no.nav.brukernotifikasjon.schemas.Innboks) {
        validateNonNullFieldMaxLength(externalNokkel.getSystembruker(), "systembruker", 100)
        validateNonNullFieldMaxLength(externalNokkel.getEventId(), "eventId", 50)
        validateNonNullFieldMaxLength(externalValue.getFodselsnummer(), "fodselsnummer", 11)
        validateNonNullFieldMaxLength(externalValue.getGrupperingsId(), "grupperingsId", 100)
        validateNonNullFieldMaxLength(externalValue.getTekst(), "tekst", 500)
        validateMaxLength(externalValue.getLink(), "link", 200)
        validateSikkerhetsnivaa(externalValue.getSikkerhetsnivaa())
    }

}