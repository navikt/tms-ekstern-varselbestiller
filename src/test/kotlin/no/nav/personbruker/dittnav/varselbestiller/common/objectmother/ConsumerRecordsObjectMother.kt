package no.nav.personbruker.dittnav.varselbestiller.common.objectmother

import no.nav.brukernotifikasjon.schemas.*
import no.nav.personbruker.dittnav.varselbestiller.beskjed.AvroBeskjedObjectMother
import no.nav.personbruker.dittnav.varselbestiller.done.AvroDoneObjectMother
import no.nav.personbruker.dittnav.varselbestiller.nokkel.AvroNokkelObjectMother
import no.nav.personbruker.dittnav.varselbestiller.oppgave.AvroOppgaveObjectMother
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.common.TopicPartition

object ConsumerRecordsObjectMother {

    fun <T> giveMeConsumerRecordsWithThisConsumerRecord(concreteRecord: ConsumerRecord<Nokkel, T>): ConsumerRecords<Nokkel, T> {
        val records = mutableMapOf<TopicPartition, List<ConsumerRecord<Nokkel, T>>>()
        records[TopicPartition(concreteRecord.topic(), 1)] = listOf(concreteRecord)
        return ConsumerRecords(records)
    }

    fun giveMeANumberOfBeskjedRecords(numberOfRecords: Int, topicName: String, withEksternVarsling: Boolean = false): ConsumerRecords<Nokkel, Beskjed> {
        val records = mutableMapOf<TopicPartition, List<ConsumerRecord<Nokkel, Beskjed>>>()
        val recordsForSingleTopic = createBeskjedRecords(topicName, numberOfRecords, withEksternVarsling)
        records[TopicPartition(topicName, numberOfRecords)] = recordsForSingleTopic
        return ConsumerRecords(records)
    }

    private fun createBeskjedRecords(topicName: String, totalNumber: Int, withEksternVarsling: Boolean): List<ConsumerRecord<Nokkel, Beskjed>> {
        val allRecords = mutableListOf<ConsumerRecord<Nokkel, Beskjed>>()
        for (i in 0 until totalNumber) {
            val schemaRecord = AvroBeskjedObjectMother.createBeskjedWithEksternVarsling(i, withEksternVarsling)
            val nokkel = AvroNokkelObjectMother.createNokkelWithEventId(i)

            allRecords.add(ConsumerRecord(topicName, i, i.toLong(), nokkel, schemaRecord))
        }
        return allRecords
    }

    fun <T> createConsumerRecord(topicName: String, actualEvent: T): ConsumerRecord<Nokkel, T> {
        val nokkel = AvroNokkelObjectMother.createNokkelWithEventId(1)
        return ConsumerRecord(topicName, 1, 0, nokkel, actualEvent)
    }

    fun giveMeANumberOfDoneRecords(numberOfRecords: Int, topicName: String): ConsumerRecords<Nokkel, Done> {
        val records = mutableMapOf<TopicPartition, List<ConsumerRecord<Nokkel, Done>>>()
        val recordsForSingleTopic = createDoneRecords(topicName, numberOfRecords)
        records[TopicPartition(topicName, numberOfRecords)] = recordsForSingleTopic
        return ConsumerRecords(records)
    }

    private fun createDoneRecords(topicName: String, totalNumber: Int): List<ConsumerRecord<Nokkel, Done>> {
        val allRecords = mutableListOf<ConsumerRecord<Nokkel, Done>>()
        for (i in 0 until totalNumber) {
            val schemaRecord = AvroDoneObjectMother.createDone("$i")
            val nokkel = AvroNokkelObjectMother.createNokkelWithEventId(i)
            allRecords.add(ConsumerRecord(topicName, i, i.toLong(), nokkel, schemaRecord))
        }
        return allRecords
    }

    fun giveMeANumberOfOppgaveRecords(numberOfRecords: Int, topicName: String, withEksternVarsling: Boolean = false): ConsumerRecords<Nokkel, Oppgave> {
        val records = mutableMapOf<TopicPartition, List<ConsumerRecord<Nokkel, Oppgave>>>()
        val recordsForSingleTopic = createOppgaveRecords(topicName, numberOfRecords, withEksternVarsling)
        records[TopicPartition(topicName, numberOfRecords)] = recordsForSingleTopic
        return ConsumerRecords(records)
    }

    private fun createOppgaveRecords(topicName: String, totalNumber: Int, withEksternVarsling: Boolean): List<ConsumerRecord<Nokkel, Oppgave>> {
        val allRecords = mutableListOf<ConsumerRecord<Nokkel, Oppgave>>()
        for (i in 0 until totalNumber) {
            val schemaRecord = AvroOppgaveObjectMother.createOppgaveWithEksternVarsling(i, withEksternVarsling)
            val nokkel = AvroNokkelObjectMother.createNokkelWithEventId(i)
            allRecords.add(ConsumerRecord(topicName, i, i.toLong(), nokkel, schemaRecord))
        }
        return allRecords
    }
}