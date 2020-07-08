package no.nav.personbruker.dittnav.varsel.bestiller


import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import no.nav.brukernotifikasjon.schemas.Beskjed
import no.nav.brukernotifikasjon.schemas.Done
import no.nav.brukernotifikasjon.schemas.Nokkel
import no.nav.brukernotifikasjon.schemas.Oppgave
import no.nav.common.KafkaEnvironment
import no.nav.personbruker.dittnav.varsel.bestiller.beskjed.AvroBeskjedObjectMother
import no.nav.personbruker.dittnav.varsel.bestiller.beskjed.BeskjedEventService
import no.nav.personbruker.dittnav.varsel.bestiller.common.CapturingEventProcessor
import no.nav.personbruker.dittnav.varsel.bestiller.common.RecordKeyValueWrapper
import no.nav.personbruker.dittnav.varsel.bestiller.common.database.H2Database
import no.nav.personbruker.dittnav.varsel.bestiller.common.kafka.Consumer
import no.nav.personbruker.dittnav.varsel.bestiller.common.kafka.KafkaProducerWrapper
import no.nav.personbruker.dittnav.varsel.bestiller.common.kafka.util.KafkaTestUtil
import no.nav.personbruker.dittnav.varsel.bestiller.config.EventType
import no.nav.personbruker.dittnav.varsel.bestiller.config.Kafka
import no.nav.personbruker.dittnav.varsel.bestiller.done.DoneEventService
import no.nav.personbruker.dittnav.varsel.bestiller.done.schema.AvroDoneObjectMother
import no.nav.personbruker.dittnav.varsel.bestiller.metrics.EventMetricsProbe
import no.nav.personbruker.dittnav.varsel.bestiller.metrics.ProducerNameResolver
import no.nav.personbruker.dittnav.varsel.bestiller.metrics.ProducerNameScrubber
import no.nav.personbruker.dittnav.varsel.bestiller.metrics.StubMetricsReporter
import no.nav.personbruker.dittnav.varsel.bestiller.nokkel.createNokkelWithEventId
import no.nav.personbruker.dittnav.varsel.bestiller.oppgave.AvroOppgaveObjectMother
import no.nav.personbruker.dittnav.varsel.bestiller.oppgave.OppgaveEventService
import org.amshove.kluent.`should equal`
import org.amshove.kluent.shouldEqualTo
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test

class EndToEndTestIT {

    private val beskjedTopic = "dittnavBeskjedTopic"
    private val targetBeskjedTopic = "targetBeskjedTopic"
    private val oppgaveTopic = "dittnavOppgaveTopic"
    private val targetOppgaveTopic = "targetOppgaveTopic"
    private val doneTopic = "dittnavDoneTopic"
    private val targetDoneTopic = "targetDoneTopic"

    private val topicList = listOf(
            beskjedTopic, targetBeskjedTopic,
            oppgaveTopic, targetOppgaveTopic,
            doneTopic, targetDoneTopic
    )
    private val embeddedEnv = KafkaTestUtil.createDefaultKafkaEmbeddedInstance(topicList)
    private val testEnvironment = KafkaTestUtil.createEnvironmentForEmbeddedKafka(embeddedEnv)

    private val metricsReporter = StubMetricsReporter()
    val database = H2Database()
    val nameResolver = ProducerNameResolver(database)
    val nameScrubber = ProducerNameScrubber(nameResolver)
    private val metricsProbe = EventMetricsProbe(metricsReporter, nameScrubber)

    private val adminClient = embeddedEnv.adminClient

    private val beskjedEvents = (1..10).map { createNokkelWithEventId(it) to AvroBeskjedObjectMother.createBeskjed(it) }.toMap()
    private val oppgaveEvents = (1..10).map { createNokkelWithEventId(it) to AvroOppgaveObjectMother.createOppgave(it) }.toMap()
    private val doneEvents = (1..10).map { createNokkelWithEventId(it) to AvroDoneObjectMother.createDone(it) }.toMap()

    private val capturedBeskjedRecords = ArrayList<RecordKeyValueWrapper<Beskjed>>()
    private val capturedOppgaveRecords = ArrayList<RecordKeyValueWrapper<Oppgave>>()
    private val capturedDoneRecords = ArrayList<RecordKeyValueWrapper<Done>>()

    init {
        embeddedEnv.start()
    }

    @AfterAll
    fun tearDown() {
        adminClient?.close()
        embeddedEnv.tearDown()
    }

    @Test
    fun `Kafka instansen i minnet har blitt staret`() {
        embeddedEnv.serverPark.status `should equal` KafkaEnvironment.ServerParkStatus.Started
    }

    @Test
    fun `Skal lese inn Beskjed-eventer og sende dem til varsel-bestiller topic`() {
        runBlocking {
            KafkaTestUtil.produceEvents(testEnvironment, beskjedTopic, beskjedEvents)
        } shouldEqualTo true

        `Les inn alle beskjed eventene fra topic-en vaar og verifiser at de har blitt lagt til i varsel-bestiller topic`()

        beskjedEvents.all {
            capturedBeskjedRecords.contains(RecordKeyValueWrapper(it.key, it.value))
        }
    }

    @Test
    fun `Skal lese inn Oppgave-eventer og sende dem til varsel-bestiller topic`() {
        runBlocking {
            KafkaTestUtil.produceEvents(testEnvironment, oppgaveTopic, oppgaveEvents)
        } shouldEqualTo true

        `Les inn alle oppgave eventene fra vaar topic og verifiser at de har blitt lagt til i varsel-bestiller topic`()

        oppgaveEvents.all {
            capturedOppgaveRecords.contains(RecordKeyValueWrapper(it.key, it.value))
        }
    }

    @Test
    fun `Skal lese inn Done-eventer og sende dem til varsel-bestiller topic`() {
        runBlocking {
            KafkaTestUtil.produceEvents(testEnvironment, doneTopic, doneEvents)
        } shouldEqualTo true

        `Les inn alle done eventene fra vaar topic og verifiser at de har blitt lagt til i varsel-bestiller topic-en`()

        doneEvents.all {
            capturedDoneRecords.contains(RecordKeyValueWrapper(it.key, it.value))
        }
    }

    fun `Les inn alle beskjed eventene fra topic-en vaar og verifiser at de har blitt lagt til i varsel-bestiller topic`() {
        val consumerProps = Kafka.consumerProps(testEnvironment, EventType.BESKJED, true)
        val kafkaConsumer = KafkaConsumer<Nokkel, Beskjed>(consumerProps)

        val producerProps = Kafka.producerProps(testEnvironment, EventType.BESKJED, true)
        val kafkaProducer = KafkaProducer<Nokkel, Beskjed>(producerProps)
        val producerWrapper = KafkaProducerWrapper(targetBeskjedTopic, kafkaProducer)

        val eventService = BeskjedEventService(producerWrapper, metricsProbe)
        val consumer = Consumer(beskjedTopic, kafkaConsumer, eventService)

        kafkaProducer.initTransactions()
        runBlocking {
            consumer.startPolling()

            `Wait until all beskjed events have been received by target topic`()

            consumer.stopPolling()
        }
    }

    private fun `Wait until all beskjed events have been received by target topic`() {
        val targetConsumerProps = Kafka.consumerProps(testEnvironment, EventType.BESKJED, true)
        val targetKafkaConsumer = KafkaConsumer<Nokkel, Beskjed>(targetConsumerProps)
        val capturingProcessor = CapturingEventProcessor<Beskjed>()

        val targetConsumer = Consumer(targetBeskjedTopic, targetKafkaConsumer, capturingProcessor)

        var currentNumberOfRecords = 0

        targetConsumer.startPolling()

        while (currentNumberOfRecords < beskjedEvents.size) {
            runBlocking {
                currentNumberOfRecords = capturingProcessor.getEvents().size
                delay(100)
            }
        }

        runBlocking {
            targetConsumer.stopPolling()
        }

        capturedBeskjedRecords.addAll(capturingProcessor.getEvents())
    }

    fun `Les inn alle oppgave eventene fra vaar topic og verifiser at de har blitt lagt til i varsel-bestiller topic`() {
        val consumerProps = Kafka.consumerProps(testEnvironment, EventType.OPPGAVE, true)
        val kafkaConsumer = KafkaConsumer<Nokkel, Oppgave>(consumerProps)

        val producerProps = Kafka.producerProps(testEnvironment, EventType.OPPGAVE, true)
        val kafkaProducer = KafkaProducer<Nokkel, Oppgave>(producerProps)
        val producerWrapper = KafkaProducerWrapper(targetOppgaveTopic, kafkaProducer)

        val eventService = OppgaveEventService(producerWrapper, metricsProbe)
        val consumer = Consumer(oppgaveTopic, kafkaConsumer, eventService)

        kafkaProducer.initTransactions()
        runBlocking {
            consumer.startPolling()

            `Wait until all oppgave events have been received by target topic`()

            consumer.stopPolling()
        }
    }

    private fun `Wait until all oppgave events have been received by target topic`() {
        val targetConsumerProps = Kafka.consumerProps(testEnvironment, EventType.OPPGAVE, true)
        val targetKafkaConsumer = KafkaConsumer<Nokkel, Oppgave>(targetConsumerProps)
        val capturingProcessor = CapturingEventProcessor<Oppgave>()

        val targetConsumer = Consumer(targetOppgaveTopic, targetKafkaConsumer, capturingProcessor)

        var currentNumberOfRecords = 0

        targetConsumer.startPolling()

        while (currentNumberOfRecords < oppgaveEvents.size) {
            runBlocking {
                currentNumberOfRecords = capturingProcessor.getEvents().size
                delay(100)
            }
        }

        runBlocking {
            targetConsumer.stopPolling()
        }

        capturedOppgaveRecords.addAll(capturingProcessor.getEvents())
    }

    fun `Les inn alle done eventene fra vaar topic og verifiser at de har blitt lagt til i varsel-bestiller topic-en`() {
        val consumerProps = Kafka.consumerProps(testEnvironment, EventType.DONE, true)
        val kafkaConsumer = KafkaConsumer<Nokkel, Done>(consumerProps)

        val producerProps = Kafka.producerProps(testEnvironment, EventType.DONE, true)
        val kafkaProducer = KafkaProducer<Nokkel, Done>(producerProps)
        val producerWrapper = KafkaProducerWrapper(targetDoneTopic, kafkaProducer)

        val eventService = DoneEventService(producerWrapper, metricsProbe)
        val consumer = Consumer(doneTopic, kafkaConsumer, eventService)

        kafkaProducer.initTransactions()
        runBlocking {
            consumer.startPolling()

            `Wait until all done events have been received by target topic`()

            consumer.stopPolling()
        }
    }

    private fun `Wait until all done events have been received by target topic`() {
        val targetConsumerProps = Kafka.consumerProps(testEnvironment, EventType.DONE, true)
        val targetKafkaConsumer = KafkaConsumer<Nokkel, Done>(targetConsumerProps)
        val capturingProcessor = CapturingEventProcessor<Done>()

        val targetConsumer = Consumer(targetDoneTopic, targetKafkaConsumer, capturingProcessor)

        var currentNumberOfRecords = 0

        targetConsumer.startPolling()

        while (currentNumberOfRecords < doneEvents.size) {
            runBlocking {
                currentNumberOfRecords = capturingProcessor.getEvents().size
                delay(100)
            }
        }

        runBlocking {
            targetConsumer.stopPolling()
        }

        capturedDoneRecords.addAll(capturingProcessor.getEvents())
    }

}

