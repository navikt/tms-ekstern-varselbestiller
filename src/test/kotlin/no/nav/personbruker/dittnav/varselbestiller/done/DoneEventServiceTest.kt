package no.nav.personbruker.dittnav.varselbestiller.done

import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.brukernotifikasjon.schemas.Done
import no.nav.brukernotifikasjon.schemas.Nokkel
import no.nav.brukernotifikasjon.schemas.builders.exception.FieldValidationException
import no.nav.doknotifikasjon.schemas.DoknotifikasjonStopp
import no.nav.personbruker.dittnav.varselbestiller.common.objectmother.ConsumerRecordsObjectMother
import no.nav.personbruker.dittnav.varselbestiller.doknotifikasjonStopp.AvroDoknotifikasjonStoppObjectMother
import no.nav.personbruker.dittnav.varselbestiller.doknotifikasjonStopp.DoknotifikasjonStoppProducer
import no.nav.personbruker.dittnav.varselbestiller.doknotifikasjonStopp.DoknotifikasjonStoppTransformer
import no.nav.personbruker.dittnav.varselbestiller.metrics.EventMetricsSession
import no.nav.personbruker.dittnav.varselbestiller.metrics.MetricsCollector
import no.nav.personbruker.dittnav.varselbestiller.nokkel.AvroNokkelObjectMother
import no.nav.personbruker.dittnav.varselbestiller.varselbestilling.Varselbestilling
import no.nav.personbruker.dittnav.varselbestiller.varselbestilling.VarselbestillingObjectMother
import no.nav.personbruker.dittnav.varselbestiller.varselbestilling.VarselbestillingRepository
import org.amshove.kluent.`should be`
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DoneEventServiceTest {

    private val doknotifikasjonStoppProducer = mockk<DoknotifikasjonStoppProducer>(relaxed = true)
    private val varselbestillingRepository = mockk<VarselbestillingRepository>(relaxed = true)
    private val metricsCollector = mockk<MetricsCollector>(relaxed = true)
    private val metricsSession = mockk<EventMetricsSession>(relaxed = true)
    private val eventService = DoneEventService(doknotifikasjonStoppProducer, varselbestillingRepository, metricsCollector)

    @BeforeEach
    private fun resetMocks() {
        mockkObject(DoknotifikasjonStoppTransformer)
        clearMocks(doknotifikasjonStoppProducer)
        clearMocks(varselbestillingRepository)
        clearMocks(metricsCollector)
        clearMocks(metricsSession)
    }

    @AfterAll
    private fun cleanUp() {
        unmockkAll()
    }

    @Test
    fun `Skal forkaste eventer som mangler nokkel`() {
        val done = AvroDoneObjectMother.createDone("001")
        val cr: ConsumerRecord<Nokkel, Done> = ConsumerRecordsObjectMother.createConsumerRecordWithKey(topicName = "done", actualKey = null, actualEvent = done)
        val records = ConsumerRecordsObjectMother.giveMeConsumerRecordsWithThisConsumerRecord(cr)

        val slot = slot<suspend EventMetricsSession.() -> Unit>()
        coEvery { metricsCollector.recordMetrics(any(), capture(slot)) } coAnswers {
            slot.captured.invoke(metricsSession)
        }

        coEvery { varselbestillingRepository.fetchVarselbestillingerForEventIds(allAny()) } returns listOf(VarselbestillingObjectMother.createVarselbestilling("B-test-001", "1", "123"))

        runBlocking {
            eventService.processEvents(records)
        }

        coVerify(exactly = 0) { doknotifikasjonStoppProducer.sendEventsAndPersistCancellation(allAny())}
        coVerify (exactly = 1) { metricsSession.countNokkelWasNull() }
        confirmVerified(doknotifikasjonStoppProducer)
    }

    @Test
    fun `Skal opprette DoknotifikasjonStopp for alle eventer som har ekstern varsling`() {
        val doneEventId1 = "001"
        val doneEventId2 = "002"
        val doneEventId3 = "003"
        val doneConsumerRecord1 = ConsumerRecordsObjectMother.createConsumerRecordWithKey(topicName = "done", actualKey = AvroNokkelObjectMother.createNokkelWithEventId(doneEventId1), actualEvent = AvroDoneObjectMother.createDone(eventId = doneEventId1))
        val doneConsumerRecord2 = ConsumerRecordsObjectMother.createConsumerRecordWithKey(topicName = "done", actualKey = AvroNokkelObjectMother.createNokkelWithEventId(doneEventId2), actualEvent =  AvroDoneObjectMother.createDone(eventId = doneEventId2))
        val doneConsumerRecord3 = ConsumerRecordsObjectMother.createConsumerRecordWithKey(topicName ="done", actualKey = AvroNokkelObjectMother.createNokkelWithEventId(doneEventId3), actualEvent = AvroDoneObjectMother.createDone(eventId = doneEventId3))
        val records = ConsumerRecordsObjectMother.giveMeConsumerRecordsWithThisConsumerRecord(listOf(doneConsumerRecord1, doneConsumerRecord2, doneConsumerRecord3))

        val slot = slot<suspend EventMetricsSession.() -> Unit>()
        coEvery { metricsCollector.recordMetrics(any(), capture(slot)) } coAnswers {
            slot.captured.invoke(metricsSession)
        }
        val capturedListOfEntities = slot<Map<String, DoknotifikasjonStopp>>()
        coEvery { varselbestillingRepository.fetchVarselbestillingerForEventIds(listOf(doneEventId1, doneEventId2, doneEventId3)) } returns
                listOf(VarselbestillingObjectMother.createVarselbestilling("B-test-001", doneEventId1, "12345"),
                        VarselbestillingObjectMother.createVarselbestilling("B-test-002", doneEventId2, "12345"),
                        VarselbestillingObjectMother.createVarselbestilling("B-test-003", doneEventId3, "12345"))
        coEvery { doknotifikasjonStoppProducer.sendEventsAndPersistCancellation(capture(capturedListOfEntities)) } returns Unit

        runBlocking {
            eventService.processEvents(records)
        }

        coVerify(exactly = 1) { doknotifikasjonStoppProducer.sendEventsAndPersistCancellation(any()) }
        coVerify (exactly = 3) { metricsSession.countSuccessfulEksternvarslingForSystemUser(any()) }
        coVerify (exactly = 3) { metricsSession.countAllEventsFromKafkaForSystemUser(any()) }
        capturedListOfEntities.captured.size `should be` records.count()

        confirmVerified(doknotifikasjonStoppProducer)
    }

    @Test
    fun `Skal opprette DoknotifikasjonStopp kun for eventer som har ekstern varsling`() {
        val doneEventId1 = "001"
        val doneEventId2 = "002"
        val doneEventId3 = "003"
        val doneConsumerRecord1 = ConsumerRecordsObjectMother.createConsumerRecordWithKey(topicName = "done", actualKey = AvroNokkelObjectMother.createNokkelWithEventId(doneEventId1), actualEvent = AvroDoneObjectMother.createDone(eventId = doneEventId1))
        val doneConsumerRecord2 = ConsumerRecordsObjectMother.createConsumerRecordWithKey(topicName = "done", actualKey = AvroNokkelObjectMother.createNokkelWithEventId(doneEventId2), actualEvent =  AvroDoneObjectMother.createDone(eventId = doneEventId2))
        val doneConsumerRecord3 = ConsumerRecordsObjectMother.createConsumerRecordWithKey(topicName ="done", actualKey = AvroNokkelObjectMother.createNokkelWithEventId(doneEventId3), actualEvent = AvroDoneObjectMother.createDone(eventId = doneEventId3))
        val records = ConsumerRecordsObjectMother.giveMeConsumerRecordsWithThisConsumerRecord(listOf(doneConsumerRecord1, doneConsumerRecord2, doneConsumerRecord3))

        val slot = slot<suspend EventMetricsSession.() -> Unit>()
        coEvery { metricsCollector.recordMetrics(any(), capture(slot)) } coAnswers {
            slot.captured.invoke(metricsSession)
        }

        val capturedListOfEntities = slot<Map<String, DoknotifikasjonStopp>>()
        coEvery { varselbestillingRepository.fetchVarselbestillingerForEventIds(listOf(doneEventId1, doneEventId2, doneEventId3)) } returns
                listOf(VarselbestillingObjectMother.createVarselbestilling("B-test-001", doneEventId1, "12345"),
                        VarselbestillingObjectMother.createVarselbestilling("B-test-002", doneEventId2, "12345"))
        coEvery { doknotifikasjonStoppProducer.sendEventsAndPersistCancellation(capture(capturedListOfEntities)) } returns Unit

        runBlocking {
            eventService.processEvents(records)
        }

        coVerify(exactly = 1) { doknotifikasjonStoppProducer.sendEventsAndPersistCancellation(any())}
        coVerify (exactly = 2) { metricsSession.countSuccessfulEksternvarslingForSystemUser(any()) }
        coVerify (exactly = 3) { metricsSession.countAllEventsFromKafkaForSystemUser(any()) }
        capturedListOfEntities.captured.size `should be` 2
        confirmVerified(doknotifikasjonStoppProducer)
    }

    @Test
    fun `Skal ikke opprette DoknotifikasjonStopp hvis varsel er avbestilt tidligere`() {
        val doneEventId1 = "001"
        val doneEventId2 = "002"
        val doneEventId3 = "003"
        val doneConsumerRecord1 = ConsumerRecordsObjectMother.createConsumerRecordWithKey(topicName = "done", actualKey = AvroNokkelObjectMother.createNokkelWithEventId(doneEventId1), actualEvent = AvroDoneObjectMother.createDone(eventId = doneEventId1))
        val doneConsumerRecord2 = ConsumerRecordsObjectMother.createConsumerRecordWithKey(topicName = "done", actualKey = AvroNokkelObjectMother.createNokkelWithEventId(doneEventId2), actualEvent =  AvroDoneObjectMother.createDone(eventId = doneEventId2))
        val doneConsumerRecord3 = ConsumerRecordsObjectMother.createConsumerRecordWithKey(topicName ="done", actualKey = AvroNokkelObjectMother.createNokkelWithEventId(doneEventId3), actualEvent = AvroDoneObjectMother.createDone(eventId = doneEventId3))
        val records = ConsumerRecordsObjectMother.giveMeConsumerRecordsWithThisConsumerRecord(listOf(doneConsumerRecord1, doneConsumerRecord2, doneConsumerRecord3))

        val slot = slot<suspend EventMetricsSession.() -> Unit>()
        coEvery { metricsCollector.recordMetrics(any(), capture(slot)) } coAnswers {
            slot.captured.invoke(metricsSession)
        }

        val varselbestilling1Avbestilt = VarselbestillingObjectMother.createVarselbestilling("B-test-001", doneEventId1, "12345").copy(avbestilt = true)
        val varselbestilling2Avbestilt = VarselbestillingObjectMother.createVarselbestilling("B-test-002", doneEventId2, "12345").copy(avbestilt = true)
        val varselbestilling3IkkeAvbestilt = VarselbestillingObjectMother.createVarselbestilling("B-test-002", doneEventId3, "12345")


        val capturedListOfEntities = slot<Map<String, DoknotifikasjonStopp>>()
        coEvery { varselbestillingRepository.fetchVarselbestillingerForEventIds(listOf(doneEventId1, doneEventId2, doneEventId3)) } returns listOf(varselbestilling1Avbestilt, varselbestilling2Avbestilt, varselbestilling3IkkeAvbestilt)
        coEvery { doknotifikasjonStoppProducer.sendEventsAndPersistCancellation(capture(capturedListOfEntities)) } returns Unit

        runBlocking {
            eventService.processEvents(records)
        }

        coVerify(exactly = 1) { doknotifikasjonStoppProducer.sendEventsAndPersistCancellation(any())}
        coVerify (exactly = 1) { metricsSession.countSuccessfulEksternvarslingForSystemUser(any()) }
        capturedListOfEntities.captured.size `should be` 1
        confirmVerified(doknotifikasjonStoppProducer)
    }

    @Test
    fun `Skal haandtere at enkelte valideringer feiler og fortsette aa validere resten av batch-en`() {
        val totalNumberOfRecords = 4
        val numberOfFailedTransformations = 1
        val numberOfSuccessfulTransformations = totalNumberOfRecords - numberOfFailedTransformations

        val slot = slot<suspend EventMetricsSession.() -> Unit>()
        coEvery { metricsCollector.recordMetrics(any(), capture(slot)) } coAnswers {
            slot.captured.invoke(metricsSession)
        }

        val records = ConsumerRecordsObjectMother.giveMeANumberOfDoneRecords(numberOfRecords = totalNumberOfRecords, topicName = "dummyTopic", )
        val capturedListOfEntities = slot<Map<String, DoknotifikasjonStopp>>()
        coEvery { doknotifikasjonStoppProducer.sendEventsAndPersistCancellation(capture(capturedListOfEntities)) } returns Unit
        coEvery { varselbestillingRepository.fetchVarselbestillingerForEventIds(allAny()) } returns listOf(
                VarselbestillingObjectMother.createVarselbestilling("B-dummySystembruker-0", "0", "12345"),
                VarselbestillingObjectMother.createVarselbestilling("B-dummySystembruker-2", "1", "12345"),
                VarselbestillingObjectMother.createVarselbestilling("B-dummySystembruker-3", "2", "12345"),
                VarselbestillingObjectMother.createVarselbestilling("B-dummySystembruker-4", "3", "12345")
        )

        val fieldValidationException = FieldValidationException("Simulert feil i en test")
        val doknotifikasjonStopp = AvroDoknotifikasjonStoppObjectMother.giveMeANumberOfDoknotifikasjonStopp(5)
        coEvery { DoknotifikasjonStoppTransformer.createDoknotifikasjonStopp(ofType(Varselbestilling::class)) } throws fieldValidationException andThenMany doknotifikasjonStopp

        runBlocking {
            eventService.processEvents(records)
        }

        coVerify(exactly = 1) { doknotifikasjonStoppProducer.sendEventsAndPersistCancellation(any()) }
        coVerify(exactly = numberOfFailedTransformations) { metricsSession.countFailedEksternvarslingForSystemUser(any()) }
        coVerify (exactly = numberOfSuccessfulTransformations) { metricsSession.countSuccessfulEksternvarslingForSystemUser(any()) }
        coVerify (exactly = numberOfSuccessfulTransformations + numberOfFailedTransformations) { metricsSession.countAllEventsFromKafkaForSystemUser(any()) }
        capturedListOfEntities.captured.size `should be` numberOfSuccessfulTransformations

        confirmVerified(doknotifikasjonStoppProducer)
    }

}

