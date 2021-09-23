package no.nav.personbruker.dittnav.varselbestiller.config

import no.nav.brukernotifikasjon.schemas.Beskjed
import no.nav.brukernotifikasjon.schemas.Done
import no.nav.brukernotifikasjon.schemas.Nokkel
import no.nav.brukernotifikasjon.schemas.Oppgave
import no.nav.doknotifikasjon.schemas.Doknotifikasjon
import no.nav.doknotifikasjon.schemas.DoknotifikasjonStopp
import no.nav.personbruker.dittnav.common.metrics.MetricsReporter
import no.nav.personbruker.dittnav.common.metrics.StubMetricsReporter
import no.nav.personbruker.dittnav.common.metrics.influxdb.InfluxConfig
import no.nav.personbruker.dittnav.common.metrics.influxdb.InfluxMetricsReporter
import no.nav.personbruker.dittnav.varselbestiller.beskjed.BeskjedEventService
import no.nav.personbruker.dittnav.varselbestiller.common.database.Database
import no.nav.personbruker.dittnav.varselbestiller.common.kafka.Consumer
import no.nav.personbruker.dittnav.varselbestiller.common.kafka.KafkaProducerWrapper
import no.nav.personbruker.dittnav.varselbestiller.common.kafka.polling.PeriodicConsumerPollingCheck
import no.nav.personbruker.dittnav.varselbestiller.doknotifikasjon.DoknotifikasjonProducer
import no.nav.personbruker.dittnav.varselbestiller.doknotifikasjonStopp.DoknotifikasjonStoppProducer
import no.nav.personbruker.dittnav.varselbestiller.done.DoneEventService
import no.nav.personbruker.dittnav.varselbestiller.health.HealthService
import no.nav.personbruker.dittnav.varselbestiller.metrics.MetricsCollector
import no.nav.personbruker.dittnav.varselbestiller.metrics.ProducerNameResolver
import no.nav.personbruker.dittnav.varselbestiller.metrics.ProducerNameScrubber
import no.nav.personbruker.dittnav.varselbestiller.oppgave.OppgaveEventService
import no.nav.personbruker.dittnav.varselbestiller.varselbestilling.VarselbestillingRepository
import org.apache.kafka.clients.producer.KafkaProducer
import org.slf4j.LoggerFactory

class ApplicationContext {

    val httpClient = HttpClientBuilder.build()

    private val log = LoggerFactory.getLogger(ApplicationContext::class.java)
    val environment = Environment()
    val database: Database = PostgresDatabase(environment)

    val nameResolver = ProducerNameResolver(httpClient, environment.eventHandlerURL)
    val nameScrubber = ProducerNameScrubber(nameResolver)
    val metricsReporter = resolveMetricsReporter(environment)
    val metricsCollector = MetricsCollector(metricsReporter, nameScrubber)

    val doknotifikasjonRepository = VarselbestillingRepository(database)
    val doknotifikasjonBeskjedProducer = initializeDoknotifikasjonProducer(Eventtype.BESKJED)
    val doknotifikasjonOppgaveProducer = initializeDoknotifikasjonProducer(Eventtype.OPPGAVE)
    val doknotifikasjonStopProducer = initializeDoknotifikasjonStoppProducer()

    var beskjedConsumer = initializeBeskjedConsumer()
    var oppgaveConsumer = initializeOppgaveConsumer()
    var doneConsumer = initializeDoneConsumer()

    val healthService = HealthService(this)

    var periodicConsumerPollingCheck = initializePeriodicConsumerPollingCheck()


    private fun initializeBeskjedConsumer(): Consumer<Nokkel, Beskjed> {
        val beskjedKafkaProps = Kafka.consumerProps(environment, Eventtype.BESKJED)
        val beskjedEventService = BeskjedEventService(doknotifikasjonBeskjedProducer, doknotifikasjonRepository, metricsCollector)
        return KafkaConsumerSetup.setupKafkaConsumer(environment.beskjedTopicName, beskjedKafkaProps, beskjedEventService)
    }

    private fun initializeOppgaveConsumer(): Consumer<Nokkel, Oppgave> {
        val oppgaveKafkaProps = Kafka.consumerProps(environment, Eventtype.OPPGAVE)
        val oppgaveEventService = OppgaveEventService(doknotifikasjonOppgaveProducer, doknotifikasjonRepository, metricsCollector)
        return KafkaConsumerSetup.setupKafkaConsumer(environment.oppgaveTopicName, oppgaveKafkaProps, oppgaveEventService)
    }

    private fun initializeDoneConsumer(): Consumer<Nokkel, Done> {
        val doneKafkaProps = Kafka.consumerProps(environment, Eventtype.DONE)
        val doneEventService = DoneEventService(doknotifikasjonStopProducer, doknotifikasjonRepository, metricsCollector)
        return KafkaConsumerSetup.setupKafkaConsumer(environment.doneTopicName, doneKafkaProps, doneEventService)
    }

    private fun initializeDoknotifikasjonProducer(eventtype: Eventtype): DoknotifikasjonProducer {
        val producerProps = Kafka.producerProps(environment, eventtype)
        val kafkaProducer = KafkaProducer<String, Doknotifikasjon>(producerProps)
        kafkaProducer.initTransactions()
        val kafkaProducerDoknotifikasjon = KafkaProducerWrapper(environment.doknotifikasjonTopicName, kafkaProducer)
        return DoknotifikasjonProducer(kafkaProducerDoknotifikasjon, doknotifikasjonRepository)
    }

    private fun initializeDoknotifikasjonStoppProducer(): DoknotifikasjonStoppProducer {
        val producerProps = Kafka.producerProps(environment, Eventtype.DOKNOTIFIKASJON_STOPP)
        val kafkaProducer = KafkaProducer<String, DoknotifikasjonStopp>(producerProps)
        kafkaProducer.initTransactions()
        val kafkaProducerDoknotifikasjonStopp = KafkaProducerWrapper(environment.doknotifikasjonStopTopicName, kafkaProducer)
        return DoknotifikasjonStoppProducer(kafkaProducerDoknotifikasjonStopp, doknotifikasjonRepository)
    }

    private fun initializePeriodicConsumerPollingCheck() = PeriodicConsumerPollingCheck(this)

    fun reinitializeConsumers() {
        if (beskjedConsumer.isCompleted()) {
            beskjedConsumer = initializeBeskjedConsumer()
            log.info("beskjedConsumer har blitt reinstansiert.")
        } else {
            log.warn("beskjedConsumer kunne ikke bli reinstansiert fordi den fortsatt er aktiv.")
        }

        if (oppgaveConsumer.isCompleted()) {
            oppgaveConsumer = initializeOppgaveConsumer()
            log.info("oppgaveConsumer har blitt reinstansiert.")
        } else {
            log.warn("oppgaveConsumer kunne ikke bli reinstansiert fordi den fortsatt er aktiv.")
        }

        if (doneConsumer.isCompleted()) {
            doneConsumer = initializeDoneConsumer()
            log.info("doneConsumer har blitt reinstansiert.")
        } else {
            log.warn("doneConsumer kunne ikke bli reinstansiert fordi den fortsatt er aktiv.")
        }
    }

    fun reinitializePeriodicConsumerPollingCheck() {
        if (periodicConsumerPollingCheck.isCompleted()) {
            periodicConsumerPollingCheck = initializePeriodicConsumerPollingCheck()
            log.info("periodicConsumerPollingCheck har blitt reinstansiert.")
        } else {
            log.warn("periodicConsumerPollingCheck kunne ikke bli reinstansiert fordi den fortsatt er aktiv.")
        }
    }

    private fun resolveMetricsReporter(environment: Environment): MetricsReporter {
        return if (environment.influxdbHost == "" || environment.influxdbHost == "stub") {
            StubMetricsReporter()
        } else {
            val influxConfig = InfluxConfig(
                    applicationName = environment.applicationName,
                    hostName = environment.influxdbHost,
                    hostPort = environment.influxdbPort,
                    databaseName = environment.influxdbName,
                    retentionPolicyName = environment.influxdbRetentionPolicy,
                    clusterName = environment.clusterName,
                    namespace = environment.namespace,
                    userName = environment.influxdbUser,
                    password = environment.influxdbPassword
            )
            InfluxMetricsReporter(influxConfig)
        }
    }
}
