package podpodge.controllers

import akka.http.scaladsl.model.{ HttpEntity, MediaType, MediaTypes, StatusCodes }
import akka.http.scaladsl.server.directives.FileAndResourceDirectives.ResourceFile
import akka.stream.scaladsl.{ FileIO, StreamConverters }
import podpodge.Config
import podpodge.db.dao.EpisodeDao
import podpodge.http.HttpError
import podpodge.types.{ EpisodeId, _ }
import zio.{ Task, UIO, ZIO }

object EpisodeController {

  def getEpisodeFile(id: EpisodeId.Type): Task[HttpEntity.Default] =
    for {
      episode <- EpisodeDao.get(id).someOrFail(HttpError(StatusCodes.NotFound))
      file    <-
        UIO(
          Config.audioPath.resolve(episode.podcastId.unwrap.toString).resolve(s"${episode.externalSource}.mp3").toFile
        )
          .filterOrFail(_.exists)(HttpError(StatusCodes.NotFound))
    } yield HttpEntity.Default(
      MediaType.audio("mpeg", MediaType.NotCompressible, "mp3"),
      file.length,
      FileIO.fromPath(file.toPath)
    )

  def getThumbnail(id: EpisodeId.Type): Task[HttpEntity.Default] =
    for {
      episode <- EpisodeDao.get(id).someOrFail(HttpError(StatusCodes.NotFound))
      result  <- episode.imagePath.map(_.toFile) match {
                   case Some(imageFile) if imageFile.exists() =>
                     UIO(HttpEntity.Default(MediaTypes.`image/png`, imageFile.length, FileIO.fromPath(imageFile.toPath)))

                   case _ =>
                     Option(getClass.getResource("/question.png")).flatMap(ResourceFile.apply) match {
                       case None           => ZIO.fail(HttpError(StatusCodes.InternalServerError))
                       case Some(resource) =>
                         UIO(
                           HttpEntity.Default(
                             MediaTypes.`image/jpeg`,
                             resource.length,
                             StreamConverters.fromInputStream(() => resource.url.openStream())
                           )
                         )
                     }
                 }
    } yield result

}
