package no.nav.tms.ekstern.varselbestiller.config

import io.confluent.kafka.serializers.KafkaAvroSerializer
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.serialization.StringSerializer
import java.util.*

object Kafka {

    private fun credentialPropsAiven(securityVars: SecurityVars): Properties {
        return Properties().apply {
            put(
                KafkaAvroSerializerConfig.USER_INFO_CONFIG,
                "${securityVars.kafkaSchemaRegistryUser}:${securityVars.kafkaSchemaRegistryPassword}"
            )
            put(KafkaAvroSerializerConfig.BASIC_AUTH_CREDENTIALS_SOURCE, "USER_INFO")
            put(SaslConfigs.SASL_MECHANISM, "PLAIN")
            put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SSL")
            put(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, "jks")
            put(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, "PKCS12")
            put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, securityVars.kafkaTruststorePath)
            put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, securityVars.kafkaCredstorePassword)
            put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, securityVars.kafkaKeystorePath)
            put(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, securityVars.kafkaCredstorePassword)
            put(SslConfigs.SSL_KEY_PASSWORD_CONFIG, securityVars.kafkaCredstorePassword)
            put(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, "")
        }
    }

    fun producerProps(env: Environment): Properties {
        return Properties().apply {
            put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, env.kafkaBrokers)
            put(KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG, env.kafkaSchemaRegistry)
            put(ProducerConfig.CLIENT_ID_CONFIG, "tms-ekstern-varselbestiller")
            put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java)
            put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer::class.java)
            put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 40000)
            put(ProducerConfig.ACKS_CONFIG, "all")
            put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true")
            putAll(credentialPropsAiven(env.securityVars))
        }
    }
}

enum class Eventtype(val eventtype: String) {
    DOKNOTIFIKASJON_STOPP("doknotifikasjon-stopp"), VARSEL(" varsel")
}
