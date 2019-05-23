package jbok.core.peer

import java.net.InetSocketAddress

import cats.effect._
import cats.effect.concurrent.Ref
import cats.implicits._
import fs2.io.tcp.Socket
import javax.net.ssl.SSLContext
import jbok.codec.rlp.implicits._
import jbok.core.config.CoreConfig
import jbok.core.ledger.History
import jbok.core.messages.Status
import jbok.core.queue.Queue
import jbok.network.{Message, Request}
import jbok.network.tcp.implicits._

abstract class BaseManager[F[_]](config: CoreConfig, history: History[F], ssl: Option[SSLContext])(implicit F: Concurrent[F]) {
  def inbound: Queue[F, Peer[F], Message[F]]

  val connected: Ref[F, Map[PeerUri, (Peer[F], Socket[F])]] = Ref.unsafe(Map.empty)

  def close(uri: PeerUri): F[Unit] =
    connected.get.map(_.get(uri)).flatMap {
      case Some((_, socket)) => socket.endOfOutput >> socket.close
      case _                 => F.unit
    }

  val localStatus: F[Status] =
    for {
      genesis <- history.genesisHeader
      number  <- history.getBestBlockNumber
      td      <- history.getTotalDifficultyByNumber(number).map(_.getOrElse(BigInt(0)))
    } yield Status(history.chainId, genesis.hash, number, td, config.service.uri)

  def handshake(socket: Socket[F]): F[Peer[F]] =
    for {
      localStatus <- localStatus
      request = Request.binary[F, Status](Status.name, localStatus.asValidBytes)
      _            <- socket.writeMessage(request)
      remoteStatus <- socket.readMessage.flatMap(_.as[Status])
      remote       <- socket.remoteAddress.map(_.asInstanceOf[InetSocketAddress])
      peer <- if (!localStatus.isCompatible(remoteStatus)) {
        F.raiseError(new Exception(s"incompatible peer: ${remoteStatus}"))
      } else {
        Peer[F](PeerUri.fromTcpAddr(remote), remoteStatus)
      }
    } yield peer
}