package no.nav.personbruker.dittnav.varselbestiller.done

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import no.nav.brukernotifikasjon.schemas.Done
import no.nav.brukernotifikasjon.schemas.Nokkel
import no.nav.common.KafkaEnvironment
import no.nav.doknotifikasjon.schemas.DoknotifikasjonStopp
import no.nav.personbruker.dittnav.common.util.kafka.RecordKeyValueWrapper
import no.nav.personbruker.dittnav.common.util.kafka.producer.KafkaProducerWrapper
import no.nav.personbruker.dittnav.varselbestiller.CapturingEventProcessor
import no.nav.personbruker.dittnav.varselbestiller.common.kafka.Consumer
import no.nav.personbruker.dittnav.varselbestiller.common.kafka.KafkaEmbed
import no.nav.personbruker.dittnav.varselbestiller.common.kafka.KafkaTestUtil
import no.nav.personbruker.dittnav.varselbestiller.config.EventType
import no.nav.personbruker.dittnav.varselbestiller.config.Kafka
import no.nav.personbruker.dittnav.varselbestiller.doknotifikasjon.DoknotifikasjonStoppProducer
import no.nav.personbruker.dittnav.varselbestiller.nokkel.AvroNokkelObjectMother
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldBeEqualTo
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

@Disabled("Disabled frem til sjekk på om brukernotifikasjonen tilhørende Done-eventet faktisk har bestilt ekstern varsling er på plass")
class DoneIT {

    private val embeddedEnv = KafkaTestUtil.createDefaultKafkaEmbeddedInstance(listOf(Kafka.doneTopicName, Kafka.doknotifikasjonStopTopicName))
    private val testEnvironment = KafkaTestUtil.createEnvironmentForEmbeddedKafka(embeddedEnv)

    private val doneEvents = (1..10).map { AvroNokkelObjectMother.createNokkelWithEventId(it) to AvroDoneObjectMother.createDone(it) }.toMap()

    private val capturedDoknotifikasjonStopRecords = ArrayList<RecordKeyValueWrapper<String, DoknotifikasjonStopp>>()

    @BeforeAll
    fun setup() {
        embeddedEnv.start()
    }

    @AfterAll
    fun tearDown() {
        embeddedEnv.tearDown()
    }

    @Test
    fun `Started Kafka instance in memory`() {
        embeddedEnv.serverPark.status `should be equal to` KafkaEnvironment.ServerParkStatus.Started
    }

    @Test
    fun `Should read Done-events and send to varselbestiller-topic`() {
        runBlocking {
            KafkaTestUtil.produceEvents(testEnvironment, Kafka.doneTopicName, doneEvents)
        } shouldBeEqualTo true

        `Read all Done-events from our topic and verify that they have been sent to varselbestiller-topic`()

        doneEvents.all {
            capturedDoknotifikasjonStopRecords.contains(RecordKeyValueWrapper(it.key, it.value))
        }
    }

    fun `Read all Done-events from our topic and verify that they have been sent to varselbestiller-topic`() {
        val consumerProps = KafkaEmbed.consumerProps(testEnvironment, EventType.DONE, true)
        val kafkaConsumer = KafkaConsumer<Nokkel, Done>(consumerProps)

        val producerProps = Kafka.producerProps(testEnvironment, EventType.DOKNOTIFIKASJON_STOPP, true)
        val kafkaProducer = KafkaProducer<String, DoknotifikasjonStopp>(producerProps)
        val kafkaProducerWrapper = KafkaProducerWrapper(Kafka.doknotifikasjonStopTopicName, kafkaProducer)
        val doknotifikasjonStoppProducer = DoknotifikasjonStoppProducer(kafkaProducerWrapper)

        val eventService = DoneEventService(doknotifikasjonStoppProducer)
        val consumer = Consumer(Kafka.doneTopicName, kafkaConsumer, eventService)

        kafkaProducer.initTransactions()
        runBlocking {
            consumer.startPolling()

            `Wait until all done events have been received by target topic`()

            consumer.stopPolling()
        }
    }

    private fun `Wait until all done events have been received by target topic`() {
        val targetConsumerProps = KafkaEmbed.consumerProps(testEnvironment, EventType.DOKNOTIFIKASJON_STOPP, true)
        val targetKafkaConsumer = KafkaConsumer<String, DoknotifikasjonStopp>(targetConsumerProps)
        val capturingProcessor = CapturingEventProcessor<String, DoknotifikasjonStopp>()

        val targetConsumer = Consumer(Kafka.doknotifikasjonStopTopicName, targetKafkaConsumer, capturingProcessor)

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

        capturedDoknotifikasjonStopRecords.addAll(capturingProcessor.getEvents())
    }
}