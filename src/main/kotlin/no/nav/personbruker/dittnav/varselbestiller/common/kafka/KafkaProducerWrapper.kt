package no.nav.personbruker.dittnav.varselbestiller.common.kafka

import no.nav.personbruker.dittnav.varselbestiller.common.kafka.exception.RetriableKafkaException
import no.nav.personbruker.dittnav.varselbestiller.common.kafka.exception.UnretriableKafkaException
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.KafkaException
import org.slf4j.LoggerFactory
import java.lang.IllegalStateException

class KafkaProducerWrapper<K, V>(
    private val topicName: String,
    private val kafkaProducer: KafkaProducer<K, V>
) {

    val log = LoggerFactory.getLogger(KafkaProducerWrapper::class.java)

    fun sendEventsAndLeaveTransactionOpen(events: List<RecordKeyValueWrapper<K, V>>) {
        try {
            kafkaProducer.beginTransaction()
            events.forEach { event ->
                sendSingleEvent(event)
            }
        } catch (e: KafkaException) {
            kafkaProducer.abortTransaction()
            throw RetriableKafkaException("Et eller flere eventer feilet med en periodisk feil ved sending til kafka", e)
        } catch (e: Exception) {
            kafkaProducer.close()
            throw UnretriableKafkaException("Fant en uventet feil ved sending av eventer til kafka", e)
        }
    }

    fun abortCurrentTransaction() {
        try {
            kafkaProducer.abortTransaction()
        } catch (e: Exception) {
            /*
                KafkaProducer lar oss ikke sjekke state på transaction før vi faktisk kaller abortTransaction her.
                Fordi det kan skje at vi kaller denne etter vi allerede har kalt abortTransaction() eller close(),
                må vi gardere oss for at det kan feile her, men vi trenger ikke videre behandling.
            */
        }
    }

    fun commitCurrentTransaction() {
        kafkaProducer.commitTransaction()
    }

    private fun sendSingleEvent(event: RecordKeyValueWrapper<K, V>) {
        val producerRecord = ProducerRecord(topicName, event.key, event.value)
        kafkaProducer.send(producerRecord)
    }

    fun flushAndClose() {
        try {
            kafkaProducer.flush()
            kafkaProducer.close()
            log.info("Produsent for kafka-eventer er flushet og lukket.")
        } catch (e: Exception) {
            log.warn("Klarte ikke å flushe og lukke produsent. Det kan være eventer som ikke ble produsert.")
        }
    }
}
