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

package com.github.gekomad.musicstore.test.unit

import java.sql.Timestamp
import java.time.LocalDate
import java.util.UUID

import com.github.gekomad.musicstore.model.sql.Tables
import com.github.gekomad.musicstore.model.sql.Tables.{albums, artists}
import com.github.gekomad.musicstore.model.sql.Tables.{AlbumsType, ArtistsType}
import com.github.gekomad.musicstore.service.{AlbumDAO, ArtistDAO}
import com.github.gekomad.musicstore.utility.MyRandom
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, FunSuite}
import org.slf4j.{Logger, LoggerFactory}
import com.github.gekomad.musicstore.utility._
import com.github.gekomad.musicstore.utility.MyPredef._
import slick.dbio.Effect.Write

import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success, Try}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.util.parsing.json.JSONObject

class DBtest extends FunSuite with BeforeAndAfterAll {

  val log: Logger = LoggerFactory.getLogger(this.getClass)

  import Properties.dc.profile.api._

  val db: Database = Properties.dc.db

  private def dropSchema: Future[Unit] = {
    assert(Properties.dc.profile.toString.contains("H2Profile"), "not dropped")
    db.run((albums.schema ++ artists.schema).drop)
  }

  override def beforeAll(): Unit = {

    val ll2 = Tables.createSchema
    val res = Await.result(ll2, Duration.Inf)
    assert(res.isSuccess)

  }

  override def afterAll(): Unit = {

  }

  test("no transactionally test insert only artist") {

    val id = MyRandom.getRandomUUID

    def write1 = ArtistDAO.upsert(ArtistsType.getRandom.copy(id = id.toString))

    def write2 = AlbumDAO.upsert(AlbumsType.getRandom.copy(id = id.toString))

    val tot = for {
      _ <- db.run(write1.andThen(write2).asTry)
      ar <- ArtistDAO.load(id.toString)
      al <- AlbumDAO.load(id.toString)
    } yield (ar, al)
    val (ar, al) = Await.result(tot, Duration.Inf)
    assert(ar.isDefined)
    assert(al.isEmpty)

  }

  test("no transactionally test doesn't insert neither artist album") {

    val id = MyRandom.getRandomUUID
    val wrongId = MyRandom.getRandomUUID

    def write1 = ArtistDAO.upsert(ArtistsType.getRandom.copy(id = id.toString))

    def write2 = AlbumDAO.upsert(AlbumsType.getRandom.copy(id = id.toString, artistId = wrongId.toString))

    val tot = for {
      _ <- db.run(write1.andThen(write2).transactionally.asTry)
      ar <- ArtistDAO.load(id.toString)
      al <- AlbumDAO.load(id.toString)
    } yield (ar, al)

    val (ar, al) = Await.result(tot, Duration.Inf)
    assert(ar.isEmpty)
    assert(al.isEmpty)
  }

  test("transactional test OK") {

    val id = MyRandom.getRandomUUID

    def write1 = ArtistDAO.upsert(ArtistsType.getRandom.copy(id = id.toString))

    def write2 = AlbumDAO.upsert(AlbumsType.getRandom.copy(id = id.toString, artistId = id.toString))

    val tot = for {
      _ <- db.run(write1.andThen(write2).transactionally.asTry)
      ar <- ArtistDAO.load(id.toString)
      al <- AlbumDAO.load(id.toString)
    } yield (ar, al)
    val (ar, al) = Await.result(tot, Duration.Inf)
    assert(ar.isDefined)
    assert(al.isDefined)
  }


}