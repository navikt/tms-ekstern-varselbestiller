package no.nav.personbruker.dittnav.varsel.bestiller.doknotifikasjon

import no.nav.doknotifikasjon.schemas.Doknotifikasjon
import no.nav.doknotifikasjon.schemas.PrefererteKanal

object AvroDoknotifikasjonObjectMother {

    private val defaultBestillingsId = "1"
    private val defaultBestillerId = "bestillerid"
    private val defaultFodselsnr = "1234"
    private val defaultTittel = "Dette er Beskjed til brukeren"
    private val defaultEpostTekst = "E-posttekst"
    private val defaultSmsTekst = "SMS-tekst"
    private val defaultPrefererteKanaler = listOf(PrefererteKanal.EPOST)

    fun giveMeANumberOfDoknotifikasjoner(numberOfEvents: Int): List<Doknotifikasjon> {
        val doknotifikasjoner = mutableListOf<Doknotifikasjon>()
        for(i in 0 until numberOfEvents) {
            doknotifikasjoner.add(createDoknotifikasjon(i.toString()))
        }
        return doknotifikasjoner
    }

    fun createDoknotifikasjon(bestillingsId: String): Doknotifikasjon {
        return Doknotifikasjon(bestillingsId, defaultBestillerId, defaultFodselsnr, 1, 1, defaultTittel, defaultEpostTekst, defaultSmsTekst, defaultPrefererteKanaler)
    }

    fun createDoknotifikasjon(): Doknotifikasjon {
        return Doknotifikasjon(defaultBestillingsId, defaultBestillerId, defaultFodselsnr, 1, 1, defaultTittel, defaultEpostTekst, defaultSmsTekst, defaultPrefererteKanaler)
    }
}
