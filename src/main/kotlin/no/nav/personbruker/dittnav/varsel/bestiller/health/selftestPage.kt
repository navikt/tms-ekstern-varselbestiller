package no.nav.personbruker.dittnav.varsel.bestiller.health

import io.ktor.application.ApplicationCall
import io.ktor.html.respondHtml
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.coroutineScope
import kotlinx.html.*

suspend fun ApplicationCall.buildSelftestPage(healthService: HealthService) = coroutineScope {

    val healthChecks = healthService.getHealthChecks()
    val hasFailedChecks = healthChecks.any { healthStatus -> Status.ERROR == healthStatus.status }

    respondHtml(status =
        if(hasFailedChecks)
            { HttpStatusCode.ServiceUnavailable}
        else { HttpStatusCode.OK})
    {
        head {
            title { +"Selftest dittnav-varselbestiller" }
        }
        body {
            var text = if(hasFailedChecks) {
                "FEIL"
            } else {
                "Service-status: OK"
            }
            h1 {
                style = if(hasFailedChecks) {
                    "background: red;font-weight:bold"
                } else {
                    "background: green"
                }
                +text
            }
            table {
                thead {
                    tr { th { +"SELFTEST dittnav-varselbestiller" } }
                }
                tbody {
                    healthChecks.map {
                        tr {
                            td { +it.serviceName }
                            td {
                                style = if(it.status == Status.OK) {
                                    "background: green"
                                } else {
                                    "background: red;font-weight:bold"
                                }
                                +it.status.toString()
                            }
                            td { +it.statusMessage }
                        }
                    }
                }
            }
        }
    }
}
