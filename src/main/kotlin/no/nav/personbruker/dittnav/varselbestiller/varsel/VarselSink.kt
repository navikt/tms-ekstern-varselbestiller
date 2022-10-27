package no.nav.personbruker.dittnav.varselbestiller.varsel

import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.personbruker.dittnav.varselbestiller.doknotifikasjon.DoknotifikasjonCreator.createDoknotifikasjonFromVarsel
import no.nav.personbruker.dittnav.varselbestiller.doknotifikasjon.DoknotifikasjonProducer
import no.nav.personbruker.dittnav.varselbestiller.varselbestilling.VarselbestillingRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class VarselSink(
    rapidsConnection: RapidsConnection,
    private val doknotifikasjonProducer: DoknotifikasjonProducer,
    private val varselbestillingRepository: VarselbestillingRepository,
    private val rapidMetricsProbe: RapidMetricsProbe,
    private val writeToDb: Boolean
) :
    River.PacketListener {
    private val log: Logger = LoggerFactory.getLogger(VarselSink::class.java)

    init {
        River(rapidsConnection).apply {
            validate { it.demandAny("@event_name", listOf("beskjed", "oppgave", "innboks")) }
            validate { it.demandValue("eksternVarsling", true) }
            //aktiv == true?
            validate { it.requireKey(
                "namespace",
                "appnavn",
                "eventId",
                "fodselsnummer",
                "sikkerhetsnivaa",
                "eksternVarsling"
            ) }
            validate { it.interestedIn(
                "prefererteKanaler",
                "smsVarslingstekst",
                "epostVarslingstekst",
                "epostVarslingstittel"
            ) }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val varsel = Varsel(
            varselType = VarselType.valueOf(packet["@event_name"].textValue().uppercase()),
            namespace = packet["namespace"].textValue(),
            appnavn = packet["appnavn"].textValue(),
            eventId = packet["eventId"].textValue(),
            fodselsnummer = packet["fodselsnummer"].textValue(),
            sikkerhetsnivaa = packet["sikkerhetsnivaa"].intValue(),
            eksternVarsling = packet["eksternVarsling"].booleanValue(),
            prefererteKanaler = packet["prefererteKanaler"].map { it.textValue() },
            smsVarslingstekst = packet["smsVarslingstekst"].textValue(),
            epostVarslingstekst = packet["epostVarslingstekst"].textValue(),
            epostVarslingstittel = packet["epostVarslingstittel"].textValue()
        )

        val dokNotifikasjon = createDoknotifikasjonFromVarsel(varsel)

        runBlocking {
            val isDuplicateVarselbestilling =
                varselbestillingRepository.fetchVarselbestillingerForEventIds(listOf(varsel.eventId)).isNotEmpty()

            if (!isDuplicateVarselbestilling) {
                if (writeToDb) {
                    doknotifikasjonProducer.sendAndPersistBestillingBatch(
                        listOf(varsel.toVarselBestilling()),
                        listOf(dokNotifikasjon)
                    )
                    log.info("Behandlet varsel fra rapid med eventid ${varsel.eventId}")
                } else {
                    log.info("Dryrun: varsel fra rapid med eventid ${varsel.eventId}")
                }
            }
            rapidMetricsProbe.countDoknotifikasjonProduced()
        }
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        log.error(problems.toString())
    }
}
