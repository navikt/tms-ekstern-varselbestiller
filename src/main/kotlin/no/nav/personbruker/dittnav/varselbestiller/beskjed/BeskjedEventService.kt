package no.nav.personbruker.dittnav.varselbestiller.beskjed

import no.nav.brukernotifikasjon.schemas.internal.BeskjedIntern
import no.nav.brukernotifikasjon.schemas.internal.NokkelIntern
import no.nav.doknotifikasjon.schemas.Doknotifikasjon
import no.nav.personbruker.dittnav.varselbestiller.common.EventBatchProcessorService
import no.nav.personbruker.dittnav.varselbestiller.common.database.ListPersistActionResult
import no.nav.personbruker.dittnav.varselbestiller.common.exceptions.UntransformableRecordException
import no.nav.personbruker.dittnav.varselbestiller.config.Eventtype
import no.nav.personbruker.dittnav.varselbestiller.doknotifikasjon.DoknotifikasjonCreator
import no.nav.personbruker.dittnav.varselbestiller.doknotifikasjon.DoknotifikasjonProducer
import no.nav.personbruker.dittnav.varselbestiller.metrics.EventMetricsSession
import no.nav.personbruker.dittnav.varselbestiller.metrics.MetricsCollector
import no.nav.personbruker.dittnav.varselbestiller.metrics.Producer
import no.nav.personbruker.dittnav.varselbestiller.varselbestilling.Varselbestilling
import no.nav.personbruker.dittnav.varselbestiller.varselbestilling.VarselbestillingRepository
import no.nav.personbruker.dittnav.varselbestiller.varselbestilling.VarselbestillingTransformer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class BeskjedEventService(
        private val doknotifikasjonProducer: DoknotifikasjonProducer,
        private val varselbestillingRepository: VarselbestillingRepository,
        private val metricsCollector: MetricsCollector
) : EventBatchProcessorService<NokkelIntern, BeskjedIntern> {

    private val log: Logger = LoggerFactory.getLogger(BeskjedEventService::class.java)

    override suspend fun processEvents(events: ConsumerRecords<NokkelIntern, BeskjedIntern>) {
        val successfullyTransformedEvents = mutableMapOf<String, Doknotifikasjon>()
        val problematicEvents = mutableListOf<ConsumerRecord<NokkelIntern, BeskjedIntern>>()
        val varselbestillinger = mutableListOf<Varselbestilling>()

        metricsCollector.recordMetrics(eventType = Eventtype.BESKJED_INTERN) {
            events.forEach { event ->
                try {
                    val beskjedKey = event.key()
                    val beskjedEvent = event.value()
                    val producer = Producer(event.namespace, event.appnavn)
                    countAllEventsFromKafkaForProducer(producer)
                    if(beskjedEvent.getEksternVarsling()) {
                        val doknotifikasjonKey = DoknotifikasjonCreator.createDoknotifikasjonKey(beskjedKey, Eventtype.BESKJED_INTERN)
                        val doknotifikasjon = DoknotifikasjonCreator.createDoknotifikasjonFromBeskjed(beskjedKey, beskjedEvent)
                        successfullyTransformedEvents[doknotifikasjonKey] = doknotifikasjon
                        varselbestillinger.add(VarselbestillingTransformer.fromBeskjed(beskjedKey, beskjedEvent, doknotifikasjon))
                        countSuccessfulEksternVarslingForProducer(producer)
                    }
                } catch (e: Exception) {
                    countFailedEksternVarslingForProducer(Producer(event.namespace, event.appnavn))
                    problematicEvents.add(event)
                    log.warn("Transformasjon av beskjed-event fra Kafka feilet, fullfører batch-en før polling stoppes.", e)
                }
            }
            if (successfullyTransformedEvents.isNotEmpty()) {
                produceDoknotifikasjonerAndPersistToDB(this, successfullyTransformedEvents, varselbestillinger)
            }
            if (problematicEvents.isNotEmpty()) {
                throwExceptionForProblematicEvents(problematicEvents)
            }
        }
    }

    private suspend fun produceDoknotifikasjonerAndPersistToDB(eventMetricsSession: EventMetricsSession,
                                                               successfullyTransformedEvents: Map<String, Doknotifikasjon>,
                                                               varselbestillinger: List<Varselbestilling>): ListPersistActionResult<Varselbestilling> {
        val duplicateVarselbestillinger = varselbestillingRepository.fetchVarselbestillingerForBestillingIds(successfullyTransformedEvents.keys.toList())
        return if(duplicateVarselbestillinger.isEmpty()) {
            produce(successfullyTransformedEvents, varselbestillinger)
        } else {
            val duplicateBestillingIds = duplicateVarselbestillinger.map { it.bestillingsId }
            val remainingTransformedEvents = successfullyTransformedEvents.filterKeys { bestillingsId -> !duplicateBestillingIds.contains(bestillingsId) }
            val varselbestillingerToOrder = varselbestillinger.filter { !duplicateBestillingIds.contains(it.bestillingsId) }
            logDuplicateVarselbestillinger(eventMetricsSession, duplicateVarselbestillinger)
            produce(remainingTransformedEvents, varselbestillingerToOrder)
        }
    }

    private suspend fun produce(successfullyTransformedEvents: Map<String, Doknotifikasjon>, varselbestillinger: List<Varselbestilling>): ListPersistActionResult<Varselbestilling> {
        return doknotifikasjonProducer.sendAndPersistEvents(successfullyTransformedEvents, varselbestillinger)
    }

    private fun logDuplicateVarselbestillinger(eventMetricsSession: EventMetricsSession, duplicateVarselbestillinger: List<Varselbestilling>) {
        duplicateVarselbestillinger.forEach{
            log.info("Varsel med bestillingsid ${it.bestillingsId} er allerede bestilt, bestiller ikke på nytt.")
            eventMetricsSession.countDuplicateVarselbestillingForProducer(Producer(it.namespace, it.appnavn))
        }
    }

    private fun throwExceptionForProblematicEvents(problematicEvents: MutableList<ConsumerRecord<NokkelIntern, BeskjedIntern>>) {
        val message = "En eller flere beskjed-eventer kunne ikke sendes til varselbestiller fordi transformering feilet."
        val exception = UntransformableRecordException(message)
        exception.addContext("antallMislykkedeTransformasjoner", problematicEvents.size)
        throw exception
    }
}
