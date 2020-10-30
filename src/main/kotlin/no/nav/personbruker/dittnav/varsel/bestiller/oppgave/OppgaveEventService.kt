package no.nav.personbruker.dittnav.varsel.bestiller.oppgave

import no.nav.brukernotifikasjon.schemas.Nokkel
import no.nav.brukernotifikasjon.schemas.Oppgave
import no.nav.doknotifikasjon.schemas.Doknotifikasjon
import no.nav.personbruker.dittnav.common.util.kafka.RecordKeyValueWrapper
import no.nav.personbruker.dittnav.varsel.bestiller.common.EventBatchProcessorService
import no.nav.personbruker.dittnav.varsel.bestiller.common.exceptions.FieldValidationException
import no.nav.personbruker.dittnav.varsel.bestiller.common.exceptions.NokkelNullException
import no.nav.personbruker.dittnav.varsel.bestiller.common.exceptions.UnvalidatableRecordException
import no.nav.personbruker.dittnav.varsel.bestiller.common.kafka.serializer.getNonNullKey
import no.nav.personbruker.dittnav.varsel.bestiller.config.EventType
import no.nav.personbruker.dittnav.varsel.bestiller.doknotifikasjon.DoknotifikasjonProducer
import no.nav.personbruker.dittnav.varsel.bestiller.doknotifikasjon.DoknotifikasjonTransformer
import no.nav.personbruker.dittnav.varsel.bestiller.metrics.EventMetricsProbe
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.slf4j.LoggerFactory

class OppgaveEventService(
        private val doknotifikasjonProducer: DoknotifikasjonProducer,
        private val metricsProbe: EventMetricsProbe
) : EventBatchProcessorService<Nokkel, Oppgave> {

    private val log = LoggerFactory.getLogger(OppgaveEventService::class.java)

    override suspend fun processEvents(events: ConsumerRecords<Nokkel, Oppgave>) {
        val successfullyValidatedEvents = mutableListOf<RecordKeyValueWrapper<String, Doknotifikasjon>>()
        val problematicEvents = mutableListOf<ConsumerRecord<Nokkel, Oppgave>>()
        events.forEach { event ->
            try {
                if(skalVarsleEksternt(event.value())) {
                    val oppgaveKey = event.getNonNullKey()
                    val oppgave = event.value()
                    val doknotifikasjonKey = DoknotifikasjonTransformer.createDoknotifikasjonKey(oppgaveKey, EventType.OPPGAVE)
                    val doknotifikasjonEvent = DoknotifikasjonTransformer.createDoknotifikasjonFromOppgave(oppgaveKey, oppgave)
                    successfullyValidatedEvents.add(RecordKeyValueWrapper(doknotifikasjonKey, doknotifikasjonEvent))
                }
            } catch (e: NokkelNullException) {
                log.warn("Oppgave-eventet manglet nøkkel. Topic: ${event.topic()}, Partition: ${event.partition()}, Offset: ${event.offset()}", e)
            } catch (e: FieldValidationException) {
                log.warn("Eventet kan ikke brukes fordi det inneholder valideringsfeil, oppgave-eventet vil bli forkastet. EventId: ${event.eventId}, context: ${e.context}", e)
            } catch (e: Exception) {
                problematicEvents.add(event)
                log.warn("Validering av oppgave-event fra Kafka fikk en uventet feil, fullfører batch-en.", e)
            }
        }
        doknotifikasjonProducer.produceDoknotifikasjon(successfullyValidatedEvents)
        kastExceptionHvisMislykkedValidering(problematicEvents)
    }

    private fun skalVarsleEksternt(event: Oppgave?): Boolean {
        return event != null && event.getEksternVarsling()
    }

    private fun kastExceptionHvisMislykkedValidering(problematicEvents: MutableList<ConsumerRecord<Nokkel, Oppgave>>) {
        if (problematicEvents.isNotEmpty()) {
            val message = "En eller flere oppgave-eventer kunne ikke sendes til varselbestiller fordi validering feilet."
            val exception = UnvalidatableRecordException(message)
            exception.addContext("antallMislykkedValidering", problematicEvents.size)
            throw exception
        }
    }
}
