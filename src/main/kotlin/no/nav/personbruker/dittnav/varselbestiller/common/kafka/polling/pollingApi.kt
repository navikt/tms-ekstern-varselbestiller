package no.nav.personbruker.dittnav.varselbestiller.common.kafka.polling

import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.response.respondText
import io.ktor.routing.Routing
import io.ktor.routing.get
import no.nav.personbruker.dittnav.varselbestiller.config.ApplicationContext
import no.nav.personbruker.dittnav.varselbestiller.config.KafkaConsumerSetup

fun Routing.pollingApi(appContext: ApplicationContext) {

    get("/internal/polling/start") {
        val responseText = "Polling etter eventer har blitt startet."
        restartPolling(appContext)
        call.respondText(text = responseText, contentType = ContentType.Text.Plain)
    }

    get("/internal/polling/stop") {
        val responseText = "All polling etter eventer har blitt stoppet."
        KafkaConsumerSetup.stopAllKafkaConsumers(appContext)
        call.respondText(text = responseText, contentType = ContentType.Text.Plain)
    }
}

private suspend fun restartPolling(appContext: ApplicationContext) {
    KafkaConsumerSetup.stopAllKafkaConsumers(appContext)
    appContext.reinitializeConsumers()
    KafkaConsumerSetup.startAllKafkaPollers(appContext)
}