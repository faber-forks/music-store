/*
    Copyright (C) Giuseppe Cannella

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package com.github.gekomad.musicstore.service.kafka

import cakesolutions.kafka.KafkaConsumer
import cats.effect.IO
import com.github.gekomad.musicstore.service.ProductService
import com.github.gekomad.musicstore.service.kafka.model.Avro.AvroProduct
import com.github.gekomad.musicstore.utility.MyPredef._
import scala.concurrent.ExecutionContext.Implicits.global
import com.github.gekomad.musicstore.utility.Properties.Kafka
import com.typesafe.config.ConfigFactory
import org.apache.kafka.clients.consumer.{ConsumerConfig, ConsumerRecords, OffsetResetStrategy}
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.{Logger, LoggerFactory}
import scala.collection.JavaConverters.mapAsJavaMap
import scala.concurrent.Future
import scala.concurrent.duration._

object Consumers {

  val log: Logger = LoggerFactory.getLogger(this.getClass)

  trait Consumer {
    def consume(): Unit
  }

  trait KafkaConsumerConf extends Consumer {
    type A
    type B
    val conf: KafkaConsumer.Conf[A, B]
    val topic: List[String]
  }

  final case class KafkaConsumer1(kafka: Kafka) extends KafkaConsumerConf {

    type A = String
    type B = Array[Byte]

    val conf = KafkaConsumer.Conf(
      keyDeserializer = new StringDeserializer,
      valueDeserializer = new org.apache.kafka.common.serialization.ByteArrayDeserializer,
      bootstrapServers = kafka.bootstrapServers,
      groupId = kafka.groupId,
      enableAutoCommit = true,
      autoOffsetReset = OffsetResetStrategy.EARLIEST
    )

    private val kafkaConsumer = KafkaConsumer(conf)

    val (topic, _) = kafka.artistTopic.unzip

    def consume(): Unit = {

      import scala.collection.JavaConverters._

      def consumeMessages(records: ConsumerRecords[String, Array[Byte]]) = {

        val read = records.asScala.map { iterator =>
          log.debug(s"Getting message from topics ${List(topic)} .............")
          val message = iterator.value
          deserializeAvro[AvroProduct](message)
        }

        val v = read.flatten

        ProductService.storeList(v.toList)
      }

      kafkaConsumer.subscribe(topic.asJava)

      while (true) {
        val records = kafkaConsumer.poll(100.seconds.toMillis)
        consumeMessages(records).map {
          _.foreach {
            _.foreach {
              _.unsafeToFuture()
            }
          }
        }.recover { case f =>
          log.error("err", f)
        }
      }

      kafkaConsumer.close()
    }

  }

  /////////////////////////////////////////

  final case class KafkaConsumerDlq(kafka: Kafka) extends KafkaConsumerConf {

    type A = String
    type B = Array[Byte]

    val conf = KafkaConsumer.Conf(ConfigFactory.parseMap(mapAsJavaMap(Map[String, AnyRef](
      ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG -> kafka.bootstrapServers,
      ConsumerConfig.GROUP_ID_CONFIG -> kafka.groupId))),
      keyDeserializer = new StringDeserializer,
      valueDeserializer = new org.apache.kafka.common.serialization.ByteArrayDeserializer
    )

    private val kafkaConsumer = KafkaConsumer(conf)

    val topic = List(kafka.dlqTopic._1)

    def consume(): Unit = {

      import scala.collection.JavaConverters._

      kafkaConsumer.subscribe(topic.asJava)

      while (true) {

        val records = kafkaConsumer.poll(10.seconds.toMillis)

        val read = records.asScala.map { iterator =>
          log.debug(s"Getting message from topics ${List(topic)} .............")
          val message = iterator.value
          deserializeAvro[AvroProduct](message)
        }

        val v = read.flatten
        v.foreach(a => log.debug(s"read from dlq: ${a.theType} ${a.payload}"))
      }

      kafkaConsumer.close()
    }
  }


}
